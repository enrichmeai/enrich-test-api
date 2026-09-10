---
title: 'Story 7.2: Make failed cleanup visible'
epic: 7
status: review
created: '2026-09-09'
source: docs/specs/epics.md
---

# Story 7.2: Make failed cleanup visible

## Story

As a maintainer, I can see when teardown failed rather than inheriting a polluted emulator.

## What is there

`CloudExtension.afterAll` (`test-core/src/main/java/org/deveasy/test/core/junit/CloudExtension.java`
:97–121 at `ef907e1`) releases tracked buckets and queues in two copies of the same loop. Each
loop body is:

```java
try {
  storage.deleteBucket(b);
} catch (Throwable ignore) {
}
```

at :105–108, and again for queues at :114–117. A delete that fails leaves the resource on the
shared emulator, the name in the tracking set, and no trace anywhere. The next test class inherits
the resource and, if it asks for the same name, Story 7.3's failure — with nothing pointing back at
the class that leaked it.

The `catch (Throwable)` is the only thing keeping a teardown failure from failing the class, so it
cannot simply be removed. The behaviour to keep is "one failure does not stop the rest, and does not
change the result"; the behaviour to add is "and somebody can see it happened".

## Files

| Path | Role |
|---|---|
| `test-core/src/main/java/org/deveasy/test/core/junit/CloudExtension.java` | Under change: `afterAll` :97–121. Story 7.1 adds two more loops of the same shape here, so land the two stories together and write the loop once. |
| `test-core/src/test/java/org/deveasy/test/core/junit/CloudExtensionCleanupTest.java` | Shared with Story 7.1. The failure cases live beside the success cases because they need the same fixtures and the same launcher harness. |
| `test-core/src/test/java/org/deveasy/test/core/junit/support/Fake*.java` | Each fake gains a way to make `delete*` of a named resource throw, and records which deletes were attempted. |

## Design

**One loop, four callers.** `afterAll` becomes four calls to one private `release` helper taking the
store key, the capability type, the tracking key, a human-readable kind (`bucket`, `queue`, `topic`,
`table`) and a `BiConsumer<C, String>` for the delete. The helper copies the tracking set before
iterating, because a successful delete through the wrapper removes the name from that set.

**Failures go to `java.util.logging` at `WARNING`.** The message names the kind, the resource name
and the cause, and the throwable is attached to the record so the stack is available. There is no
choice of logging library here: AD-1 fixes `test-core`'s only non-test dependency as
`junit-jupiter-api` at `provided`, and the `slf4j-api` line in the root POM is a convergence pin for
`test-cloud-aws`'s transitive graph, not something `test-core` may depend on. JUL needs nothing and
prints `WARNING` to stderr by default, which Surefire captures into the console and the report.

`ExtensionContext.publishReportEntry` was considered and not used: Surefire's JUnit Platform
provider does not forward report entries anywhere a maintainer reading a build log would see them.

**Catch `RuntimeException`, not `Throwable`.** Every capability method declares `RuntimeException`
as its failure channel and none declares a checked exception, so `RuntimeException` is the whole of
what "the delete failed" can look like. An `Error` — `OutOfMemoryError`, `LinkageError` — is not a
failed teardown and is left to propagate. That is a narrowing of what is swallowed, on purpose, and
it is the one place this story changes what reaches JUnit.

**The result is unchanged because nothing is rethrown.** Not because a failure is recorded and then
suppressed: the helper never throws, so JUnit sees `afterAll` complete normally. The question of
whether it *should* see it is below.

## Acceptance criteria

**AC-1 — the failure is visible, with the name and the cause.** With a fake whose `deleteBucket`
throws for `b-fails`, running a fixture class that ensured `b-fails` produces exactly one JUL record
at `WARNING` on the `org.deveasy.test.core.junit.CloudExtension` logger whose message contains
`bucket` and `b-fails`, and whose attached throwable is the exception the fake threw. Assert by
attaching a `Handler` for the duration of the launch, and remove it afterwards.

**AC-2 — one failure does not stop the rest, across kinds.** In the same fixture, a second bucket, a
queue, a topic and a table are all ensured. After the class they are all gone from the fakes, and
only `b-fails` remains. The bucket that fails must be the first kind released so the test proves the
later loops still ran.

**AC-3 — the test run's result is unchanged.** The launcher's summary for that fixture reports its
test as succeeded and zero failures, with the `WARNING` from AC-1 having been emitted. A fixture
whose own test fails still reports exactly one failure — the teardown does not add a second.

**AC-4 — nothing is logged when nothing fails.** A fixture whose resources all release cleanly
emits no `WARNING` on that logger. This pins the channel as a signal, not noise.

## A question to raise, not to decide

Should a teardown failure fail the build? Today it does not, and this story keeps it that way. The
case for failing: a leaked resource on a shared emulator is exactly the cross-class pollution Epic 7
exists to stop, and a warning in a green build is easy to never read. The case against: a teardown
that fails because the emulator was already stopping, or because a test deleted the resource itself
through another handle, would fail a class whose tests all passed, which is the "test that breaks is
not the test that caused it" shape in a new coat. A middle option is an opt-in — a system property
or an attribute on `@WithCloud` — that promotes the warning to an `afterAll` failure. Which of these
the maintainer wants is not derivable from the PRD or the ADRs, so this story records the question
and the PR raises it. The implementation should not pre-empt it by making any of the three
structurally hard.

## Out of scope

- Retrying a failed delete.
- Reporting through anything other than JUL. If a logging facade ever joins `test-core`, AD-1 is
  the document that has to change first.

## Definition of done

- `mvn -B verify` green on JDK 17 with Docker running, no skip flags.
- No `catch (Throwable)` left in `afterAll`.
- Every new test asserts on a returned value, a thrown type, a captured log record, or fake state
  (SM-C2).
- The teardown-fails-the-build question appears in the PR body as a question.
