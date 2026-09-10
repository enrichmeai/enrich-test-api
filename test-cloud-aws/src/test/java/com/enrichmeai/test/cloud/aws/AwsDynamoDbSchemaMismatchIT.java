/*
 * Copyright 2025
 * Apache License, Version 2.0
 */
package com.enrichmeai.test.cloud.aws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;

/**
 * Story 7.3: {@code ensureTable} against a table that already exists either finds the schema it was
 * asked for, or fails in {@code ensureTable} itself with a message naming the table and both
 * schemas. It never adopts a table keyed differently and lets the failure land later in {@code
 * putItem}.
 */
class AwsDynamoDbSchemaMismatchIT {

  private NoSqlTable table;
  private DynamoDbClient ddb;
  private final List<String> created = new ArrayList<>();

  @BeforeEach
  void setUp() {
    TestCloudConfig config =
        TestCloudConfig.builder()
            .provider(CloudProvider.AWS)
            .mode(CloudMode.EMULATOR)
            .regionOrLocation("eu-west-1")
            .build();
    AwsCloudAdapter adapter = new AwsCloudAdapter();
    adapter.initialize(config);
    table = adapter.noSqlTable();
    ddb = AwsClients.dynamodb(config);
  }

  @AfterEach
  void tearDown() {
    for (String name : created) {
      table.deleteTable(name);
    }
  }

  @Test
  @DisplayName("partition-key mismatch fails in ensureTable, naming table and both schemas")
  void partitionKeyMismatch_failsInEnsureTable() {
    String t = fresh("orders");
    table.ensureTable(t, "id");
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("id", "a1");
    item.put("name", "from-class-a");
    table.putItem(t, item);

    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> table.ensureTable(t, "orderId"));

    String msg = ex.getMessage();
    assertTrue(msg.contains("'" + t + "'"), msg);
    assertTrue(msg.contains("[partitionKey=id, sortKey=<none>]"), msg);
    assertTrue(msg.contains("[partitionKey=orderId, sortKey=<none>]"), msg);

    // The rejected table is untouched: same key, same data.
    assertEquals("id", hashKeyOf(t));
    Map<String, Object> stillThere = table.getItem(t, "a1");
    assertNotNull(stillThere);
    assertEquals("from-class-a", stillThere.get("name"));
  }

  @Test
  @DisplayName("sort-key mismatch fails in both directions")
  void sortKeyMismatch_bothDirections() {
    String t = fresh("nosort");
    table.ensureTable(t, "id");
    IllegalStateException wantsSort =
        assertThrows(IllegalStateException.class, () -> table.ensureTable(t, "id", "ts"));
    assertTrue(wantsSort.getMessage().contains("[partitionKey=id, sortKey=<none>]"));
    assertTrue(wantsSort.getMessage().contains("[partitionKey=id, sortKey=ts]"));

    String u = fresh("withsort");
    table.ensureTable(u, "id", "ts");
    IllegalStateException wantsNone =
        assertThrows(IllegalStateException.class, () -> table.ensureTable(u, "id"));
    assertTrue(wantsNone.getMessage().contains("[partitionKey=id, sortKey=ts]"));
    assertTrue(wantsNone.getMessage().contains("[partitionKey=id, sortKey=<none>]"));
  }

  @Test
  @DisplayName("the same schema twice is idempotent and the table stays usable")
  void sameSchema_isIdempotent() {
    String t = fresh("idem");
    table.ensureTable(t, "id");
    table.ensureTable(t, "id");
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("id", "x");
    item.put("v", 1);
    table.putItem(t, item);
    assertEquals(1, ((Number) table.getItem(t, "x").get("v")).intValue());

    String u = fresh("idem-sort");
    table.ensureTable(u, "id", "ts");
    table.ensureTable(u, "id", "ts");
    Map<String, Object> keyed = new LinkedHashMap<>();
    keyed.put("id", "x");
    keyed.put("ts", "t1");
    keyed.put("v", 2);
    table.putItem(u, keyed);
    assertEquals(2, ((Number) table.getItem(u, "x", "t1").get("v")).intValue());
  }

  @Test
  @DisplayName("a blank sort key means no sort key")
  void blankSortKey_meansNone() {
    String t = fresh("blank");
    table.ensureTable(t, "id");
    table.ensureTable(t, "id", "");
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("id", "b");
    table.putItem(t, item);
    assertNotNull(table.getItem(t, "b"));
  }

  private String fresh(String prefix) {
    String name =
        "dev-easy-schema-"
            + prefix
            + "-"
            + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    created.add(name);
    return name;
  }

  private String hashKeyOf(String tableName) {
    for (KeySchemaElement e :
        ddb.describeTable(DescribeTableRequest.builder().tableName(tableName).build())
            .table()
            .keySchema()) {
      if (e.keyType() == KeyType.HASH) return e.attributeName();
    }
    return null;
  }
}
