---
title: 'Story 1.6: Cover the remaining test-core classes'
epic: 1
status: ready-for-dev
created: '2026-09-06'
source: docs/specs/epics.md
---

# Story 1.6: Cover the remaining test-core classes

## Story

As a maintainer, test-core's line target does not rest entirely on one story.

## Why this story exists

Like Story 1.5, it was not in the first draft. It was added after checking Epic 1's arithmetic on
*both* modules rather than one.

test-core needs +39 covered lines. Story 1.1 touches `CloudExtension` and its two nested tracking
types, which between them hold 50 of the module's 74 uncovered lines. Leaving the line target to
1.1 alone means covering 78% of everything it reaches, with no margin for a single awkward path —
the exact trap Story 1.5 was created to avoid in `test-cloud-aws`.

The other 24 lines are in four small classes with almost no branching. This is the cheapest work in
the epic and it is what turns Epic 1 from arithmetically-just-possible into comfortable.

## Files

| Path | Coverage |
|---|---|
| `test-core/src/main/java/com/enrichmeai/test/core/cloud/CloudServiceType.java` | 0 / 7 lines |
| `test-core/src/main/java/com/enrichmeai/test/core/cloud/spi/CloudAdapters.java` | 13 / 20 lines, 2 / 4 branches |
| `test-core/src/main/java/com/enrichmeai/test/core/cloud/TestCloudConfig.java` | 8 / 14 lines |
| `test-core/src/main/java/com/enrichmeai/test/core/cloud/TestCloudConfig.Builder` | 11 / 15 lines |

Unit tests, no container. Name them `*Test` so Surefire runs them, beside the existing ones under
`test-core/src/test/java/com/enrichmeai/test/core/`. (An empty `CoreSmokeTest.java` used to sit
there, left over from a "create it, run it, delete it" example in `.junie/guidelines.md`; it was
removed with the package rename, so there is no existing smoke test to extend.)

## Acceptance criteria

**AC-1 — `CloudServiceType`.** Every declared value is exercised. Zero of its 7 lines are currently
covered, so `values()` and `valueOf` round-tripping is enough to move all of them.

**AC-2 — `CloudAdapters` finds an adapter.** With the test fake registered via
`test-core/src/test/resources/META-INF/services/com.enrichmeai.test.core.cloud.spi.CloudAdapter`,
`CloudAdapters.get` returns it and the returned adapter has been initialised with the supplied
config.

**AC-3 — `CloudAdapters` fails clearly when it does not.** Requesting a provider with no registered
adapter produces an explicit failure **naming the provider**, not a `NoSuchElementException`. This
is not an incidental assertion: it is FR-3's stated testable consequence, and nothing currently
verifies it. If the code does not behave this way, **that is a finding** — record it, and do not
weaken the assertion to match.

**AC-4 — `TestCloudConfig` round-trips.** Every field set through the builder reads back from the
built value: provider, mode, region or location, and project or account.

**AC-5 — immutability is asserted, not asserted-in-a-comment.** FR-4 requires that mutating a
built config is impossible. Verify it by reflection over the declared methods — no setter, no
mutator — rather than by a comment saying the type has none. A comment is not a test.

## Interaction with Story 6.4

`CloudServiceType`'s 7 uncovered lines are also `SECRETS` and `KMS` territory. Story 6.4 may delete
those values outright, which would remove some of these lines rather than cover them. Whichever
lands first, re-measure before assuming this story's contribution still holds. Do not let that
uncertainty delay this story — it is cheap enough to redo.

## Definition of done

- `mvn -B verify` green with Docker running, no skip flags.
- Every new test asserts on a value, a thrown type, or a reflective property. No test that merely
  calls a method.
- Floors untouched; Story 1.4 raises them once the epic lands.
