/*
 * Copyright 2025
 * Apache License, Version 2.0
 */
package com.enrichmeai.test.cloud.aws;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.enrichmeai.test.cloud.aws.internal.AwsClients;
import com.enrichmeai.test.core.cloud.CloudMode;
import com.enrichmeai.test.core.cloud.CloudProvider;
import com.enrichmeai.test.core.cloud.TestCloudConfig;
import com.enrichmeai.test.core.cloud.capability.NoSqlTable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

/**
 * Story 1.2: what {@code AwsDynamoDB} does when a table or an item is not there, with and without a
 * sort key, across pages, and for every value type it marshals.
 *
 * <p>The class does not propagate provider errors; it returns a neutral value ({@code null}, an
 * empty list, or nothing). These tests pin that behaviour as it is. Whether a failed scan should be
 * distinguishable from an empty table is a separate question, raised on the PR.
 */
class AwsDynamoDbErrorPathsIT {

  private TestCloudConfig config;
  private NoSqlTable table;
  private final List<String> created = new ArrayList<>();

  @BeforeEach
  void setUp() {
    config =
        TestCloudConfig.builder()
            .provider(CloudProvider.AWS)
            .mode(CloudMode.EMULATOR)
            .regionOrLocation("eu-west-1")
            .build();
    AwsCloudAdapter adapter = new AwsCloudAdapter();
    adapter.initialize(config);
    table = adapter.noSqlTable();
  }

  @AfterEach
  void tearDown() {
    for (String name : created) {
      table.deleteTable(name);
    }
  }

  private String newName(String prefix) {
    String name = prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    created.add(name);
    return name;
  }

