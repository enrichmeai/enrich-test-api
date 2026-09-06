/*
 * AWS SNS+SQS implementation of PubSub capability (minimal v0.4).
 */
package org.deveasy.test.cloud.aws;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.deveasy.test.cloud.aws.internal.AwsClients;
import org.deveasy.test.cloud.aws.internal.LocalStackHolder;
import org.deveasy.test.core.cloud.CloudMode;
import org.deveasy.test.core.cloud.TestCloudConfig;
import org.deveasy.test.core.cloud.capability.PubSub;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;
import software.amazon.awssdk.services.sns.model.CreateTopicResponse;
import software.amazon.awssdk.services.sns.model.DeleteTopicRequest;
import software.amazon.awssdk.services.sns.model.ListTopicsRequest;
import software.amazon.awssdk.services.sns.model.ListTopicsResponse;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.SnsException;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sns.model.Topic;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;

public final class AwsPubSub implements PubSub {

  private final TestCloudConfig cfg;
  private final SnsClient sns;
  private final SqsClient sqs;

  public AwsPubSub(TestCloudConfig cfg) {
    this.cfg = cfg;
    if (cfg.mode() == CloudMode.EMULATOR) {
      LocalStackHolder.ensureStartedSns();
    }
    this.sns = AwsClients.sns(cfg);
    this.sqs = AwsClients.sqs(cfg);
  }

  @Override
  public void ensureTopic(String name) {
    // Try to find topic by name
    String arn = findTopicArnByName(name);
    if (arn != null) return;
    // Create topic if not found
    sns.createTopic(CreateTopicRequest.builder().name(name).build());
  }

  @Override
  public void deleteTopic(String name) {
    String arn = findTopicArnByName(name);
    if (arn == null) return; // ignore if not exists
    try {
      sns.deleteTopic(DeleteTopicRequest.builder().topicArn(arn).build());
    } catch (SnsException e) {
      // best effort: ignore not found
      if (!isNotFound(e)) throw e;
    }
  }

  @Override
  public void ensureSubscription(String topic, String subscription) {
    // topic is SNS topic name; subscription is SQS queue name
    String topicArn = ensureAndGetTopicArn(topic);
    String queueUrl = getQueueUrl(subscription);
    String queueArn = getQueueArnByUrl(queueUrl);

    // Ensure SQS policy allows SNS topic to send messages
    String policyJson =
        "{\n"
            + "  \"Version\": \"2012-10-17\",\n"
            + "  \"Statement\": [\n"
            + "    {\n"
            + "      \"Sid\": \"Allow-SNS-SendMessage\",\n"
            + "      \"Effect\": \"Allow\",\n"
            + "      \"Principal\": \"*\",\n"
            + "      \"Action\": \"sqs:SendMessage\",\n"
            + "      \"Resource\": \"%s\",\n"
            + "      \"Condition\": { \"ArnEquals\": { \"aws:SourceArn\": \"%s\" } }\n"
            + "    }\n"
            + "  ]\n"
            + "}";
    policyJson = String.format(policyJson, queueArn, topicArn);
    sqs.setQueueAttributes(
        SetQueueAttributesRequest.builder()
            .queueUrl(queueUrl)
            .attributesWithStrings(Collections.singletonMap("Policy", policyJson))
            .build());

    // Create subscription (idempotent). Set RawMessageDelivery=true for simplicity
    sns.subscribe(
        SubscribeRequest.builder()
            .topicArn(topicArn)
            .protocol("sqs")
            .endpoint(queueArn)
            .attributes(java.util.Map.of("RawMessageDelivery", "true"))
            .returnSubscriptionArn(true)
            .build());
  }

  @Override
  public void publish(String topic, String body) {
    String topicArn = ensureAndGetTopicArn(topic);
    sns.publish(PublishRequest.builder().topicArn(topicArn).message(body).build());
  }

  @Override
  public Optional<String> receive(String subscription) {
    // Delegate to SQS-backed queue; subscription is queue name
    return new AwsQueue(cfg).receive(subscription);
  }

  @Override
  public Optional<String> receive(String subscription, Duration timeout) {
    return new AwsQueue(cfg).receive(subscription, timeout);
  }

  private String ensureAndGetTopicArn(String name) {
    String arn = findTopicArnByName(name);
    if (arn != null) return arn;
    CreateTopicResponse created = sns.createTopic(CreateTopicRequest.builder().name(name).build());
    return created.topicArn();
  }

  private String findTopicArnByName(String name) {
    String nextToken = null;
    do {
      ListTopicsResponse res =
          sns.listTopics(ListTopicsRequest.builder().nextToken(nextToken).build());
      List<Topic> topics = res.topics();
      if (topics != null) {
        for (Topic t : topics) {
          String arn = t.topicArn();
          if (arn != null && arn.endsWith(":" + name)) {
            return arn;
          }
        }
      }
      nextToken = res.nextToken();
    } while (nextToken != null && !nextToken.isBlank());
    return null;
  }

  private String getQueueUrl(String name) {
    return sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(name).build()).queueUrl();
  }

  private String getQueueArnByUrl(String url) {
    GetQueueAttributesResponse attrs =
        sqs.getQueueAttributes(
            GetQueueAttributesRequest.builder()
                .queueUrl(url)
                .attributeNames(QueueAttributeName.QUEUE_ARN)
                .build());
    return attrs.attributes().get(QueueAttributeName.QUEUE_ARN);
  }

  private boolean isNotFound(SnsException e) {
    // SNS uses generic SnsException; inspect status code if available
    Integer status = e.statusCode();
    return status != null && status == 404;
  }
}
