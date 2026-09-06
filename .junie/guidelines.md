# enrich-test-api – Project Development Guidelines

This document captures project-specific knowledge to speed up development, testing, and debugging of this repository. It assumes you are an experienced Java developer familiar with Maven, JUnit, and Testcontainers.


## Prerequisites and Environment
- JDK: 17 (Temurin recommended; CI uses actions/setup-java@v4 with Temurin 17).
- Build tool: Maven 3.9+.
- Docker: Required for integration tests that use Testcontainers/LocalStack (AWS emulation). Ensure Docker daemon is available and the current user can run Docker commands.
- Network: Testcontainers pulls images on first run. Allow outbound network to Docker Hub/GHCR.

Optional environment variables commonly used with Testcontainers (set only if needed):
- TESTCONTAINERS_RYUK_DISABLED=false (default; keep Ryuk enabled unless inside restricted CI)
- TESTCONTAINERS_CHECKS_DISABLE=true (use to bypass environment checks when you know Docker works)
- DOCKER_HOST, DOCKER_TLS_VERIFY, etc. (if using remote Docker)


## Repository layout (modules)
- test-core: Provider‑agnostic Cloud API, config, and SPI (CloudAdapter). No vendor SDK dependencies.
- test-feature: Provider‑neutral BDD layer (Cucumber glue + JUnit Platform suite) and feature files under src/test/resources/features.
- test-cloud-aws: AWS adapter (SDK v2 + LocalStack/Testcontainers). Implements minimal S3 (BlobStorage) and SQS (Queue). SNS wiring present for future Pub/Sub.

