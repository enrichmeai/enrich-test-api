# Changelog

## [Unreleased]

### Fixed
- The build compiles again. The 2018 legacy step definitions in `test-feature/src/main`
  and `test-core`'s `ResourceHelper` referenced dependencies that had been removed from
  the root POM, so `mvn verify` failed at compile.
- Integration tests now run. The `*IT` classes and the Cucumber `*Suite` matched no
  configured plugin, so none of them had ever executed; `maven-failsafe-plugin` is now
  bound to `integration-test`/`verify`.
- SQS against LocalStack. AWS SDK v2 uses the JSON protocol for SQS, which
  `localstack/localstack:2.3` does not serve; the emulator image is now 3.8.
- `maven-checkstyle-plugin` runs. It was failing to initialise, and `checkstyle.xml`
  declared a `FileExtensions` module that does not exist.
- The `errorprone` profile runs. Error Prone was listed under the compiler plugin's
  `<dependencies>`, which does not place it on javac's processor path.
- Both GitHub Actions workflows trigger. They listened for `main` while the default
  branch was `master`, so neither had ever run.

### Changed
- **BREAKING: Maven coordinates.** The groupId and the parent artifactId both change.
  Consumers must update their dependency declarations.

  | | Before | After |
  | --- | --- | --- |
  | groupId | `org.deveasy` | `com.enrichmeai` |
  | artifactId (parent) | `dev-easy-test` | `enrich-test-api` |

  `com.enrichmeai` follows `enrichmeai.com`, the domain this project demonstrably
  owns and the one the organisation's site is served on. Module artifactIds
  (`test-core`, `test-cloud-aws`, `test-feature`) are unchanged.
- **The repository is renamed** `dev-easy-test-api` to `enrich-test-api`. GitHub
  redirects the old URLs. Badge, SCM and clone URLs are updated.
- **Java packages are deliberately NOT renamed.** They remain `org.deveasy.*` and no
  longer match the groupId. Renaming them is a breaking change across every source
  file and would also rename both
  `META-INF/services/org.deveasy.test.core.cloud.spi.CloudAdapter` registration
  files, whose names derive from the interface's fully qualified name. Tracked as
  Epic 2 in `docs/specs/epics.md`; the decision is open.
- Dependency versions are centralised in the parent as BOM imports (AWS SDK v2,
  Testcontainers, JUnit, Cucumber, Jackson), per ADR 0005.
- OWASP Dependency-Check moved out of the default lifecycle into an `owasp` profile,
  at one version shared by the POM and CI.
- JaCoCo coverage floors are now per module and set to measured values. See README.
- Story 6.2's premise in `docs/specs/epics.md` was wrong and is corrected. It claimed the
  `.gitignore` patterns `src/main/resources/` and `src/test/resources/` matched at every
  depth. A pattern with a mid-pattern separator is anchored to its own directory, so both
  are root-anchored and inert in this multi-module layout. Verified with
  `git check-ignore -v --no-index`. The lines are still dead and still misleading, which is
  why the story survives.
- Story 1.2's acceptance criteria described `AwsDynamoDB` surfacing provider errors. It
  swallows them and returns `null` or an empty list. The criteria now match the code.
- The PRD's provenance pointed at a branch that has since been merged and deleted; it now
  tracks `main` at `3f31287`. PRD open question 6 is marked resolved, pointing at ADR 0006.
- Story 7.3 blamed the wrong line and is retitled. It claimed `AwsDynamoDB`'s static `KEYS`
  map went stale because `deleteTable` does not evict it. It does not: `ensureTableInternal`
  calls `cacheKeysFromDescribe` on the table-exists path, which re-reads the live schema. The
  real defect is that `ensureTable` returns without comparing the existing table's key schema
  to the one requested, so a second test class silently inherits the first's table and the
  failure surfaces later in `putItem` — which has no catch — as a raw `ValidationException`.
- The README's opening line said "A toolkit for testing Java applications against cloud
  services", which describes integration-testing an application. The library tests cloud
  interactions. Corrected, and the limitation added to the PRD's Non-Users list where it is
  the largest practical exclusion.

### Added
- BMAD Method 6.12.0 and four planning artifacts under `docs/specs/`: product brief,
  PRD, architecture spine and epics.
- ADR 0006 records that GitHub Pages is disabled for this repository, and why. The
  decision had been taken but existed only in a pull request in another repository.
- `docs/specs/implementation/`: expanded story files for the eight stories that can be
  built without a maintainer decision, plus a generated `sprint-status.yaml` covering all
  29 stories.
- `docs/specs/implementation-readiness.md`: the readiness verdict, and the open decisions
  that block the rest of the backlog.
- Epic 7 in `docs/specs/epics.md`, from three defects found while reading the code the
  coverage stories point at: `CloudExtension` never tracks or releases topics and tables,
  its cleanup swallows `Throwable` silently, and `AwsDynamoDB` caches table key schemas in
  a static map that `deleteTable` does not evict.
- Epic 8, recording that a framework application cannot reach the emulator. Nothing in the
  provider-neutral API exposes an endpoint or a credential, so a Spring Boot, Quarkus or
  Micronaut context under test cannot be pointed at the emulator this library starts. The
  only route is `org.deveasy.test.cloud.aws.internal.LocalStackHolder`, which costs the
  provider neutrality the library exists for. Three candidate shapes for a connection
  accessor are set out; none is chosen. This is new capability, not repair, and remains a
  scope expansion for the maintainer to sign off. It is not release-blocking: `CloudAdapter`
  is a plain interface on Java 17, so the accessor can arrive later as a `default` method
  without breaking implementers.
- Stories 1.5 and 1.6, because Epic 1's coverage arithmetic left no margin on either
  module. In `test-cloud-aws`, stories 1.2 and 1.3 expose 98 uncovered branches against a
  need of 79; in `test-core`, story 1.1 reaches 50 uncovered lines against a need of 39.
  Each new story takes the cheap remainder so neither target rests on one story delivering
  four fifths of everything it touches.

### Removed
- The 18 legacy 2018 classes under `test-feature/src/main/java/org/deveasy/test/feature/`
  (`steps/`, `state/`, `config/`, `types/`) and `test-core`'s `ResourceHelper`.

## [0.3.0-alpha1-private.1] - 2025-11-13

### Added
- AWS BlobStorage (S3) capability with LocalStack support
- AWS Queue (SQS) capability with delete-on-receive semantics
- AWS PubSub (SNS+SQS) capability
- AWS NoSqlTable (DynamoDB) capability
- Core SPI for cloud provider adapters
- JUnit 5 @WithCloud extension for capability injection
- Cucumber step definitions for all capabilities
- Quality gates: JaCoCo coverage, Spotless, Checkstyle, Error Prone, OWASP

### Changed
- Migrated from Cucumber 6 to Cucumber 7
- Upgraded to JUnit 5 Jupiter
- Modernized all dependencies

### Known Limitations
- Only AWS provider implemented
- EMULATOR mode only (`CloudMode.LIVE` needs additional testing)
- No Azure or GCP adapters yet
