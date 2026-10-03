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
import java.util.Locale;
import java.util.Map;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;

/** AWS adapter implementation. Provides BlobStorage (S3) in v0.3. */
public final class AwsCloudAdapter implements CloudAdapter {

  /** The LocalStack service behind each capability this adapter implements. */
  private static final Map<CloudServiceType, Service> SERVICES = services();

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
      SERVICES.forEach(
          (type, service) ->
              p.put(
                  ConnectionProperties.endpoint(type), ls.getEndpointOverride(service).toString()));
    }
    return Collections.unmodifiableMap(p);
  }

  private static Map<CloudServiceType, Service> services() {
    Map<CloudServiceType, Service> m = new LinkedHashMap<>();
    m.put(CloudServiceType.STORAGE, Service.S3);
    m.put(CloudServiceType.QUEUE, Service.SQS);
    m.put(CloudServiceType.PUBSUB, Service.SNS);
    m.put(CloudServiceType.NOSQL, Service.DYNAMODB);
    return Collections.unmodifiableMap(m);
  }

  private void ensureInitialized() {
    if (this.config == null) {
      throw new IllegalStateException(
          "AwsCloudAdapter not initialized. Call initialize(TestCloudConfig) first.");
    }
  }
}
