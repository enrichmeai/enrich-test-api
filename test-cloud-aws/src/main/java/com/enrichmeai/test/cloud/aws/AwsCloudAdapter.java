/*
 * Copyright 2025
 * Apache License, Version 2.0
 */
package com.enrichmeai.test.cloud.aws;

import com.enrichmeai.test.core.cloud.CloudProvider;
import com.enrichmeai.test.core.cloud.TestCloudConfig;
import com.enrichmeai.test.core.cloud.capability.BlobStorage;
import com.enrichmeai.test.core.cloud.capability.NoSqlTable;
import com.enrichmeai.test.core.cloud.capability.PubSub;
import com.enrichmeai.test.core.cloud.capability.Queue;
import com.enrichmeai.test.core.cloud.spi.CloudAdapter;

/** AWS adapter implementation. Provides BlobStorage (S3) in v0.3. */
public final class AwsCloudAdapter implements CloudAdapter {

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

  private void ensureInitialized() {
    if (this.config == null) {
      throw new IllegalStateException(
          "AwsCloudAdapter not initialized. Call initialize(TestCloudConfig) first.");
    }
  }
}
