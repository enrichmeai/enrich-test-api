# enrich-test-api

[![Build](https://github.com/enrichmeai/enrich-test-api/actions/workflows/build.yml/badge.svg)](https://github.com/enrichmeai/enrich-test-api/actions/workflows/build.yml)
[![Quality gates](https://github.com/enrichmeai/enrich-test-api/actions/workflows/quality-gates.yml/badge.svg)](https://github.com/enrichmeai/enrich-test-api/actions/workflows/quality-gates.yml)
![Java](https://img.shields.io/badge/Java-17-blue)
![License](https://img.shields.io/badge/License-Apache_2.0-green)

A toolkit for testing Java **cloud interactions** against local emulators rather than real cloud accounts.

The core defines small, provider-neutral capability interfaces. Provider adapters implement them and keep the vendor SDKs to themselves. Tests written against the core do not name a cloud provider.

**What it does and does not do.** A test asks for a `BlobStorage` or a `Queue` and drives it directly, so you can assert that your code puts the right bytes in the right bucket. It does **not** configure your application under test: nothing in the provider-neutral API exposes an endpoint or a credential, so you cannot point a Spring Boot, Quarkus or Micronaut context at the emulator this library starts. For that today you would use Testcontainers' LocalStack module directly. Closing the gap is [Epic 8](docs/specs/epics.md) and needs an SPI decision first.

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

Both workflows build on **JDK 17**, matching the release target, so a contributor's local `mvn -B verify` is the same build CI runs. An automated upgrade proposed moving everything to 21; it was declined, because nothing in the code uses a feature later than 17 and raising the target only narrows who can adopt the library. The conditions under which that would be revisited — after Epic 1, as a 17 + 21 matrix — are recorded in [ADR 0007](docs/adr/0007-decline-the-java-21-bump.md).


## Specifications

BMAD Method 6.12.0 is installed in this repo. The planning artifacts live in `docs/specs/`:

| Document | Contents |
| --- | --- |
| [product-brief.md](docs/specs/product-brief.md) | Problem, solution, users, scope |
| [PRD.md](docs/specs/PRD.md) | Glossary, user journeys, FR-1 to FR-10, non-goals, open questions |
| [architecture.md](docs/specs/architecture.md) | Ports-and-adapters spine, AD-1 to AD-9, stack, dependency-direction diagram |
| [epics.md](docs/specs/epics.md) | Eight epics of work not yet done, including the coverage gap and the framework-support gap |
| [implementation-readiness.md](docs/specs/implementation-readiness.md) | Which stories can be built now, and the decisions that block the rest |
| [implementation/](docs/specs/implementation/) | Expanded story files for the unblocked work, and `sprint-status.yaml` |

Eight of the twenty-nine stories are expanded to file-and-line detail and can be picked up
today. Seven are decisions only the maintainer can make, so they are deliberately left at
epic grain rather than expanded into invented answers; `implementation-readiness.md` lists
them. Three of those decisions — the package rename, the connection-accessor shape, and any
Maven Central release — are cheap while the library is unpublished with one adapter and
permanent afterwards.

The specs describe the tree as it is, with gaps named as gaps. Architecture decision
records remain in `docs/adr/`.


## Before a public release

Nothing here is published to any registry, and this section is what would have to be true
first. It is not a roadmap with dates; it is the list of things that are currently wrong or
undecided, kept honest against the backlog in [epics.md](docs/specs/epics.md).

**Defects that must be fixed.** The library misbehaves in exactly the case it is aimed at:
several test classes in one run. The emulator container is shared for the whole JVM, the
JUnit extension never releases topics or tables, and `ensureTable` returns without checking
that an existing table's key schema matches the one requested — so a second test class can
silently inherit the first's table, and the failure surfaces later in `putItem`, which has no
catch, as a raw SDK `ValidationException`. The test that breaks is not the test that caused
it. That is Epic 7, and it is the clearest blocker.

**One decision that becomes permanent.** The Java packages are `org.deveasy.*` while the
coordinates are `com.enrichmeai`. Renaming is free today and irreversible after publication,
because no later change rescues a consumer's `import` statements. That is Story 2.1.

**Housekeeping.** The version is `0.3.0-alpha1-private.1`, which cannot go to a public
registry as it stands, and the POM still carries OSSRH publishing configuration pointing at a
decommissioned host, which would have to go before any modern publishing setup arrives
(Story 6.3).

**Known and deliberately not blocking.** Coverage sits below its target, and `CloudExtension`
— the injection path every user touches — is the thinnest part of it at 12 of 42 branches
covered (Epic 1). `CloudMode.LIVE` is declared and branched on but untested and unguarded
(Epic 4). One provider, emulator only, so the portability claim is a design intention rather
than a demonstrated property (Epic 3). The framework-connection gap described above is Epic 8;
it is not release-blocking, because `CloudAdapter` is a plain interface on Java 17 and an
accessor can be added later as a `default` method without breaking implementers.

The full readiness assessment, including the decisions that are open, is in
[implementation-readiness.md](docs/specs/implementation-readiness.md).


## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). In short: branch, keep `mvn -B verify` green, use conventional commit messages, and sign off your commits.


## License

Apache License 2.0. See [LICENSE](LICENSE).
