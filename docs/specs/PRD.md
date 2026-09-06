---
title: enrich-test-api
type: prd
status: draft
created: '2026-09-06'
updated: '2026-09-06'
---

# PRD: enrich-test-api

## 0. Document Purpose

This PRD is for the maintainer and any contributor picking up work on `com.enrichmeai:enrich-test-api`. It is written against the tree at commit `075e283`, and every capability described below was executed in a green `mvn -B verify` run, not read off documentation. It is a developer-product PRD, so it carries the API-contract and dependency-policy clusters and drops the consumer-product ones. It builds on the five ADRs in `docs/adr/` rather than restating them; where an ADR already settles a decision it is referenced, not duplicated. The companion architecture spine is `docs/specs/architecture.md`.

## 1. Vision

`enrich-test-api` lets a Java test exercise real cloud service semantics without a cloud account. The test declares a capability, the library supplies an implementation backed by a local emulator, and the test never names a provider.

The point is not the emulator, which Testcontainers already provides. The point is that the capability interfaces live in a module with no vendor SDK on its classpath, so a test compiled against them carries no provider knowledge. A second provider becomes a classpath change rather than a rewrite.

It matters because the alternatives are a mock that tests itself, or a live account that is slow, costly and unavailable offline.

## 2. Target User

### 2.1 Jobs To Be Done

- When my service writes to S3, I want a test that fails if my key encoding is wrong, so that I find it before deployment rather than after.
- When I join a team, I want to run the whole test suite on my laptop offline, so that I am productive without waiting for cloud credentials.
- When I own several services, I want one testing pattern across all of them, so that I stop maintaining copies of the same container bootstrap.
- When I write acceptance criteria in Gherkin, I want steps in business language, so that a non-engineer can read the scenario.

### 2.2 Non-Users (v1)

- Teams needing Azure or GCP today. The enum declares `AZURE` and `GCP`; no adapter implements them.
- Teams that must test against a real cloud account. `CloudMode.LIVE` exists in the enum with no tested path behind it.
- Teams that cannot run Docker in CI. There is no Docker-free profile.
- Non-JVM teams. This is a Java 17 library with no wire protocol.

### 2.3 Key User Journeys

- **UJ-1. Priya writes her first integration test against S3.**
  - **Persona + context:** backend engineer on an order service, has Docker running, has never used this library.
  - **Entry state:** a Maven project with a failing hand-rolled S3 mock she wants to delete.
  - **Path:** adds the `test-core` dependency and `test-cloud-aws` at test scope; annotates a test class `@WithCloud(provider = AWS, mode = EMULATOR)`; declares a `BlobStorage` parameter on the test method; calls `ensureBucket` then `putObject`.
  - **Climax:** the run pulls LocalStack, starts it once, and the assertion on `exists` passes against a real S3 API surface.
  - **Resolution:** she deletes the mock. The test now fails when her key encoding is wrong.
  - **Edge case:** Docker is not running, so container startup fails. She needs an error that says Docker is unavailable, not a socket timeout stack trace.

- **UJ-2. Sam standardises testing across six repositories.**
  - **Persona + context:** platform engineer; each repository has its own copied LocalStack bootstrap that has drifted.
  - **Entry state:** six repositories, six different container lifecycles, three different LocalStack versions.
  - **Path:** replaces each bootstrap with the single dependency pair; deletes the per-repository container code; pins one emulator version centrally in the library.
  - **Climax:** all six suites pass with one shared lifecycle, and a LocalStack upgrade is now one version bump in one place.
  - **Resolution:** new services inherit the pattern with no copied code.

- **UJ-3. Dana drives the same infrastructure from a feature file.**
  - **Persona + context:** QA engineer who keeps acceptance criteria in Gherkin.
  - **Entry state:** a `.feature` file describing queue behaviour in business language.
  - **Path:** writes `Given cloud provider is "aws"` and `And cloud mode is "emulator"`, then queue steps that never mention SQS.
  - **Climax:** the JUnit Platform suite runs the feature against LocalStack and the scenario passes.
  - **Resolution:** the same feature file would run against a second provider by changing one Gherkin word.

## 3. Glossary

- **Capability** — a provider-neutral interface describing one service shape. Marker supertype `Capability`. Four exist: BlobStorage, Queue, PubSub, NoSqlTable.
- **CloudAdapter** — the SPI a provider implements. Supplies one instance per Capability it supports. Discovered by `ServiceLoader`. One adapter per CloudProvider.
- **CloudProvider** — the enum naming a cloud: `AWS`, `AZURE`, `GCP`. Declaring a value does not imply an adapter exists.
- **CloudMode** — `EMULATOR` or `LIVE`. Selects whether the adapter targets a local emulator or a real account.
- **TestCloudConfig** — the immutable value carrying CloudProvider, CloudMode, region or location, and project or account. Built through a builder. One per adapter initialisation.
- **Emulator** — the local container standing in for the provider. Today `localstack/localstack:3.8`.
- **Capability injection** — the JUnit 5 mechanism by which a test method parameter typed as a Capability is resolved and supplied by `CloudExtension`.
- **Provider-neutral test** — a test whose compile-time dependencies contain no vendor SDK.

