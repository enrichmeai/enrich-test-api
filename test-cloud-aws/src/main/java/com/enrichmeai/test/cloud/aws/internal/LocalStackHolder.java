/*
 * LocalStack holder for AWS emulator (S3 for now).
 */
package com.enrichmeai.test.cloud.aws.internal;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Lazily starts a LocalStack container with S3, SQS, SNS, and DYNAMODB services. This class is
 * intentionally simple and uses a static holder pattern.
 */
public final class LocalStackHolder {

  private static final DockerImageName LOCALSTACK_IMAGE =
      DockerImageName.parse("localstack/localstack:3.8");

  private static final AtomicReference<LocalStackContainer> REF = new AtomicReference<>();

  private LocalStackHolder() {}

  public static LocalStackContainer ensureStartedS3() {
    return ensureStarted(
        REF,
        () -> {
          LocalStackContainer container =
              new LocalStackContainer(LOCALSTACK_IMAGE)
                  .withServices("s3", "sqs", "sns", "dynamodb");
          // Let Testcontainers manage lifecycle (stop on JVM shutdown)
          container.start();
          return container;
        },
        LocalStackContainer::stop);
  }

  /**
   * Returns the instance in {@code ref}, starting one with {@code start} if there is none. When two
   * callers race, both may start an instance; the one whose compare-and-set loses stops its own and
   * returns the winner's.
   *
   * <p>The winner is read straight back: a failed compare-and-set means {@code ref} already held a
   * value, and nothing ever clears it. (The spin-wait that used to follow could not be reached.)
   *
   * <p>Package-private so the race can be tested without Docker (Story 1.3).
   */
  static <C> C ensureStarted(AtomicReference<C> ref, Supplier<C> start, Consumer<C> stop) {
    C existing = ref.get();
    if (existing != null) {
      return existing;
    }
    C started = start.get();
    if (ref.compareAndSet(null, started)) {
      return started;
    }
    stop.accept(started);
    return ref.get();
  }

  public static LocalStackContainer ensureStartedSns() {
    // SNS runs in the same LocalStack container; ensure it's started
    return ensureStartedS3();
  }

  // Alias with canonical acronym casing for strict TDD expectations
  public static LocalStackContainer ensureStartedSNS() {
    // SNS shares the same container, no additional startup needed
    return ensureStartedS3();
  }

  public static LocalStackContainer ensureStartedDynamoDB() {
    // DynamoDB runs in the same LocalStack container; ensure it's started
    return ensureStartedS3();
  }

  public static LocalStackContainer get() {
    return REF.get();
  }
}
