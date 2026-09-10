---
title: 'Story 7.3: Make ensureTable reject a table whose schema does not match'
epic: 7
status: review
created: '2026-09-09'
source: docs/specs/epics.md
---

# Story 7.3: Make ensureTable reject a table whose schema does not match

## Story

As a test author, asking for a table with a partition key I named either gets me that table or
fails loudly — it never silently hands me someone else's.

## The defect, at the line

`AwsDynamoDB.ensureTableInternal` (`test-cloud-aws/src/main/java/org/deveasy/test/cloud/aws/AwsDynamoDB.java`
:78–113 at `ef907e1`):

```java
try {
  ddb.describeTable(DescribeTableRequest.builder().tableName(tableName).build());   // :80
  cacheKeysFromDescribe(tableName);                                                 // :82
  return;                                                                           // :83
} catch (ResourceNotFoundException notFound) {
}
```

If the table exists the method returns at :83 having read the live schema into the cache and
having never looked at `pk` or `sk`. The create path at :87–112 is the only place the requested
keys are used. So:

1. Class A calls `ensureTable("orders", "id")`. Created with partition key `id`.
2. Class A ends. Before Story 7.1 nothing releases the table; the container is shared per JVM.
3. Class B calls `ensureTable("orders", "orderId")`. `describeTable` succeeds; return at :83.
4. B calls `putItem` (:145–149). No catch. The SDK's `ValidationException` about a missing key
   attribute is what B's author sees, from a line that did nothing wrong.

Story 7.1 removes the common trigger. It does not remove the defect: a table left by a test that
does not use the extension, by an aborted run, or by the feature suite (`nosqltable-smoke.feature`
creates `dev-easy-test-users` keyed on `userId` and never deletes it) reproduces it in one class.

## Files

| Path | Role |
|---|---|
| `test-cloud-aws/src/main/java/org/deveasy/test/cloud/aws/AwsDynamoDB.java` | Under change: `ensureTableInternal` :78–113, `cacheKeysFromDescribe` :270–290, `TableKeys` :45–53. `deleteTable` :134–143 is discussed below and not changed. |
| `test-cloud-aws/src/test/java/org/deveasy/test/cloud/aws/AwsDynamoDbSchemaMismatchIT.java` | New. Named `*IT` so Failsafe runs it with the container. |
| `test-cloud-aws/src/test/java/org/deveasy/test/cloud/aws/CloudExtensionMultiClassIT.java` | Story 7.1's end-to-end test; its class B is the case this story makes pass for the right reason. |

## Current coverage

`AwsDynamoDB` 107 / 193 lines, 43 / 130 branches, from the same JDK 17 baseline as Story 7.1.
Module: line 344 / 516 = 0.667 against a floor of 0.66; branch 81 / 228 = 0.355 against 0.35. The
headroom is 0.007 and 0.005. The new comparison is a handful of lines and about eight branch
outcomes; the IT below has to reach nearly all of them or the module's check fails. Same rule as
7.1: that is the gate working. Do not move the floor.

## Design

**Describe once, compare, then decide.** The exists path reads the live `KeySchema` into a
`TableKeys` and compares it with the requested `(pk, sk)`. Equal: cache it and return — the
idempotent case the interface promises. Different: throw `IllegalStateException`. Absent: create,
as now. `cacheKeysFromDescribe` and the new exists path share one describe-and-parse helper so the
schema is read once, not twice as at :80 and :82 today.

**`IllegalStateException`, with all three facts in the message.** The table name, the schema found
and the schema requested, in that order, so the message reads as the situation rather than as a
stack trace. The interface's javadoc reserves `IllegalArgumentException` for null or blank
arguments; this is a conflict with the world, not with the argument, and the error convention in
the architecture spine ("vendor exceptions are caught at the adapter boundary") is met by throwing a
`java.*` type. Shape:

```
Table 'orders' already exists with key schema [partitionKey=id, sortKey=<none>] but
ensureTable was asked for [partitionKey=orderId, sortKey=<none>]. The existing table was
left untouched; delete it or use a different table name.
```

