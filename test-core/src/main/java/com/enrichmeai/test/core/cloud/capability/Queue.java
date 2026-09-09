/*
 * Copyright 2025
 * Apache License, Version 2.0
 */
package com.enrichmeai.test.core.cloud.capability;

import com.enrichmeai.test.core.cloud.Capability;
import java.time.Duration;
import java.util.Optional;

/**
 * Provider-agnostic point-to-point queue capability for testing message queues.
 *
 * <p>Implementations typically wrap services like SQS (AWS), Azure Queue Storage, or Service Bus
 * queues. The API is intentionally minimal and focused on common test flows (create, send, receive,
 * delete).
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
 * Queue queue = adapter.queue();
 * queue.ensureQueue("orders");
 * queue.send("orders", "{\"id\":123}");
 * Optional<String> msg = queue.receive("orders", Duration.ofSeconds(5));
 * }</pre>
 *
 * @since 0.3.0
 */
public interface Queue extends Capability {

  /**
   * Ensures a queue exists, creating it if necessary. Idempotent.
   *
   * @param name the queue name (must follow provider naming rules)
   * @throws IllegalArgumentException if {@code name} is null, blank, or invalid for the provider
   * @throws RuntimeException if creation fails due to provider-side errors
   */
  void ensureQueue(String name);

  /**
   * Deletes the queue if it exists.
   *
   * @param name the queue name
   * @throws IllegalArgumentException if {@code name} is null or blank
   * @throws RuntimeException if deletion fails due to provider-side errors
   */
  void deleteQueue(String name);

  /**
   * Sends a textual message to the given queue.
   *
   * @param queue the queue name
   * @param body the message body as a UTF-8 string
   * @throws IllegalArgumentException if {@code queue} or {@code body} is null or blank
   * @throws RuntimeException if the provider rejects the message or send fails
   */
  void send(String queue, String body);

  /**
   * Receives a single message if available, removing it from the queue when returned.
   * Implementations should use reasonable defaults for visibility timeouts.
   *
   * @param queue the queue name
   * @return an {@link Optional} with the message body if present; empty if none available
   * @throws IllegalArgumentException if {@code queue} is null or blank
   * @throws RuntimeException if the receive operation fails
   */
  Optional<String> receive(String queue);

  /**
   * Polls until a message is available or the timeout expires.
   *
   * @param queue the queue name
   * @param timeout maximum time to wait before giving up
   * @return an {@link Optional} with the message body if one arrives in time; empty otherwise
   * @throws IllegalArgumentException if {@code queue} or {@code timeout} is null, or timeout is
   *     negative
   * @throws RuntimeException if the provider operation fails while polling
   */
  Optional<String> receive(String queue, Duration timeout);
}
