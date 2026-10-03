/*
 * Copyright 2025
 * Apache License, Version 2.0
 */
package com.enrichmeai.test.cloud.aws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.enrichmeai.test.core.cloud.CloudMode;
import com.enrichmeai.test.core.cloud.CloudProvider;
import com.enrichmeai.test.core.cloud.ConnectionProperties;
import com.enrichmeai.test.core.cloud.TestCloudConfig;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The parts of {@code connectionProperties()} that need no emulator. */
class AwsCloudAdapterConnectionPropertiesTest {

  @Test
  void liveModeHasNoEndpointsAndNoCredentials() {
    AwsCloudAdapter adapter = new AwsCloudAdapter();
    adapter.initialize(
        TestCloudConfig.builder()
            .provider(CloudProvider.AWS)
            .mode(CloudMode.LIVE)
            .regionOrLocation("eu-west-2")
            .build());

    assertEquals(
        Map.of(
            ConnectionProperties.PROVIDER, "aws",
            ConnectionProperties.MODE, "live",
            ConnectionProperties.REGION, "eu-west-2"),
        adapter.connectionProperties());
  }

  @Test
  void regionDefaultsLikeTheClients() {
    AwsCloudAdapter adapter = new AwsCloudAdapter();
    adapter.initialize(TestCloudConfig.builder().mode(CloudMode.LIVE).build());
    assertEquals("us-east-1", adapter.connectionProperties().get(ConnectionProperties.REGION));
  }

  @Test
  void theMapIsUnmodifiable() {
    AwsCloudAdapter adapter = new AwsCloudAdapter();
    adapter.initialize(TestCloudConfig.builder().mode(CloudMode.LIVE).build());
    assertThrows(
        UnsupportedOperationException.class, () -> adapter.connectionProperties().put("x", "y"));
  }

  @Test
  void uninitializedAdapterFails() {
    assertThrows(IllegalStateException.class, () -> new AwsCloudAdapter().connectionProperties());
  }
}
