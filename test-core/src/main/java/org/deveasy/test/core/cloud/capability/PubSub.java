/*
 * Copyright 2025
 * Apache License, Version 2.0
 */
package org.deveasy.test.core.cloud.capability;

import java.time.Duration;
import java.util.Optional;
import org.deveasy.test.core.cloud.Capability;

/**
 * Provider-agnostic publish/subscribe capability for testing topic-based messaging.
 *
 * <p>Implementations typically wrap services like SNS+SQS (AWS), Azure Service Bus
 * Topics/Subscriptions, or Google Cloud Pub/Sub. The API focuses on common test flows (create
 * topic/subscription, publish, receive).
 *
 * <p><b>Thread Safety:</b> Implementations are not guaranteed to be thread-safe. Prefer separate
 * instances per test or synchronize access if sharing across threads.
 *
 * <p><b>Example Usage:</b>
 *
 * <pre>{@code
 * TestCloudConfig config = new TestCloudConfig(CloudProvider.AWS);
 * CloudAdapter adapter = CloudAdapters.getAdapter(CloudProvider.AWS);
 * adapter.initialize(config);
 * PubSub pubSub = adapter.pubSub();
 * pubSub.ensureTopic("events");
 * pubSub.ensureSubscription("events", "events-sub");
 * pubSub.publish("events", "order-created");
 * Optional<String> msg = pubSub.receive("events-sub", Duration.ofSeconds(5));
 * }</pre>
 *
 * @since 0.3.0
 */
public interface PubSub extends Capability {

  /**
   * Ensures a topic exists. Idempotent.
   *
   * @param name the topic name (must follow provider rules)
   * @throws IllegalArgumentException if {@code name} is null, blank, or invalid for the provider
   * @throws RuntimeException if creation fails due to provider-side errors
   */
  void ensureTopic(String name);

  /**
   * Deletes a topic if present.
   *
   * @param name the topic name
   * @throws IllegalArgumentException if {@code name} is null or blank
   * @throws RuntimeException if deletion fails due to provider-side errors
   */
  void deleteTopic(String name);

  /**
   * Ensures a subscription exists from a topic to a destination (provider-specific semantics). For
   * example, SNS topic to SQS queue, or a Pub/Sub subscription.
   *
   * @param topic the topic name
   * @param subscription the subscription/destination name
   * @throws IllegalArgumentException if any argument is null or blank
   * @throws RuntimeException if creation fails due to provider-side errors
   */
  void ensureSubscription(String topic, String subscription);

  /**
   * Publishes a textual message to a topic.
   *
   * @param topic the topic name
   * @param body the message body as a UTF-8 string
   * @throws IllegalArgumentException if {@code topic} or {@code body} is null or blank
   * @throws RuntimeException if the publish operation fails
   */
  void publish(String topic, String body);

  /**
   * Receives a single message from a subscription if available.
   *
   * @param subscription the subscription/destination name
   * @return an {@link Optional} with the message body if present; empty if none available
   * @throws IllegalArgumentException if {@code subscription} is null or blank
   * @throws RuntimeException if the receive operation fails
   */
  Optional<String> receive(String subscription);

  /**
   * Polls until a message is available on the subscription or the timeout expires.
   *
   * @param subscription the subscription/destination name
   * @param timeout maximum time to wait before giving up
   * @return an {@link Optional} with the message body if one arrives in time; empty otherwise
   * @throws IllegalArgumentException if {@code subscription} or {@code timeout} is null, or timeout
   *     is negative
   * @throws RuntimeException if the provider operation fails while polling
   */
  Optional<String> receive(String subscription, Duration timeout);
}