**The comparison is on key names, both keys, strictly.** A request without a sort key against a
table that has one is a mismatch: `putItem` of an item carrying only the partition key would fail
on that table for the same reason as the original defect. A blank sort key means "no sort key",
which is what the create path at :95 already does with it, and the cached `TableKeys` stores `null`
in that case rather than the blank string it stores today.

**Key attribute types are not compared.** The port has no notion of key type and the adapter only
ever creates `S` keys, so a table with the right names and a numeric key can only have been created
outside the library. Detecting it is cheap but it is a different story with a different reproduction,
and it is recorded here so nobody thinks it was missed.

**The existing table is not touched on mismatch.** Not deleted, not recreated. The epic raises
recreate-on-mismatch as a question and this story does not answer it; see below.

## The `KEYS` sub-point, and a corrected premise

The epic keeps a minor item from the earlier draft: that `KEYS` is a `private static final Map`
"that nothing ever evicts", so after `deleteTable` a `getItem` with no intervening `ensureTable`
reads a stale key name. The brief for this story repeats it: clear the map in `deleteTable`.

**That is not what the code does.** `deleteTable` at :142 ends with `KEYS.remove(tableName)`, and
has since the class was introduced in `b8a71c2`. The eviction the sub-point asks for is already
there. Nothing in this story changes `deleteTable`, and a test written to "prove" the eviction
would assert a value that is `null` whether or not the eviction happened — `getItem` on a missing
table returns `null` from `ensureKeys` and `null` again from the `ResourceNotFoundException` catch
at :162 — which is exactly the assertion-shaped nothing SM-C2 warns about. It is not written.

What survives of the point: `KEYS` is static, so it outlives any `AwsDynamoDB` instance and is
shared between them, and it grows by one small entry per table created and not deleted through the
library. With Story 7.1 in place every table the extension hands out is deleted through it. That is
a design smell rather than a defect, and it is left alone.

The two earlier drafts of this story each blamed a different wrong line. The corrected premises are
recorded here and in `epics.md` so the next reader does not re-derive them.

## Acceptance criteria

**AC-1 — partition-key mismatch fails in `ensureTable`.** Create a table with `ensureTable(t, "id")`.
`ensureTable(t, "orderId")` throws `IllegalStateException` whose message contains `t`, `id` and
`orderId`. The assertion is on the `ensureTable` call; no `putItem` is involved.

**AC-2 — sort-key mismatch, both directions.** `ensureTable(t, "id")` then `ensureTable(t, "id",
"ts")` throws; `ensureTable(u, "id", "ts")` then `ensureTable(u, "id")` throws. Each message names
the sort key on the side that has one and `<none>` on the side that does not.

**AC-3 — same schema is idempotent.** `ensureTable(t, "id")` twice, and `ensureTable(u, "id", "ts")`
twice, both return normally, and a `putItem`/`getItem` round trip afterwards succeeds on each.

**AC-4 — a blank sort key means none.** `ensureTable(t, "id")` then `ensureTable(t, "id", "")`
returns normally.

**AC-5 — the rejected table is untouched.** After AC-1's rejection, `describeTable` on the emulator
still reports partition key `id`, and an item put before the rejection is still readable through
`getItem`.

**AC-6 — the floors hold.** `test-cloud-aws`'s JaCoCo check passes with `test-cloud-aws/pom.xml`
:94 and :99 untouched. Report before and after from `target/site/jacoco/jacoco.csv`.

## A question to raise, not to decide

On a mismatch, should `ensureTable` recreate the table with the requested schema instead of
failing? It would make class B "just work". It would also delete class A's data on the strength of
B having asked for a different key name, which is a destructive operation triggered by a naming
collision and is exactly the kind of thing Story 4.3 exists to guard against in `LIVE` mode. This
story fails loudly and leaves the table alone. The PR raises the alternative as a question.

## Out of scope

- Comparing key attribute types. See above.
- `putItem`'s lack of a catch block, and `scan`/`query` returning an empty list on any
  `DynamoDbException`. Both are Story 1.2 findings.
- Any change to `deleteTable`.

## Definition of done

- `mvn -B verify` green on JDK 17 with Docker running, no skip flags.
- Floors untouched; before/after from the CSV.
- Every new test asserts on a thrown type and message, a returned value, or emulator state
  (SM-C2).
- The recreate-on-mismatch question appears in the PR body as a question.
