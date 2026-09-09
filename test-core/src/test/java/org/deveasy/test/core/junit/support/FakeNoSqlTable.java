package org.deveasy.test.core.junit.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.deveasy.test.core.cloud.capability.NoSqlTable;

/**
 * In-memory NoSqlTable: each table remembers its key names and holds items keyed by their partition
 * (and sort) key values. Items must carry the table's key attributes.
 */
public final class FakeNoSqlTable implements NoSqlTable {

  private static final class Table {
    final String pk;
    final String sk; // nullable
    final Map<String, Map<String, Object>> items = new ConcurrentHashMap<>();

    Table(String pk, String sk) {
      this.pk = pk;
      this.sk = sk;
    }
  }

  private final Map<String, Table> tables = new ConcurrentHashMap<>();
  private final List<String> deleteAttempts = new CopyOnWriteArrayList<>();
  private final Set<String> failDeletes = ConcurrentHashMap.newKeySet();
  private final Set<String> rejectEnsures = ConcurrentHashMap.newKeySet();

  /** Names of the tables that currently exist. */
  public Set<String> tables() {
    return Collections.unmodifiableSet(tables.keySet());
  }

  /** The partition key a table was created with, or {@code null} if it does not exist. */
  public String partitionKeyOf(String table) {
    Table t = tables.get(table);
    return t == null ? null : t.pk;
  }

  /** The sort key a table was created with, or {@code null} if none or the table is absent. */
  public String sortKeyOf(String table) {
    Table t = tables.get(table);
    return t == null ? null : t.sk;
  }

  /** Every table name {@link #deleteTable} was called with, in order, whether or not it threw. */
  public List<String> deleteAttempts() {
    return Collections.unmodifiableList(deleteAttempts);
  }

  /** Makes {@link #deleteTable} throw for the named table, leaving it in place. */
  public void failDeleteOf(String name) {
    failDeletes.add(name);
  }

  /** Makes both {@code ensureTable} overloads throw for the named table, as an adapter would. */
  public void rejectEnsureOf(String name) {
    rejectEnsures.add(name);
  }

  @Override
  public void ensureTable(String tableName, String partitionKey) {
    ensure(tableName, partitionKey, null);
  }

  @Override
  public void ensureTable(String tableName, String partitionKey, String sortKey) {
    ensure(tableName, partitionKey, sortKey);
  }

  private void ensure(String tableName, String pk, String sk) {
    if (rejectEnsures.contains(tableName)) {
      throw new IllegalStateException("simulated rejection of ensureTable for '" + tableName + "'");
    }
    tables.computeIfAbsent(tableName, k -> new Table(pk, sk));
  }

  @Override
  public void deleteTable(String tableName) {
    deleteAttempts.add(tableName);
    if (failDeletes.contains(tableName)) {
      throw new IllegalStateException("simulated failure deleting table '" + tableName + "'");
    }
    tables.remove(tableName);
  }

  @Override
  public void putItem(String tableName, Map<String, Object> item) {
    Table t = existing(tableName);
    Object pkv = item.get(t.pk);
    Object skv = t.sk == null ? null : item.get(t.sk);
    if (pkv == null || (t.sk != null && skv == null)) {
      throw new IllegalArgumentException("item is missing a key attribute of table " + tableName);
    }
    t.items.put(key(pkv, skv), new LinkedHashMap<>(item));
  }

  @Override
  public Map<String, Object> getItem(String tableName, String partitionKeyValue) {
    Map<String, Object> item = existing(tableName).items.get(key(partitionKeyValue, null));
    return item == null ? null : new LinkedHashMap<>(item);
  }

  @Override
  public Map<String, Object> getItem(
      String tableName, String partitionKeyValue, String sortKeyValue) {
    Map<String, Object> item = existing(tableName).items.get(key(partitionKeyValue, sortKeyValue));
    return item == null ? null : new LinkedHashMap<>(item);
  }

  @Override
  public void deleteItem(String tableName, String partitionKeyValue) {
    existing(tableName).items.remove(key(partitionKeyValue, null));
  }

  @Override
  public void deleteItem(String tableName, String partitionKeyValue, String sortKeyValue) {
    existing(tableName).items.remove(key(partitionKeyValue, sortKeyValue));
  }

  @Override
  public List<Map<String, Object>> scan(String tableName) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Map<String, Object> item : existing(tableName).items.values()) {
      out.add(new LinkedHashMap<>(item));
    }
    return out;
  }

  @Override
  public List<Map<String, Object>> query(String tableName, String partitionKeyValue) {
    Table t = existing(tableName);
    List<Map<String, Object>> out = new ArrayList<>();
    for (Map<String, Object> item : t.items.values()) {
      if (Objects.equals(String.valueOf(item.get(t.pk)), partitionKeyValue)) {
        out.add(new LinkedHashMap<>(item));
      }
    }
    return out;
  }

  private Table existing(String tableName) {
    Table t = tables.get(tableName);
    if (t == null) throw new IllegalStateException("no such table: " + tableName);
    return t;
  }

  private static String key(Object pkv, Object skv) {
    return String.valueOf(pkv) + '/' + (skv == null ? "" : String.valueOf(skv));
  }
}
