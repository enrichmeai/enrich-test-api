---
title: enrich-test-api
type: epics-and-stories
status: draft
created: '2026-09-06'
updated: '2026-09-06'
sources: ['docs/specs/PRD.md', 'docs/specs/architecture.md', 'docs/adr/0006-github-pages-disabled.md']
---

# Epics and Stories — enrich-test-api

Backlog derived from the PRD's open questions, out-of-scope list and coverage gap. Except
where a story says otherwise, it describes work not yet done.

Headings follow the `## Epic N:` / `### Story N.M:` grammar that
`docs/specs/implementation/sprint-status.yaml` is generated from. Changing a title changes
the generated key, so rerun sprint planning after renaming anything here.

## How to read the backlog

Two kinds of item are mixed together, and they are not interchangeable.

**Work** can be picked up now. Epic 1, Story 6.2 and Story 6.3 are work, and each has an
expanded story file under `docs/specs/implementation/` carrying file paths, method names
and concrete acceptance criteria.

**Decisions** are not work. Stories 2.1, 3.1, 4.1, 5.1, 5.3 and 6.4 each ask the maintainer
to choose something no amount of implementation can settle: a breaking package rename, a
second cloud provider, whether an unimplemented enum value stays, who owns a repository
secret. They are left at epic grain deliberately. Expanding them into implementation
detail before the choice is made would be inventing the answer. `docs/specs/implementation-readiness.md`
collects them in one place.

Epic 3 is the largest piece of work in the backlog and is entirely downstream of one
decision, so its stories stay coarse until that decision lands.

---

## Epic 1: Raise coverage to the stated target

**Why:** the floors in the build are measured values, not the target. test-core sits at
line 0.5843 and branch 0.3043 against a target of 0.80 and 0.70; test-cloud-aws at 0.6667
and 0.3553. The gap is concentrated in a few classes, so the work is knowable rather than
open-ended. Validates FR-9 and SM-3, guarded by SM-C2.

**Done when:** both modules meet line 0.80 and branch 0.70, and the JaCoCo floors have been
raised to match.

**The arithmetic.** Measured from `test-core/target/site/jacoco/jacoco.csv` and
`test-cloud-aws/target/site/jacoco/jacoco.csv` after the last green `mvn -B verify`:

| Module | Lines now | Lines needed | Branches now | Branches needed |
|---|---|---|---|---|
| test-core | 104 / 178 | 143 (+39) | 14 / 46 | 33 (+19) |
| test-cloud-aws | 344 / 516 | 413 (+69) | 81 / 228 | 160 (+79) |

Only 32 uncovered branches exist in test-core and 30 of them are in `CloudExtension`, so
Story 1.1 is not merely the largest contributor to the branch target, it is very nearly the
only one. In test-cloud-aws, Stories 1.2 and 1.3 together expose 98 uncovered branches
against a need of 79, which is feasible but leaves almost no slack: it would require
covering roughly four fifths of everything they touch. Story 1.5 exists to provide that
headroom.

**A guard, carried into every story below.** SM-C2 in the PRD names the failure mode
directly: coverage bought with tests that assert nothing is worse than a low honest number.
Every acceptance criterion here names a behaviour, not a percentage. A test that executes a
line without asserting on its effect does not satisfy these criteria even if the report
moves.

### Story 1.1: Cover CloudExtension

As a maintainer, I can rely on the JUnit extension's branches being tested, so that
capability injection failures surface in CI rather than in a user's project.

Context: `test-core/src/main/java/org/deveasy/test/core/junit/CloudExtension.java`, 52 of 82
lines and 12 of 42 branches covered, plus the two nested tracking types at 13 of 19 and 0 of
14 lines. It is the single largest gap in test-core and holds 30 of the module's 32
uncovered branches.

