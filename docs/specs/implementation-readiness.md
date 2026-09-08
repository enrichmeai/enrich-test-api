---
title: enrich-test-api
type: implementation-readiness
status: current
created: '2026-09-06'
updated: '2026-09-08'
sources: ['docs/specs/PRD.md', 'docs/specs/architecture.md', 'docs/specs/epics.md']
---

# Implementation Readiness — enrich-test-api

The gate asks one question: **could a developer implement these epics without inventing decisions
that nothing records?**

## Verdict: CONCERNS

Partly. Eight of the twenty-nine stories are expanded and can be built today, and one is already
complete.

The blocking list is shorter than it first looked. The library is unpublished with no consumers, so
three items previously written up as decisions — the package rename, removing `CloudMode.LIVE`,
removing `SECRETS` and `KMS` — are not decisions at all. Each was a decision only because changing
it would break someone, and there is nobody to break. They are free work.

That leaves **three genuine decisions** nobody but the maintainer can settle, and only one item on
the whole list with a hard deadline. Epic 7's three stories are unblocked work not yet expanded to
story files; the rest sit downstream of the three decisions.

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
| 1.6 Cover the remaining test-core classes | Added by this pass, for the same reason as 1.5 |
| 6.2 Correct the `.gitignore` patterns | Premise re-verified and corrected |
| 6.3 Remove the inert publishing configuration | All seven items located by line number |

## Decisions required — the blocking list

**Read this first: the library is unpublished and has no consumers.** That is not a detail, it is
what sorts this list. Three of the items below were written as decisions because changing them
would be a breaking change. There is nobody to break. For those, the deliberation is over before it
starts and only the work remains.

### Not decisions — free work, do it before publishing

**A. Move the Java packages to `com.enrichmeai.*`.** (Story 2.1, PRD Q1)
Written up as a decision on the grounds that renaming is breaking across every source file. With no
consumers it breaks nothing: 20 main sources, 12 test sources, and two `META-INF/services` files
whose *names* encode the interface FQN. The coordinates are already `com.enrichmeai`, so the only
question was ever whether the cost was worth the consistency, and today the cost is an afternoon.

This is the one item on the whole list with a hard deadline. Maven Central artifacts cannot be
changed or deleted, so the first publish pins every future consumer's `import` statements to
`org.deveasy.*` permanently. Free now, irreversible later. **Do it before any release.**

**B. Take `CloudMode.LIVE` out until it works.** (Story 4.1, PRD Q3)
Written up as a decision because removing an enum value is breaking. It breaks nobody. `AwsClients`
does branch on `LIVE` and build clients from the default credential chain, so the mode is not
absent — it is untested, undocumented, and unguarded against destructive operations on a real
account. Shipping an enum value that does not work is a promise the code does not keep. Removing it
costs nothing today and it can come back when Stories 4.2–4.4 are real.

**C. Take `SECRETS` and `KMS` out of `CloudServiceType`.** (Story 6.4, PRD Q4)
Same reasoning, smaller. Declared with no interface behind them, and free to remove. Note it
interacts with F below: one candidate shape there keys endpoints on `CloudServiceType`, so settling
the enum first is the cheaper order.

### Genuine decisions — nobody but the maintainer can settle these

**D. Azure or GCP for the second adapter?** (Story 3.1, PRD Q2)
The product's central claim — that a test written against the core runs on any provider — is
unproven with one adapter, and the architecture spine names this the single largest architectural
risk. Emulator coverage constrains the answer: Azurite covers blob and queue but neither Service Bus
nor Cosmos, so a first Azure adapter could not implement PubSub or NoSqlTable against a supported
emulator. **Blocks:** all of Epic 3, the largest body of work in the backlog. This is a genuine
product-direction call with real cost either way.

**E. Who provisions `NVD_API_KEY`, and does the audit gate merges or only report?** (Stories 5.1,
5.3, PRD Q5)
Only the repository owner can create the secret, so this is blocked on you in the most literal
sense. The gate is wired, guarded, and has never audited anything. **Blocks:** Story 5.2, which
cannot be scoped until a first report exists.

