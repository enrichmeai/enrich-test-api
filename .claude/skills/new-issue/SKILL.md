---
name: new-issue
description: Turn a rough idea, bug report or feature request into ready-to-build GitHub issue(s) in enrichmeai/enrich-test-api, in the claude-task template's shape, de-duplicated, split to S/M size and labelled. Use for "new issue", "log this", "create a task/feature for …", "turn this into issues", or when Joseph describes work in a sentence.
---

# /new-issue — from a sentence to ready issue(s)

Same skill as in `valuedocs` and `culvert`. **Ready** means the `claude-task` template
(`.github/ISSUE_TEMPLATE/claude-task.md`) is filled: Goal · Measure · Evidence of done · Risk · Surface ·
Out of scope · Stop and ask if. `/groom` and `/build-task` rely on it.

## 1. De-duplicate first
`gh issue list -R enrichmeai/enrich-test-api --search "<words> in:title,body" --state all --limit 20`, and the
planned backlog in `docs/specs/` (the epics and stories BMAD produced). An open near-duplicate gets a comment,
not a new issue. A planned story with no issue becomes the issue, citing its story ID.

## 2. Size and split
- **S/M** (≤2 days, one concern): one issue. Bigger: a parent issue plus S/M sub-issues in build order.
- **A new capability** (a new interface in `test-core/.../capability/`) is two issues at least: the
  provider-neutral interface in `test-core` (no SDK), then its AWS implementation in `test-cloud-aws`. Each
  method must be implementable on more than one provider's emulator (the Capabilities rule on the site).
- **A second provider adapter**, a live mode, or anything that changes the dependency direction: an ADR in
  `docs/adr/` first, and Joseph's call.

## 3. Write it
- **Goal**: one sentence. **Measure**: tests, coverage (per module, line and branch), or why none.
- **Evidence of done**: the red test that fails on `main` today, the green proof, `mvn -B verify` green.
- **Risk**: exactly one of docs / code / build-release. **Surface**: modules and files, found by grepping.
- **Out of scope** and **Stop and ask if**: every question you could not answer from the code, `STATUS.md`,
  the ADRs or a ruling from Joseph. Never invent an answer.

## 4. Label, never dispatch
`wave:W1|W2|W3` (W1 only if the next release needs it), an owner (`claude-ready` / `founder` / `mac-session`)
and an `area:*`. Create a missing label once with `gh label create`. **Never** add `claude`: that starts a paid
build and is Joseph's call.

## 5. Draft or create
Default: show the drafts and wait for "go". On "create", `gh issue create -R enrichmeai/enrich-test-api …`.
Reply with the links, wave and owner of each, and anything under "Stop and ask if".
