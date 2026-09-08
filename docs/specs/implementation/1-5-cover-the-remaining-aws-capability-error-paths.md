---
title: 'Story 1.5: Cover the remaining AWS capability error paths'
epic: 1
status: ready-for-dev
created: '2026-09-06'
source: docs/specs/epics.md
---

# Story 1.5: Cover the remaining AWS capability error paths

## Story

As a maintainer, I have enough coverage headroom that Story 1.2 does not have to be perfect.

## Why this story exists

It was not in the first draft of the backlog. It was added after checking whether Epic 1's
arithmetic actually closed.

`test-cloud-aws` needs +79 branches. Stories 1.2 and 1.3 between them expose 98. That is a pass on
paper and a trap in practice: it requires covering roughly four fifths of every branch those two
stories touch, so any single awkward path — the LocalStack race, an SDK behaviour that resists
testing — drops the epic below its target with no slack left. This story adds 48 more reachable
branches.

| Class | Uncovered lines | Uncovered branches |
|---|---|---|
| `AwsBlobStorage` | 31 | 28 |
| `AwsPubSub` | 8 | 16 |
| `AwsQueue` | 14 | 4 |

## Files

All under `test-cloud-aws/src/main/java/org/deveasy/test/cloud/aws/`, with existing integration
tests beside each: `AwsBlobStorageIT`, `AwsPubSubIT`, `AwsQueueIT`. Extend those or add `*IT`
siblings — Failsafe includes `*IT`, Surefire will not start a container.

## AwsBlobStorage

The uncovered branches are status-code checks in catch blocks. Line numbers as of this writing:

- `:55` `e.statusCode() != 404` in `ensureBucket` — the "bucket already exists" path versus a real error.
- `:60` and `:64` region handling — `region == null || isBlank()`, and `!"us-east-1".equals(region)`,
  which is the branch that adds a `CreateBucketConfiguration`.
- `:90`, `:95` `deleteBucket` tolerating a missing bucket.
- `:125` `getObject` returning `null` on a 404 or `NoSuchKey`, rethrowing otherwise.
- `:137` `deleteObject` tolerating a missing key.
- `:149` `listKeys` when the response has no contents.
- `:165` `exists` returning `false` on 404.

**AC-1.** `getObject` for a key that does not exist returns `null`. `exists` for the same key returns
`false`. Both asserted separately — they take different paths.

**AC-2.** `listKeys` on an empty bucket returns an empty list, and on a bucket with keys under two
prefixes returns only those matching the requested prefix.

**AC-3.** `deleteBucket` and `deleteObject` against names that do not exist return without throwing.

**AC-4.** A bucket created with a non-`us-east-1` region takes the `CreateBucketConfiguration`
branch. If LocalStack does not honour a non-default region well enough to assert on, say so and skip
this one criterion explicitly rather than leaving it silently unmet.

**AC-5.** `putObject(InputStream)` with a stream that throws on read produces the wrapped
`RuntimeException` whose message is `Failed to read input stream for putObject`.

## AwsPubSub

Sixteen uncovered branches, concentrated in topic-ARN resolution (`:127`-`:141`) and the
`isNotFound(e)` handling at `:62`-`:64`.

**AC-6.** `deleteTopic` on a topic that does not exist returns without throwing.

**AC-7.** `receive(subscription, Duration)` on a subscription with nothing published returns
`Optional.empty()` and returns within roughly the requested timeout. Assert on the empty result;
assert on elapsed time only loosely, or not at all — a tight timing assertion is a flaky test.

**AC-8.** Resolving a topic ARN by name is exercised for both the found and not-found cases. Note the
lookup matches on `arn.endsWith(":" + name)`; a test with two topics whose names share a suffix is
worth writing, and if it fails, that is a finding to raise rather than a test to weaken.

## AwsQueue

Only four uncovered branches, but the retry loop at `:48`-`:77` is worth an assertion regardless.

**AC-9.** `receive(queue, Duration)` on an empty queue returns `Optional.empty()`.

**AC-10.** `ensureQueue` is idempotent: calling it twice leaves one usable queue and does not throw.

## Definition of done

- `mvn -B verify` green with Docker running, no skip flags.
- Every assertion is on a returned value or a thrown type, never on a call merely completing.
- Any criterion deliberately not met is named in the PR, with the reason. Silence is not a pass.