Key dirs:
- test-core/src/main/java/org/deveasy/test/core/cloud/** — core API and SPI
- test-feature/src/test/java/org/deveasy/test/feature/cloud/** — Cucumber glue and JUnit Platform suite(s)
- test-feature/src/test/resources/features/** — provider‑neutral features
- test-cloud-aws/src/main/java/org/deveasy/test/cloud/aws/** — AWS adapter + LocalStack client wiring


## Build and verification
Typical workflows:
- Full build + all checks (as in CI):
  mvn -B -DskipTests=false verify
  This runs unit tests, ITs, Spotless check, Checkstyle, Enforcer, and produces JaCoCo reports.

- Aggregate coverage HTML (mirrors CI coverage job):
  mvn -DskipTests=true verify -pl :enrich-test-api -am
  Output is under each module’s target/site/jacoco*.

- Module-scoped build (useful when iterating on one area):
  mvn -pl test-core -am -DskipTests=true package

Notes:
- The parent POM wires maven-surefire-plugin 3.2.5; tests are JUnit 4 or JUnit 5 depending on the module.
- JaCoCo is report-only; build doesn’t fail on coverage.
- Enforcer enforces Java 17 and dependency convergence; duplicate classes rule is temporarily relaxed in parent POM comments.


## Running tests
There are two types of tests in this repo.

1) Plain JUnit tests (e.g., in test-cloud-aws)
- Execute all:
  mvn -DskipTests=false test
- Execute in a specific module only (avoids starting Docker for others):
  mvn -pl test-cloud-aws -am -DskipTests=false test
- Execute specific test class:
  mvn -pl test-cloud-aws -Dtest=org.deveasy.test.cloud.aws.AwsBlobStorageIT test

2) Cucumber features via JUnit Platform (module: test-feature)
- The suite org.deveasy.test.feature.cloud.CucumberQueueSuite looks on classpath resource path "features" and uses glue package org.deveasy.test.feature.cloud.
- To run only the feature suite module (and bring AWS adapter on test classpath):
  mvn -pl test-feature -am -DskipTests=false test

Provider adapter discovery
- Adapters implement org.deveasy.test.core.cloud.spi.CloudAdapter and are discovered via Java ServiceLoader. The AWS adapter is present when you include the test-cloud-aws module on the test classpath. In test-feature/pom.xml this is already declared with scope test and excludes test-core to avoid duplicate classes.

Testcontainers/LocalStack
- CloudMode.EMULATOR uses LocalStack via Testcontainers. Ensure Docker is up; otherwise features/ITs that touch AWS S3/SQS will fail or hang.
- Default region is taken from TestCloudConfig.regionOrLocation() with fallback to us-east-1.


## Adding new tests
- JUnit-based tests:
  - Place under src/test/java in the relevant module.
  - Prefer JUnit Jupiter (5) for new code. test-feature already declares junit-jupiter; test-cloud-aws tests are Jupiter-based. If you need JUnit 5 in another module, add org.junit.jupiter:junit-jupiter as testScope and ensure surefire >= 3.0.
  - Example command to run one new test:
    mvn -pl test-cloud-aws -Dtest=fully.qualified.ClassName test

- Cucumber features (provider-neutral):
  - Put .feature files under test-feature/src/test/resources/features/.
  - Implement glue in org.deveasy.test.feature.cloud (or a subpackage) so the existing @Cucumber suite picks it up.
  - If the feature uses cloud capabilities, ensure the corresponding provider adapter (e.g., test-cloud-aws) is on the test classpath of test-feature. This is already configured; adding another provider module will require a similar test-scope dependency.
  - You can parameterize provider/mode/region in scenarios using the selection steps in CloudSelectionSteps.


## Creating and running a simple test (verified workflow)
This is a minimal, non-Docker example you can use when validating your environment without pulling LocalStack.

- Create a trivial JUnit 4 or 5 test in any module that already has the respective test engine on classpath. For a quick check without Docker involvement, prefer test-core with JUnit 4.

Example (JUnit 4 in test-core):
- File: test-core/src/test/java/org/deveasy/test/core/CoreSmokeTest.java
  public class CoreSmokeTest {
      @org.junit.Test
      public void addsTwoNumbers() {
          org.junit.Assert.assertEquals(5, 2 + 3);
      }
  }

Run only this test to avoid pulling/starting Docker:
- Using Maven module targeting:
  mvn -pl test-core -DskipTests=false -Dtest=org.deveasy.test.core.CoreSmokeTest test

Clean up afterwards by deleting the temporary test file.

Note: We validated this workflow locally during documentation authoring by running a temporary unit test in test-core with only that module built.


## Adding a new cloud provider adapter
- Implement org.deveasy.test.core.cloud.spi.CloudAdapter and the capability interfaces you support.
- Wire Java ServiceLoader: create META-INF/services/org.deveasy.test.core.cloud.spi.CloudAdapter with the fully qualified class name of your adapter.
- For emulator-first strategy, add Testcontainers setup for the provider’s local emulators (or official ones) and expose client builders in an internal helper like test-cloud-aws/internal/AwsClients.
- Keep vendor SDK dependencies confined to the provider module.


## Code style, quality, and CI
- Formatting: Spotless with Google Java Format (verify phase). To apply locally:
  mvn spotless:apply
- Style: Checkstyle (checkstyle.xml). Enforced at verify phase.
- Enforcer: Java 17, dependency convergence, reproducible builds.
- Coverage: JaCoCo report + aggregate at verify.
- CI: .github/workflows/build.yml runs full verify with tests using Docker, then a separate job aggregates coverage.


## Troubleshooting tips
- Tests hang immediately after starting: ensure Docker is accessible and not blocked by corporate VPN/firewall. Try TESTCONTAINERS_CHECKS_DISABLE=true to bypass environment checks if you know Docker works.
- "No CloudAdapter found" during features: ensure the provider adapter module is on the test classpath (e.g., test-cloud-aws as test dependency in test-feature) and that META-INF/services entry exists.
- LocalStack port conflicts: Testcontainers maps random high ports; conflicts are rare. Clear stale containers with docker ps -a and docker rm -f <id> if needed.
- Java version errors: Ensure JAVA_HOME points to JDK 17 and Maven uses it.


## Notes
- Trunk-based development; keep main green. Prefer small increments. See README.md for architecture and roadmap details.
- Reproducible builds: project.build.outputTimestamp is set; keep artifacts deterministic when adding resources.
