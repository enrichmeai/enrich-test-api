/*
 * Copyright 2025
 * Apache License, Version 2.0
 */
package org.deveasy.test.cloud.aws;

import java.time.Duration;
import java.util.Optional;
import org.deveasy.test.core.cloud.CloudMode;
import org.deveasy.test.core.cloud.CloudProvider;
import org.deveasy.test.core.cloud.TestCloudConfig;
import org.deveasy.test.core.cloud.capability.PubSub;
import org.deveasy.test.core.cloud.capability.Queue;
import org.junit.jupiter.api.*;

class AwsPubSubIT {

  private AwsCloudAdapter adapter;
  private PubSub pubsub;
  private Queue queue;

  private String topic;
  private String subscriptionQueue;

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
    pubsub = adapter.pubSub();
    queue = adapter.queue();

    topic = "test-topic";
    subscriptionQueue = "test-queue";

    // Ensure resources exist
    pubsub.ensureTopic(topic);
    queue.ensureQueue(subscriptionQueue);
    pubsub.ensureSubscription(topic, subscriptionQueue);
  }

  @AfterEach
  void tearDown() {
    try {
      queue.deleteQueue(subscriptionQueue);
    } catch (Throwable ignored) {
    }
    try {
      pubsub.deleteTopic(topic);
    } catch (Throwable ignored) {
    }
  }

  @Test
  @DisplayName("Publish to topic and receive via subscription (5s timeout)")
  void publishAndReceiveViaSubscription() {
    String payload = "{\"orderId\":123}";

    pubsub.publish(topic, payload);

    Optional<String> received = pubsub.receive(subscriptionQueue, Duration.ofSeconds(5));
    Assertions.assertTrue(received.isPresent(), "should receive a message within timeout");
    // RawMessageDelivery=true: SQS receives the raw body
    Assertions.assertEquals(
        payload,
        received.get(),
        () -> "Expected raw message body to match payload, but got: " + received.orElse("<empty>"));

    // After delete-on-receive semantics via delegated SQS, subsequent receive should be empty
    Optional<String> again = pubsub.receive(subscriptionQueue, Duration.ofSeconds(2));
    Assertions.assertTrue(again.isEmpty(), "queue should be empty after delete-on-receive");
  }
}
