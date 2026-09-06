# enrich-test-api

[![Build](https://github.com/enrichmeai/enrich-test-api/actions/workflows/build.yml/badge.svg)](https://github.com/enrichmeai/enrich-test-api/actions/workflows/build.yml)
[![Quality gates](https://github.com/enrichmeai/enrich-test-api/actions/workflows/quality-gates.yml/badge.svg)](https://github.com/enrichmeai/enrich-test-api/actions/workflows/quality-gates.yml)
![Java](https://img.shields.io/badge/Java-17-blue)
![License](https://img.shields.io/badge/License-Apache_2.0-green)

A toolkit for testing Java applications against cloud services, using local emulators rather than real cloud accounts.

The core defines small, provider-neutral capability interfaces. Provider adapters implement them and keep the vendor SDKs to themselves. Tests written against the core do not name a cloud provider.

Status: early-stage private alpha. AWS is the only provider, and only in emulator mode.


## Coordinates

| | |
| --- | --- |
| groupId | `com.enrichmeai` |
| artifactId | `enrich-test-api` (parent) |
| version | `0.3.0-alpha1-private.1` |
| modules | `test-core`, `test-cloud-aws`, `test-feature` |

The Java packages are still `org.deveasy.*`. Renaming them is a breaking API change
across every source file and is deliberately not part of the coordinate rebrand.


## Project structure (modules)

| Module | Contents |
| --- | --- |
| `test-core` | Provider-agnostic capability interfaces, `TestCloudConfig`, the `CloudAdapter` SPI, and the JUnit 5 `@WithCloud` extension. No vendor SDKs. |
| `test-cloud-aws` | AWS adapter: AWS SDK v2 plus Testcontainers/LocalStack. Implements BlobStorage (S3), Queue (SQS), PubSub (SNS+SQS) and NoSqlTable (DynamoDB). |
| `test-feature` | Provider-neutral Cucumber glue and JUnit Platform suites. No main sources; everything lives under `src/test`. |

Key directories:
- `test-core/src/main/java/org/deveasy/test/core/cloud/` core API, config, capabilities
- `test-feature/src/test/java/org/deveasy/test/feature/cloud/` Cucumber glue, suites, scenario state
- `test-feature/src/test/resources/features/` provider-neutral feature files
- `test-cloud-aws/src/main/java/org/deveasy/test/cloud/aws/` AWS adapter and client wiring
- `docs/adr/` architecture decision records


## Architecture

Capability interfaces live in `test-core`: `BlobStorage`, `Queue`, `PubSub`, `NoSqlTable`. Runtime configuration is a single immutable `TestCloudConfig` carrying provider, mode, region and overrides.

Adapters are discovered with `java.util.ServiceLoader`. An adapter implements `org.deveasy.test.core.cloud.spi.CloudAdapter` and registers under `META-INF/services/`. Putting `test-cloud-aws` on the classpath is enough for the AWS adapter to be found. See [ADR 0001](docs/adr/0001-use-service-provider-interface.md).

Emulators come first. The AWS adapter starts a LocalStack container through Testcontainers, so a test run needs Docker but no cloud credentials. See [ADR 0002](docs/adr/0002-emulator-first-testing.md).


## Quick start

Requirements:
- Java 17
- Maven 3.9+
- Docker running, for the emulator-backed tests

Build and run everything:

```bash
mvn -B verify
```

That runs unit tests, the LocalStack integration tests, the Cucumber suites, and every quality gate. There are no skip flags to add.

To run only the AWS module:

```bash
mvn -B -pl test-cloud-aws -am verify
```

Testcontainers pulls `localstack/localstack:3.8` on the first run, which is roughly 1.3 GB.

Select a provider and mode from a feature file:

```gherkin
Given cloud provider is "aws"
And cloud mode is "emulator"
And cloud region is "eu-west-1"
```


## What runs in a build

| Suite | Where | Count |
| --- | --- | --- |
| Unit tests (Surefire) | test-core, test-feature | 8 |
| Integration tests (Failsafe) | test-cloud-aws, against LocalStack | 6 |
| Cucumber scenarios (Failsafe) | test-feature, against LocalStack | 7 |

The `*IT` and `*Suite` classes are picked up by `maven-failsafe-plugin`, configured in the root POM. Surefire's default includes do not match either naming pattern.


## Quality gates

These are the gates as the POM enforces them today.

| Gate | Tool | Enforced at | Behaviour |
| --- | --- | --- | --- |
| Formatting | Spotless, google-java-format | `verify` | Fails on any deviation. `mvn spotless:apply` fixes it. |
| Style | Checkstyle 3.6.0 | `verify` | Fails on violations. Rules are import hygiene only: unused, redundant and star imports. Main sources only. |
| Build hygiene | Maven Enforcer | `validate` | Java 17 or above, and full dependency convergence. |
| Coverage | JaCoCo | `verify` | Per-module floors, see below. |
| Static analysis | Error Prone | `-Perrorprone` only | Findings at ERROR fail the build. Currently only WARN findings exist. |
| Supply chain | OWASP Dependency-Check | `-Powasp` only | Not part of a plain `verify`; it needs an NVD API key. |

### Coverage floors

JaCoCo floors are set per module to the ratios each module actually reaches. They are a ratchet against regression, not a target that has been met.

| Module | Line covered | Line floor | Branch covered | Branch floor |
| --- | --- | --- | --- | --- |
| test-core | 104/178, 0.58 | 0.58 | 14/46, 0.30 | 0.30 |
| test-cloud-aws | 344/516, 0.66 | 0.66 | 81/228, 0.35 | 0.35 |
| test-feature | no main sources | none | no main sources | none |

The project target remains line 0.80 and branch 0.70. Neither module meets it. Raise the floors as tests are added; do not lower them.


## Continuous integration

GitHub Actions, two workflows, both triggered on pushes and pull requests against `main`.

- `build.yml` runs `mvn -B verify` on an Ubuntu runner with Docker, then uploads the Surefire and Failsafe reports and the JaCoCo HTML.
- `quality-gates.yml` runs Spotless and Checkstyle, then Enforcer, then Error Prone, then the OWASP audit.

The OWASP job is skipped unless an `NVD_API_KEY` secret is present on the repository, because Dependency-Check cannot build its database without one. The job annotates the run when it skips.


## Specifications

BMAD Method 6.12.0 is installed in this repo. The planning artifacts live in `docs/specs/`:

| Document | Contents |
| --- | --- |
| [product-brief.md](docs/specs/product-brief.md) | Problem, solution, users, scope |
| [PRD.md](docs/specs/PRD.md) | Glossary, user journeys, FR-1 to FR-10, non-goals, open questions |
| [architecture.md](docs/specs/architecture.md) | Ports-and-adapters spine, AD-1 to AD-9, stack, dependency-direction diagram |
| [epics.md](docs/specs/epics.md) | Six epics of work not yet done, including the coverage gap |

The specs describe the tree as it is, with gaps named as gaps. Architecture decision
records remain in `docs/adr/`.


## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). In short: branch, keep `mvn -B verify` green, use conventional commit messages, and sign off your commits.


## License

Apache License 2.0. See [LICENSE](LICENSE).