Acceptance: an unsupported capability type raises `ParameterResolutionException` naming the
type; a missing capability on a present adapter fails with a message naming the capability;
a class without `@WithCloud` raises `ExtensionConfigurationException`; tracked buckets and
queues are released after the test class; `TrackingQueue` is exercised at all, including
that `deleteQueue` removes the name from the tracking set. test-core branch coverage is at
or above 0.70.

Expanded: `docs/specs/implementation/1-1-cover-cloudextension.md`.

### Story 1.2: Cover the AwsDynamoDB error paths

As a maintainer, I can rely on the adapter's error handling being tested, so that the way it
degrades under a provider error is a known, asserted property rather than an assumption.

Context: `test-cloud-aws/src/main/java/org/deveasy/test/cloud/aws/AwsDynamoDB.java`, 107 of
193 lines and 43 of 130 branches covered. It holds 87 of the module's 147 uncovered branches,
more than every other class combined. Error handling dominates the uncovered half.

Acceptance: the `ResourceNotFoundException` and `DynamoDbException` catch blocks are exercised
and each is asserted on its *actual* behaviour, which is to swallow and return a neutral value —
`getItem` returns `null`, `deleteItem` returns silently, `scan` and `query` return an empty list;
get and delete with and without a sort key are covered; `waitForActive` is covered for the
table-not-yet-visible path.

A finding to raise rather than silently encode: `scan` and `query` return `Collections.emptyList()`
on `DynamoDbException`, so a genuine provider failure is indistinguishable from an empty table.
Write the test to the behaviour that exists, and open the question of whether it is the behaviour
that should exist. Do not change it inside a coverage story.

Expanded: `docs/specs/implementation/1-2-cover-the-awsdynamodb-error-paths.md`.

### Story 1.3: Cover AwsClients and LocalStackHolder

As a maintainer, I can rely on client construction and the container race being tested.

Context: `AwsClients` 51 of 71 lines and 6 of 12 branches; `LocalStackHolder` 13 of 24 lines
and 3 of 8 branches, including the compare-and-set race described in AD-5.

Acceptance: each of the four client factory methods is covered in both `EMULATOR` and `LIVE`
mode; `defaultRegion` is covered for null, blank and supplied values; the holder's already-started
fast path is covered; the race is either tested or its untestability is recorded as a finding
with a proposed seam.

Expanded: `docs/specs/implementation/1-3-cover-awsclients-and-localstackholder.md`. That file
records a real obstacle: `LocalStackHolder` is a static-only class with no reset seam, so the
race cannot be tested without either a design change or reflection.

### Story 1.4: Raise the floors

As a maintainer, I can see the ratchet move up as the tests land.

Acceptance: the `<limit>` values in `test-core/pom.xml` and `test-cloud-aws/pom.xml` are raised
to the newly measured values; the comment recording the 0.80 and 0.70 target is updated or
removed once met; the coverage tables in `README.md` and `STATUS.md` match the build.

Expanded: `docs/specs/implementation/1-4-raise-the-floors.md`.

### Story 1.5: Cover the remaining AWS capability error paths

As a maintainer, I have enough coverage headroom that Story 1.2 does not have to be perfect.

Context: discovered while checking Epic 1's arithmetic. `AwsBlobStorage` has 31 uncovered lines
and 28 uncovered branches, `AwsPubSub` 8 and 16, `AwsQueue` 14 and 4. Without this story the
epic depends on Stories 1.2 and 1.3 delivering four fifths of every branch they touch.

Acceptance: the not-found and empty-result paths of `AwsBlobStorage.getObject`, `exists` and
`listKeys` are covered; `AwsPubSub` receive-with-timeout returns empty on an idle subscription;
`AwsQueue` receive-with-timeout returns empty on an empty queue. Each assertion is on the
returned value or the thrown type, not on the call completing.

Expanded: `docs/specs/implementation/1-5-cover-the-remaining-aws-capability-error-paths.md`.

---

## Epic 2: Resolve the identity inconsistency

**Why:** the Maven coordinates are `com.enrichmeai:enrich-test-api` but the Java packages are
`org.deveasy.*`. Every consumer sees the mismatch in their import statements. This is PRD open
question 1.

