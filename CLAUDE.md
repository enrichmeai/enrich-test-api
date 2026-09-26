# CLAUDE.md — enrich-test-api

Guidance for Claude Code in this repository. It reaches every session.

## What this is

- **enrich-test-api**: provider-neutral cloud test infrastructure for Java. Tests ask for a capability
  (`BlobStorage`, `Queue`, `PubSub`, `NoSqlTable`) and get one backed by an emulator container, with no
  vendor SDK on the test's compile classpath. Java 17, Maven multi-module:
  - `test-core`: the capabilities, the `CloudAdapter` SPI and the JUnit 5 extension. **No cloud SDK here, ever**
    (ADR 0001, 0003). That dependency direction is the product.
  - `test-cloud-aws`: the AWS adapter (S3, SQS, SNS+SQS, DynamoDB) on LocalStack via Testcontainers,
    found through `ServiceLoader`.
  - `test-feature`: Cucumber scenarios. It has no main sources and is not published.
- **Truth lives in:** `STATUS.md` (what is built and measured), `CHANGELOG.md`, `docs/adr/` (decisions,
  do not relitigate), `docs/specs/` (BMAD planning: brief, PRD, architecture, epics).
- **Releases:** a `v*` tag runs `.github/workflows/release.yml` → Maven Central through the Central Portal
  (ADR 0009), with Joseph pressing Publish. Nothing has been published yet; `main` is `0.3.0-alpha1-SNAPSHOT`.
- **The site page** `enrichmeai.github.io/enrich-test-api/` is checked against this repo every day by that
  repo's `release-sync` workflow: the version on `main`, the next tag, what Maven Central has, one row per
  published artifact and one per capability interface. A new capability, module or release here opens an
  `enrich-test-api-sync` issue there. Keep `CHANGELOG.md` current: the site's "what's new" is written from it.

## Autonomous build loop (Joseph, 2026-09-26)

**Claude builds, the `reviewer` agent verifies, Joseph merges.** The same loop as `valuedocs` and
`culvert`: one issue, one branch off `main`, one PR into `main`. BMAD (`_bmad/`, `.claude/skills/bmad-*`)
stays the planning tool; this loop is how the plan gets built.

`/new-issue` (idea → ready issue) → `/groom` (Release board) → `/build-task <issue>` → hooks on every edit → `reviewer` → fix (max 3 attempts) → `/compound` → PR

**The four rules. They are not negotiable:**
1. **Verify before you write.** Any use of an external library, API, emulator, CLI flag or config key
   (AWS SDK v2, LocalStack, Testcontainers, JUnit 5 extension API, Cucumber, Maven plugins, GitHub
   Actions, …) is checked against the official docs with WebFetch **before** the code is written, at the
   version the build resolves. Never guess a signature, flag or version. Start from § "Pinned docs".
2. **Done means green.** A task is not done until the fast gates pass locally and `mvn -B verify` is
   green, locally with Docker or in CI's `build` workflow on the PR (cite the run). A gate that could not
   run is reported as not run, never as passing.
3. **Three attempts, then stop.** After 3 failed fix attempts at the same gate or reviewer finding, stop
   and write the Blocker summary (format in `.claude/skills/build-task/SKILL.md` § 6).
4. **Pinned docs first.** Prefer § "Pinned docs" over open-ended search. If you had to find a new one, add it there in `/compound`.

**Fast gates (exact commands):**

| Changed | Command |
|---|---|
| Java in module `M` | `mvn -B -q -pl M -am spotless:check test-compile`, then `mvn -B -pl M -am test -Dtest='<classes you touched>' -Dsurefire.failIfNoSpecifiedTests=false` |
| Formatting failed | `mvn spotless:apply` (google-java-format), then re-check |
| Anything under `test-cloud-aws` or `test-feature` | `mvn -B verify` (needs Docker: LocalStack ITs and Cucumber). Without Docker, CI's `build` run on the PR is the proof |
| `.claude/hooks/**`, `.claude/settings.json` | `.claude/hooks/test-hooks.sh` (add a case for every new guard, and prove it RED first) |

