/*
 * AWS DynamoDB implementation of NoSqlTable capability.
 */
package org.deveasy.test.cloud.aws;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.deveasy.test.cloud.aws.internal.AwsClients;
import org.deveasy.test.cloud.aws.internal.LocalStackHolder;
import org.deveasy.test.core.cloud.CloudMode;
import org.deveasy.test.core.cloud.TestCloudConfig;
import org.deveasy.test.core.cloud.capability.NoSqlTable;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;

public final class AwsDynamoDB implements NoSqlTable {

  private static final class TableKeys {
    final String pk;
    final String sk; // nullable

    TableKeys(String pk, String sk) {
      this.pk = pk;
      this.sk = sk;
    }
  }

  private static final Map<String, TableKeys> KEYS = new ConcurrentHashMap<>();

  private final TestCloudConfig cfg;
  private final DynamoDbClient ddb;

  public AwsDynamoDB(TestCloudConfig cfg) {
    this.cfg = cfg;
    if (cfg.mode() == CloudMode.EMULATOR) {
      LocalStackHolder.ensureStartedDynamoDB();
    }
    this.ddb = AwsClients.dynamodb(cfg);
  }

  @Override
  public void ensureTable(String tableName, String partitionKey) {
    ensureTableInternal(tableName, partitionKey, null);
  }

  @Override
  public void ensureTable(String tableName, String partitionKey, String sortKey) {
    ensureTableInternal(tableName, partitionKey, sortKey);
  }

  private void ensureTableInternal(String tableName, String pk, String sk) {
    try {
      ddb.describeTable(DescribeTableRequest.builder().tableName(tableName).build());
      // cache keys if not yet
      cacheKeysFromDescribe(tableName);
      return;
    } catch (ResourceNotFoundException notFound) {
      // create
    }
    List<AttributeDefinition> attrs = new ArrayList<>();
    attrs.add(
        AttributeDefinition.builder()
            .attributeName(pk)
            .attributeType(ScalarAttributeType.S)
            .build());
    List<KeySchemaElement> schema = new ArrayList<>();
    schema.add(KeySchemaElement.builder().attributeName(pk).keyType(KeyType.HASH).build());
    if (sk != null && !sk.isBlank()) {
      attrs.add(
          AttributeDefinition.builder()
              .attributeName(sk)
              .attributeType(ScalarAttributeType.S)
              .build());
      schema.add(KeySchemaElement.builder().attributeName(sk).keyType(KeyType.RANGE).build());
    }
    CreateTableRequest.Builder b =
        CreateTableRequest.builder()
            .tableName(tableName)
            .billingMode(BillingMode.PAY_PER_REQUEST)
            .attributeDefinitions(attrs)
            .keySchema(schema);
    ddb.createTable(b.build());
    // wait until active (simple describe loop)
    waitForActive(tableName);
    KEYS.put(tableName, new TableKeys(pk, sk));
  }

  private void waitForActive(String tableName) {
    for (int i = 0; i < 50; i++) {
      try {
        DescribeTableResponse d =
            ddb.describeTable(DescribeTableRequest.builder().tableName(tableName).build());
        if (d.table() != null && d.table().tableStatus() == TableStatus.ACTIVE) return;
        try {
          Thread.sleep(100);
        } catch (InterruptedException ignored) {
        }
      } catch (ResourceNotFoundException e) {
        try {
          Thread.sleep(100);
        } catch (InterruptedException ignored) {
        }
      }
    }
  }

  @Override
  public void deleteTable(String tableName) {
    try {
      ddb.deleteTable(DeleteTableRequest.builder().tableName(tableName).build());
    } catch (ResourceNotFoundException ignored) {
    } catch (DynamoDbException e) {
      // best effort
    }
    KEYS.remove(tableName);
  }

