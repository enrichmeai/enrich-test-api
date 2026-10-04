/*
 * Copyright 2025
 * Apache License, Version 2.0
 */
package com.enrichmeai.test.cloud.aws;

import com.enrichmeai.test.cloud.aws.internal.AwsClients;
import com.enrichmeai.test.cloud.aws.internal.LocalStackHolder;
import com.enrichmeai.test.core.cloud.CloudMode;
import com.enrichmeai.test.core.cloud.CloudProvider;
import com.enrichmeai.test.core.cloud.CloudServiceType;
import com.enrichmeai.test.core.cloud.ConnectionProperties;
import com.enrichmeai.test.core.cloud.TestCloudConfig;
import com.enrichmeai.test.core.cloud.capability.BlobStorage;
import com.enrichmeai.test.core.cloud.capability.NoSqlTable;
import com.enrichmeai.test.core.cloud.capability.PubSub;
import com.enrichmeai.test.core.cloud.capability.Queue;
import com.enrichmeai.test.core.cloud.spi.CloudAdapter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.testcontainers.localstack.LocalStackContainer;

/** AWS adapter implementation. Provides BlobStorage (S3) in v0.3. */
public final class AwsCloudAdapter implements CloudAdapter {

  /**
   * The services this adapter implements. LocalStack serves them all on one endpoint, so each
   * {@code cloud.endpoint.<service>} key carries the same URI.
   */
  private static final List<CloudServiceType> SERVICES =
      List.of(
          CloudServiceType.STORAGE,
          CloudServiceType.QUEUE,
          CloudServiceType.PUBSUB,
          CloudServiceType.NOSQL);

  private TestCloudConfig config;

  @Override
  public CloudProvider provider() {
    return CloudProvider.AWS;
  }

  @Override
  public void initialize(TestCloudConfig config) {
    this.config = config;
  }

  @Override
  public BlobStorage blobStorage() {
    ensureInitialized();
    return new AwsBlobStorage(config);
  }

  @Override
  public Queue queue() {
    ensureInitialized();
    return new AwsQueue(config);
  }

  @Override
  public PubSub pubSub() {
    ensureInitialized();
    return new AwsPubSub(config);
  }

  @Override
  public NoSqlTable noSqlTable() {
    ensureInitialized();
    return new AwsDynamoDB(config);
  }

  /**
   * The same endpoints, region and credentials the capabilities use (ADR 0011). In emulator mode
   * this starts LocalStack if it is not running. In live mode only the provider, mode and region
   * are present: the application uses AWS's default endpoints and credential chain.
   */
  @Override
  public Map<String, String> connectionProperties() {
    ensureInitialized();
    Map<String, String> p = new LinkedHashMap<>();
    p.put(ConnectionProperties.PROVIDER, provider().name().toLowerCase(Locale.ROOT));
    p.put(ConnectionProperties.MODE, config.mode().name().toLowerCase(Locale.ROOT));
    p.put(ConnectionProperties.REGION, AwsClients.region(config));
    if (config.mode() == CloudMode.EMULATOR) {
      LocalStackContainer ls = LocalStackHolder.ensureStartedS3();
      p.put(ConnectionProperties.CREDENTIALS_KEY, ls.getAccessKey());
      p.put(ConnectionProperties.CREDENTIALS_SECRET, ls.getSecretKey());
      String endpoint = ls.getEndpoint().toString();
      SERVICES.forEach(type -> p.put(ConnectionProperties.endpoint(type), endpoint));
    }
    return Collections.unmodifiableMap(p);
  }

  private void ensureInitialized() {
    if (this.config == null) {
      throw new IllegalStateException(
          "AwsCloudAdapter not initialized. Call initialize(TestCloudConfig) first.");
    }
  }
}
