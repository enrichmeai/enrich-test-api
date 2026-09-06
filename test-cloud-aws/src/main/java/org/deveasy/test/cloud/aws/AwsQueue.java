/*
 * AWS SQS implementation of Queue capability (minimal v0.3).
 */
package org.deveasy.test.cloud.aws;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.deveasy.test.cloud.aws.internal.AwsClients;
import org.deveasy.test.core.cloud.TestCloudConfig;
import org.deveasy.test.core.cloud.capability.Queue;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;

/** Minimal SQS-backed Queue implementation with delete-on-receive semantics. */
public final class AwsQueue implements Queue {

  private final TestCloudConfig cfg;
  private final SqsClient sqs;

  public AwsQueue(TestCloudConfig cfg) {
    this.cfg = cfg;
    this.sqs = AwsClients.sqs(cfg);
  }

  @Override
  public void ensureQueue(String name) {
    // LocalStack SQS can respond with 500s while warming up. Be resilient and idempotent.
    Instant deadline = Instant.now().plusSeconds(15);
    SqsException last = null;
    int attempt = 0;
    while (Instant.now().isBefore(deadline)) {
      // 1) Try to resolve existing queue URL (fast path)
      try {
        getQueueUrl(name);
        return; // exists
      } catch (QueueDoesNotExistException e) {
        // proceed to creation path
      } catch (SqsException e) {
        // transient error on GetQueueUrl; try create path below
        last = e;
      }

      // 2) Try to create the queue (idempotent; safe if it already exists)
      try {
        sqs.createQueue(CreateQueueRequest.builder().queueName(name).build());
        // After create, loop will re-try getQueueUrl and return
      } catch (SqsException e) {
        last = e;
        // ignore and retry until deadline (e.g., 500 during warm-up)
      }

      // Backoff before next iteration
      try {
        Thread.sleep(Math.min(1000, 100 * (1 << Math.min(5, attempt++))));
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
      }
    }

    // One last attempt to resolve before giving up
    try {
      getQueueUrl(name);
    } catch (SqsException e) {
      if (last != null) throw last;
      throw e;
    }
  }

  @Override
  public void deleteQueue(String name) {
    try {
      String url = getQueueUrl(name);
      sqs.deleteQueue(DeleteQueueRequest.builder().queueUrl(url).build());
    } catch (QueueDoesNotExistException ignored) {
      // Queue doesn't exist; deletion is idempotent, so this is acceptable
    }
  }

  @Override
  public void send(String queue, String body) {
    String url = getQueueUrl(queue);
    sqs.sendMessage(SendMessageRequest.builder().queueUrl(url).messageBody(body).build());
  }

  @Override
  public Optional<String> receive(String queue) {
    return receive(queue, Duration.ofSeconds(1));
  }

  @Override
  public Optional<String> receive(String queue, Duration timeout) {
    String url = getQueueUrl(queue);
    Instant end = Instant.now().plus(timeout);
    while (Instant.now().isBefore(end)) {
      int wait = (int) Math.max(1, Math.min(20, Duration.between(Instant.now(), end).getSeconds()));
      ReceiveMessageResponse resp =
          sqs.receiveMessage(
              ReceiveMessageRequest.builder()
                  .queueUrl(url)
                  .maxNumberOfMessages(1)
                  .waitTimeSeconds(wait)
                  .visibilityTimeout(30)
                  .build());
      List<Message> msgs = resp.messages();
      if (msgs != null && !msgs.isEmpty()) {
        Message m = msgs.get(0);
        // delete-on-receive semantics
        sqs.deleteMessage(
            DeleteMessageRequest.builder().queueUrl(url).receiptHandle(m.receiptHandle()).build());
        return Optional.ofNullable(m.body());
      }
    }
    return Optional.empty();
  }

  private String getQueueUrl(String name) {
    GetQueueUrlResponse res = sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(name).build());
    return res.queueUrl();
  }
}
