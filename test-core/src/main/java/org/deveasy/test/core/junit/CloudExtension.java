/*
 * Copyright 2025
 * Apache License, Version 2.0
 */
package org.deveasy.test.core.junit;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.deveasy.test.core.cloud.TestCloudConfig;
import org.deveasy.test.core.cloud.capability.BlobStorage;
import org.deveasy.test.core.cloud.capability.NoSqlTable;
import org.deveasy.test.core.cloud.capability.PubSub;
import org.deveasy.test.core.cloud.capability.Queue;
import org.deveasy.test.core.cloud.spi.CloudAdapter;
import org.deveasy.test.core.cloud.spi.CloudAdapters;
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
 */
public final class CloudExtension
    implements BeforeAllCallback, AfterAllCallback, ParameterResolver {

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
    // Tracking sets for cleanup
    store.put(KEY_TRACK_BUCKETS, new HashSet<String>());
    store.put(KEY_TRACK_QUEUES, new HashSet<String>());
    store.put(KEY_TRACK_TOPICS, new HashSet<String>());
    store.put(KEY_TRACK_TABLES, new HashSet<String>());

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
      // not tracking currently; placeholder for future
      store.put(KEY_PUBSUB, pubsub);
    }
    NoSqlTable nosql = adapter.noSqlTable();
    if (nosql != null) {
      // not tracking currently; placeholder for future
      store.put(KEY_NOSQL, nosql);
    }
  }

  @SuppressWarnings("unchecked")
  private static Set<String> tracked(ExtensionContext.Store store, String key) {
    return (Set<String>) store.get(key);
  }

  @Override
  public void afterAll(ExtensionContext context) {
    ExtensionContext.Store store = store(context);

    // Best-effort cleanup using tracked resource names
    BlobStorage storage = (BlobStorage) store.get(KEY_STORAGE, BlobStorage.class);
    if (storage != null) {
      for (String b : new ArrayList<>(tracked(store, KEY_TRACK_BUCKETS))) {
        try {
          storage.deleteBucket(b);
        } catch (Throwable ignore) {
        }
      }
    }
    Queue queue = (Queue) store.get(KEY_QUEUE, Queue.class);
    if (queue != null) {
      for (String q : new ArrayList<>(tracked(store, KEY_TRACK_QUEUES))) {
        try {
          queue.deleteQueue(q);
        } catch (Throwable ignore) {
        }
      }
    }
    // PubSub/NoSql cleanup could be added when capabilities stabilize.
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
}