  @Override
  public void putItem(String tableName, Map<String, Object> item) {
    Map<String, AttributeValue> av = toAttributes(item);
    ddb.putItem(PutItemRequest.builder().tableName(tableName).item(av).build());
  }

  @Override
  public Map<String, Object> getItem(String tableName, String partitionKeyValue) {
    TableKeys k = ensureKeys(tableName);
    if (k == null || k.pk == null) return null;
    Map<String, AttributeValue> key = new HashMap<>();
    key.put(k.pk, toAttributeValue(partitionKeyValue));
    try {
      GetItemResponse resp =
          ddb.getItem(GetItemRequest.builder().tableName(tableName).key(key).build());
      if (resp.item() == null || resp.item().isEmpty()) return null;
      return fromAttributes(resp.item());
    } catch (ResourceNotFoundException e) {
      return null;
    }
  }

  @Override
  public Map<String, Object> getItem(
      String tableName, String partitionKeyValue, String sortKeyValue) {
    TableKeys k = ensureKeys(tableName);
    if (k == null || k.pk == null || k.sk == null) return null;
    Map<String, AttributeValue> key = new HashMap<>();
    key.put(k.pk, toAttributeValue(partitionKeyValue));
    key.put(k.sk, toAttributeValue(sortKeyValue));
    try {
      GetItemResponse resp =
          ddb.getItem(GetItemRequest.builder().tableName(tableName).key(key).build());
      if (resp.item() == null || resp.item().isEmpty()) return null;
      return fromAttributes(resp.item());
    } catch (ResourceNotFoundException e) {
      return null;
    }
  }

  @Override
  public void deleteItem(String tableName, String partitionKeyValue) {
    TableKeys k = ensureKeys(tableName);
    if (k == null || k.pk == null) return; // nothing we can do
    Map<String, AttributeValue> key = new HashMap<>();
    key.put(k.pk, toAttributeValue(partitionKeyValue));
    try {
      ddb.deleteItem(DeleteItemRequest.builder().tableName(tableName).key(key).build());
    } catch (DynamoDbException ignored) {
    }
  }

  @Override
  public void deleteItem(String tableName, String partitionKeyValue, String sortKeyValue) {
    TableKeys k = ensureKeys(tableName);
    if (k == null || k.pk == null || k.sk == null) return;
    Map<String, AttributeValue> key = new HashMap<>();
    key.put(k.pk, toAttributeValue(partitionKeyValue));
    key.put(k.sk, toAttributeValue(sortKeyValue));
    try {
      ddb.deleteItem(DeleteItemRequest.builder().tableName(tableName).key(key).build());
    } catch (DynamoDbException ignored) {
    }
  }

  @Override
  public List<Map<String, Object>> scan(String tableName) {
    List<Map<String, Object>> out = new ArrayList<>();
    Map<String, AttributeValue> startKey = null;
    do {
      ScanRequest.Builder b = ScanRequest.builder().tableName(tableName);
      if (startKey != null && !startKey.isEmpty()) b.exclusiveStartKey(startKey);
      try {
        ScanResponse resp = ddb.scan(b.build());
        if (resp.items() != null) {
          for (Map<String, AttributeValue> it : resp.items()) {
            out.add(fromAttributes(it));
          }
        }
        startKey = resp.lastEvaluatedKey();
      } catch (DynamoDbException e) {
        return Collections.emptyList();
      }
    } while (startKey != null && !startKey.isEmpty());
    return out;
  }

