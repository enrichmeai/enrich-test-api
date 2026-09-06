/*
 * Copyright 2025
 * Apache License, Version 2.0
 */
package org.deveasy.test.cloud.aws;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.deveasy.test.core.cloud.CloudMode;
import org.deveasy.test.core.cloud.CloudProvider;
import org.deveasy.test.core.cloud.TestCloudConfig;
import org.deveasy.test.core.cloud.capability.BlobStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AwsBlobStorageIT {

  private AwsCloudAdapter adapter;
  private BlobStorage storage;
  private String bucket;

  @BeforeEach
  void setUp() {
    TestCloudConfig config =
        TestCloudConfig.builder()
            .provider(CloudProvider.AWS)
            .mode(CloudMode.EMULATOR)
            .regionOrLocation("eu-west-1")
            .build();

    adapter = new AwsCloudAdapter();
    adapter.initialize(config);
    storage = adapter.blobStorage();

    bucket = "dev-easy-test-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    storage.ensureBucket(bucket);
  }

  @AfterEach
  void tearDown() {
    try {
      // Best-effort cleanup
      List<String> keys = storage.listKeys(bucket, "");
      for (String k : keys) {
        storage.deleteObject(bucket, k);
      }
      storage.deleteBucket(bucket);
    } catch (Throwable ignored) {
    }
  }

  @Test
  @DisplayName("S3 minimal flow: ensure -> put JSON -> get -> list -> exists")
  void s3MinimalFlow() {
    String key = "k1.json";
    String json = "{\"hello\":\"world\"}";

    storage.putObject(bucket, key, json.getBytes(StandardCharsets.UTF_8), "application/json");

    byte[] bytes = storage.getObject(bucket, key);
    Assertions.assertNotNull(bytes, "object should be retrievable");
    String fetched = new String(bytes, StandardCharsets.UTF_8);
    Assertions.assertTrue(fetched.contains("\"hello\":\"world\""));

    List<String> keys = storage.listKeys(bucket, "k1");
    Assertions.assertTrue(keys.contains(key), "listKeys should contain the uploaded key");

    Assertions.assertTrue(
        storage.exists(bucket, key), "exists should return true for stored object");
  }
}
