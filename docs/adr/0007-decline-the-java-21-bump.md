# 7. The Java 21 bump is declined; 17 stays

Date: 2026-09-08
Status: Accepted

## Context

An automated upgrade left an uncommitted change in the working tree raising Java 17 to 21 in
seven places at once: `maven.compiler.source`, `target` and `release`, the compiler plugin's
`<release>`, the javadoc `<source>`, the Enforcer's `requireJavaVersion`, and the
`java-version` in both GitHub Actions workflows.

This ADR exists because the change is plausible, will be proposed again — by a person or by
the next run of the same tool — and answering it from scratch each time wastes the answer.

**The baseline was already decided, and recorded twice.** The architecture spine's stack table
pins `Java | 17 (Temurin)`. ADR 0005 says "Java 17 (Temurin recommended). Enforced via Maven
Enforcer." The spine's stated purpose is to fix the invariants that keep independently-built
parts from diverging; this is one of them. The change arrived with no ADR and no entry in the
backlog behind it.

It was not an oversight, either. The spine carries a `Deferred` section listing what was
consciously postponed — the Java package rename is in it. A JDK bump is not. The analysis never
raised it, because nothing in the product needs it.

## Decision

**The release target stays 17.** `maven.compiler.release`, the compiler plugin's `<release>`
and the javadoc `<source>` remain at 17.

**CI stays on 17 too.** Both workflows use `java-version: '17'`, matching the target.

**`requireJavaVersion` stays `[17,)`.**

### Why not raise the target

Nothing in the code needs it. Checked across all three modules: no `record`, no `sealed`, no
`permits`, no pattern-matching `switch`, no `Thread.ofVirtual`, no `SequencedCollection`, no
`StructuredTaskScope`. The bump buys nothing that exists today.

The library has no consumers and is unpublished, so the cost is not that a bump would break
someone — it would not. The cost is that the target is a floor on who *can adopt*, and raising
it narrows that floor for no gain. A jar built with `release 21` carries class file version 65
and will not load on a 17 JVM. Enterprise Java, which is who this is aimed at, moves slowly.

That the library is test-scoped softens this without removing it: a consumer would need JDK 21
to build and run their tests, not to run their service. A lower bar, still a bar, still erected
for nothing.

### Why not build on 21 while targeting 17

This was considered and rejected on the project's own success metrics.

**SM-4** says a plain `mvn -B verify` stays green on a clean machine with only Docker as a
prerequisite. A single JDK matching the target keeps a contributor's local run identical to
CI. Building on a different JDK makes CI a strict superset of what any human runs, which is
where divergence starts.

The argument for building on 21 is that it proves the library works on a newer runtime. That
proof is worth only as much as the tests that exercise it, and coverage is currently line 0.58
and branch 0.30 in the core. Most paths never execute, so most of the library would be
unproven on 21 either way.

**SM-C2** is the direct objection: raising a number without raising defect detection is worse
than a low honest number. A JDK 21 leg at branch coverage 0.30 is the CI equivalent of coverage
bought with tests that assert nothing.

### Why not a 17 + 21 matrix

Not wrong — premature. Its value is proportional to coverage, which is what Epic 1 exists to
fix. Nothing in the eight epics asks for it, so it is unspecified work competing with specified
work. Epic 3 will reshape CI anyway when a second provider arrives, and a JDK matrix built now
would be reworked into a provider matrix then.

## Revisit when

Epic 1 has landed and both modules meet line 0.80 and branch 0.70. At that point a matrix over
17 and 21 tests a claim the build actually makes — `requireJavaVersion` says `[17,)` and
nothing currently verifies the floor or anything above it — and the signal is worth its cost.

Two implementation notes for whoever does it, both discovered rather than guessed:
`actions/upload-artifact@v4` errors on duplicate artifact names, and `build.yml` uploads
`test-reports` and `coverage-jacoco` with fixed names, so each needs a `-${{ matrix.java }}`
suffix or the second leg fails. And `quality-gates.yml` should stay single-JDK: Spotless,
Checkstyle, Enforcer and OWASP are not JDK-behaviour-sensitive enough to justify doubling them.

## Consequences

+ The recorded baseline in ADR 0005 and the architecture spine stays true, so the three places
  that state the Java version agree.
+ A contributor's local `mvn -B verify` is the same build CI runs.
+ The floor stays as wide as it can be for a library nobody has adopted yet, which is when
  width is cheapest to keep.
- Nothing verifies the library on a JDK later than 17. That is a real gap, it is accepted
  deliberately, and the condition for closing it is written above rather than left to taste.
- An automated upgrade will propose this again. This document is the answer.

## Note

Verified while working through it: `mvn -B verify` runs green locally on Temurin 25.0.4 with a
release target of 17 — 21 tests, no skip flags, `javap` reporting major version 61 on the
emitted classes. So a contributor on a newer JDK is not blocked. What CI standardises on is a
separate question from what a contributor happens to have installed.

---
