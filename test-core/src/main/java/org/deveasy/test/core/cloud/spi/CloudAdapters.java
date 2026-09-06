/*
 * Copyright 2025
 * Apache License, Version 2.0
 */
package org.deveasy.test.core.cloud.spi;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import org.deveasy.test.core.cloud.CloudProvider;
import org.deveasy.test.core.cloud.TestCloudConfig;

/** Helper to discover and cache {@link CloudAdapter} implementations via {@link ServiceLoader}. */
public final class CloudAdapters {

  private static final ServiceLoader<CloudAdapter> LOADER = ServiceLoader.load(CloudAdapter.class);
  private static final Map<CloudProvider, CloudAdapter> CACHE = new ConcurrentHashMap<>();

  private CloudAdapters() {}

  /**
   * Returns an initialized adapter for the given provider using the supplied config. Caches adapter
   * instances per provider.
   *
   * @throws IllegalStateException if no adapter is found for the provider
   */
  public static CloudAdapter get(CloudProvider provider, TestCloudConfig config) {
    Objects.requireNonNull(provider, "provider");
    Objects.requireNonNull(config, "config");
    CloudAdapter adapter =
        CACHE.computeIfAbsent(
            provider,
            p ->
                findAdapter(p)
                    .orElseThrow(
                        () ->
                            new IllegalStateException(
                                "No CloudAdapter found on classpath for provider: "
                                    + p
                                    + ". Ensure the corresponding test-cloud-"
                                    + p.name().toLowerCase()
                                    + " module is added and META-INF/services is configured.")));
    // Initialize each time with current config; adapters should be idempotent
    adapter.initialize(config);
    return adapter;
  }

  /** Try to find an adapter without throwing. */
  public static Optional<CloudAdapter> tryGet(CloudProvider provider) {
    return findAdapter(provider);
  }

  private static Optional<CloudAdapter> findAdapter(CloudProvider provider) {
    for (CloudAdapter a : LOADER) {
      try {
        if (a.provider() == provider) {
          return Optional.of(a);
        }
      } catch (Throwable ignore) {
        // ignore misconfigured implementations
      }
    }
    return Optional.empty();
  }
}