  @Override
  public List<Map<String, Object>> query(String tableName, String partitionKeyValue) {
    TableKeys k = ensureKeys(tableName);
    if (k == null || k.pk == null) return Collections.emptyList();
    List<Map<String, Object>> out = new ArrayList<>();
    Map<String, AttributeValue> startKey = null;
    do {
      QueryRequest.Builder b =
          QueryRequest.builder()
              .tableName(tableName)
              .keyConditionExpression("#pk = :v")
              .expressionAttributeNames(Collections.singletonMap("#pk", k.pk))
              .expressionAttributeValues(
                  Collections.singletonMap(":v", toAttributeValue(partitionKeyValue)));
      if (startKey != null && !startKey.isEmpty()) b.exclusiveStartKey(startKey);
      try {
        QueryResponse resp = ddb.query(b.build());
        if (resp.items() != null) {
          for (Map<String, AttributeValue> it : resp.items()) {
            out.add(fromAttributes(it));
          }
        }
        startKey = resp.lastEvaluatedKey();
      } catch (DynamoDbException e) {
        return Collections.emptyList();
      }
    } while (startKey != null && !startKey.isEmpty());
    return out;
  }

  // Helpers

  private TableKeys ensureKeys(String table) {
    TableKeys k = KEYS.get(table);
    if (k != null) return k;
    return cacheKeysFromDescribe(table);
  }

  private TableKeys cacheKeysFromDescribe(String table) {
    try {
      DescribeTableResponse d =
          ddb.describeTable(DescribeTableRequest.builder().tableName(table).build());
      if (d.table() == null) return null;
      String pk = null;
      String sk = null;
      for (KeySchemaElement e : d.table().keySchema()) {
        if (e.keyType() == KeyType.HASH) pk = e.attributeName();
        else if (e.keyType() == KeyType.RANGE) sk = e.attributeName();
      }
      if (pk != null) {
        TableKeys tk = new TableKeys(pk, sk);
        KEYS.put(table, tk);
        return tk;
      }
      return null;
    } catch (ResourceNotFoundException e) {
      return null;
    }
  }

  private static Map<String, AttributeValue> toAttributes(Map<String, Object> item) {
    return item.entrySet().stream()
        .filter(e -> e.getValue() != null)
        .collect(Collectors.toMap(Map.Entry::getKey, e -> toAttributeValue(e.getValue())));
  }

  private static Map<String, Object> fromAttributes(Map<String, AttributeValue> item) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<String, AttributeValue> e : item.entrySet()) {
      out.put(e.getKey(), fromAttributeValue(e.getValue()));
    }
    return out;
  }

  private static AttributeValue toAttributeValue(Object v) {
    if (v == null) return AttributeValue.builder().nul(true).build();
    if (v instanceof AttributeValue) return (AttributeValue) v;
    if (v instanceof String) return AttributeValue.builder().s((String) v).build();
    if (v instanceof Number) return AttributeValue.builder().n(String.valueOf(v)).build();
    if (v instanceof Boolean) return AttributeValue.builder().bool((Boolean) v).build();
    if (v instanceof byte[])
      return AttributeValue.builder().b(SdkBytes.fromByteArray((byte[]) v)).build();
    return AttributeValue.builder().s(v.toString()).build();
  }

  private static Object fromAttributeValue(AttributeValue v) {
    if (v == null) return null;
    if (v.s() != null) return v.s();
    if (v.n() != null) return parseNumber(v.n());
    if (v.bool() != null) return v.bool();
    if (v.b() != null) return v.b().asByteArray();
    if (v.nul() != null && v.nul()) return null;
    if (v.m() != null && !v.m().isEmpty()) {
      Map<String, Object> m = new LinkedHashMap<>();
      v.m().forEach((k, av) -> m.put(k, fromAttributeValue(av)));
      return m;
    }
    if (v.l() != null && !v.l().isEmpty()) {
      List<Object> list = new ArrayList<>();
      for (AttributeValue av : v.l()) list.add(fromAttributeValue(av));
      return list;
    }
    return null;
  }

  private static Object parseNumber(String n) {
    try {
      if (n.contains(".") || n.contains("e") || n.contains("E")) {
        return Double.parseDouble(n);
      } else {
        long l = Long.parseLong(n);
        if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) return (int) l;
        return l;
      }
    } catch (NumberFormatException e) {
      return n; // fallback to string
    }
  }
}
