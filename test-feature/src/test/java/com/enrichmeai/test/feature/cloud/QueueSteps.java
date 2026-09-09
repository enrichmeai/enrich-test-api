package com.enrichmeai.test.feature.cloud;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.enrichmeai.test.core.cloud.capability.Queue;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Duration;

/** Provider-neutral Queue steps backed by the resolved CloudAdapter. */
public class QueueSteps {

  private Queue queue;
  private String queueName;

  @Given("a queue named {string}")
  public void aQueueNamed(String name) {
    this.queue = CloudSelectionSteps.adapter().queue();
    this.queueName = name;
    queue.ensureQueue(name);
    ScenarioState.put("queueName", name);
  }

  @When("I send a message {string}")
  public void iSendAMessage(String body) {
    ensureQueue();
    queue.send(queueName, body);
  }

  @When("I send {int} messages")
  public void iSendMessages(int count) {
    ensureQueue();
    for (int i = 0; i < count; i++) {
      queue.send(queueName, "message-" + i);
    }
  }

  @Then("within {int}s I receive a message matching {string}")
  public void withinSecondsIReceiveAMessageMatching(int seconds, String expectedSubstring) {
    ensureQueue();
    var received = queue.receive(queueName, Duration.ofSeconds(seconds));
    assertTrue(received.isPresent(), "Expected to receive a message within timeout");
    assertTrue(
        received.get().contains(expectedSubstring),
        () -> "Received payload did not contain expected substring. payload=" + received.get());
  }

  @Then("no messages are available")
  public void noMessagesAreAvailable() {
    ensureQueue();
    var received = queue.receive(queueName, Duration.ofSeconds(1));
    assertFalse(received.isPresent(), "Expected no messages to be available");
  }

  private void ensureQueue() {
    if (queue == null) {
      queue = CloudSelectionSteps.adapter().queue();
      queueName = ScenarioState.get("queueName");
    }
  }
}
