package com.enrichmeai.test.core.junit.support;

import com.enrichmeai.test.core.cloud.CloudProvider;
import com.enrichmeai.test.core.cloud.TestCloudConfig;
import com.enrichmeai.test.core.cloud.capability.BlobStorage;
import com.enrichmeai.test.core.cloud.capability.NoSqlTable;
import com.enrichmeai.test.core.cloud.capability.PubSub;
import com.enrichmeai.test.core.cloud.capability.Queue;
import com.enrichmeai.test.core.cloud.spi.CloudAdapter;

/**
 * A registered adapter whose {@code provider()} throws, standing in for a broken implementation on
 * a user's classpath. {@code CloudAdapters} must skip it rather than fail the lookup. It is listed
 * after {@link FakeCloudAdapter} in {@code META-INF/services}, so an AWS lookup never reaches it.
 */
public final class MisconfiguredCloudAdapter implements CloudAdapter {

  @Override
  public CloudProvider provider() {
    throw new IllegalStateException("misconfigured adapter");
  }

  @Override
  public void initialize(TestCloudConfig config) {}

  @Override
  public BlobStorage blobStorage() {
    return null;
  }

  @Override
  public Queue queue() {
    return null;
  }

  @Override
  public PubSub pubSub() {
    return null;
  }

  @Override
  public NoSqlTable noSqlTable() {
    return null;
  }
}
