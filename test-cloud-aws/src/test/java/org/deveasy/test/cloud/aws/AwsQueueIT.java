/*
 * Copyright 2025
 * Apache License, Version 2.0
 */
package org.deveasy.test.cloud.aws;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.deveasy.test.core.cloud.CloudMode;
import org.deveasy.test.core.cloud.CloudProvider;
import org.deveasy.test.core.cloud.TestCloudConfig;
import org.deveasy.test.core.cloud.capability.Queue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AwsQueueIT {

  private AwsCloudAdapter adapter;
  private Queue queue;
  private String qname;

  @BeforeEach
  void setUp() {
    TestCloudConfig config =
        TestCloudConfig.builder()
            .provider(CloudProvider.AWS)
            .mode(CloudMode.EMULATOR)
            .regionOrLocation("eu-west-1")
            .build();

    adapter = new AwsCloudAdapter();
    adapter.initialize(config);
    queue = adapter.queue();

    qname = "dev-easy-test-q-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    queue.ensureQueue(qname);
  }

  @AfterEach
  void tearDown() {
    try {
      queue.deleteQueue(qname);
    } catch (Throwable ignored) {
    }
  }

  @Test
  @DisplayName("SQS minimal flow: ensure -> send -> receive (timeout) -> delete")
  void sqsMinimalFlow() {
    String payload = "{\"orderId\":123}";

    queue.send(qname, payload);

    Optional<String> received = queue.receive(qname, Duration.ofSeconds(5));
    Assertions.assertTrue(received.isPresent(), "should receive a message within timeout");
    Assertions.assertTrue(received.get().contains("\"orderId\":123"));

    // After delete-on-receive semantics, subsequent receive should be empty (eventually consistent
    // OK)
    Optional<String> again = queue.receive(qname, Duration.ofSeconds(1));
    Assertions.assertTrue(again.isEmpty(), "queue should be empty after delete-on-receive");
  }
}
