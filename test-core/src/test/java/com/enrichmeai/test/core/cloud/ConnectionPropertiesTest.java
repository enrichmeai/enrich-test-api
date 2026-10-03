package com.enrichmeai.test.core.cloud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.enrichmeai.test.core.junit.support.FakeCloudAdapter;
import org.junit.jupiter.api.Test;

class ConnectionPropertiesTest {

  @Test
  void endpointKeysAreTheServiceTypeInLowerCase() {
    assertEquals("cloud.endpoint.storage", ConnectionProperties.endpoint(CloudServiceType.STORAGE));
    assertEquals("cloud.endpoint.queue", ConnectionProperties.endpoint(CloudServiceType.QUEUE));
    assertEquals("cloud.endpoint.pubsub", ConnectionProperties.endpoint(CloudServiceType.PUBSUB));
    assertEquals("cloud.endpoint.nosql", ConnectionProperties.endpoint(CloudServiceType.NOSQL));
  }

  @Test
  void fixedKeysAreTheContract() {
    assertEquals("cloud.provider", ConnectionProperties.PROVIDER);
    assertEquals("cloud.mode", ConnectionProperties.MODE);
    assertEquals("cloud.region", ConnectionProperties.REGION);
    assertEquals("cloud.credentials.key", ConnectionProperties.CREDENTIALS_KEY);
    assertEquals("cloud.credentials.secret", ConnectionProperties.CREDENTIALS_SECRET);
  }

  @Test
  void endpointRejectsNull() {
    assertThrows(NullPointerException.class, () -> ConnectionProperties.endpoint(null));
  }

  @Test
  void anAdapterThatDoesNotExposeItsConnectionSaysSo() {
    UnsupportedOperationException e =
        assertThrows(
            UnsupportedOperationException.class,
            () -> new FakeCloudAdapter().connectionProperties());
    assertEquals(
        FakeCloudAdapter.class.getName() + " does not expose connection properties",
        e.getMessage());
  }
}
