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
| version | `0.3.0-alpha1-SNAPSHOT` on `main`; the first release will be `0.3.0-alpha1` |
| modules | `test-core`, `test-cloud-aws`, `test-feature` |

The Java packages are `com.enrichmeai.*`, matching the groupId. They were `org.deveasy.*`
until 2026-09-09; the rename is free while nothing is published and permanent afterwards, which
is why it landed before the first release. See [ADR 0008](docs/adr/0008-move-the-java-packages-to-com-enrichmeai.md).


## Project structure (modules)

| Module | Contents |
| --- | --- |
| `test-core` | Provider-agnostic capability interfaces, `TestCloudConfig`, the `CloudAdapter` SPI, and the JUnit 5 `@WithCloud` extension. No vendor SDKs. |
| `test-cloud-aws` | AWS adapter: AWS SDK v2 plus Testcontainers/LocalStack. Implements BlobStorage (S3), Queue (SQS), PubSub (SNS+SQS) and NoSqlTable (DynamoDB). |
| `test-feature` | Provider-neutral Cucumber glue and JUnit Platform suites. No main sources; everything lives under `src/test`. |

Key directories:
- `test-core/src/main/java/com/enrichmeai/test/core/cloud/` core API, config, capabilities
- `test-feature/src/test/java/com/enrichmeai/test/feature/cloud/` Cucumber glue, suites, scenario state
- `test-feature/src/test/resources/features/` provider-neutral feature files
- `test-cloud-aws/src/main/java/com/enrichmeai/test/cloud/aws/` AWS adapter and client wiring
- `docs/adr/` architecture decision records


## Architecture

Capability interfaces live in `test-core`: `BlobStorage`, `Queue`, `PubSub`, `NoSqlTable`. Runtime configuration is a single immutable `TestCloudConfig` carrying provider, mode, region and overrides.

Adapters are discovered with `java.util.ServiceLoader`. An adapter implements `com.enrichmeai.test.core.cloud.spi.CloudAdapter` and registers under `META-INF/services/`. Putting `test-cloud-aws` on the classpath is enough for the AWS adapter to be found. See [ADR 0001](docs/adr/0001-use-service-provider-interface.md).

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
today. Three are genuine decisions only the maintainer can settle — the second provider, the
`NVD_API_KEY` owner and audit policy, and the shape of the connection accessor — and are left
at epic grain rather than expanded into invented answers.

A further three read as decisions but are not, because the library is unpublished with no
consumers: the package rename, removing `CloudMode.LIVE`, and removing `SECRETS` and `KMS`
were only ever weighed against a breaking-change cost that does not exist yet. They are free
work. Only the package rename has a deadline, since publishing makes it permanent.
`implementation-readiness.md` has the full list.

The specs describe the tree as it is, with gaps named as gaps. Architecture decision
records remain in `docs/adr/`.


## Releasing

Releases go to Maven Central through the Sonatype Central Portal, from a tag, on JDK 17.
The mechanics are recorded in [ADR 0009](docs/adr/0009-release-to-maven-central-through-the-central-portal.md);
this is the operator's view.

**Once, before the first release** — none of this can be done from the repository:

1. Verify the `com.enrichmeai` namespace on [central.sonatype.com](https://central.sonatype.com)
   (a TXT record on `enrichmeai.com`).
2. Generate a Portal **user token** and store it as the repository secrets
   `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD`.
3. Create a signing key, publish its public half, and store the private half:

   ```sh
   gpg --quick-gen-key "enrichmeai release <joseph.a.aruja@gmail.com>" rsa4096 sign 2y
   gpg --list-keys --keyid-format long          # note the key id
   gpg --keyserver keyserver.ubuntu.com --send-keys <key id>
   gpg --armor --export-secret-keys <key id> | gh secret set GPG_PRIVATE_KEY
   gh secret set GPG_PASSPHRASE                  # the passphrase you chose
   ```

**Every release:**

```sh
git tag v0.3.0-alpha1 <commit that is green on build and quality-gates>
git push origin v0.3.0-alpha1
```

The `release` workflow builds on Temurin 17, runs the full verify including the LocalStack
tests, signs everything, uploads the bundle for `enrich-test-api`, `test-core` and
`test-cloud-aws` (not `test-feature`, which has no main sources), and creates a GitHub
release. The Portal validates the bundle and holds it: **publishing is a manual click on
central.sonatype.com** while `autoPublish` is `false` in the root POM. A plain `mvn -B verify`
never activates the `release` profile and needs none of the secrets.

To rehearse locally without uploading anything, run the profile up to signing with a
throwaway key:

```sh
export JAVA_HOME=<a JDK 17>
MAVEN_GPG_PASSPHRASE=<your key's passphrase> mvn -B -Prelease -pl '!test-feature' verify
ls test-core/target/*.asc
```


## Before a public release

Nothing here is published to any registry, and this section is what would have to be true
first. It is not a roadmap with dates; it is the list of things that are currently wrong or
undecided, kept honest against the backlog in [epics.md](docs/specs/epics.md).

**Defects that were fixed.** The library used to misbehave in exactly the case it is aimed at:
several test classes in one run. The emulator container is shared for the whole JVM, the
JUnit extension never released topics or tables, and `ensureTable` returned without checking
that an existing table's key schema matched the one requested — so a second test class could
silently inherit the first's table, and the failure surfaced later in `putItem`, which has no
catch, as a raw SDK `ValidationException`. Epic 7 closed all three: the extension now tracks
and releases every capability's resources, a release that fails is logged with the resource
name and cause instead of being swallowed, and `ensureTable` fails fast with a message naming
the table and both schemas. Two questions from that work are still open — whether a failed
teardown should fail the build, and whether a schema mismatch should recreate the table
rather than fail — and both are recorded in the Epic 7 story files.

**Free work, cheap now and not later.** Nothing is published and there are no consumers, so
several things usually treated as breaking changes cost nothing today. The Java packages now
match the `com.enrichmeai` coordinates (Story 2.2, [ADR 0008](docs/adr/0008-move-the-java-packages-to-com-enrichmeai.md)),
which was the one item with a hard deadline: permanent after publication, since no later change
rescues a consumer's `import` statements. Still open: take `CloudMode.LIVE` out until it works
(Story 4.1) and drop `SECRETS` and `KMS` from `CloudServiceType` (Story 6.4); both are declared
but unimplemented, and removing them breaks nobody.

**Housekeeping.** The version is `0.3.0-alpha1-private.1`, which cannot go to a public
registry as it stands, and the POM still carries OSSRH publishing configuration pointing at a
decommissioned host, which would have to go before any modern publishing setup arrives
(Story 6.3).

**A product question rather than a technical one.** The framework gap described above is
Epic 8. It is not blocked by compatibility — an accessor can be added at any time — but with no
users the useful question is not what breaks existing consumers, it is what makes a first
install worth doing. Most Java engineers testing cloud-backed services are on Spring Boot, and
today they would find they cannot point their application context at the emulator and go back
to Testcontainers. Whether that makes it a launch feature is a judgement, not a deduction.

**Known and deliberately not blocking.** Coverage sits below its target, and `CloudExtension`
— the injection path every user touches — is the thinnest part of it at 12 of 42 branches
covered (Epic 1). One provider, emulator only, so the portability claim is a design intention
rather than a demonstrated property (Epic 3).

The full readiness assessment, including the decisions that are open, is in
[implementation-readiness.md](docs/specs/implementation-readiness.md).


## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). In short: branch, keep `mvn -B verify` green, use conventional commit messages, and sign off your commits.


## License

Apache License 2.0. See [LICENSE](LICENSE).
