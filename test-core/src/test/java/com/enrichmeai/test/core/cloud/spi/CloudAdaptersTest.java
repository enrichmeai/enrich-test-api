package com.enrichmeai.test.core.cloud.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.enrichmeai.test.core.cloud.CloudProvider;
import com.enrichmeai.test.core.cloud.TestCloudConfig;
import com.enrichmeai.test.core.junit.support.FakeCloudAdapter;
import org.junit.jupiter.api.Test;

/**
 * Story 1.6: how {@link CloudAdapters} finds an adapter through {@code ServiceLoader}, and how it
 * fails when there is none. The test classpath registers {@code FakeCloudAdapter} (AWS) and {@code
 * MisconfiguredCloudAdapter}, whose {@code provider()} throws.
 */
class CloudAdaptersTest {

  private static TestCloudConfig config(String region) {
    return TestCloudConfig.builder().provider(CloudProvider.AWS).regionOrLocation(region).build();
  }

  @Test
  void get_returnsTheRegisteredAdapter_initialisedWithTheGivenConfig() {
    TestCloudConfig first = config("eu-west-1");
    CloudAdapter adapter = CloudAdapters.get(CloudProvider.AWS, first);
    FakeCloudAdapter fake = assertInstanceOf(FakeCloudAdapter.class, adapter);
    assertSame(first, fake.config());

    // The instance is cached per provider, and initialised again with each config.
    TestCloudConfig second = config("us-east-1");
    assertSame(adapter, CloudAdapters.get(CloudProvider.AWS, second));
    assertSame(second, fake.config());
  }

  @Test
  void get_withNoAdapterForTheProvider_failsNamingTheProviderAndTheModule() {
    IllegalStateException e =
        assertThrows(
            IllegalStateException.class,
            () -> CloudAdapters.get(CloudProvider.GCP, config("europe-west2")));
    assertTrue(e.getMessage().contains("provider: GCP"), e.getMessage());
    assertTrue(e.getMessage().contains("test-cloud-gcp"), e.getMessage());
  }

  @Test
  void get_rejectsNullArguments() {
    NullPointerException p =
        assertThrows(NullPointerException.class, () -> CloudAdapters.get(null, config("x")));
    assertEquals("provider", p.getMessage());
    NullPointerException c =
        assertThrows(NullPointerException.class, () -> CloudAdapters.get(CloudProvider.AWS, null));
    assertEquals("config", c.getMessage());
  }

  @Test
  void tryGet_findsTheAdapter_orReturnsEmpty_skippingOneThatThrows() {
    assertInstanceOf(FakeCloudAdapter.class, CloudAdapters.tryGet(CloudProvider.AWS).orElseThrow());
    // Reaching AZURE means asking every registered adapter, including the one that throws.
    assertTrue(CloudAdapters.tryGet(CloudProvider.AZURE).isEmpty());
  }
}
