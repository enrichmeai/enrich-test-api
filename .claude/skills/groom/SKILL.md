---
name: groom
description: Review and groom every open issue and PR in enrichmeai/enrich-test-api against the next release, and rewrite the Release board issue. Use for "groom", "what's pending", "what's left for the release", "what should I do next", or before picking the next task. Fans out to the Sonnet `groomer` agent; incremental after the first run.
---

# Grooming enrich-test-api for the next release

Same skill as in `valuedocs` and `culvert`. It reads and labels issues here and never changes code.
The output is one issue, the **Release board** (title starts `Release board`, label `board`), rewritten on
every run: **what blocks the next release, what Claude can take next, and what only Joseph can do.**

## Rules
- **W1 = the next release**: the version on `main` without `-SNAPSHOT` (today `0.3.0-alpha1`, the first
  publish). `W2` = the release after, `W3` = later. The planned backlog in `docs/specs/` and `STATUS.md`
  are the plan; where labels disagree with them, list it under Drift.
- **Labels only from the set:** `wave:W1|W2|W3`, `area:*`, and owners `claude-ready` (buildable unattended,
  tests prove it), `founder` (Joseph: the tag, Portal publishing, keys, rulings), `mac-session` (needs
  credentials or a real account). The first run creates missing labels.
- **Safe writes happen; closures wait.** Labels and one grooming comment per item. Closures are proposed on
  the board as checkboxes; Joseph ticks; the next run closes with the evidence.
- **Evidence or it did not happen:** a merged PR or SHA, the other issue number, or the ruling.
- **Usage cap:** one groomer per ~25 items, at most 10 per run; incremental after the first run.

## Steps
1. Snapshot open issues and PRs (`gh issue list -R enrichmeai/enrich-test-api --state open --json …`,
   `gh pr list … --json number,title,isDraft,updatedAt,mergeable,statusCheckRollup,closingIssuesReferences`)
   and read the current board for its `last-groomed` stamp and ticked closures.
2. Close what Joseph ticked, re-checking the evidence first.
3. Fan out to `groomer` (Sonnet) in batches of ≤25 with the W1 version and this rubric.
4. Apply safe writes: label changes; a `Grooming (proposed — edit or reply to correct)` comment only on
   a W1 item that is not ready.
5. Rewrite the board, in this order: `last-groomed · main <sha> · next release <version>`; **Release gate**
   (N W1 open by owner, and the critical path); **Joseph queue**; **Claude queue (next 5)**; **Needs
   grooming**; **Proposed closures**; **Open PRs** (checks, mergeable, DCO, stale >24 h); **Drift** (labels vs
   `docs/specs/`, `STATUS.md` vs the build, coverage floors vs measured values); counts. Reply to Joseph with
   the gate, his queue and the Claude queue.

`/groom dispatch N` adds `claude` to the top N of the Claude queue, only when Joseph asks.