## 4. Features

### 4.1 Provider-neutral capability API

**Description:** `test-core` declares the interfaces a test writes against and contains no vendor SDK. Realizes UJ-1, UJ-3. This is the load-bearing decision of the product; ADR 0001 and ADR 0005 both bind it.

**Functional Requirements:**

#### FR-1: Capability interfaces are vendor-free

A test author can compile a test against any Capability without an AWS, Azure or GCP SDK on the compile classpath. Realizes UJ-1.

**Consequences (testable):**
- `test-core`'s resolved compile scope contains no `software.amazon.awssdk`, `com.azure` or `com.google.cloud` artifact.
- `mvn -pl test-core -am verify` succeeds with no provider module in the reactor.

#### FR-2: Four capabilities are implemented

A test author can obtain BlobStorage, Queue, PubSub or NoSqlTable and exercise the operations each declares.

**Consequences (testable):**
- BlobStorage supports ensure and delete bucket, put object from bytes or stream, get, delete, list by prefix, exists.
- Queue supports ensure and delete queue, send, receive, receive with a `Duration` timeout.
- PubSub supports ensure and delete topic, ensure subscription, publish, receive, receive with timeout.
- NoSqlTable supports ensure table with partition key and optional sort key, delete table, put item, get and delete item by partition key and optional sort key, scan, query.
- Each capability has at least one integration test that passes against the emulator.

**Out of Scope:**
- `CloudServiceType.SECRETS` and `CloudServiceType.KMS`. The enum values exist; no interface stands behind them.

### 4.2 Runtime adapter discovery

**Description:** an adapter is found at runtime by `ServiceLoader`, not by a compile-time reference. Realizes UJ-2. Governed by ADR 0001.

**Functional Requirements:**

#### FR-3: Adapters are discovered, not imported

A user can make an adapter available by placing its jar on the test classpath and doing nothing else.

**Consequences (testable):**
- The AWS adapter registers under `META-INF/services/org.deveasy.test.core.cloud.spi.CloudAdapter`.
- Removing `test-cloud-aws` from the classpath leaves `test-core` compiling and its own tests passing.
- Requesting a provider with no adapter present yields an explicit failure naming the provider, not a `NoSuchElementException`.

#### FR-4: Configuration is a single immutable value

A user can express provider, mode, region or location, and project or account through one `TestCloudConfig` built by a builder, and hand it to `CloudAdapter.initialize`.

**Consequences (testable):**
- Mutating any field after build is impossible; the type exposes no setters.
- The same config object initialises any adapter.

### 4.3 JUnit 5 capability injection

**Description:** `CloudExtension` resolves Capability-typed test parameters. `@WithCloud` declares the provider and mode. Realizes UJ-1.

**Functional Requirements:**

#### FR-5: Capability parameters are injected

A test author can annotate a class `@WithCloud` and declare a Capability parameter on a test method, and receive a working instance.

**Consequences (testable):**
- A parameter typed as a supported Capability resolves.
- A parameter typed as an unsupported Capability raises `ParameterResolutionException` naming the type.
- Resources opened for the test are tracked and released after the test class.

### 4.4 Emulator lifecycle

**Description:** the AWS adapter starts one LocalStack container per JVM, shared across test classes, and lets Testcontainers stop it at shutdown. Realizes UJ-1, UJ-2.

**Functional Requirements:**

#### FR-6: One emulator per JVM, started lazily

A user running the full suite can expect the emulator to start at most once and be reused by every test class in that JVM.

**Consequences (testable):**
- Concurrent first calls produce exactly one running container; the losing thread's container is stopped.
- The container starts only when a capability is first requested, not at class load.
- The image is pinned to an exact tag, not `latest`.

**Feature-specific NFRs:**
- The emulator image must serve the AWS JSON protocol for SQS. `localstack/localstack:2.3` does not, and AWS SDK v2 requires it; this was an observed HTTP 500 on every SQS call, fixed by moving to 3.8.

### 4.5 Provider-neutral BDD glue

**Description:** Cucumber steps select a provider and mode in Gherkin, then operate capabilities in business language. Realizes UJ-3. Governed by ADR 0004.

**Functional Requirements:**

#### FR-7: Gherkin selects the provider

A feature author can write `Given cloud provider is "aws"` and `And cloud mode is "emulator"` and have the suite resolve the matching adapter.

**Consequences (testable):**
- Feature files under `test-feature/src/test/resources/features` execute in a `mvn verify` run.
- No step definition names a vendor SDK type.
- The suite runs through the JUnit Platform, requiring `junit-platform-suite-engine` at test scope; without it the suite is discovered and silently runs zero tests.

### 4.6 Enforced quality gates

**Description:** the build fails on formatting, style, dependency convergence and coverage regression. Realizes the maintainer's need for a build whose green means something.

**Functional Requirements:**

#### FR-8: A plain verify runs every gate and needs no flags

