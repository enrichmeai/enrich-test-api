---
title: 'Story 1.2: Cover the AwsDynamoDB error paths'
epic: 1
status: ready-for-dev
created: '2026-09-06'
source: docs/specs/epics.md
---

# Story 1.2: Cover the AwsDynamoDB error paths

## Story

As a maintainer, I can rely on the adapter's error handling being tested, so that the way it
degrades under a provider error is a known, asserted property rather than an assumption.

## Why this one matters most in its module

`test-cloud-aws` has 147 uncovered branches. `AwsDynamoDB` holds 87 of them — more than every other
class in the module put together. The module's branch target needs 79. This story is the majority
of that number on its own.

## Files

| Path | Role |
|---|---|
| `test-cloud-aws/src/main/java/org/deveasy/test/cloud/aws/AwsDynamoDB.java` | Under test. Do not modify in this story. |
| `test-cloud-aws/src/test/java/org/deveasy/test/cloud/aws/AwsDynamoDbIT.java` | The existing integration test. Extend it, or add siblings following the `*IT` naming that Failsafe includes. |

**Naming is load-bearing.** Surefire runs `*Test`, Failsafe runs `*IT`, `IT*`, `*ITCase` and
`*Suite`. A new class named `AwsDynamoDbErrorTest` runs under Surefire with no container and will
fail; name it `AwsDynamoDbErrorIT`.

## Current coverage

`AwsDynamoDB` 107 / 193 lines, 43 / 130 branches. `AwsDynamoDB.TableKeys` is fully covered.

## Read this before writing assertions

The class does not propagate provider errors. It swallows them and returns a neutral value. Line
numbers as of this writing:

| Site | Catches | Result |
|---|---|---|
| `getItem(table, pk)` :162 | `ResourceNotFoundException` | returns `null` |
| `getItem(table, pk, sk)` :180 | `ResourceNotFoundException` | returns `null` |
| `deleteTable` :138, :139 | `ResourceNotFoundException`, `DynamoDbException` | returns silently |
| `deleteItem` :193, :206 | `DynamoDbException` | returns silently |
| `scan` :225 | `DynamoDbException` | returns `Collections.emptyList()` |
| `query` :255 | `DynamoDbException` | returns `Collections.emptyList()` |
| `cacheKeysFromDescribe` :287 | `ResourceNotFoundException` | returns `null` |
| `waitForActive` :125 | `ResourceNotFoundException` | sleeps and retries |

Assert on that behaviour. An assertion that a call throws will fail.

## A finding to raise, not to fix

`scan` and `query` returning an empty list on `DynamoDbException` means a provider failure is
indistinguishable from an empty table. A caller cannot tell "there is nothing here" from "the
request failed". That is worth changing, and it is not this story's job: a coverage story that also
changes behaviour makes both harder to review. Write the test to the behaviour that exists, and
raise the question in the PR so it can become its own item.

The same applies to `ensureKeys`/`cacheKeysFromDescribe` returning `null`, which then makes
`getItem` return `null` — two different meanings arriving as the same value.

## Acceptance criteria

**AC-1 — not-found on read.** `getItem` against a table that does not exist returns `null`, for both
the partition-key-only and the partition-plus-sort-key overloads.

**AC-2 — empty item.** `getItem` for a key that is absent from an existing table returns `null`, and
the test distinguishes this from AC-1 by asserting the table does exist.

**AC-3 — sort keys both ways.** `ensureTable` with and without a sort key, then `putItem`, `getItem`
and `deleteItem` against each. Covers the `sk != null && !sk.isBlank()` branch at :95 and the
`k.sk == null` guards at :171 and :200.

**AC-4 — delete is forgiving.** `deleteTable` on a table that does not exist returns without
throwing. `deleteItem` for an absent key returns without throwing.

**AC-5 — empty results.** `scan` and `query` on an existing empty table return an empty list, not
`null`.

**AC-6 — pagination.** `scan` on a table with more items than one page returns all of them. This is
the `exclusiveStartKey` loop at :216 and :246, which no current test reaches.

**AC-7 — value marshalling.** `toAttributeValue` is covered for `null`, `String`, `Number`,
`Boolean` and `byte[]`, and for the fallthrough `toString` case. Assert by round-tripping through
`putItem` and `getItem`, not by calling the private method.

## Definition of done

- `mvn -B verify` green with Docker running, no skip flags.
- No floor raised here; Story 1.4 does that once the epic lands.
- Every new test asserts on a returned value or a thrown type.
