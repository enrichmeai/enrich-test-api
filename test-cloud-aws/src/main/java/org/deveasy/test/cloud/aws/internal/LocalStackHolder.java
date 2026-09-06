/*
 * LocalStack holder for AWS emulator (S3 for now).
 */
package org.deveasy.test.cloud.aws.internal;

import java.util.concurrent.atomic.AtomicReference;
import org.testcontainers.containers.localstack.LocalStackContainer;
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
    LocalStackContainer existing = REF.get();
    if (existing != null) {
      return existing;
    }
    LocalStackContainer container =
        new LocalStackContainer(LOCALSTACK_IMAGE)
            .withServices(
                LocalStackContainer.Service.S3,
                LocalStackContainer.Service.SQS,
                LocalStackContainer.Service.SNS,
                LocalStackContainer.Service.DYNAMODB);
    // Let Testcontainers manage lifecycle (stop on JVM shutdown)
    container.start();
    if (!REF.compareAndSet(null, container)) {
      // Another thread won the race; stop ours and wait for the winner
      container.stop();
      LocalStackContainer winner;
      int spins = 0;
      do {
        winner = REF.get();
        if (winner != null) return winner;
        try {
          Thread.sleep(10);
        } catch (InterruptedException ignored) {
        }
      } while (++spins < 50);
      throw new IllegalStateException("LocalStack container race: winner not visible");
    }
    return container;
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