The build's own gates (`mvn -B verify`): Spotless, Checkstyle, Enforcer (Java 17+, dependency
convergence) and the per-module JaCoCo floors. Never lower a floor or skip a gate to get green.

**Hooks (`.claude/settings.json`, `.claude/hooks/`):**
- **After each edit:** syntax checks for JSON, YAML and `pom.xml`.
- **When the turn ends:** `spotless:check test-compile` for each module whose Java or `pom.xml` this branch
  touches. It blocks the stop once. Opt out with `CLAUDE_SKIP_STOP_COMPILE=1`.
- **Before a Bash command:** `guard-destructive.sh` asks before force pushes, pushes to `main`, tags (a `v*`
  tag releases), `mvn deploy` or `-Prelease`, the `aws` / `gcloud` CLIs (this library is emulator-only),
  `gh pr merge|release|workflow`, `reset --hard`, `clean -f`, recursive `rm`, and `git commit -s` /
  `--signoff` (a DCO sign-off is a person's, never Claude's). Cases pinned in `test-hooks.sh`.

**Releases are batched (Joseph, 2026-09-26).** Features merge into `main` one PR at a time. A release
happens only once a chunk of features is done: the `wave:W1` set on the Release board. No PR changes
the version, tags or publishes. Each PR adds its line under `## [Unreleased]` in `CHANGELOG.md`. When
every W1 item is closed, `/groom` proposes the release, and Joseph decides. Then one release PR turns
`[Unreleased]` into the version's section. Joseph pushes the `v*` tag, `release.yml` uploads the bundle,
and he presses **Publish** on the Central Portal by hand. Claude never tags or publishes.

**DCO.** Every commit needs a `Signed-off-by` matching its author (`CONTRIBUTING.md`). Only a person can
certify the DCO: never add one for Claude and never forge Joseph's. On a Claude-authored PR, list the
sign-off as a Joseph action (`git rebase --signoff origin/main && git push --force-with-lease`).

**Where things for Joseph go.** Merges, the release tag, Portal publishing, keys (NVD, Central, GPG) and
rulings go on the PR or issue they belong to; `/groom` lists them in the Joseph queue. Never post a secret.

**One session per repo.** One Claude session works here at a time. The GitHub Action builder (`claude`
label, `.github/workflows/claude.yml`) counts as that session.

**Usage discipline (Max plan).** One task per session. Pinned docs before search. The 3-attempt cap.

### Pinned docs

Versions the build resolves (root `pom.xml`): Java 17, JUnit 5.10.2, Testcontainers 1.20.1, Cucumber
7.15.0, AWS SDK v2 2.25.64, LocalStack image 3.8 (`LocalStackHolder`). Added 2026-09-26; nothing below has
been fetched from a session yet — the first session that fetches a row marks it ✓.

| Area | Doc |
|---|---|
| AWS SDK for Java 2.x | https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/home.html |
| LocalStack (AWS services coverage) | https://docs.localstack.cloud/references/coverage/ |
| Testcontainers Java, LocalStack module | https://java.testcontainers.org/modules/localstack/ |
| JUnit 5.10 extension model | https://junit.org/junit5/docs/5.10.2/user-guide/#extensions |
| Cucumber JVM | https://cucumber.io/docs/cucumber/api/?lang=java |
| Java `ServiceLoader` (17) | https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html |
| Spotless Maven plugin | https://github.com/diffplug/spotless/tree/main/plugin-maven |
| Maven Central Portal publishing | https://central.sonatype.org/publish/publish-portal-maven/ |
| GitHub Actions workflow syntax | https://docs.github.com/en/actions/writing-workflows/workflow-syntax-for-github-actions |
| Claude Code hooks, subagents, permissions | https://code.claude.com/docs/en/hooks · https://code.claude.com/docs/en/sub-agents · https://code.claude.com/docs/en/permissions |

If WebFetch is blocked, the "verify before you write" gate **did not run**. Say so and treat it as a blocker.