**Done when:** either the packages match the groupId, or a written decision records why they
stay.

### Story 2.1: Decide whether the packages move

**Decision required. Blocked on the maintainer.**

As a maintainer, I can point to a recorded decision rather than re-litigating it.

Acceptance: an ADR states whether packages move to `com.enrichmeai.*`, weighing the breaking-change
cost against the alpha status. If the answer is no, the README note explaining the mismatch stays
and says the mismatch is deliberate.

### Story 2.2: Execute the rename

**Blocked on Story 2.1.**

As a consumer, I can import types whose package matches the coordinates I depend on.

Context: 20 main sources and 12 test sources, plus two `META-INF/services` files whose *names*
encode the interface's fully qualified name — `test-cloud-aws/src/main/resources/META-INF/services/org.deveasy.test.core.cloud.spi.CloudAdapter`
and the same path under `test-core/src/test/resources/`. Renaming the package without renaming
those two files leaves `ServiceLoader` finding nothing, and the failure is a capability that
silently does not resolve rather than a compile error.

Acceptance: `mvn -B verify` green with Docker running; both service-registration files renamed;
`docs/adr/` and `.junie/` guidelines updated; a migration note in the CHANGELOG under a BREAKING
heading.

---

## Epic 3: Prove the SPI with a second provider

**Why:** the product's central claim is that a test written against the core runs on any provider.
One adapter does not demonstrate that. Until a second exists, AD-1 and AD-3 are asserted, not
proven. The architecture spine names this the single largest architectural risk. Validates SM-2.

**Done when:** an existing provider-neutral test passes against a second adapter with no change to
`test-core` and no change to the test.

**Grain:** deliberately coarse. Every implementation detail below depends on which provider is
chosen, so expanding these before Story 3.1 would be speculative.

### Story 3.1: Choose the provider and emulator

**Decision required. Blocked on the maintainer.**

Acceptance: an ADR records Azure or GCP, and which emulator covers which capability. Emulator
coverage is uneven and constrains the choice: Azurite covers blob and queue but neither Service
Bus nor Cosmos, so a first Azure adapter could not implement PubSub or NoSqlTable against a
supported emulator.

### Story 3.2: Implement the adapter for one capability

**Blocked on Story 3.1.**

Acceptance: `test-cloud-<provider>` implements `BlobStorage`; registers via `META-INF/services`;
adds no dependency to `test-core`; is consumed at test scope.

### Story 3.3: Run an existing feature file unchanged

**Blocked on Story 3.2.**

As a feature author, I can change one Gherkin word and run the same scenario against a different
cloud.

Acceptance: `storage-smoke.feature` passes against the new provider with only the
`Given cloud provider is "..."` value changed. If it does not, the required core change is itself
the finding and needs an ADR before it is made.

### Story 3.4: Make the Cucumber suite provider-parameterised

**Blocked on Story 3.2.**

Context: `CucumberQueueSuite` hardcodes its glue and selects one provider per run.

Acceptance: the suite runs the same features against each adapter present on the classpath.

---

## Epic 4: Make CloudMode.LIVE real or remove it

**Why:** the enum offers a mode the code does not implement end to end. That is a promise the
library does not keep, and it is PRD open question 3.

**Done when:** `LIVE` either works with a documented credential story, or is gone.

**Note on scope.** `AwsClients` does branch on `LIVE` and builds clients from the default
credential provider chain, so the mode is not entirely absent. What is absent is any test, any
credential documentation, and any guardrail. Story 1.3 covers those branches for coverage
purposes without making the mode usable; do not read a green build as evidence that `LIVE` works.

### Story 4.1: Decide keep or remove

**Decision required. Blocked on the maintainer.**

Acceptance: an ADR. Removing is a breaking enum change; keeping requires the three stories below.

### Story 4.2: Credential resolution

**Blocked on Story 4.1.**

