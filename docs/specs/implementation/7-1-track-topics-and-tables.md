---
title: 'Story 7.1: Track topics and tables'
epic: 7
status: review
created: '2026-09-09'
source: docs/specs/epics.md
---

# Story 7.1: Track topics and tables

## Story

As a test author, resources my test creates do not affect the next test class in the same run.

## Why this is the release blocker

The PRD's UJ-2 is several test classes sharing one emulator. AD-5 makes the LocalStack container
one-per-JVM on purpose, so anything a class does not release is still there for the next class.
`CloudExtension` releases buckets and queues. It stores topics and tables raw, behind two copies of
`// not tracking currently; placeholder for future`, so every topic and every table a test creates
outlives the class that created it. Story 7.3 is where that surfaces; this story removes the cause.

FR-5's third consequence — "resources opened for the test are tracked and released after the test
class" — is currently true for half the capabilities.

## Files

Line numbers are against `ef907e1`, the commit this story was expanded from.

| Path | Role |
|---|---|
| `test-core/src/main/java/org/deveasy/test/core/junit/CloudExtension.java` | Under change. `beforeAll` :80–89 stores `PubSub` and `NoSqlTable` unwrapped; the tracking sets for topics and tables are already created at :66–67 and never read. `afterAll` :97–121 releases buckets and queues only; the comment at :120 defers the rest. The two existing wrappers are `TrackingBlobStorage` :181–231 and `TrackingQueue` :233–268. |
| `test-core/src/test/java/org/deveasy/test/core/junit/support/FakeCloudAdapter.java` | Returns `null` for `Queue`, `PubSub` and `NoSqlTable` at :34–47 and a fresh `FakeBlobStorage` on every call at :31. To assert on state after `afterAll`, the test needs a handle on the same instance the extension used, so the fake must hand out shared, resettable instances. |
| `test-core/src/test/java/org/deveasy/test/core/junit/support/FakeQueue.java`, `FakePubSub.java`, `FakeNoSqlTable.java` | New. In-memory fakes with just enough behaviour to be asserted against: what exists, and which deletes were attempted. |
| `test-core/src/test/java/org/deveasy/test/core/junit/CloudExtensionCleanupTest.java` | New. Runs fixture classes through the JUnit Platform `Launcher` so that `afterAll` has actually run by the time the assertions execute. |
| `test-core/pom.xml` | Gains `junit-platform-launcher` at `test` scope. Its version comes from the imported `junit-bom` (AD-7). Test scope keeps AD-1 intact: the module's only non-test dependency stays `junit-jupiter-api` at `provided`. |
| `test-cloud-aws/src/test/java/org/deveasy/test/cloud/aws/CloudExtensionMultiClassIT.java` | New. The end-to-end version of the acceptance test, against LocalStack, named `*IT` so Failsafe runs it. |
| `test-cloud-aws/pom.xml` | Same `junit-platform-launcher` test dependency. |

**Naming is load-bearing.** Surefire runs `*Test`; Failsafe runs `*IT`, `IT*`, `*ITCase` and
`*Suite`. The fixture classes the launcher tests drive are `static` nested classes, which neither
plugin selects directly (both exclude `**/*$*` by default), so they run only when a test launches
them on purpose.

## Current coverage

Measured by `mvn -B clean verify` on JDK 17 before any change on this branch:

| Class | Lines | Branches |
|---|---|---|
| `CloudExtension` | 52 / 82 | 12 / 42 |
| `CloudExtension.TrackingBlobStorage` | 13 / 19 | 0 / 0 |
| `CloudExtension.TrackingQueue` | 0 / 14 | 0 / 0 |

Module: line 104 / 178 = 0.584, branch 14 / 46 = 0.304, against floors of 0.58 and 0.30.

**The trap.** Two new wrappers are roughly 55 new main lines in a module whose line floor has 0.004
of headroom. If they land uncovered the ratio falls to about 0.45 and the JaCoCo check fails the
build. That is the gate working, not a reason to move the floor. Every method on both wrappers must
be exercised by a test that asserts on the fake underneath, which is AC-5 below. Do not lower a
floor to pass; SM-3 says floors never move down.

## Design

**Two wrappers, same shape as the two that exist.** `TrackingPubSub` and `TrackingNoSqlTable` are
private static nested classes of `CloudExtension`, delegating every method and recording names on
`ensureTopic` / `ensureTable` and forgetting them on `deleteTopic` / `deleteTable`. `beforeAll` wraps
and stores them exactly as it does for storage and queues, and the placeholder comments go.

