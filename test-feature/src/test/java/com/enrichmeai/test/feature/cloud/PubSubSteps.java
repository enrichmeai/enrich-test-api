package com.enrichmeai.test.feature.cloud;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.enrichmeai.test.core.cloud.capability.PubSub;
import com.enrichmeai.test.core.cloud.capability.Queue;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Duration;
import java.util.Optional;

/**
 * Provider-neutral Pub/Sub steps (topic + subscription via queue) backed by the resolved
 * CloudAdapter.
 */
public class PubSubSteps {

  private PubSub pubsub;
  private Queue queue;

  @Given("a topic named {string}")
  public void aTopicNamed(String topicName) {
    ensureClients();
    pubsub.ensureTopic(topicName);
    ScenarioState.put("topicName", topicName);
  }

  @Given("a subscription from {string} to queue {string}")
  public void aSubscription(String topicName, String queueName) {
    ensureClients();
    // Ensure destination queue exists, then wire subscription
    queue.ensureQueue(queueName);
    pubsub.ensureSubscription(topicName, queueName);
    ScenarioState.put("queueName", queueName);
  }

  @When("I publish {string} to topic {string}")
  public void iPublish(String message, String topicName) {
    ensureClients();
    pubsub.publish(topicName, message);
  }

  @Then("within {int}s the queue {string} receives a message containing {string}")
  public void queueReceivesMessage(int seconds, String queueName, String content) {
    ensureClients();
    Optional<String> msg = queue.receive(queueName, Duration.ofSeconds(seconds));
    assertTrue(msg.isPresent(), "Expected to receive a message within timeout");
    assertTrue(
        msg.get().contains(content),
        () ->
            "Received payload did not contain expected substring. payload="
                + msg.orElse("<empty>"));
  }

  private void ensureClients() {
    if (pubsub == null) {
      pubsub = CloudSelectionSteps.adapter().pubSub();
    }
    if (queue == null) {
      queue = CloudSelectionSteps.adapter().queue();
    }
  }
}