Acceptance: `LIVE` uses the provider's default credential chain; nothing in the library stores or
logs credentials; a run with no credentials fails with a message that says exactly that.

### Story 4.3: Guardrails against destructive operations

**Blocked on Story 4.1.**

As an engineer, I cannot accidentally delete a production bucket by running a test.

Acceptance: destructive operations in `LIVE` mode require explicit opt-in; resources created in
`LIVE` are namespaced per run; the README carries a warning.

### Story 4.4: Keep LIVE out of the default build

**Blocked on Story 4.1.**

Acceptance: `LIVE` tests sit behind a Maven profile; `mvn -B verify` never touches a real account.

---

## Epic 5: Complete the supply-chain gate

**Why:** the OWASP job is wired, guarded and currently skipped, because the repository has no
`NVD_API_KEY` secret. The gate exists but has never audited anything. PRD open question 5.

**Done when:** the audit runs in CI and its result has a defined consequence.

### Story 5.1: Provision the secret

**Decision required. Blocked on the maintainer.** Nobody but the repository owner can create this.

Acceptance: `NVD_API_KEY` exists on the repository; the `deps` job runs the audit instead of
annotating a skip.

### Story 5.2: Triage the first report

**Blocked on Story 5.1.**

Context: `failBuildOnCVSS` is 7. The first real run may fail on transitive findings, including the
deliberately pinned older `slf4j-api` 1.7.36 and `commons-codec` 1.15, both held down to satisfy
the Enforcer's convergence rule.

Acceptance: every finding is either fixed or suppressed with a recorded reason; the suppression
file is committed and referenced from the POM.

### Story 5.3: Decide whether the audit blocks merges

**Decision required. Blocked on the maintainer, and on seeing Story 5.2's output.**

Acceptance: a recorded decision on gate versus report, and the quality-gates table in `README.md`
matches whichever it is.

---

## Epic 6: Close the remaining documentation and hygiene gaps

**Why:** small items that each mislead a reader or hide a footgun.

### Story 6.1: Decide the fate of GitHub Pages

**Status: done.** Recorded in `docs/adr/0006-github-pages-disabled.md`.

Pages is disabled for this repository. It was not merely publishing a private alpha's README on
the company apex domain; it was shadowing the authored product page at the same path, because an
organisation site and a project site cannot both own one path on one apex domain. The ADR records
the decision and the failure mode to watch for.

### Story 6.2: Correct the misleading .gitignore patterns

As a contributor, I can trust that a file I add appears in `git status`.

Context: **the premise recorded in the previous draft of this backlog was wrong, and the correction
matters.** That draft said `src/main/resources/` and `src/test/resources/` have no leading slash
and therefore match at every depth. They do not. A gitignore pattern containing a separator anywhere
but at its end is anchored to the directory of the `.gitignore` file, so both patterns are anchored
to the repository root. Verified with `git check-ignore -v --no-index`:
`test-feature/src/test/resources/features/new.feature` is **not** ignored, while a hypothetical
root-level `src/test/resources/x.txt` is.

The patterns are therefore inert in this multi-module layout, not dangerous — but they are dead,
they describe a single-module layout this project does not have, and they were convincing enough to
be written into a backlog as a live defect. The genuinely odd line is `.gitignore` itself at line
41, which makes the file ignore itself; inert today only because the file is already tracked.

Acceptance: the two resources patterns are removed or anchored with a leading slash and a comment
saying which layout they are for; line 41 is removed or justified in a comment;
`git check-ignore -v --no-index` output for a new file under each module's resources directory is
recorded in the commit message as evidence.

Expanded: `docs/specs/implementation/6-2-correct-the-misleading-gitignore-patterns.md`.

### Story 6.3: Remove the inert publishing configuration

As a maintainer, I can read the POM without inferring capabilities the project does not have.