**A name is tracked once the delegate has succeeded, not before.** The two existing wrappers add the
name first and delegate second. The new two delegate first. The reason is Story 7.3: once
`ensureTable` rejects a table whose schema does not match, the table it rejected belongs to someone
else, and enrolling it for deletion at the end of this class would delete a table this class was
just told it does not own — the "silently dropping a table" footgun the epic names. The asymmetry
with the existing wrappers is deliberate and this is where it is recorded. Aligning the other two is
cheap, and is left out of this story so the diff stays about topics and tables.

**`afterAll` releases four kinds, not two.** Order: buckets, queues, topics, tables. Story 7.2
restructures the loop bodies; this story only adds the two loops. Land 7.2 in the same change if
both are being done, since the two edits overlap on the same lines.

**Subscriptions are not tracked, and there is nothing to track them with.** `PubSub` has no
`deleteSubscription`; per AD-3 a method only joins a capability if two providers' emulators can
implement it, and that has not been shown. On SNS a subscription goes with its topic, and the queue
end of it is a `Queue` resource that `TrackingQueue` already releases. Recorded so nobody reads the
gap as an oversight.

**Topics created as a side effect are not tracked.** `AwsPubSub.publish` :110 and
`ensureSubscription` :71 both call `ensureAndGetTopicArn`, which creates the topic if it is absent.
The port's contract says `ensureTopic` is how a topic comes to exist, and the wrapper tracks the
contract, not one adapter's side effects. A test that publishes to a topic it never ensured leaks
that topic. That is an adapter quirk worth its own line in a later story, not a reason to make the
wrapper guess.

## Acceptance criteria

**AC-1 — the wrappers are what gets injected.** A test method declaring a `PubSub` parameter and one
declaring a `NoSqlTable` parameter each receive an object that is not the fake itself, and a call
through it is observable on the fake: `ensureTopic("t")` makes `t` exist on `FakePubSub`;
`ensureTable("orders", "id")` makes `orders` exist on `FakeNoSqlTable` with partition key `id`.

**AC-2 — topics and tables are released after the class.** A fixture class run through the launcher
ensures a topic and a table and does nothing else. After the launch returns, neither exists on the
fake, and the fake records exactly one delete for each name.

**AC-3 — deleting inside the test untracks.** A fixture that ensures a table and then deletes it in
the same test method causes exactly one delete on the fake, not two. Same for a topic.

**AC-4 — a rejected `ensureTable` is not enrolled for deletion.** With the fake configured to reject
`ensureTable` for a name that already exists, a fixture whose test calls it fails, and after the
class that table still exists on the fake with no delete attempted. This is the track-after-success
rule and the point at which it becomes visible.

**AC-5 — the epic's acceptance, twice.** Test class A ensures a table; test class B, run after A in
the same JVM, observes it absent.
- With the fakes in `test-core`: after launching A, the fake has no such table; launching B then
  succeeds.
- Against LocalStack in `test-cloud-aws`: class A calls `ensureTable("orders", "id")`; class B calls
  `ensureTable("orders", "orderId")` and succeeds, and a `describeTable` on the emulator shows the
  partition key `orderId`. This is UJ-2 end to end, and before this story it fails in B — after
  Story 7.3 in `ensureTable`, before it in `putItem`.

**AC-6 — every wrapper method is exercised.** Each method on `TrackingPubSub` and
`TrackingNoSqlTable` is called at least once by a test that asserts on a returned value or on the
fake's state afterwards. `publish` followed by both `receive` overloads; `putItem`, both `getItem`
overloads, both `deleteItem` overloads, `scan` and `query`. This is the coverage trap above, and it
is a criterion rather than a hope.

**AC-7 — the floors hold without moving.** `mvn -B verify` passes the `test-core` JaCoCo check with
the floors in `test-core/pom.xml` :56 and :61 untouched. Report the before and after line and branch
ratios from `target/site/jacoco/jacoco.csv`, not from the HTML.

## Out of scope

- Aligning `TrackingBlobStorage` and `TrackingQueue` to the track-after-success rule. Noted above.
- Any change to `AwsPubSub`'s implicit topic creation.
- The null-capability branches in `beforeAll` and `resolveParameter`. Story 1.1 owns those, and it
  will want the fake to be able to return `null` again; the shared-instance design here leaves that
  a one-line addition rather than a redesign.

## Definition of done

- `mvn -B verify` green on JDK 17 with Docker running, no skip flags.
- The two placeholder comments are gone from `CloudExtension`.
- No assertion-free test. Every new test asserts on a returned value, a thrown type, or observable
  state on a fake or on the emulator (SM-C2).
- Coverage floors untouched; before/after numbers reported from the CSV.
