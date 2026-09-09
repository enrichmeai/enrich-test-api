package com.enrichmeai.test.core.junit.support;

import com.enrichmeai.test.core.cloud.capability.Queue;
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

/** In-memory Queue: a named FIFO per queue, no visibility timeout, no waiting. */
public final class FakeQueue implements Queue {
  private final Map<String, Deque<String>> queues = new ConcurrentHashMap<>();
  private final List<String> deleteAttempts = new CopyOnWriteArrayList<>();
  private final Set<String> failDeletes = ConcurrentHashMap.newKeySet();

  /** Names of the queues that currently exist. */
  public Set<String> queues() {
    return Collections.unmodifiableSet(queues.keySet());
  }

  /** Every queue name {@link #deleteQueue} was called with, in order, whether or not it threw. */
  public List<String> deleteAttempts() {
    return Collections.unmodifiableList(deleteAttempts);
  }

  /** Makes {@link #deleteQueue} throw for the named queue, leaving it in place. */
  public void failDeleteOf(String name) {
    failDeletes.add(name);
  }

  @Override
  public void ensureQueue(String name) {
    queues.computeIfAbsent(name, k -> new ConcurrentLinkedDeque<>());
  }

  @Override
  public void deleteQueue(String name) {
    deleteAttempts.add(name);
    if (failDeletes.contains(name)) {
      throw new IllegalStateException("simulated failure deleting queue '" + name + "'");
    }
    queues.remove(name);
  }

  @Override
  public void send(String queue, String body) {
    existing(queue).addLast(body);
  }

  @Override
  public Optional<String> receive(String queue) {
    return Optional.ofNullable(existing(queue).pollFirst());
  }

  @Override
  public Optional<String> receive(String queue, Duration timeout) {
    return receive(queue);
  }

  private Deque<String> existing(String queue) {
    Deque<String> q = queues.get(queue);
    if (q == null) throw new IllegalStateException("no such queue: " + queue);
    return q;
  }
}