Context: the POM carries a full OSSRH publishing setup that nothing exercises, against a host that
has been decommissioned. Six separate pieces, all verified present at the line numbers given in the
expanded story: a `sonatype.org` entry in `<pluginRepositories>`, an OSSRH `<distributionManagement>`
block, a `sign-source-javadoc` profile, `nexus-staging-maven-plugin` in both `pluginManagement` and
`<plugins>` with `<extensions>true</extensions>`, a `wagon-ssh` build extension, and a
`maven-release-plugin` configured with `<releaseProfiles>release</releaseProfiles>` naming a profile
that **does not exist in this POM**.

The `<pluginRepositories>` entry is the one with a live cost: every build may attempt plugin
resolution against `oss.sonatype.org`, which no longer serves it.

Acceptance: the configuration is removed, or a comment records that publishing is out of scope and
each piece is retained deliberately. `mvn -B verify` stays green either way. Maven Central publishing
is explicitly **not** in this story's scope; this is deletion of dead configuration, not the setup of
a working release path.

Expanded: `docs/specs/implementation/6-3-remove-the-inert-publishing-configuration.md`.

### Story 6.4: Decide on SECRETS and KMS

**Decision required. Blocked on the maintainer.**

Context: declared in `CloudServiceType` with no interface behind them. PRD open question 4. The enum
is 7 lines and entirely uncovered, so whichever way this goes it also removes a small piece of
Epic 1's line gap.

Acceptance: interfaces added, or the enum values removed, or a comment on the enum records them as
reserved and says no interface is planned yet.

---

## Epic 7: Finish resource cleanup in the JUnit extension

**Why:** discovered while reading `CloudExtension` for Story 1.1. The extension tracks and cleans up
buckets and queues. It does not track topics or tables — both are stored with a `// not tracking
currently; placeholder for future` comment and no wrapper — so every topic and table a test creates
survives the run.

Because the emulator container is shared for the whole JVM, a table created by one test class is
still there for the next. `ensureTable` against an existing table with a different key schema is
where this surfaces, as a confusing failure in a test that did nothing wrong. In `LIVE` mode the same
gap would leak real resources, which makes this a prerequisite for Epic 4 rather than an
independent nicety.

The cleanup that does exist swallows `Throwable` silently, so a failing teardown is invisible.

**Done when:** every capability the extension hands out has its resources released after the test
class, and a failure to release is visible.

### Story 7.1: Track topics and tables

As a test author, resources my test creates do not affect the next test class in the same run.

Acceptance: `TrackingPubSub` and `TrackingNoSqlTable` wrappers exist alongside the two that already
do; `afterAll` deletes tracked topics and tables; a test asserts that a table created in one class is
absent by the time a second class runs.

### Story 7.2: Make failed cleanup visible

As a maintainer, I can see when teardown failed rather than inheriting a polluted emulator.

Context: `catch (Throwable ignore) {}` appears twice in `afterAll`.

Acceptance: a cleanup failure is logged with the resource name and the cause; one failure does not
prevent the remaining resources from being released; the test run's own result is not changed by a
teardown failure. Whether teardown failure should fail the build is called out for decision rather
than assumed.

### Story 7.3: Fix the stale key cache in AwsDynamoDB

As a test author, recreating a table with a different key schema does not silently use the old one.

Context: found alongside Story 7.1. `AwsDynamoDB` keeps a `private static final Map<String, TableKeys> KEYS`
keyed by table name only. It is static, so it is shared by every instance in the JVM, and nothing
evicts from it. `deleteTable` does not clear it. Because the emulator container is also shared for the
whole JVM, a table dropped and recreated with a different partition or sort key keeps the first
schema's cached keys, and `getItem` then builds a request against the wrong attribute names. The
symptom is a lookup that returns `null` for an item that is present.

This compounds Story 7.1: without cleanup, tables persist; with a stale cache, the persistence is
also incorrect.

Acceptance: `deleteTable` evicts the table's entry; a test creates a table with one partition key,
deletes it, recreates it with a different partition key, and reads back an item it wrote. Whether
the cache should be per-instance rather than static is raised as a question, not decided here.
