/*
 * Copyright 2025
 * Apache License, Version 2.0
 */
package com.enrichmeai.test.core.junit;

import com.enrichmeai.test.core.cloud.TestCloudConfig;
import com.enrichmeai.test.core.cloud.capability.BlobStorage;
import com.enrichmeai.test.core.cloud.capability.NoSqlTable;
import com.enrichmeai.test.core.cloud.capability.PubSub;
import com.enrichmeai.test.core.cloud.capability.Queue;
import com.enrichmeai.test.core.cloud.spi.CloudAdapter;
import com.enrichmeai.test.core.cloud.spi.CloudAdapters;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * JUnit 5 extension that provisions a CloudAdapter and injects cloud capabilities into test method
 * parameters.
 *
 * <p>Every capability handed to a test is wrapped so that the buckets, queues, topics and tables
 * the test creates through it are released in {@link #afterAll}. The emulator is shared for the
 * whole JVM, so anything not released here is still there for the next test class.
 */
public final class CloudExtension
    implements BeforeAllCallback, AfterAllCallback, ParameterResolver {

  private static final Logger LOG = Logger.getLogger(CloudExtension.class.getName());

  private static final ExtensionContext.Namespace NS =
      ExtensionContext.Namespace.create(CloudExtension.class);

  private static final String KEY_ADAPTER = "adapter";
  private static final String KEY_STORAGE = "storage";
  private static final String KEY_QUEUE = "queue";
  private static final String KEY_PUBSUB = "pubsub";
  private static final String KEY_NOSQL = "nosql";

  private static final String KEY_TRACK_BUCKETS = "track.buckets";
  private static final String KEY_TRACK_QUEUES = "track.queues";
  private static final String KEY_TRACK_TOPICS = "track.topics";
  private static final String KEY_TRACK_TABLES = "track.tables";

  @Override
  public void beforeAll(ExtensionContext context) {
    WithCloud cfg = findWithCloud(context);
    TestCloudConfig config =
        TestCloudConfig.builder()
            .provider(cfg.provider())
            .mode(cfg.mode())
            .regionOrLocation(cfg.region())
            .build();

    CloudAdapter adapter = CloudAdapters.get(cfg.provider(), config);

    ExtensionContext.Store store = store(context);
    store.put(KEY_ADAPTER, adapter);
    // Tracking sets for cleanup; insertion-ordered so resources are released in creation order
    store.put(KEY_TRACK_BUCKETS, new LinkedHashSet<String>());
    store.put(KEY_TRACK_QUEUES, new LinkedHashSet<String>());
    store.put(KEY_TRACK_TOPICS, new LinkedHashSet<String>());
    store.put(KEY_TRACK_TABLES, new LinkedHashSet<String>());

    // Wrap capabilities with trackers if available
    BlobStorage storage = adapter.blobStorage();
    if (storage != null) {
      storage = new TrackingBlobStorage(storage, tracked(store, KEY_TRACK_BUCKETS));
      store.put(KEY_STORAGE, storage);
    }
    Queue queue = adapter.queue();
    if (queue != null) {
      queue = new TrackingQueue(queue, tracked(store, KEY_TRACK_QUEUES));
      store.put(KEY_QUEUE, queue);
    }
    PubSub pubsub = adapter.pubSub();
    if (pubsub != null) {
      pubsub = new TrackingPubSub(pubsub, tracked(store, KEY_TRACK_TOPICS));
      store.put(KEY_PUBSUB, pubsub);
    }
    NoSqlTable nosql = adapter.noSqlTable();
    if (nosql != null) {
      nosql = new TrackingNoSqlTable(nosql, tracked(store, KEY_TRACK_TABLES));
      store.put(KEY_NOSQL, nosql);
    }
  }

  @SuppressWarnings("unchecked")
  private static Set<String> tracked(ExtensionContext.Store store, String key) {
    return (Set<String>) store.get(key);
  }

  /**
   * Releases every resource the test class created through an injected capability.
   *
   * <p>One failure does not stop the rest: each release that throws is logged at {@link
   * Level#WARNING} with the resource's kind, name and cause, and the remaining resources are still
   * attempted. Nothing is rethrown, so a teardown failure does not change the test class's result.
   */
  @Override
  public void afterAll(ExtensionContext context) {
    ExtensionContext.Store store = store(context);
    release(
        store,
        KEY_STORAGE,
        BlobStorage.class,
        KEY_TRACK_BUCKETS,
        "bucket",
        BlobStorage::deleteBucket);
    release(store, KEY_QUEUE, Queue.class, KEY_TRACK_QUEUES, "queue", Queue::deleteQueue);
    release(store, KEY_PUBSUB, PubSub.class, KEY_TRACK_TOPICS, "topic", PubSub::deleteTopic);
    release(store, KEY_NOSQL, NoSqlTable.class, KEY_TRACK_TABLES, "table", NoSqlTable::deleteTable);
  }

  private static <C> void release(
      ExtensionContext.Store store,
      String capabilityKey,
      Class<C> capabilityType,
      String trackingKey,
      String kind,
      BiConsumer<C, String> delete) {
    C capability = store.get(capabilityKey, capabilityType);
    if (capability == null) {
      return;
    }
    // Copy first: a successful delete through the wrapper removes the name from the tracking set.
    for (String name : new ArrayList<>(tracked(store, trackingKey))) {
      try {
        delete.accept(capability, name);
      } catch (RuntimeException e) {
        // RuntimeException is the failure channel every capability contract declares. An Error is
        // not a failed teardown and is left to propagate.
        LOG.log(Level.WARNING, "Cleanup failed for " + kind + " '" + name + "': " + e, e);
      }
    }
  }

  @Override
  public boolean supportsParameter(
      ParameterContext parameterContext, ExtensionContext extensionContext)
      throws ParameterResolutionException {
    Class<?> type = parameterContext.getParameter().getType();
    return BlobStorage.class.isAssignableFrom(type)
        || Queue.class.isAssignableFrom(type)
        || PubSub.class.isAssignableFrom(type)
        || NoSqlTable.class.isAssignableFrom(type);
  }

  @Override
  public Object resolveParameter(ParameterContext parameterContext, ExtensionContext context)
      throws ParameterResolutionException {
    Class<?> type = parameterContext.getParameter().getType();
    ExtensionContext.Store store = store(context);
    if (BlobStorage.class.isAssignableFrom(type)) {
      Object v = store.get(KEY_STORAGE);
      if (v == null)
        throw new ParameterResolutionException("BlobStorage capability not available for provider");
      return v;
    }
    if (Queue.class.isAssignableFrom(type)) {
      Object v = store.get(KEY_QUEUE);
      if (v == null)
        throw new ParameterResolutionException("Queue capability not available for provider");
      return v;
    }
    if (PubSub.class.isAssignableFrom(type)) {
      Object v = store.get(KEY_PUBSUB);
      if (v == null)
        throw new ParameterResolutionException("PubSub capability not available for provider");
      return v;
    }
    if (NoSqlTable.class.isAssignableFrom(type)) {
      Object v = store.get(KEY_NOSQL);
      if (v == null)
        throw new ParameterResolutionException("NoSqlTable capability not available for provider");
      return v;
    }
    throw new ParameterResolutionException("Unsupported parameter type: " + type);
  }

  private static WithCloud findWithCloud(ExtensionContext context) {
    Class<?> testClass = context.getRequiredTestClass();
    WithCloud cfg = testClass.getAnnotation(WithCloud.class);
    if (cfg == null) {
      throw new ExtensionConfigurationException(
          "@WithCloud must be present on test class to use CloudExtension");
    }
    return cfg;
  }

  private static ExtensionContext.Store store(ExtensionContext context) {
    return context.getStore(NS);
  }

  // Tracking wrappers
  private static final class TrackingBlobStorage implements BlobStorage {
    private final BlobStorage delegate;
    private final Set<String> buckets;

    TrackingBlobStorage(BlobStorage delegate, Set<String> buckets) {
      this.delegate = delegate;
      this.buckets = buckets;
    }

    @Override
    public void ensureBucket(String name) {
      buckets.add(name);
      delegate.ensureBucket(name);
    }

    @Override
    public void deleteBucket(String name) {
      delegate.deleteBucket(name);
      buckets.remove(name);
    }

    @Override
    public void putObject(String bucket, String key, byte[] data, String contentType) {
      delegate.putObject(bucket, key, data, contentType);
    }

    @Override
    public void putObject(String bucket, String key, InputStream data, String contentType) {
      delegate.putObject(bucket, key, data, contentType);
    }

    @Override
    public byte[] getObject(String bucket, String key) {
      return delegate.getObject(bucket, key);
    }

    @Override
    public void deleteObject(String bucket, String key) {
      delegate.deleteObject(bucket, key);
    }

    @Override
    public List<String> listKeys(String bucket, String prefix) {
      return delegate.listKeys(bucket, prefix);
    }

    @Override
    public boolean exists(String bucket, String key) {
      return delegate.exists(bucket, key);
    }
  }

  private static final class TrackingQueue implements Queue {
    private final Queue delegate;
    private final Set<String> queues;

    TrackingQueue(Queue delegate, Set<String> queues) {
      this.delegate = delegate;
      this.queues = queues;
    }

    @Override
    public void ensureQueue(String name) {
      queues.add(name);
      delegate.ensureQueue(name);
    }

    @Override
    public void deleteQueue(String name) {
      delegate.deleteQueue(name);
      queues.remove(name);
    }

    @Override
    public void send(String queue, String body) {
      delegate.send(queue, body);
    }

    @Override
    public Optional<String> receive(String queue) {
      return delegate.receive(queue);
    }

    @Override
    public Optional<String> receive(String queue, java.time.Duration timeout) {
      return delegate.receive(queue, timeout);
    }
  }

  /**
   * Tracks topics. A name is recorded only once the delegate has succeeded: a topic the adapter
   * refused to create is not this class's to delete.
   */
  private static final class TrackingPubSub implements PubSub {
    private final PubSub delegate;
    private final Set<String> topics;

    TrackingPubSub(PubSub delegate, Set<String> topics) {
      this.delegate = delegate;
      this.topics = topics;
    }

    @Override
    public void ensureTopic(String name) {
      delegate.ensureTopic(name);
      topics.add(name);
    }

    @Override
    public void deleteTopic(String name) {
      delegate.deleteTopic(name);
      topics.remove(name);
    }

    @Override
    public void ensureSubscription(String topic, String subscription) {
      delegate.ensureSubscription(topic, subscription);
    }

    @Override
    public void publish(String topic, String body) {
      delegate.publish(topic, body);
    }

    @Override
    public Optional<String> receive(String subscription) {
      return delegate.receive(subscription);
    }

    @Override
    public Optional<String> receive(String subscription, Duration timeout) {
      return delegate.receive(subscription, timeout);
    }
  }

  /**
   * Tracks tables. A name is recorded only once the delegate has succeeded: a table that {@code
   * ensureTable} rejected because its schema did not match belongs to someone else, and must not be
   * deleted at the end of this class.
   */
  private static final class TrackingNoSqlTable implements NoSqlTable {
    private final NoSqlTable delegate;
    private final Set<String> tables;

    TrackingNoSqlTable(NoSqlTable delegate, Set<String> tables) {
      this.delegate = delegate;
      this.tables = tables;
    }

    @Override
    public void ensureTable(String tableName, String partitionKey) {
      delegate.ensureTable(tableName, partitionKey);
      tables.add(tableName);
    }

    @Override
    public void ensureTable(String tableName, String partitionKey, String sortKey) {
      delegate.ensureTable(tableName, partitionKey, sortKey);
      tables.add(tableName);
    }

    @Override
    public void deleteTable(String tableName) {
      delegate.deleteTable(tableName);
      tables.remove(tableName);
    }

    @Override
    public void putItem(String tableName, Map<String, Object> item) {
      delegate.putItem(tableName, item);
    }

    @Override
    public Map<String, Object> getItem(String tableName, String partitionKeyValue) {
      return delegate.getItem(tableName, partitionKeyValue);
    }

    @Override
    public Map<String, Object> getItem(
        String tableName, String partitionKeyValue, String sortKeyValue) {
      return delegate.getItem(tableName, partitionKeyValue, sortKeyValue);
    }

    @Override
    public void deleteItem(String tableName, String partitionKeyValue) {
      delegate.deleteItem(tableName, partitionKeyValue);
    }

    @Override
    public void deleteItem(String tableName, String partitionKeyValue, String sortKeyValue) {
      delegate.deleteItem(tableName, partitionKeyValue, sortKeyValue);
    }

    @Override
    public List<Map<String, Object>> scan(String tableName) {
      return delegate.scan(tableName);
    }

    @Override
    public List<Map<String, Object>> query(String tableName, String partitionKeyValue) {
      return delegate.query(tableName, partitionKeyValue);
    }
  }
}
