/*
 * Copyright 2025
 * Apache License, Version 2.0
 */
package com.enrichmeai.test.core.cloud;

import com.enrichmeai.test.core.cloud.spi.CloudAdapter;
import java.util.Locale;
import java.util.Objects;

/**
 * The provider-neutral keys of {@link CloudAdapter#connectionProperties()} (ADR 0011).
 *
 * <p>The map is how an application under test reaches the emulator: copy its values into the
 * application's own configuration keys, from a Spring {@code @DynamicPropertySource} or the map a
 * Quarkus {@code QuarkusTestResourceLifecycleManager#start()} returns. These keys are the contract;
 * every adapter honours them, and an adapter may add keys of its own under {@code <provider>.} (for
 * example {@code aws.}), which are not part of it.
 *
 * <pre>{@code
 * Map<String, String> p = CloudAdapters.get(CloudProvider.AWS, config).connectionProperties();
 * registry.add("my.app.storage.endpoint", () -> p.get(ConnectionProperties.endpoint(CloudServiceType.STORAGE)));
 * registry.add("my.app.region", () -> p.get(ConnectionProperties.REGION));
 * }</pre>
 *
 * @since 0.3.0
 */
public final class ConnectionProperties {

  /** The provider, lower case: {@code aws}, {@code azure} or {@code gcp}. Always present. */
  public static final String PROVIDER = "cloud.provider";

  /** The mode, lower case: {@code emulator} or {@code live}. Always present. */
  public static final String MODE = "cloud.mode";

  /** The region or location the adapter uses. Present when the provider has one. */
  public static final String REGION = "cloud.region";

  /**
   * The credential's identifier half (for an access-key pair, the key id; for an account-key pair,
   * the account name). Absent when the client should use the platform's default credentials.
   */
  public static final String CREDENTIALS_KEY = "cloud.credentials.key";

  /** The credential's secret half. Absent whenever {@link #CREDENTIALS_KEY} is absent. */
  public static final String CREDENTIALS_SECRET = "cloud.credentials.secret";

  private static final String ENDPOINT_PREFIX = "cloud.endpoint.";

  private ConnectionProperties() {}

  /**
   * The key of the endpoint URI for one service, for example {@code cloud.endpoint.storage}.
   * Present for each service the adapter implements when the client must be pointed somewhere other
   * than the provider's default (always, in emulator mode).
   *
   * @param service the service
   * @return the key
   */
  public static String endpoint(CloudServiceType service) {
    Objects.requireNonNull(service, "service");
    return ENDPOINT_PREFIX + service.name().toLowerCase(Locale.ROOT);
  }
}
