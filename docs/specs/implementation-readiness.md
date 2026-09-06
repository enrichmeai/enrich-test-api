---
title: enrich-test-api
type: implementation-readiness
status: current
created: '2026-09-06'
updated: '2026-09-06'
sources: ['docs/specs/PRD.md', 'docs/specs/architecture.md', 'docs/specs/epics.md']
---

# Implementation Readiness — enrich-test-api

The gate asks one question: **could a developer implement these epics without inventing decisions
that nothing records?**

## Verdict: CONCERNS

Partly. Seven of the twenty-five stories can be built today from what is written down. Six cannot be
built by anyone, at any skill level, because they are not implementation problems — they are choices
only the maintainer can make. The remaining twelve sit downstream of those six.

This is not a defect in the planning. It is what the planning found. The useful response is to work
the ready lane and answer the six questions in parallel, not to wait.

## Ready to build now

Each has an expanded story file under `docs/specs/implementation/` carrying file paths, line
numbers, method names and acceptance criteria written against code that was read, not assumed.

| Story | Why it is ready |
|---|---|
| 1.1 Cover `CloudExtension` | Gap measured per class; the blocking obstacle (the fake adapter cannot return `null` capabilities) is named with two ways round it |
| 1.2 Cover the `AwsDynamoDB` error paths | Every catch block located by line, with its *actual* swallow-and-return behaviour recorded |
| 1.3 Cover `AwsClients` and `LocalStackHolder` | Ready with one flagged assumption and one design choice the story forces into the open |
| 1.4 Raise the floors | Mechanical, once 1.1–1.3 and 1.5 land |
| 1.5 Cover the remaining AWS capability error paths | Added by this pass; see below |
| 6.2 Correct the `.gitignore` patterns | Premise re-verified and corrected |
| 6.3 Remove the inert publishing configuration | All seven items located by line number |

## Decisions required — the blocking list

None of these can be delegated to an implementer. Each is one question.

1. **Do the Java packages move to `com.enrichmeai.*`?** (Story 2.1, PRD Q1)
   Breaking across 20 main and 12 test sources, plus two `META-INF/services` files whose *names*
   encode the interface FQN. Cheap now at `0.3.0-alpha1-private.1` with no published consumers;
   expensive later. **Blocks:** Story 2.2.

2. **Azure or GCP for the second adapter?** (Story 3.1, PRD Q2)
   The product's central claim — that a test written against the core runs on any provider — is
   unproven with one adapter. The architecture spine names this the single largest architectural
   risk. Emulator coverage constrains the answer: Azurite covers blob and queue but neither Service
   Bus nor Cosmos. **Blocks:** the whole of Epic 3, the largest piece of work in the backlog.

3. **Does `CloudMode.LIVE` stay?** (Story 4.1, PRD Q3)
   `AwsClients` does branch on it and build clients from the default credential chain, so it is not
   absent — it is untested, undocumented and unguarded. Keeping it means a credential story, a
   destructive-operation guard and a profile. Removing it is a breaking enum change. **Blocks:**
   Stories 4.2–4.4. Interacts with Epic 7: without resource cleanup, `LIVE` would leak real
   resources.

4. **Who provisions `NVD_API_KEY`, and does the audit gate merges or only report?** (Stories 5.1
   and 5.3, PRD Q5)
   Only the repository owner can create the secret. The gate is wired, guarded, and has never
   audited anything. **Blocks:** Story 5.2, which cannot be scoped until the first report exists.

5. **Do `SECRETS` and `KMS` stay in `CloudServiceType`?** (Story 6.4, PRD Q4)
   Declared with no interface behind them. Smallest of the six.

PRD Q6, on GitHub Pages, is **resolved** — see `docs/adr/0006-github-pages-disabled.md`.

## What this pass changed in the plan

**One story's premise was wrong and has been corrected.** Story 6.2 claimed the `.gitignore`
patterns `src/main/resources/` and `src/test/resources/` were unanchored and therefore silently
swallowing new files at every depth. They are not: a gitignore pattern with a mid-pattern separator
is anchored to its own directory, so both are root-anchored and inert in this multi-module layout.
Verified with `git check-ignore -v --no-index`. The story survives — the lines are still dead and
still misleading — but on honest grounds.

**Epic 1's arithmetic did not close with enough margin, so Story 1.5 was added.** Stories 1.2 and
1.3 expose 98 uncovered branches in `test-cloud-aws` against a need of 79. That is a pass on paper
that requires covering four fifths of everything they touch. Story 1.5 adds 48 more reachable
branches so a single awkward path does not sink the epic.

**Epic 7 is new, from reading the code the stories point at.** Three defects, none previously
recorded:

- `CloudExtension` tracks and cleans up buckets and queues but not topics or tables. Both are stored
  behind a `// not tracking currently; placeholder for future` comment. Because the emulator
  container is shared for the whole JVM, resources survive between test classes.
- Cleanup swallows `Throwable` silently, twice, so a failed teardown is invisible.
- `AwsDynamoDB` caches table key schemas in a `static` map keyed by table name, and `deleteTable`
  does not evict. A table dropped and recreated with a different key schema keeps the stale schema,
  and `getItem` then returns `null` for an item that is present.

The third compounds the first: without cleanup, tables persist, and the cache makes that persistence
incorrect rather than merely untidy.

**One acceptance criterion contradicted the code and was rewritten.** A draft of Story 1.2 required
that `ResourceNotFoundException` and `DynamoDbException` each "surface a message naming the table".
They do not — the class swallows both and returns `null` or an empty list. A test written to the
draft would have failed and looked like a code bug. The criterion now describes the behaviour that
exists, and the question of whether it is the right behaviour is raised separately rather than
smuggled into a coverage story.

## Standing guard on Epic 1

PRD counter-metric SM-C2 says coverage reached by tests that assert nothing is worse than a low
honest number. Every acceptance criterion in Epic 1 names a behaviour rather than a percentage, and
each story repeats the guard so an implementer reading only that story cannot drift into chasing
the number. The floors move in Story 1.4, last, from measured values — never padded downward, since
a floor below what the build achieves is permission to regress by the size of the gap.

## Next

- `docs/specs/implementation/sprint-status.yaml` tracks all 25 stories. Regenerate it after any
  epic title changes, since keys derive from titles.
- Start with Story 1.1: it holds 30 of test-core's 32 uncovered branches, so nothing else in that
  module moves the floor.
- Answer the five decisions above whenever convenient. Only decision 2 blocks a large amount of
  work.
