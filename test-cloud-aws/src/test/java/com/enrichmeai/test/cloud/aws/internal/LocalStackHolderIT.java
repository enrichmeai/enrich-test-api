package com.enrichmeai.test.cloud.aws.internal;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.testcontainers.localstack.LocalStackContainer;

/**
 * Story 1.3, AC-3 and AC-5: one LocalStack container per JVM, whichever entry point asks for it.
 */
class LocalStackHolderIT {

  @Test
  void everyEntryPoint_returnsTheOneRunningContainer() {
    LocalStackContainer first = LocalStackHolder.ensureStartedS3();
    assertTrue(first.isRunning());
    assertSame(first, LocalStackHolder.ensureStartedS3());
    assertSame(first, LocalStackHolder.ensureStartedSns());
    assertSame(first, LocalStackHolder.ensureStartedSNS());
    assertSame(first, LocalStackHolder.ensureStartedDynamoDB());
    assertSame(first, LocalStackHolder.get());
  }
}
