/*
 * Copyright 2025
 * Apache License, Version 2.0
 */
package org.deveasy.test.core.cloud.capability;

import java.util.List;
import java.util.Map;
import org.deveasy.test.core.cloud.Capability;

/**
 * Provider-agnostic NoSQL table capability for testing key-value and document stores.
 *
 * <p>Implementations typically wrap services like DynamoDB (AWS), Cosmos DB (Azure), or
 * Firestore/Datastore (GCP). The API is intentionally minimal and focuses on common test flows such
 * as create table, put, get, delete, query, and scan.
 *
 * <p><b>Thread Safety:</b> Implementations are not guaranteed to be thread-safe. Prefer separate
 * instances per test or synchronize access if sharing across threads.
 *
 * <p><b>Example Usage:</b>
 *
 * <pre>{@code
 * TestCloudConfig config = new TestCloudConfig(CloudProvider.AWS);
 * CloudAdapter adapter = CloudAdapters.getAdapter(CloudProvider.AWS);
 * adapter.initialize(config);
 * NoSqlTable db = adapter.noSqlTable();
 * db.ensureTable("Users", "userId");
 * Map<String,Object> item = new HashMap<>();
 * item.put("userId", "u-1");
 * item.put("name", "Alice");
 * db.putItem("Users", item);
 * Map<String,Object> loaded = db.getItem("Users", "u-1");
 * }</pre>
 *
 * @since 0.3.0
 */
public interface NoSqlTable extends Capability {

  // Table management

  /**
   * Ensures a table exists with the given partition (hash) key. Idempotent.
   *
   * @param tableName the table name
   * @param partitionKey the partition/hash key attribute name
   * @throws IllegalArgumentException if any argument is null or blank
   * @throws RuntimeException if creation fails due to provider-side errors
   */
  void ensureTable(String tableName, String partitionKey);

  /**
   * Ensures a table exists with the given partition (hash) and sort (range) key. Idempotent.
   *
   * @param tableName the table name
   * @param partitionKey the partition/hash key attribute name
   * @param sortKey the sort/range key attribute name
   * @throws IllegalArgumentException if any argument is null or blank
   * @throws RuntimeException if creation fails due to provider-side errors
   */
  void ensureTable(String tableName, String partitionKey, String sortKey);

  /**
   * Deletes a table if present.
   *
   * @param tableName the table name
   * @throws IllegalArgumentException if {@code tableName} is null or blank
   * @throws RuntimeException if deletion fails due to provider-side errors
   */
  void deleteTable(String tableName);

  // Item operations

  /**
   * Puts or overwrites an item represented as a schema-less map.
   *
   * @param tableName the table name
   * @param item the item attributes as a map; keys are attribute names, values are
   *     primitive/collection types
   * @throws IllegalArgumentException if {@code tableName} or {@code item} is null or blank
   * @throws RuntimeException if the put operation fails
   */
  void putItem(String tableName, Map<String, Object> item);

  /**
   * Gets an item by partition key value.
   *
   * @param tableName the table name
   * @param partitionKeyValue the partition/hash key value
   * @return the item as a map, or {@code null} if not found
   * @throws IllegalArgumentException if any argument is null or blank
   * @throws RuntimeException if the get operation fails
   */
  Map<String, Object> getItem(String tableName, String partitionKeyValue);

  /**
   * Gets an item by partition and sort key values.
   *
   * @param tableName the table name
   * @param partitionKeyValue the partition/hash key value
   * @param sortKeyValue the sort/range key value
   * @return the item as a map, or {@code null} if not found
   * @throws IllegalArgumentException if any argument is null or blank
   * @throws RuntimeException if the get operation fails
   */
  Map<String, Object> getItem(String tableName, String partitionKeyValue, String sortKeyValue);

  /**
   * Deletes an item by partition key value.
   *
   * @param tableName the table name
   * @param partitionKeyValue the partition/hash key value
   * @throws IllegalArgumentException if any argument is null or blank
   * @throws RuntimeException if the delete operation fails
   */
  void deleteItem(String tableName, String partitionKeyValue);

  /**
   * Deletes an item by partition and sort key values.
   *
   * @param tableName the table name
   * @param partitionKeyValue the partition/hash key value
   * @param sortKeyValue the sort/range key value
   * @throws IllegalArgumentException if any argument is null or blank
   * @throws RuntimeException if the delete operation fails
   */
  void deleteItem(String tableName, String partitionKeyValue, String sortKeyValue);

  // Query/scan

  /**
   * Scans all items in a table.
   *
   * @param tableName the table name
   * @return a list of items; order is undefined; never {@code null}
   * @throws IllegalArgumentException if {@code tableName} is null or blank
   * @throws RuntimeException if the scan operation fails
   */
  List<Map<String, Object>> scan(String tableName);

  /**
   * Queries by partition key value.
   *
   * @param tableName the table name
   * @param partitionKeyValue the partition/hash key value to match
   * @return a list of items matching the key; order is undefined; never {@code null}
   * @throws IllegalArgumentException if any argument is null or blank
   * @throws RuntimeException if the query operation fails
   */
  List<Map<String, Object>> query(String tableName, String partitionKeyValue);
}
