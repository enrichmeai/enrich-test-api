/*
 * Copyright 2025
 * Apache License, Version 2.0
 */
package com.enrichmeai.test.core.cloud.spi;

import com.enrichmeai.test.core.cloud.CloudProvider;
import com.enrichmeai.test.core.cloud.TestCloudConfig;
import com.enrichmeai.test.core.cloud.capability.BlobStorage;
import com.enrichmeai.test.core.cloud.capability.NoSqlTable;
import com.enrichmeai.test.core.cloud.capability.PubSub;
import com.enrichmeai.test.core.cloud.capability.Queue;

/**
 * Service Provider Interface (SPI) for cloud adapters.
 *
 * <p>Implementations live in provider-specific modules (e.g., {@code test-cloud-aws}, {@code
 * test-cloud-azure}, {@code test-cloud-gcp}) and are discovered via Java's {@link
 * java.util.ServiceLoader} mechanism using the {@code
 * META-INF/services/com.enrichmeai.test.core.cloud.spi.CloudAdapter} resource.
 *
 * <p><b>Thread Safety:</b> Implementations are typically lightweight facades over provider SDK
 * clients. They are not required to be thread-safe; prefer one adapter instance per test class or
 * synchronize access when sharing.
 *
 * <p><b>Example Usage:</b>
 *
 * <pre>{@code
 * CloudAdapter adapter = CloudAdapters.getAdapter(CloudProvider.AWS);
 * adapter.initialize(new TestCloudConfig(CloudProvider.AWS));
 * BlobStorage storage = adapter.blobStorage();
 * storage.ensureBucket("test");
 * }</pre>
 *
 * @since 0.3.0
 */
public interface CloudAdapter {

  /**
   * Returns the cloud provider this adapter supports.
   *
   * @return the {@link CloudProvider}
   */
  CloudProvider provider();

  /**
   * Initializes the adapter with the resolved test configuration. Implementations may create
   * provider clients and perform health checks here.
   *
   * @param config the test cloud configuration to initialize the adapter with
   * @throws IllegalArgumentException if {@code config} is null or invalid
   * @throws RuntimeException if initialization fails (e.g., credentials or emulator not reachable)
   */
  void initialize(TestCloudConfig config);

  /**
   * Returns the blob storage capability, or {@code null} if unsupported by this adapter.
   *
   * @return the {@link BlobStorage} capability, or {@code null}
   */
  BlobStorage blobStorage();

  /**
   * Returns the queue capability, or {@code null} if unsupported by this adapter.
   *
   * @return the {@link Queue} capability, or {@code null}
   */
  Queue queue();

  /**
   * Returns the publish/subscribe capability, or {@code null} if unsupported by this adapter.
   *
   * @return the {@link PubSub} capability, or {@code null}
   */
  PubSub pubSub();

  /**
   * Returns the NoSQL table capability, or {@code null} if unsupported by this adapter.
   *
   * @return the {@link NoSqlTable} capability, or {@code null}
   */
  NoSqlTable noSqlTable();
}
