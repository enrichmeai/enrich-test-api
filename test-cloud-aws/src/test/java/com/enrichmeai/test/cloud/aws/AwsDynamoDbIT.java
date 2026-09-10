/*
 * Copyright 2025
 * Apache License, Version 2.0
 */
package com.enrichmeai.test.cloud.aws;

import com.enrichmeai.test.core.cloud.CloudMode;
import com.enrichmeai.test.core.cloud.CloudProvider;
import com.enrichmeai.test.core.cloud.TestCloudConfig;
import com.enrichmeai.test.core.cloud.capability.NoSqlTable;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AwsDynamoDBIT {

  private AwsCloudAdapter adapter;
  private NoSqlTable table;
  private String tableName;

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
    table = adapter.noSqlTable();

    tableName =
        "dev-easy-test-ddb-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    table.ensureTable(tableName, "id", "timestamp");
  }

  @AfterEach
  void tearDown() {
    try {
      table.deleteTable(tableName);
    } catch (Throwable ignored) {
    }
  }

  @Test
  @DisplayName("DynamoDB: put item and get by (id,timestamp)")
  void putAndGetItem() {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("id", "u1");
    item.put("timestamp", "2025-01-01T00:00:00Z");
    item.put("name", "Alice");
    item.put("age", 30);
    table.putItem(tableName, item);

    Map<String, Object> got = table.getItem(tableName, "u1", "2025-01-01T00:00:00Z");
    Assertions.assertNotNull(got, "item should be retrievable by full key");
    Assertions.assertEquals("Alice", got.get("name"));
    Object age = got.get("age");
    Assertions.assertTrue(age instanceof Number, "age should be a number type");
    Assertions.assertEquals(30, ((Number) age).intValue());
  }

  @Test
  @DisplayName("DynamoDB: scan table returns all items")
  void scanTable() {
    // put multiple items
    for (int i = 1; i <= 3; i++) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", "scan-" + i);
      item.put("timestamp", "2025-01-01T00:00:0" + i + "Z");
      item.put("value", i);
      table.putItem(tableName, item);
    }

    List<Map<String, Object>> all = table.scan(tableName);
    Assertions.assertNotNull(all, "scan should not be null");
    Assertions.assertTrue(all.size() >= 3, "scan should return at least the inserted items");
    boolean containsAny = all.stream().anyMatch(m -> Objects.equals(m.get("id"), "scan-1"));
    Assertions.assertTrue(containsAny, "scan result should contain one of the inserted items");
  }

  @Test
  @DisplayName("DynamoDB: query by partition key returns matching items")
  void queryByPartitionKey() {
    // two items with same id (partition key), different timestamps (sort key)
    Map<String, Object> it1 = new LinkedHashMap<>();
    it1.put("id", "q1");
    it1.put("timestamp", "2025-01-01T10:00:00Z");
    it1.put("payload", "first");
    table.putItem(tableName, it1);

    Map<String, Object> it2 = new LinkedHashMap<>();
    it2.put("id", "q1");
    it2.put("timestamp", "2025-01-01T11:00:00Z");
    it2.put("payload", "second");
    table.putItem(tableName, it2);

    List<Map<String, Object>> byPk = table.query(tableName, "q1");
    Assertions.assertNotNull(byPk, "query should not be null");
    Assertions.assertEquals(2, byPk.size(), "query by partition key should return both items");
    Set<Object> payloads = new HashSet<>();
    for (Map<String, Object> m : byPk) payloads.add(m.get("payload"));
    Assertions.assertTrue(
        payloads.contains("first") && payloads.contains("second"),
        "queried items should contain both payloads");
  }
}
