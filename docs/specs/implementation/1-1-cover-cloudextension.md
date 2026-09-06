---
title: 'Story 1.1: Cover CloudExtension'
epic: 1
status: ready-for-dev
created: '2026-09-06'
source: docs/specs/epics.md
---

# Story 1.1: Cover CloudExtension

## Story

As a maintainer, I can rely on the JUnit extension's branches being tested, so that capability
injection failures surface in CI rather than in a user's project.

## Why this one first

`test-core` has 46 branches in total. `CloudExtension` holds 42 of them and 30 of the 32 that are
uncovered. The module's branch floor cannot move without this story; no other class in the module
has enough branches to matter.

## Files

| Path | Role |
|---|---|
| `test-core/src/main/java/org/deveasy/test/core/junit/CloudExtension.java` | Under test. Do not modify in this story. |
| `test-core/src/test/java/org/deveasy/test/core/junit/WithCloudInjectionTest.java` | The one existing test. Extend or sit beside it. |
| `test-core/src/test/java/org/deveasy/test/core/junit/support/FakeCloudAdapter.java` | The in-module fake adapter. Most of this story is making it configurable. |
| `test-core/src/test/java/org/deveasy/test/core/junit/support/FakeBlobStorage.java` | Existing fake capability. |
| `test-core/src/test/resources/META-INF/services/org.deveasy.test.core.cloud.spi.CloudAdapter` | Registers the fake. Its **filename** is the SPI interface's FQN — do not rename it. |

## Current coverage

| Class | Lines | Branches |
|---|---|---|
| `CloudExtension` | 52 / 82 | 12 / 42 |
| `CloudExtension.TrackingBlobStorage` | 13 / 19 | 0 / 0 |
| `CloudExtension.TrackingQueue` | 0 / 14 | 0 / 0 |

`TrackingQueue` is at zero. Nothing in the suite has ever obtained a `Queue` from the extension.

## The obstacle, and the shape of the fix

Almost every uncovered branch in `beforeAll` and `resolveParameter` is a null check on one of the
four capabilities:

```java
BlobStorage storage = adapter.blobStorage();
if (storage != null) { ... }
```

`FakeCloudAdapter` today returns a fixed set. To reach both sides of all eight of those branches
the fake must be able to return `null` for any capability on demand. **Make the fake configurable
before writing any test** — for example a constructor or static setter taking the set of capability
types to support. Everything else in this story falls out of that.

`ServiceLoader` instantiates the registered adapter through its no-argument constructor, so the
configuration cannot be a constructor argument alone. Either give the fake a mutable static switch
reset per test, or register a small number of distinct fake classes each supporting a different
subset. The second is uglier but avoids shared mutable state between tests; pick one and say which
in the commit message.

## Acceptance criteria

**AC-1 — unsupported type.** Calling `resolveParameter` with a parameter whose type is not one of
the four capabilities raises `ParameterResolutionException` whose message contains the type name.
Note this branch is unreachable through JUnit itself, because `supportsParameter` returns false
first, so the extension must be driven directly for this one.

**AC-2 — capability absent.** With an adapter that returns `null` for a capability, requesting that
capability raises `ParameterResolutionException` and the message names the capability. Cover all
four: `BlobStorage`, `Queue`, `PubSub`, `NoSqlTable`.

**AC-3 — missing annotation.** A test class using the extension without `@WithCloud` raises
`ExtensionConfigurationException`, and the message says `@WithCloud`.

**AC-4 — tracking works.** A test that calls `ensureBucket("b")` leaves no bucket behind after the
class: assert against the underlying fake, not against the tracking set. The same for a queue via
`ensureQueue`.

**AC-5 — TrackingQueue is exercised.** Every method on `TrackingQueue` is called at least once, and
`deleteQueue` is asserted to remove the name from tracking so `afterAll` does not attempt a second
delete. Both `receive` overloads are called, including the `Duration` one.

**AC-6 — the number moves for the right reason.** `mvn -B -pl test-core verify` reports test-core
branch coverage at or above 0.70 and line coverage at or above 0.80.

## Out of scope

Do not fix what the tests expose. Two known issues live in this file and both belong to Epic 7:
`afterAll` swallows `Throwable`, and `PubSub` and `NoSqlTable` are never tracked at all. Write tests
that assert the behaviour as it is. If a test is awkward to write *because* of one of those, say so
in the commit message and leave the code alone.

## Definition of done

- `mvn -B verify` green with Docker running, no skip flags.
- Coverage floors in `test-core/pom.xml` untouched — Story 1.4 raises them once the whole epic lands.
- No assertion-free test. Every new test asserts on a return value, a thrown type, or observable
  state on the fake.
