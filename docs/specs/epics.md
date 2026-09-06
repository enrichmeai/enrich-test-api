---
title: enrich-test-api
type: epics-and-stories
status: draft
created: '2026-09-06'
updated: '2026-09-06'
sources: ['docs/specs/PRD.md', 'docs/specs/architecture.md']
---

# Epics and Stories — enrich-test-api

Backlog derived from the PRD's open questions, out-of-scope list and coverage gap. It describes work not yet done. Nothing here is claimed as complete.

Epics are ordered by what unblocks the most. Epic 1 and Epic 2 both address risks that are live today; Epic 3 is the largest but depends on nothing.

---

## Epic 1 — Raise coverage to the stated target

**Why:** the floors in the build are measured values, not the target. test-core sits at line 0.58 and branch 0.30 against a target of 0.80 and 0.70; test-cloud-aws at 0.66 and 0.35. The gap is concentrated in two places, so the work is knowable rather than open-ended. Validates FR-9, SM-3, guarded by SM-C2.

**Done when:** both modules meet line 0.80 and branch 0.70, and the JaCoCo floors have been raised to match.

- **Story 1.1 — Cover `CloudExtension`.**
  As a maintainer, I can rely on the JUnit extension's branches being tested, so that capability injection failures surface in CI rather than in a user's project.
  Context: 52 of 82 lines and 12 of 42 branches covered. It is the single largest gap in test-core.
  Acceptance: unsupported capability type raises `ParameterResolutionException` naming the type; missing adapter fails with a message naming the provider; tracked resources are released after the test class; the nested tracking types are exercised. test-core branch coverage is at or above 0.70.

- **Story 1.2 — Cover the `AwsDynamoDB` error paths.**
  As a maintainer, I can rely on adapter error handling being tested, so that a provider error becomes a clear failure rather than a leaked SDK exception.
  Context: 107 of 193 lines and 43 of 130 branches covered. Error handling dominates the uncovered half.
  Acceptance: `ResourceNotFoundException` and `DynamoDbException` paths are exercised; get and delete with and without a sort key are covered; scan and query on an empty table are covered.

- **Story 1.3 — Cover `AwsClients` and `LocalStackHolder`.**
  As a maintainer, I can rely on client construction and the container race being tested.
  Context: `AwsClients` 51 of 71 lines; `LocalStackHolder` 13 of 24 lines, 3 of 8 branches, including the compare-and-set race in AD-5.
  Acceptance: a test exercises concurrent first calls and asserts exactly one container results.

- **Story 1.4 — Raise the floors.**
  As a maintainer, I can see the ratchet move up as the tests land.
  Acceptance: floors in both module POMs are raised to the new measured values; the comment recording the 0.80/0.70 target is updated; README and STATUS tables match.

---

## Epic 2 — Resolve the identity inconsistency

**Why:** the Maven coordinates are `com.enrichmeai:enrich-test-api` but the Java packages are `org.deveasy.*`. Every consumer sees the mismatch in their import statements. This is PRD open question 1.

**Done when:** either the packages match the groupId, or a written decision records why they stay.

- **Story 2.1 — Decide.**
  As a maintainer, I can point to a recorded decision rather than re-litigating it.
  Acceptance: an ADR states whether packages move to `com.enrichmeai.*`, with the breaking-change cost and the alpha status weighed. If the answer is no, the README note explaining the mismatch stays and says so deliberately.

- **Story 2.2 — Execute the rename, if decided.**
  As a consumer, I can import types whose package matches the coordinates I depend on.
  Context: touches roughly 40 source files and the `META-INF/services` file name in both `test-cloud-aws/src/main/resources` and `test-core/src/test/resources`, which is derived from the interface's fully qualified name.
  Acceptance: `mvn -B verify` green; both service-registration files renamed; ADRs and Junie guidelines updated; a migration note added to the CHANGELOG.

---

## Epic 3 — Prove the SPI with a second provider

**Why:** the product's central claim is that a test written against the core runs on any provider. One adapter does not demonstrate that. Until a second exists, AD-1 and AD-3 are asserted, not proven. The architecture spine names this the single largest architectural risk. Validates SM-2.

**Done when:** an existing provider-neutral test passes against a second adapter with no change to `test-core` and no change to the test.