**F. What shape does the connection accessor take?** (Story 8.1)
A flat property map, typed accessors keyed on `CloudServiceType`, or a `ConnectionDetails`
capability. **Blocks:** Stories 8.2 and 8.3. New capability rather than repair, so implementing it
is a scope expansion to sign off separately from agreeing the gap is real.

Not release-blocking on compatibility grounds — `CloudAdapter` is a plain interface on Java 17, so
any shape can arrive later as a `default` method, and with no implementers but your own even that
courtesy is optional. But see the next section: compatibility is the wrong axis to judge this on.

PRD Q6, on GitHub Pages, is **resolved** — see `docs/adr/0006-github-pages-disabled.md`.

### What "no consumers" changes about Epic 8

An earlier draft of this document downgraded Epic 8 to "can safely wait" because the accessor can be
added later without breaking anyone. That is true and it is beside the point. With no users, the
question is not *what breaks existing consumers* — it is **what makes a stranger's first install
worth doing.**

On that axis Epic 8 looks quite different. The PRD's primary user is a Java backend engineer testing
a service that talks to S3, SQS, SNS or DynamoDB. That engineer is very probably on Spring Boot.
Today they would install the library, find they cannot point their application context at the
emulator, and go back to Testcontainers.

So Epic 8 may well be a launch feature — not because deferring it is expensive, but because shipping
without it means shipping something much of the target audience cannot use for the thing they came
for. That is a product judgement, and it is a better question than the compatibility one that
replaced it in the first draft.

The same correction applies to the Java baseline: an argument that a version bump "excludes
consumers on 17" is an argument about consumers that do not exist. The live question is only whether
to narrow who *can adopt*. See `docs/adr/0007-decline-the-java-21-bump.md`.

### The one hard deadline

Only item A is irreversible at publication, and only because Maven Central artifacts cannot be
changed or deleted. Everything else on this list can be changed after a release at a cost, or
before one for free. Item D is not on the clock but is what would falsify whichever shape F picks:
a second adapter is the first real test of whether the abstraction holds.

## What the first pass changed in the plan

**One story's premise was wrong and has been corrected.** Story 6.2 claimed the `.gitignore`
patterns `src/main/resources/` and `src/test/resources/` were unanchored and therefore silently
swallowing new files at every depth. They are not: a gitignore pattern with a mid-pattern separator
is anchored to its own directory, so both are root-anchored and inert in this multi-module layout.
Verified with `git check-ignore -v --no-index`. The story survives — the lines are still dead and
still misleading — but on honest grounds.

**Epic 1's arithmetic did not close with enough margin on either module, so Stories 1.5 and 1.6
were added.** In `test-cloud-aws`, Stories 1.2 and 1.3 expose 98 uncovered branches against a need
of 79 — a pass on paper that requires covering four fifths of everything they touch. Story 1.5 adds
48 more reachable branches.

`test-core` has the same shape and it was nearly missed. Story 1.1 reaches 50 of the module's 74
uncovered lines against a need of 39, which is 78% of everything in scope. Its acceptance criteria
had also inherited a demand for the module's 0.80 line target, which that story cannot responsibly
own. The criterion now claims only the branch target, which Story 1.1 does own outright — 30 of the
module's 32 uncovered branches — and Story 1.6 takes the 24 cheap lines in `CloudServiceType`,
`CloudAdapters` and `TestCloudConfig`.

**Epic 7 is new, from reading the code the stories point at.** Three defects, none previously
recorded:

- `CloudExtension` tracks and cleans up buckets and queues but not topics or tables. Both are stored
  behind a `// not tracking currently; placeholder for future` comment. Because the emulator
  container is shared for the whole JVM, resources survive between test classes.
- Cleanup swallows `Throwable` silently, twice, so a failed teardown is invisible.
- `ensureTable` returns without comparing an existing table's key schema to the one just requested,
  so a second test class silently inherits the first's table. See the correction below — this bullet
  originally blamed the wrong line.

The third compounds the first: without cleanup, tables persist, and `ensureTable` then hands the
next test class a table it did not ask for.

**One acceptance criterion contradicted the code and was rewritten.** A draft of Story 1.2 required
that `ResourceNotFoundException` and `DynamoDbException` each "surface a message naming the table".
They do not — the class swallows both and returns `null` or an empty list. A test written to the
draft would have failed and looked like a code bug. The criterion now describes the behaviour that
exists, and the question of whether it is the right behaviour is raised separately rather than
smuggled into a coverage story.

