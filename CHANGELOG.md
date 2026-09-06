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
- Dependency versions are centralised in the parent as BOM imports (AWS SDK v2,
  Testcontainers, JUnit, Cucumber, Jackson), per ADR 0005.
- OWASP Dependency-Check moved out of the default lifecycle into an `owasp` profile,
  at one version shared by the POM and CI.
- JaCoCo coverage floors are now per module and set to measured values. See README.

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
- EMULATOR mode only (REAL mode needs additional testing)
- No Azure or GCP adapters yet
