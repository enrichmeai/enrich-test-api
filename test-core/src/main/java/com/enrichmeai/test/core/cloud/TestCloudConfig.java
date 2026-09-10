/*
 * Copyright 2025
 * Apache License, Version 2.0
 */
package com.enrichmeai.test.core.cloud;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Unified configuration used by provider adapters and test glue. Immutable and buildable via {@link
 * Builder}.
 */
public final class TestCloudConfig {

  private final CloudProvider provider;
  private final CloudMode mode;
  private final String
      regionOrLocation; // aws:region, azure:location, gcp:region/zone (provider-specific)
  private final String
      projectOrAccount; // gcp:projectId, aws:account alias (optional), azure:tenant/sub alias
  private final Map<String, String> overrides; // provider/service specific endpoint overrides

  private TestCloudConfig(Builder b) {
    this.provider = Objects.requireNonNull(b.provider, "provider");
    this.mode = Objects.requireNonNull(b.mode, "mode");
    this.regionOrLocation = b.regionOrLocation;
    this.projectOrAccount = b.projectOrAccount;
    this.overrides = Collections.unmodifiableMap(new HashMap<>(b.overrides));
  }

  public CloudProvider provider() {
    return provider;
  }

  public CloudMode mode() {
    return mode;
  }

  public String regionOrLocation() {
    return regionOrLocation;
  }

  public String projectOrAccount() {
    return projectOrAccount;
  }

  public Map<String, String> overrides() {
    return overrides;
  }

  public String override(String key) {
    return overrides.get(key);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private CloudProvider provider = CloudProvider.AWS;
    private CloudMode mode = CloudMode.EMULATOR;
    private String regionOrLocation;
    private String projectOrAccount;
    private final Map<String, String> overrides = new HashMap<>();

    public Builder provider(CloudProvider provider) {
      this.provider = provider;
      return this;
    }

    public Builder mode(CloudMode mode) {
      this.mode = mode;
      return this;
    }

    public Builder regionOrLocation(String value) {
      this.regionOrLocation = value;
      return this;
    }

    public Builder projectOrAccount(String value) {
      this.projectOrAccount = value;
      return this;
    }

    public Builder override(String key, String value) {
      this.overrides.put(key, value);
      return this;
    }

    public TestCloudConfig build() {
      return new TestCloudConfig(this);
    }
  }
}
