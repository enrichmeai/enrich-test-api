package org.deveasy.test.core.junit.support;

import java.time.Duration;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import org.deveasy.test.core.cloud.capability.PubSub;

/**
 * In-memory PubSub: topics fan out to named subscriptions, each of which is a FIFO the test can
 * receive from. A topic must exist before it is subscribed to or published to.
 */
public final class FakePubSub implements PubSub {
  private final Map<String, Set<String>> topics = new ConcurrentHashMap<>();
  private final Map<String, Deque<String>> delivered = new ConcurrentHashMap<>();
  private final List<String> deleteAttempts = new CopyOnWriteArrayList<>();
  private final Set<String> failDeletes = ConcurrentHashMap.newKeySet();

  /** Names of the topics that currently exist. */
  public Set<String> topics() {
    return Collections.unmodifiableSet(topics.keySet());
  }

  /** Subscriptions attached to a topic; empty if the topic does not exist. */
  public Set<String> subscriptions(String topic) {
    Set<String> subs = topics.get(topic);
    return subs == null ? Collections.emptySet() : Collections.unmodifiableSet(subs);
  }

  /** Every topic name {@link #deleteTopic} was called with, in order, whether or not it threw. */
  public List<String> deleteAttempts() {
    return Collections.unmodifiableList(deleteAttempts);
  }

  /** Makes {@link #deleteTopic} throw for the named topic, leaving it in place. */
  public void failDeleteOf(String name) {
    failDeletes.add(name);
  }

  @Override
  public void ensureTopic(String name) {
    topics.computeIfAbsent(name, k -> ConcurrentHashMap.newKeySet());
  }

  @Override
  public void deleteTopic(String name) {
    deleteAttempts.add(name);
    if (failDeletes.contains(name)) {
      throw new IllegalStateException("simulated failure deleting topic '" + name + "'");
    }
    Set<String> subs = topics.remove(name);
    if (subs != null) subs.forEach(delivered::remove);
  }

  @Override
  public void ensureSubscription(String topic, String subscription) {
    existing(topic).add(subscription);
    delivered.computeIfAbsent(subscription, k -> new ConcurrentLinkedDeque<>());
  }

  @Override
  public void publish(String topic, String body) {
    for (String sub : existing(topic)) {
      delivered.computeIfAbsent(sub, k -> new ConcurrentLinkedDeque<>()).addLast(body);
    }
  }

  @Override
  public Optional<String> receive(String subscription) {
    Deque<String> q = delivered.get(subscription);
    return q == null ? Optional.empty() : Optional.ofNullable(q.pollFirst());
  }

  @Override
  public Optional<String> receive(String subscription, Duration timeout) {
    return receive(subscription);
  }

  private Set<String> existing(String topic) {
    Set<String> subs = topics.get(topic);
    if (subs == null) throw new IllegalStateException("no such topic: " + topic);
    return subs;
  }
}