- **Story 3.1 — Choose the provider and emulator.**
  Acceptance: an ADR records Azure or GCP, and which emulator covers which capability. Note that emulator coverage is uneven: Azurite covers blob and queue but not Service Bus or Cosmos, which constrains which capabilities a first Azure adapter can implement.

- **Story 3.2 — Implement the adapter for one capability.**
  Acceptance: `test-cloud-<provider>` implements BlobStorage; registers via `META-INF/services`; adds no dependency to `test-core`; the module is test-scoped where consumed.

- **Story 3.3 — Run an existing feature file unchanged.**
  As a feature author, I can change one Gherkin word and run the same scenario against a different cloud.
  Acceptance: `storage-smoke.feature` passes against the new provider with only the `Given cloud provider is "..."` value changed. If it does not, the resulting core change is itself the finding and needs an ADR.

- **Story 3.4 — Make the Cucumber suite provider-parameterised.**
  Context: the suite currently hardcodes glue and selects one provider per run.
  Acceptance: the suite runs the same features against each adapter present on the classpath.

---

## Epic 4 — Make `CloudMode.LIVE` real or remove it

**Why:** the enum offers a mode the code does not implement. That is a promise the library does not keep, and it is PRD open question 3.

**Done when:** `LIVE` either works with a documented credential story, or is gone.

- **Story 4.1 — Decide keep or remove.**
  Acceptance: an ADR. Removing is a breaking enum change; keeping requires the stories below.

- **Story 4.2 — Credential resolution.**
  Acceptance: `LIVE` uses the provider's default credential chain; nothing in the library stores or logs credentials; a run with no credentials fails with a message that says so.

- **Story 4.3 — Guardrails against destructive operations.**
  As an engineer, I cannot accidentally delete a production bucket by running a test.
  Acceptance: destructive operations in `LIVE` mode require explicit opt-in; resources created in `LIVE` are namespaced per run; the README carries a warning.

- **Story 4.4 — Keep it out of the default build.**
  Acceptance: `LIVE` tests sit behind a profile; `mvn -B verify` never touches a real account.

---

## Epic 5 — Complete the supply-chain gate

**Why:** the OWASP job is wired, guarded and currently skipped, because the repository has no `NVD_API_KEY` secret. The gate exists but has never audited anything. PRD open question 5.

**Done when:** the audit runs in CI and its result has a defined consequence.

- **Story 5.1 — Provision the secret.**
  Acceptance: `NVD_API_KEY` exists on the repository; the `deps` job runs the audit instead of annotating a skip.

- **Story 5.2 — Triage the first report.**
  Context: `failBuildOnCVSS` is 7. The first real run may fail on transitive findings, including the deliberately pinned older `slf4j-api` and `commons-codec`.
  Acceptance: every finding is either fixed or suppressed with a recorded reason; the suppression file is committed.

- **Story 5.3 — Decide whether it blocks merges.**
  Acceptance: recorded decision on gate versus report, and the README's quality-gates table matches whichever it is.

---

## Epic 6 — Close the remaining documentation and hygiene gaps

**Why:** small items that each mislead a reader or hide a footgun.

- **Story 6.1 — Decide the fate of GitHub Pages.**
  Context: Pages publishes this private alpha's README on the company apex domain. PRD open question 6.
  Acceptance: Pages is disabled, or a decision records why a private alpha is served publicly.

- **Story 6.2 — Fix the unanchored `.gitignore` patterns.**
  Context: `src/main/resources/` and `src/test/resources/` have no leading slash, so they match at every depth. Already-tracked files are unaffected, which is why the build passes, but a newly added resource is silently ignored. A contributor adding a feature file or a service registration would not see it in `git status`.
  Acceptance: patterns anchored or removed; a new file under any module's resources directory shows up in `git status`.

- **Story 6.3 — Remove or verify the inert publishing configuration.**
  Context: the POM carries OSSRH `distributionManagement`, a `nexus-staging-maven-plugin` bound as an extension, a `wagon-ssh` extension and a `sign-source-javadoc` profile. None is exercised, and OSSRH's `oss.sonatype.org` host is decommissioned.
  Acceptance: either removed, or a note records that publishing is out of scope and the configuration is retained deliberately.

- **Story 6.4 — Decide on `SECRETS` and `KMS`.**
  Context: declared in `CloudServiceType` with no interface behind them. PRD open question 4.
  Acceptance: interfaces added, or the enum values removed, or a note records them as reserved.
