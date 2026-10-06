package com.enrichmeai.test.cloud.aws.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Story 1.3, AC-4: the start-once race in {@link LocalStackHolder}, through its package-private
 * seam, with plain objects standing in for containers. No Docker.
 */
class LocalStackHolderRaceTest {

  @Test
  void theFirstCaller_startsOne_andKeepsIt() {
    AtomicReference<Object> ref = new AtomicReference<>();
    AtomicInteger starts = new AtomicInteger();
    List<Object> stopped = new ArrayList<>();

    Object first = LocalStackHolder.ensureStarted(ref, () -> started(starts), stopped::add);

    assertSame(first, ref.get());
    assertEquals(1, starts.get());
    assertEquals(List.of(), stopped);
  }

  @Test
  void aLaterCaller_getsTheSameInstance_withoutStartingAnother() {
    Object existing = new Object();
    AtomicReference<Object> ref = new AtomicReference<>(existing);
    AtomicInteger starts = new AtomicInteger();

    Object got = LocalStackHolder.ensureStarted(ref, () -> started(starts), o -> {});

    assertSame(existing, got);
    assertEquals(0, starts.get());
  }

  @Test
  void theLoserOfTheRace_stopsItsOwn_andReturnsTheWinners() {
    AtomicReference<Object> ref = new AtomicReference<>();
    Object winner = new Object();
    Object loser = new Object();
    List<Object> stopped = new ArrayList<>();

    // Another caller wins while this one is starting its instance: after this caller saw an empty
    // reference, and before its compare-and-set.
    Object got =
        LocalStackHolder.ensureStarted(
            ref,
            () -> {
              ref.set(winner);
              return loser;
            },
            stopped::add);

    assertSame(winner, got);
    assertSame(winner, ref.get());
    assertEquals(List.of(loser), stopped);
  }

  private static Object started(AtomicInteger starts) {
    starts.incrementAndGet();
    return new Object();
  }
}
