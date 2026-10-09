# Project Status

Last updated: 2026-10-06

## Snapshot

- `0.3.0-alpha2` is on Maven Central (published 2026-10-07; `0.3.0-alpha1` on 2026-10-04; ADR 0009, 0010): `com.enrichmeai:test-core` and `com.enrichmeai:test-cloud-aws`. `main` is `0.3.0-alpha3-SNAPSHOT`.
- Java 17, Maven multi-module. `mvn -B verify`, with Docker and no skip flags, is green on `main` in `build` run 37221033703 (`d4f0f65`).
- Cloud SPI in `test-core`; one provider adapter, `test-cloud-aws`.
- Four capabilities implemented against LocalStack: BlobStorage (S3), Queue (SQS), PubSub (SNS+SQS) and NoSqlTable (DynamoDB).
- CI is GitHub Actions only. `build` (the full `verify`), `quality-gates` and `examples` (the Spring Boot and Quarkus projects) run on every pull request and on `main`. `release` runs when `pom.xml` changes on `main`, or by hand (ADR 0010). `claude` is the issue builder.

## What runs in a build

| Suite | Runner | Count |
| --- | --- | --- |
| Unit tests | Surefire | 49 (test-core 32, test-cloud-aws 12, test-feature 5) |
| Integration tests against LocalStack | Failsafe | 29, in 10 classes |
| Cucumber scenarios against LocalStack | Failsafe | 7 |

Counted from the "Tests run" lines of `build` run 37501463684 on `main` (`fcc90a4`), 2026-10-06.

Before September 2026 the integration tests and the Cucumber suite matched no configured plugin and had never executed. Wiring `maven-failsafe-plugin` exposed a genuine SQS failure against `localstack/localstack:2.3`, because AWS SDK v2 speaks the JSON protocol to SQS and that image does not serve it. The emulator image is now 3.8.

## Quality gates, as configured

| Gate | Enforced at | Behaviour |
| --- | --- | --- |
| Spotless, google-java-format | `verify` | Fails on deviation |
| Checkstyle 3.6.0 | `verify` | Fails on violation; import hygiene rules only, main sources |
| Maven Enforcer | `validate` | Java 17+, dependency convergence |
| JaCoCo | `verify` | Per-module floors at the measured values, truncated to two places (see Coverage) |
| Error Prone | `-Perrorprone` | ERROR findings fail; the `Error Prone` job is green in `quality-gates` run 37358430621 |
| OWASP Dependency-Check | `-Powasp` | Excluded from a plain `verify`; needs an NVD API key |

## Coverage

| Module | Line | Branch |
| --- | --- | --- |
| test-core | 224/224, 1.00 | 42/42, 1.00 |
| test-cloud-aws | 495/535, 0.93 | 179/240, 0.75 |
| test-feature | no main sources | no main sources |

Read from the "Coverage totals (JaCoCo)" step of `build` run 37501463684 on `main` (`fcc90a4`), 2026-10-06, after Epic 1's tests (#30–#34). Both modules meet the target of line 0.80 and branch 0.70. The floors in the POMs are these values truncated to two places (test-core 1.00 / 1.00, test-cloud-aws 0.92 / 0.74; Story 1.4, #37). What test-cloud-aws still misses is mostly provider-error paths that LocalStack does not produce on demand: `AwsDynamoDB` (31 of 138 branches), `AwsBlobStorage` (14 of 40), `AwsPubSub` (12 of 26) and `AwsQueue`'s warm-up retries (4 of 10).

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
