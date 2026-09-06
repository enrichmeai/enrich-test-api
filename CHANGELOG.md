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
  owns and the one on its GitHub Pages certificate. Module artifactIds
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

### Added
- BMAD Method 6.12.0 and four planning artifacts under `docs/specs/`: product brief,
  PRD, architecture spine and epics.

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
