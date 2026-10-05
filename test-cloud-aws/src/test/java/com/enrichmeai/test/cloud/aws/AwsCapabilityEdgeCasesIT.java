/*
 * Copyright 2025
 * Apache License, Version 2.0
 */
package com.enrichmeai.test.cloud.aws;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.enrichmeai.test.core.cloud.CloudMode;
import com.enrichmeai.test.core.cloud.CloudProvider;
import com.enrichmeai.test.core.cloud.TestCloudConfig;
import com.enrichmeai.test.core.cloud.capability.BlobStorage;
import com.enrichmeai.test.core.cloud.capability.PubSub;
import com.enrichmeai.test.core.cloud.capability.Queue;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Story 1.5: the paths of {@code AwsBlobStorage}, {@code AwsPubSub} and {@code AwsQueue} that the
 * happy-path ITs do not reach: regions with and without a location constraint, resources that are
 * not there, a stream that fails, and the empty receive.
 */
class AwsCapabilityEdgeCasesIT {

  private static TestCloudConfig config(String region) {
    return TestCloudConfig.builder()
        .provider(CloudProvider.AWS)
        .mode(CloudMode.EMULATOR)
        .regionOrLocation(region)
        .build();
  }

  private static String name(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
  }

  private static byte[] utf8(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }

  // ---- BlobStorage ------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "A bucket is created with a location constraint outside us-east-1, without one in it")
  void bucketsInEachKindOfRegion() {
    for (String region : new String[] {"eu-west-1", "us-east-1", null}) {
      BlobStorage storage = new AwsBlobStorage(config(region));
      String bucket = name("b");
      storage.ensureBucket(bucket);
      storage.ensureBucket(bucket); // already there: the head succeeds and nothing is created
      storage.putObject(bucket, "k", utf8("v"), "text/plain");
      assertTrue(storage.exists(bucket, "k"), "region " + region);
      storage.deleteBucket(bucket);
    }
  }

  @Test
  @DisplayName("Reads and deletes of what is not there are quiet, and deleteBucket empties first")
  void blobStorageAbsences() {
    AwsBlobStorage storage = new AwsBlobStorage(config("eu-west-1"));
    String bucket = name("b");
    String missingBucket = name("never");
    storage.ensureBucket(bucket);

    assertNull(storage.getObject(bucket, "absent"));
    assertNull(storage.getString(bucket, "absent"));
    assertFalse(storage.exists(bucket, "absent"));
    assertDoesNotThrow(() -> storage.deleteObject(bucket, "absent"));
    assertNull(storage.getObject(missingBucket, "k"));
    assertFalse(storage.exists(missingBucket, "k"));
    assertDoesNotThrow(() -> storage.deleteBucket(missingBucket));

    storage.putJson(bucket, "docs/a.json", "{\"a\":1}");
    storage.putObject(bucket, "docs/b.txt", new ByteArrayInputStream(utf8("b")), "text/plain");
    storage.putObject(bucket, "other/c.txt", utf8("c"), "text/plain");
    assertEquals("{\"a\":1}", storage.getString(bucket, "docs/a.json"));
    assertArrayEquals(utf8("b"), storage.getObject(bucket, "docs/b.txt"));
    assertEquals(List.of("docs/a.json", "docs/b.txt"), storage.listKeys(bucket, "docs/"));
    assertEquals(3, storage.listKeys(bucket, null).size());

    // A bucket with objects in it is emptied, then deleted.
    storage.deleteBucket(bucket);
    assertNull(storage.getObject(bucket, "docs/a.json"));
  }

  @Test
  @DisplayName("A stream that fails to read surfaces as a RuntimeException naming putObject")
  void aFailingStream() {
    BlobStorage storage = new AwsBlobStorage(config("eu-west-1"));
    String bucket = name("b");
    storage.ensureBucket(bucket);
    InputStream broken =
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException("disk gone");
          }
        };
    try {
      RuntimeException e =
          assertThrows(
              RuntimeException.class,
              () -> storage.putObject(bucket, "k", broken, "application/octet-stream"));
      assertTrue(e.getMessage().contains("putObject"), e.getMessage());
      assertInstanceOf(IOException.class, e.getCause());
    } finally {
      storage.deleteBucket(bucket);
    }
  }

  // ---- PubSub -----------------------------------------------------------------------------------

  @Test
  @DisplayName("Topics: ensure twice, publish to one not yet created, delete what is not there")
  void topicLifecycle() {
    TestCloudConfig cfg = config("eu-west-1");
    PubSub pubsub = new AwsPubSub(cfg);
    Queue queue = new AwsQueue(cfg);
    String topic = name("t");
    String later = name("t-later");
    String sub = name("q");

    assertDoesNotThrow(() -> pubsub.deleteTopic(name("never")));

    pubsub.ensureTopic(topic);
    pubsub.ensureTopic(topic); // found by name: not created twice
    queue.ensureQueue(sub);
    try {
      pubsub.ensureSubscription(topic, sub);
      pubsub.publish(topic, "hello");
      assertEquals(Optional.of("hello"), pubsub.receive(sub, Duration.ofSeconds(10)));
      assertEquals(Optional.empty(), pubsub.receive(sub));

      // publish creates a topic that does not exist yet
      pubsub.publish(later, "nobody listens");
    } finally {
      queue.deleteQueue(sub);
      pubsub.deleteTopic(topic);
      pubsub.deleteTopic(later);
    }
  }

  // ---- Queue ------------------------------------------------------------------------------------

  @Test
  @DisplayName("Queues: ensure twice, an empty receive, and deleting one that is not there")
  void queueAbsences() {
    Queue queue = new AwsQueue(config("eu-west-1"));
    String q = name("q");
    assertDoesNotThrow(() -> queue.deleteQueue(name("never")));
    queue.ensureQueue(q);
    queue.ensureQueue(q);
    try {
      assertEquals(Optional.empty(), queue.receive(q, Duration.ofSeconds(1)));
    } finally {
      queue.deleteQueue(q);
    }
  }
}
