---
title: 'Story 1.3: Cover AwsClients and LocalStackHolder'
epic: 1
status: ready-for-dev
created: '2026-09-06'
source: docs/specs/epics.md
---

# Story 1.3: Cover AwsClients and LocalStackHolder

## Story

As a maintainer, I can rely on client construction and the container race being tested.

## Files

| Path | Coverage |
|---|---|
| `test-cloud-aws/src/main/java/com/enrichmeai/test/cloud/aws/internal/AwsClients.java` | 51 / 71 lines, 6 / 12 branches |
| `test-cloud-aws/src/main/java/com/enrichmeai/test/cloud/aws/internal/LocalStackHolder.java` | 13 / 24 lines, 3 / 8 branches |

## AwsClients — the cheap half

Six uncovered branches, and they are all one shape. Each of `s3`, `sqs`, `sns` and `dynamodb` is:

```java
if (cfg.mode() == CloudMode.EMULATOR) { ...localstack... } else { ...default chain... }
```

Only the `EMULATOR` side has ever run. The remaining two branches are in `defaultRegion`:
`(r == null || r.isBlank()) ? "us-east-1" : r`.

**AC-1 — LIVE-mode construction.** Each of the four factory methods, called with a config in
`CloudMode.LIVE`, returns a non-null client. Assert on the returned client, and assert the test
performed no network call.

> **Assumption to verify first, before writing four tests on top of it.** This story assumes AWS
> SDK v2 resolves credentials lazily, so `S3Client.builder().region(...).credentialsProvider(DefaultCredentialsProvider.create()).build()`
> succeeds on a machine with no AWS credentials and no network. That is the documented behaviour but
> it has not been executed here. Verify it with one throwaway test before building on it. If it turns
> out the builder resolves eagerly, **that is the finding** — record it, cover `defaultRegion` alone,
> and hand the remaining LIVE branches to Epic 4 where the credential story belongs.

**AC-2 — region defaulting.** `defaultRegion` covered for `null`, `""` or `"   "`, and a supplied
region such as `eu-west-2`. Assert the region on the built client, not just that it built.

Note these are unit tests with no container. Name them `AwsClientsTest` so Surefire runs them; do
not use the `*IT` suffix here.

## LocalStackHolder — the expensive half, and a real obstacle

The class is `final`, has a private constructor, and holds its container in a
`private static final AtomicReference<LocalStackContainer> REF` with no reset. Three of its four
public methods (`ensureStartedSns`, `ensureStartedSNS`, `ensureStartedDynamoDB`) simply delegate to
`ensureStartedS3`.

**The race cannot be tested as the class stands.** `ensureStartedS3` starts a container *before* the
compare-and-set, so exercising the losing path means genuinely starting two LocalStack containers
and throwing one away — tens of seconds and a second Docker image pull — and even then `REF` cannot
be reset between tests, so the fast path and the race path cannot both be tested in one JVM without
reflection.

Do not quietly reach for reflection. Choose deliberately and record the choice:

1. **Extract a seam.** Move the race logic to a package-private static method taking a
   `Supplier<LocalStackContainer>` and an `AtomicReference` as parameters, leaving the public method
   as a thin caller over the static field. The race is then unit-testable with a fake supplier, no
   Docker, and both branches reachable. This is a small production change inside a coverage story,
   which is why it needs to be a conscious decision rather than a reflex.
2. **Record it as untestable.** Cover only the fast path, and write the reason down.

**AC-3 — fast path.** A second call to `ensureStartedS3` returns the same instance as the first.
Assert identity, not non-nullness.

**AC-4 — the race.** Either a test proves that concurrent first calls leave exactly one container in
`REF` and stop the loser, or a note in the code and in the PR records why not, naming option 1 as
the proposed fix. A story that silently skips this has not met its acceptance.

**AC-5 — the aliases.** `ensureStartedSns`, `ensureStartedSNS` and `ensureStartedDynamoDB` each
return the same instance as `ensureStartedS3`. Two of those three differ only in the casing of an
acronym and one is commented "for strict TDD expectations"; raise whether all three should exist,
and do not delete any of them in this story.

## Definition of done

- `mvn -B verify` green with Docker running, no skip flags.
- If option 1 was taken, the production change is in its own commit, separate from the tests.
- The race decision is written down somewhere a future reader will find it.
