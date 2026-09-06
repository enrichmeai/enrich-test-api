package org.deveasy.test.core.junit.support;

import org.deveasy.test.core.cloud.CloudProvider;
import org.deveasy.test.core.cloud.TestCloudConfig;
import org.deveasy.test.core.cloud.capability.BlobStorage;
import org.deveasy.test.core.cloud.capability.NoSqlTable;
import org.deveasy.test.core.cloud.capability.PubSub;
import org.deveasy.test.core.cloud.capability.Queue;
import org.deveasy.test.core.cloud.spi.CloudAdapter;

/**
 * Test-only CloudAdapter wired via ServiceLoader for unit tests. It returns an in-memory
 * BlobStorage implementation and null for others.
 */
public final class FakeCloudAdapter implements CloudAdapter {
  private TestCloudConfig config;

  @Override
  public CloudProvider provider() {
    // Use AWS as the provider so @WithCloud(provider=AWS) can resolve this adapter in unit tests
    return CloudProvider.AWS;
  }

  @Override
  public void initialize(TestCloudConfig config) {
    this.config = config; // not used but kept for parity
  }

  @Override
  public BlobStorage blobStorage() {
    return new FakeBlobStorage();
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
