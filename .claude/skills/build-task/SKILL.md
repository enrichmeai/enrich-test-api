---
name: build-task
description: The autonomous build loop for ONE enrich-test-api task — spec → verify docs → red test → implement (hooks check each edit) → reviewer agent → fix (max 3 attempts) → /compound → PR into main. Use for "build #N", "take the next task", "work on X", or when Joseph hands over a task to do unattended. With no argument, takes the top item of the Claude queue on the Release board.
---

# /build-task — one task, start to reviewed PR

Same loop as in `valuedocs` and `culvert`. One task per session.

## 0. Pick and pin the spec
- No argument: the first item in the **Claude queue** on the Release board (`gh issue list --label board`).
- Write the spec down first: acceptance criteria, the modules it touches, and the test that proves each.
  Missing or ambiguous → ask on the issue and stop.
- **The dependency rule:** nothing in `test-core` may import a cloud SDK (ADR 0001, 0003). A change that needs
  one there is the wrong design; stop and ask.

## 1. Set up
`git fetch origin main`, then `git worktree add <scratchpad>/<topic> -b <branch> origin/main`. Claim it on
the issue (branch name) or open the PR as a draft first.

## 2. Verify before writing
For every SDK call, emulator behaviour, JUnit/Cucumber API or plugin option the change uses, WebFetch its
entry in CLAUDE.md § "Pinned docs" at the version the root `pom.xml` resolves, and note the URL. LocalStack's
coverage page decides whether an AWS operation is emulated at all: check it before relying on one.

## 3. Red, then green
Write the test that proves the first criterion and show it failing for the right reason, then implement.
A capability method gets a unit test in `test-core` (against a fake adapter) and an integration test against
LocalStack in `test-cloud-aws`. The Stop hook runs `spotless:check test-compile` on touched modules; run
`mvn spotless:apply` for formatting.

## 4. Gates — not done until these pass
The fast gates (CLAUDE.md), then `mvn -B verify` (Docker). Quote the exact command and the Surefire/Failsafe
counts. Without Docker, push, open the PR as a **draft**, and read CI's `build` run; never claim a suite you
have not seen green. If coverage moved, update `STATUS.md`'s table with the measured numbers. Never lower a
JaCoCo floor.

## 5. Review
Launch the `reviewer` agent with the spec. Fix every BLOCKER, MAJOR and UNVERIFIED item; answer each MINOR in
one line. Re-run the reviewer after fixing.

## 6. The 3-attempt cap
After the 3rd failed fix-and-recheck on the same gate or finding, stop and post on the issue:
```
Blocked: <gate or finding>
Tried: 1. … 2. … 3. … (what each changed, what it showed)
Evidence: <error lines, doc URLs, run IDs>
Hypothesis: <best guess at the root cause>
Needs: <the decision, access or information that would unblock it>
```
Leave the PR as a draft. Do not widen scope to route around it.

## 7. Compound, then hand over
- Update every doc that states a fact this changed: `STATUS.md`, `CHANGELOG.md` (Unreleased), `README.md`,
  the ADRs' status lines. A new capability or module also changes the site page; its daily check will open an
  `enrich-test-api-sync` issue in `enrichmeai.github.io` after merge.
- Run `/compound`. Mark the PR ready; end with branch, head SHA, files changed, gates and counts, reviewer
  verdict, the Compound lines, and for Joseph: the **DCO sign-off** (only a person can certify it) and the merge. Never change the version, tag or publish: releases are batched (CLAUDE.md § "Releases are batched").