  private static Map<String, Object> item(Object... kv) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
    return m;
  }

  @Test
  @DisplayName("AC-1: reads against a table that does not exist return null or empty")
  void readsAgainstAMissingTable() {
    String missing = "never-created-" + UUID.randomUUID();

    assertNull(table.getItem(missing, "a"));
    assertNull(table.getItem(missing, "a", "b"));
    assertEquals(List.of(), table.query(missing, "a"));
    assertEquals(List.of(), table.scan(missing));
  }

  @Test
  @DisplayName("AC-2: an absent key in an existing table returns null")
  void absentKeyInAnExistingTable() {
    String name = newName("ddb-absent");
    table.ensureTable(name, "id");
    table.putItem(name, item("id", "present"));

    assertNotNull(table.getItem(name, "present"), "the table exists and is readable");
    assertNull(table.getItem(name, "absent"));
  }

  @Test
  @DisplayName("AC-3: a partition-key-only table, and a composite-key table, both ways")
  void sortKeysBothWays() {
    String pkOnly = newName("ddb-pk");
    table.ensureTable(pkOnly, "id", " "); // a blank sort key means none
    table.putItem(pkOnly, item("id", "k1", "v", "one"));
    assertEquals("one", table.getItem(pkOnly, "k1").get("v"));
    // The sort-key overloads have no sort key to use on this table.
    assertNull(table.getItem(pkOnly, "k1", "anything"));
    table.deleteItem(pkOnly, "k1", "anything");
    assertNotNull(table.getItem(pkOnly, "k1"), "the sort-key delete did nothing");
    table.deleteItem(pkOnly, "k1");
    assertNull(table.getItem(pkOnly, "k1"));

    String composite = newName("ddb-pksk");
    table.ensureTable(composite, "id", "ts");
    table.putItem(composite, item("id", "k1", "ts", "t1", "v", "one"));
    assertEquals("one", table.getItem(composite, "k1", "t1").get("v"));
    table.deleteItem(composite, "k1", "t1");
    assertNull(table.getItem(composite, "k1", "t1"));
  }

  @Test
  @DisplayName("AC-4: deleting what is not there does not throw")
  void deletesAreForgiving() {
    String missing = "never-created-" + UUID.randomUUID();
    assertDoesNotThrow(() -> table.deleteTable(missing));
    assertDoesNotThrow(() -> table.deleteItem(missing, "a"));
    assertDoesNotThrow(() -> table.deleteItem(missing, "a", "b"));

    String name = newName("ddb-delabsent");
    table.ensureTable(name, "id");
    assertDoesNotThrow(() -> table.deleteItem(name, "absent"));
  }

  @Test
  @DisplayName("AC-5: scan and query on an existing empty table return an empty list")
  void emptyTable() {
    String name = newName("ddb-empty");
    table.ensureTable(name, "id");
    assertEquals(List.of(), table.scan(name));
    assertEquals(List.of(), table.query(name, "nobody"));
  }

  @Test
  @DisplayName("AC-6: scan and query return every item across more than one page")
  void pagination() {
    // DynamoDB returns at most 1 MB per Scan or Query page. Five items of about 300 KB each
    // (the item limit is 400 KB) need at least two pages.
    String name = newName("ddb-pages");
    table.ensureTable(name, "id", "ts");
    String big = "x".repeat(300_000);
    for (int i = 0; i < 5; i++) {
      table.putItem(name, item("id", "same", "ts", "t" + i, "blob", big));
    }

    assertEquals(5, table.scan(name).size());
    assertEquals(5, table.query(name, "same").size());
  }

  @Test
  @DisplayName("AC-7: every value type survives a round trip")
  void valueMarshalling() {
    String name = newName("ddb-values");
    table.ensureTable(name, "id");
    byte[] bytes = {1, 2, 3};
    UUID uuid = UUID.randomUUID();
    Map<String, Object> in = item("id", "v1");
    in.put("str", "s");
    in.put("int", 42);
    in.put("long", 5_000_000_000L);
    in.put("double", 1.5);
    in.put("exp", 1e3);
    in.put("bool", true);
    in.put("bytes", bytes);
    in.put("other", uuid); // not a known type: stored as its toString()
    in.put("absent", null); // a null value is not written
    in.put(
        "map",
        AttributeValue.fromM(Map.of("inner", AttributeValue.fromS("deep")))); // passed through
    in.put("list", AttributeValue.fromL(List.of(AttributeValue.fromS("a"))));
    table.putItem(name, in);

    Map<String, Object> out = table.getItem(name, "v1");
    assertEquals("s", out.get("str"));
    assertEquals(42, out.get("int"));
    assertEquals(5_000_000_000L, out.get("long"));
    assertEquals(1.5, out.get("double"));
    assertTrue(out.get("exp") instanceof Number, String.valueOf(out.get("exp")));
    assertEquals(1000.0, ((Number) out.get("exp")).doubleValue());
    assertEquals(true, out.get("bool"));
    assertArrayEquals(bytes, (byte[]) out.get("bytes"));
    assertEquals(uuid.toString(), out.get("other"));
    assertFalse(out.containsKey("absent"));
    assertEquals(Map.of("inner", "deep"), out.get("map"));
    assertEquals(List.of("a"), out.get("list"));
  }

  @Test
  @DisplayName("A table created outside the adapter is read through its described key schema")
  void keysAreDescribedForATableTheAdapterDidNotCreate() {
    String name = newName("ddb-foreign");
    DynamoDbClient ddb = AwsClients.dynamodb(config);
    ddb.createTable(
        CreateTableRequest.builder()
            .tableName(name)
            .billingMode(BillingMode.PAY_PER_REQUEST)
            .attributeDefinitions(
                AttributeDefinition.builder()
                    .attributeName("pk")
                    .attributeType(ScalarAttributeType.S)
                    .build(),
                AttributeDefinition.builder()
                    .attributeName("sk")
                    .attributeType(ScalarAttributeType.S)
                    .build())
            .keySchema(
                KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build(),
                KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build())
            .build());
    ddb.waiter().waitUntilTableExists(r -> r.tableName(name));

    table.putItem(name, item("pk", "a", "sk", "b", "v", "found"));
    assertEquals("found", table.getItem(name, "a", "b").get("v"));
    assertEquals(1, table.query(name, "a").size());
  }
}
