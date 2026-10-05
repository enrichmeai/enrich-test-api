# Project Status

Last updated: 2026-10-05

## Snapshot

- `0.3.0-alpha1` is on Maven Central (published 2026-10-04, ADR 0009, 0010): `com.enrichmeai:test-core` and `com.enrichmeai:test-cloud-aws`. `main` is `0.3.0-alpha2-SNAPSHOT`.
- Java 17, Maven multi-module. `mvn -B verify` passes locally with Docker running and no skip flags.
- Cloud SPI in `test-core`; one provider adapter, `test-cloud-aws`.
- Four capabilities implemented against LocalStack: BlobStorage (S3), Queue (SQS), PubSub (SNS+SQS) and NoSqlTable (DynamoDB).
- CI is GitHub Actions only. `build` (the full `verify`), `quality-gates` and `examples` (the Spring Boot and Quarkus projects) run on every pull request and on `main`. `release` runs when `pom.xml` changes on `main`, or by hand (ADR 0010). `claude` is the issue builder.

## What runs in a build

| Suite | Runner | Count |
| --- | --- | --- |
| Unit tests | Surefire | 26 (test-core 17, test-cloud-aws 4, test-feature 5) |
| Integration tests against LocalStack | Failsafe | 14, in 7 classes |
| Cucumber scenarios against LocalStack | Failsafe | 7 |

Counted from the "Tests run" lines of `build` run 37221033703 on `main` (`d4f0f65`), 2026-10-04.

Before September 2026 the integration tests and the Cucumber suite matched no configured plugin and had never executed. Wiring `maven-failsafe-plugin` exposed a genuine SQS failure against `localstack/localstack:2.3`, because AWS SDK v2 speaks the JSON protocol to SQS and that image does not serve it. The emulator image is now 3.8.

## Quality gates, as configured

| Gate | Enforced at | Behaviour |
| --- | --- | --- |
| Spotless, google-java-format | `verify` | Fails on deviation |
| Checkstyle 3.6.0 | `verify` | Fails on violation; import hygiene rules only, main sources |
| Maven Enforcer | `validate` | Java 17+, dependency convergence |
| JaCoCo | `verify` | Per-module floors at measured values |
| Error Prone | `-Perrorprone` | ERROR findings fail; only WARN findings exist today |
| OWASP Dependency-Check | `-Powasp` | Excluded from a plain `verify`; needs an NVD API key |

## Coverage

| Module | Line | Branch |
| --- | --- | --- |
| test-core | 104/178, 0.58 | 14/46, 0.30 |
| test-cloud-aws | 344/516, 0.66 | 81/228, 0.35 |
| test-feature | no main sources | no main sources |

Floors are set to these measured values. The target of line 0.80 and branch 0.70 is not met. The largest single gap is `CloudExtension` in test-core at 12 of 42 branches, and the error-handling paths of `AwsDynamoDB` at 43 of 130.

## Known limitations

- AWS is the only provider. There is no Azure or GCP adapter.
- `CloudMode.EMULATOR` only. `CloudMode.LIVE` is declared in the enum but no adapter path is tested against a real account.
- Docker is required for `mvn verify`; there is no Docker-free profile.
- The OWASP audit does not run in CI until an `NVD_API_KEY` secret is added.

## Architecture decisions

See `docs/adr/`: SPI for adapters (0001), emulator-first testing (0002), capability interfaces (0003), Cucumber integration (0004), dependency modernization (0005), GitHub Pages disabled (0006), Java 17 kept (0007), packages moved to `com.enrichmeai` (0008), Maven Central through the Central Portal (0009), release on the merge of a release PR (0010), connection properties as a flat map (0011).

## Verifying locally

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/17.0.10-tem"
mvn -B verify
```

Docker must be running. The first run pulls the LocalStack image, roughly 1.3 GB.