## What the second pass changed

**Story 7.3 blamed the wrong line, and has been retitled.** It was "Fix the stale key cache in
`AwsDynamoDB`", on the reasoning that `deleteTable` fails to evict the static `KEYS` map so a
recreated table reads through a stale schema. Reading `ensureTableInternal` again: on the
table-exists path it calls `cacheKeysFromDescribe`, which re-reads the live schema and overwrites the
cache. The cache self-corrects. A developer sent to that map would have found nothing wrong.

The real defect is that `ensureTableInternal` calls `describeTable` and, if the table exists, returns
**without comparing the existing key schema to the requested one** — the `pk` and `sk` arguments are
discarded on that path. Combined with the cleanup gap, test class B asking for `orders` keyed on
`orderId` silently receives class A's table keyed on `id`, and the failure lands later in `putItem`,
which has no catch, as a raw SDK `ValidationException`. The test that breaks is not the test that
caused it. Retitled to "Make `ensureTable` reject a table whose schema does not match"; the `KEYS`
non-eviction survives as a minor sub-point, because it is still an unbounded static map.

Retitling changed the generated tracking key, so `7-3-fix-the-stale-key-cache-in-awsdynamodb` appears
as a dropped orphan in the sprint planner's report. Both keys were at `backlog`, so nothing needed
carrying across.

**Epic 8 is new: a framework application cannot reach the emulator.** The library drives cloud
capabilities directly, which is what it was designed for. It cannot configure an application under
test, and the reason is structural rather than a missing integration module: no endpoint and no
credential is reachable from the provider-neutral API. `TestCloudConfig` exposes provider, mode,
region and account; `CloudAdapter` exposes only `provider()`, `initialize()` and the four capability
getters; the capabilities are pure operations. The single route to an endpoint is
`org.deveasy.test.cloud.aws.internal.LocalStackHolder.get()`, which costs a consumer an `internal`
package, a direct dependency on `test-cloud-aws`, and Testcontainers types — undoing AD-1 and FR-1.

Nothing in the documentation said so. The README opened "A toolkit for testing Java applications
against cloud services", which reads as the thing it does not do; that line is corrected, and the
limitation is now in the PRD's Non-Users list, where it is the largest practical exclusion.

Epic 8 is **new capability, not repair.** The original engagement put new capabilities out of scope.
Specifying the gap does not change that: implementing it is a scope expansion for the maintainer to
sign off separately.

**And a correction inside this same pass.** The first draft of Epic 8 argued that every candidate
shape adds a member to `CloudAdapter`, a fixed interface, and was therefore a breaking change owed
before any Maven Central release. That was wrong, and it was wrong in the direction that manufactures
urgency. `CloudAdapter` is a plain interface with all-abstract members, and the project targets Java
17, so `default Map<String, String> connectionProperties() { return Map.of(); }` is source- and
binary-compatible — an adapter that ignores it still compiles and still links.

The practical consequence is that the release-blocker list is shorter than it looked. Only the
package rename is irreversible at publication.

## Standing guard on Epic 1

PRD counter-metric SM-C2 says coverage reached by tests that assert nothing is worse than a low
honest number. Every acceptance criterion in Epic 1 names a behaviour rather than a percentage, and
each story repeats the guard so an implementer reading only that story cannot drift into chasing
the number. The floors move in Story 1.4, last, from measured values — never padded downward, since
a floor below what the build achieves is permission to regress by the size of the gap.

## Next

- `docs/specs/implementation/sprint-status.yaml` tracks all 29 stories. Regenerate it after any
  epic title changes, since keys derive from titles.
- Start with Story 1.1: it holds 30 of test-core's 32 uncovered branches, so nothing else in that
  module moves the floor.
- Item A, the package rename, is the only thing with a hard deadline: free now, permanent after a
  first publish. Do it before any release.
- Items B and C are free work too; they were only ever "decisions" because of a compatibility cost
  that does not exist yet.
- Item D blocks the largest single body of work and is what would falsify whichever shape F picks.
- Judge Epic 8 (F) on whether a first user can do what they came for, not on compatibility.
