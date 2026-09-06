/*
 * Copyright 2025
 * Apache License, Version 2.0
 */
package org.deveasy.test.core.cloud.capability;

import java.io.InputStream;
import java.util.List;
import org.deveasy.test.core.cloud.Capability;

/**
 * Provider-agnostic blob storage capability for testing cloud storage services.
 *
 * <p>Implementations support S3 (AWS), Blob Storage (Azure), and Cloud Storage (GCP). All
 * operations are designed for test scenarios with a focus on simplicity and consistency across
 * providers.
 *
 * <p><b>Thread Safety:</b> Implementations are not guaranteed to be thread-safe. Create separate
 * instances per test or synchronize access when using the same instance across threads.
 *
 * <p><b>Example Usage:</b>
 *
 * <pre>{@code
 * TestCloudConfig config = new TestCloudConfig(CloudProvider.AWS);
 * CloudAdapter adapter = CloudAdapters.getAdapter(CloudProvider.AWS);
 * adapter.initialize(config);
 * BlobStorage storage = adapter.blobStorage();
 * storage.ensureBucket("test-bucket");
 * storage.putObject("test-bucket", "key1", "data".getBytes(), "text/plain");
 * byte[] data = storage.getObject("test-bucket", "key1");
 * }</pre>
 *
 * @since 0.3.0
 */
public interface BlobStorage extends Capability {

  /**
   * Ensures a bucket/container exists, creating it if necessary. This operation is idempotent.
   *
   * @param name the bucket/container name (must follow provider naming rules)
   * @throws IllegalArgumentException if {@code name} is null, blank, or violates provider rules
   * @throws RuntimeException if creation fails or the provider returns an unexpected error
   */
  void ensureBucket(String name);

  /**
   * Deletes a bucket/container and, if supported by the provider, all of its objects. No-op if the
   * bucket does not exist.
   *
   * @param name the bucket/container name
   * @throws IllegalArgumentException if {@code name} is null or blank
   * @throws RuntimeException if deletion fails due to provider-side issues
   */
  void deleteBucket(String name);

  /**
   * Stores an object by key using the provided byte array.
   *
   * @param bucket the bucket/container name
   * @param key the object key
   * @param data the object bytes to store
   * @param contentType the MIME content type (e.g., {@code text/plain}, {@code application/json})
   * @throws IllegalArgumentException if any argument is invalid (null/blank)
   * @throws RuntimeException if the upload fails or the provider rejects the request
   */
  void putObject(String bucket, String key, byte[] data, String contentType);

  /**
   * Stores an object by key using the provided input stream. Implementations should close the
   * stream.
   *
   * @param bucket the bucket/container name
   * @param key the object key
   * @param data the input stream containing the object payload; will be closed by the
   *     implementation
   * @param contentType the MIME content type (e.g., {@code text/plain}, {@code application/json})
   * @throws IllegalArgumentException if any argument is invalid (null/blank)
   * @throws RuntimeException if the upload fails or the provider rejects the request
   */
  void putObject(String bucket, String key, InputStream data, String contentType);

  /**
   * Retrieves the object contents as bytes.
   *
   * @param bucket the bucket/container name
   * @param key the object key
   * @return the object bytes, or {@code null} if not found
   * @throws IllegalArgumentException if {@code bucket} or {@code key} is null or blank
   * @throws RuntimeException if the download fails due to provider-side issues
   */
  byte[] getObject(String bucket, String key);

  /**
   * Deletes an object by key.
   *
   * @param bucket the bucket/container name
   * @param key the object key
   * @throws IllegalArgumentException if {@code bucket} or {@code key} is null or blank
   * @throws RuntimeException if the deletion fails due to provider-side issues
   */
  void deleteObject(String bucket, String key);

  /**
   * Lists object keys with an optional prefix.
   *
   * @param bucket the bucket/container name
   * @param prefix the key prefix filter; empty string means list all
   * @return list of object keys (possibly empty), never {@code null}
   * @throws IllegalArgumentException if {@code bucket} or {@code prefix} is null
   * @throws RuntimeException if the listing fails or is partially successful per provider
   */
  List<String> listKeys(String bucket, String prefix);

  /**
   * Checks if an object exists at the given key.
   *
   * @param bucket the bucket/container name
   * @param key the object key
   * @return {@code true} if present; {@code false} otherwise
   * @throws IllegalArgumentException if {@code bucket} or {@code key} is null or blank
   * @throws RuntimeException if the existence check fails due to provider-side issues
   */
  boolean exists(String bucket, String key);
}
