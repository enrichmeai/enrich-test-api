package org.deveasy.test.core.junit.support;

import org.deveasy.test.core.cloud.CloudProvider;
import org.deveasy.test.core.cloud.TestCloudConfig;
import org.deveasy.test.core.cloud.capability.BlobStorage;
import org.deveasy.test.core.cloud.capability.NoSqlTable;
import org.deveasy.test.core.cloud.capability.PubSub;
import org.deveasy.test.core.cloud.capability.Queue;
import org.deveasy.test.core.cloud.spi.CloudAdapter;

/**
 * Test-only CloudAdapter wired via ServiceLoader for unit tests.
 *
 * <p>It hands out one shared in-memory fake per capability, so a test can reach the very instance
 * the extension used and assert on its state after {@code afterAll} has run. ServiceLoader creates
 * this adapter through its no-argument constructor and {@code CloudAdapters} caches it for the JVM,
 * which is why the fakes are static: {@link #reset()} swaps in fresh ones.
 */
public final class FakeCloudAdapter implements CloudAdapter {

  private static volatile FakeBlobStorage blobStorage = new FakeBlobStorage();
  private static volatile FakeQueue queue = new FakeQueue();
  private static volatile FakePubSub pubSub = new FakePubSub();
  private static volatile FakeNoSqlTable noSqlTable = new FakeNoSqlTable();

  private TestCloudConfig config;

  /** Replaces every fake with an empty one. Call before launching a fixture class. */
  public static void reset() {
    blobStorage = new FakeBlobStorage();
    queue = new FakeQueue();
    pubSub = new FakePubSub();
    noSqlTable = new FakeNoSqlTable();
  }

  public static FakeBlobStorage blobStorageFake() {
    return blobStorage;
  }

  public static FakeQueue queueFake() {
    return queue;
  }

  public static FakePubSub pubSubFake() {
    return pubSub;
  }

  public static FakeNoSqlTable noSqlTableFake() {
    return noSqlTable;
  }

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
    return blobStorage;
  }

  @Override
  public Queue queue() {
    return queue;
  }

  @Override
  public PubSub pubSub() {
    return pubSub;
  }

  @Override
  public NoSqlTable noSqlTable() {
    return noSqlTable;
  }
}
