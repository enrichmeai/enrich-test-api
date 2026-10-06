package com.enrichmeai.test.core.cloud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Story 1.6, AC-1: every declared {@link CloudServiceType} round-trips through its name. The list
 * is spelled out so that removing a value (Story 6.4 may remove {@code SECRETS} and {@code KMS}) is
 * a deliberate change here, not a silent one.
 */
class CloudServiceTypeTest {

  @Test
  void theDeclaredValues_areExactlyThese() {
    assertEquals(
        List.of("STORAGE", "QUEUE", "PUBSUB", "NOSQL", "SECRETS", "KMS"),
        Arrays.stream(CloudServiceType.values()).map(Enum::name).toList());
  }

  @Test
  void everyValue_roundTripsThroughValueOf() {
    for (CloudServiceType t : CloudServiceType.values()) {
      assertSame(t, CloudServiceType.valueOf(t.name()));
    }
  }
}
