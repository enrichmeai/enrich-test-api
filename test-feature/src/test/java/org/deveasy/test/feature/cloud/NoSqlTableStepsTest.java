package org.deveasy.test.feature.cloud;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.*;
import org.deveasy.test.core.cloud.capability.NoSqlTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NoSqlTableStepsTest {

  private NoSqlTableSteps steps;
  private FakeNoSqlTable fake;
  private final String tableName = "t1";

  @BeforeEach
  void setUp() throws Exception {
    steps = new NoSqlTableSteps();
    fake = new FakeNoSqlTable();
    fake.ensureTable(tableName, "id");
    setPrivateField(steps, "table", fake);
    setPrivateField(steps, "tableName", tableName);
  }

  @Test
  void putAndGetItem_roundTrip() throws Exception {
    steps.iPutItem("id", "1", "{\"name\":\"Alice\",\"age\":30}");
    steps.iCanGetItem("id", "1");
  }

  @Test
  void scanReturnsExpectedCount() throws Exception {
    steps.iPutItem("id", "1", "{\"name\":\"A\"}");
    steps.iPutItem("id", "2", "{\"name\":\"B\"}");
    steps.scanningReturnsItems(2);
  }

  // --- helpers ---

  private static void setPrivateField(Object target, String name, Object value) throws Exception {
    Field f = target.getClass().getDeclaredField(name);
    f.setAccessible(true);
    f.set(target, value);
  }

  private static final class FakeNoSqlTable implements NoSqlTable {
    private final Map<String, String> pkByTable = new HashMap<>();
    private final Map<String, Map<String, Map<String, Object>>> store = new HashMap<>();

    @Override
    public void ensureTable(String tableName, String partitionKey) {
      pkByTable.put(tableName, partitionKey);
      store.computeIfAbsent(tableName, k -> new LinkedHashMap<>());
    }

    @Override
    public void ensureTable(String tableName, String partitionKey, String sortKey) {
      ensureTable(tableName, partitionKey);
    }

    @Override
    public void deleteTable(String tableName) {
      store.remove(tableName);
      pkByTable.remove(tableName);
    }

    @Override
    public void putItem(String tableName, Map<String, Object> item) {
      String pk = pkByTable.get(tableName);
      if (pk == null) throw new IllegalStateException("table not ensured: " + tableName);
      Object v = item.get(pk);
      if (v == null) throw new IllegalArgumentException("missing pk field: " + pk);
      store.get(tableName).put(String.valueOf(v), new LinkedHashMap<>(item));
    }

    @Override
    public Map<String, Object> getItem(String tableName, String partitionKeyValue) {
      Map<String, Map<String, Object>> t = store.get(tableName);
      if (t == null) return null;
      Map<String, Object> it = t.get(partitionKeyValue);
      return it == null ? null : new LinkedHashMap<>(it);
    }

    @Override
    public Map<String, Object> getItem(
        String tableName, String partitionKeyValue, String sortKeyValue) {
      return getItem(tableName, partitionKeyValue);
    }

    @Override
    public void deleteItem(String tableName, String partitionKeyValue) {
      Map<String, Map<String, Object>> t = store.get(tableName);
      if (t != null) t.remove(partitionKeyValue);
    }

    @Override
    public void deleteItem(String tableName, String partitionKeyValue, String sortKeyValue) {
      deleteItem(tableName, partitionKeyValue);
    }

    @Override
    public List<Map<String, Object>> scan(String tableName) {
      Map<String, Map<String, Object>> t = store.get(tableName);
      if (t == null) return Collections.emptyList();
      List<Map<String, Object>> out = new ArrayList<>();
      for (Map<String, Object> v : t.values()) {
        out.add(new LinkedHashMap<>(v));
      }
      return out;
    }

    @Override
    public List<Map<String, Object>> query(String tableName, String partitionKeyValue) {
      Map<String, Object> it = getItem(tableName, partitionKeyValue);
      if (it == null) return Collections.emptyList();
      return List.of(new LinkedHashMap<>(it));
    }
  }
}