A contributor can run `mvn -B verify` and have formatting, style, build hygiene, unit tests, integration tests, BDD scenarios and coverage floors all enforced.

**Consequences (testable):**
- No skip flag is required for a green run.
- Spotless fails on any google-java-format deviation.
- Checkstyle fails on unused, redundant or star imports in main sources.
- Enforcer requires Java 17+ and full dependency convergence.
- Failsafe executes `**/*IT.java` and `**/*Suite.java`.

#### FR-9: Coverage floors ratchet and never silently fall

A maintainer can rely on the build failing if coverage drops below the level the module already achieves.

**Consequences (testable):**
- test-core enforces line 0.58 and branch 0.30.
- test-cloud-aws enforces line 0.66 and branch 0.35.
- No floor is declared where there are no classes to measure.

**Notes:** `[NOTE FOR PM]` these floors are measured values, not the target. The target is line 0.80 and branch 0.70 and neither module meets it. Raising the floors is Epic 1.

#### FR-10: A supply-chain scan is available and never blocks a plain build

A maintainer can run an OWASP Dependency-Check audit on demand, and a normal build never depends on an NVD API key.

**Consequences (testable):**
- `mvn -B verify` does not invoke dependency-check at all.
- `mvn -B -Powasp org.owasp:dependency-check-maven:check` runs it at the version declared in the POM, with no version repeated in CI.
- CI skips the audit and annotates the run when `NVD_API_KEY` is absent.

## 5. Non-Goals (Explicit)

- This is not a mocking library. It does not stub the AWS SDK.
- This is not a cloud abstraction layer for production code. The capabilities are shaped for tests; they expose no retry, pagination or credential strategy.
- This is not a full cloud API surface. The capabilities are deliberately small.
- This does not manage cloud credentials or infrastructure. `LIVE` mode is unimplemented in practice.
- This is not published to Maven Central, and packaging for Central is out of scope.

## 6. MVP Scope

### 6.1 In Scope

- Four capabilities against AWS in `EMULATOR` mode.
- `ServiceLoader` adapter discovery and immutable `TestCloudConfig`.
- JUnit 5 `@WithCloud` capability injection.
- Provider-neutral Cucumber glue and feature files.
- Enforced gates: Spotless, Checkstyle, Enforcer, JaCoCo floors, Failsafe. Error Prone and OWASP under profiles.
- Two GitHub Actions workflows on `main`.

### 6.2 Out of Scope for MVP

- Azure and GCP adapters. Deferred to v0.5 and v0.6; the SPI is designed for them but nothing validates it with a second implementation.
- `CloudMode.LIVE`. Deferred; needs a credential story and a cost story.
- `SECRETS` and `KMS` capabilities. Deferred; enum values exist as placeholders.
- Maven Central publishing. Deferred.
- Reaching line 0.80 and branch 0.70 coverage. Deferred to Epic 1; the gap is large and honest floors are in place meanwhile.
- Renaming Java packages from `org.deveasy.*` to match the `com.enrichmeai` groupId. `[NOTE FOR PM]` this inconsistency is visible to any consumer reading an import; it is a breaking change across every source file and wants its own decision.

## 7. Success Metrics

**Primary**
- **SM-1**: Time from empty project to first passing capability test, using the README alone, under 15 minutes. Validates FR-1, FR-5.
- **SM-2**: A second provider adapter is added with zero changes to `test-core` and zero changes to existing provider-neutral tests. Validates FR-1, FR-3.

**Secondary**
- **SM-3**: Coverage floors move upward over time and never downward. Validates FR-9.
- **SM-4**: A plain `mvn -B verify` stays green on a clean machine with only Docker as a prerequisite. Validates FR-8.

**Counter-metrics (do not optimize)**
- **SM-C1**: Number of capability methods. Growing the API surface to look complete would defeat the small-interface decision in ADR 0003. Counterbalances SM-2.
- **SM-C2**: Coverage percentage reached by tests that assert nothing. Raising the JaCoCo number without raising defect detection is worse than a low honest number. Counterbalances SM-3.

## 8. Open Questions

1. Do the Java packages move from `org.deveasy.*` to `com.enrichmeai.*`? It is a breaking change and currently inconsistent with the groupId.
2. What is the second provider, Azure or GCP? The SPI is unproven until one exists.
3. Does `CloudMode.LIVE` stay in the enum while unimplemented, or come out until it works? Today it is a promise the code does not keep.
4. Should `SECRETS` and `KMS` remain in `CloudServiceType` with no interface behind them?
5. Who owns the `NVD_API_KEY` secret, and is the supply-chain audit meant to gate merges or only report?
6. Is GitHub Pages meant to serve this private alpha's README on the company apex domain?

## 9. Assumptions Index

- §2.1 — assumed the standardisation job to be real based on the repository's own history of copied bootstrap code, not on user interviews.
- §7 SM-1 — the 15 minute target is a proposed goal, not a measured one.
- §4.3 — assumed capability tracking releases resources per test class; verified in code but not covered by a test at current coverage levels.
