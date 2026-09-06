---
title: 'Story 1.4: Raise the floors'
epic: 1
status: ready-for-dev
created: '2026-09-06'
source: docs/specs/epics.md
---

# Story 1.4: Raise the floors

## Story

As a maintainer, I can see the ratchet move up as the tests land.

## Order

Last in the epic. Stories 1.1, 1.2, 1.3 and 1.5 land first; this one reads the numbers they produced
and writes them into the build. Running it earlier means guessing.

## Files

| Path | Change |
|---|---|
| `test-core/pom.xml` :56, :61 | `<minimum>0.58</minimum>` and `<minimum>0.30</minimum>` |
| `test-cloud-aws/pom.xml` | the same two limits, currently `0.66` and `0.35` |
| `README.md` :109-116 | the coverage floors table |
| `STATUS.md` :33-38 | the coverage table |

## How to get the numbers

Do not read them off the HTML report by eye. After a green `mvn -B verify`:

```
awk -F, 'NR>1{bm+=$6;bc+=$7;lm+=$8;lc+=$9} END{printf "line=%.4f (%d/%d)  branch=%.4f (%d/%d)\n", lc/(lm+lc), lc, lm+lc, bc/(bm+bc), bc, bm+bc}' \
  test-core/target/site/jacoco/jacoco.csv
```

and the same for `test-cloud-aws`. The JaCoCo CSV columns are `BRANCH_MISSED,BRANCH_COVERED` at 6
and 7, `LINE_MISSED,LINE_COVERED` at 8 and 9 — one-indexed, after `GROUP,PACKAGE,CLASS,INSTRUCTION_MISSED,INSTRUCTION_COVERED`.
Getting these columns wrong silently reports the instruction ratio as the line ratio.

## Acceptance criteria

**AC-1 — exact floors.** Each `<minimum>` is set to the measured value truncated to two decimal
places, with no padding downward. A floor set below what the build achieves is not a ratchet; it is
permission to regress by the size of the gap.

**AC-2 — the comment tells the truth.** The comment above each rule currently records that the
0.80 and 0.70 target is unmet. If the epic met the target, say so and drop the apology. If it did
not, update the numbers and keep it.

**AC-3 — the docs match.** The tables in `README.md` and `STATUS.md` carry the same covered/total
counts and ratios as the build enforces. A reader comparing the two must not find a discrepancy.

**AC-4 — it actually bites.** Verify the floor by temporarily deleting a test and confirming
`mvn -B verify` fails on the JaCoCo rule, then restore it. Say in the commit message that this was
done. A floor nobody has seen fail is a floor nobody has tested.

## Note on the root POM

There is deliberately no JaCoCo `check` at the root. The root is `packaging=pom` and `test-feature`
has no main sources, so a rule at either place passes vacuously and reads as a gate that is not one.
Do not add one back as part of "raising the floors".

## Definition of done

- `mvn -B verify` green with Docker running, no skip flags.
- AC-4 performed and stated.
