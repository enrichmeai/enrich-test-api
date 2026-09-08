# 7. Build on a newer JDK than we target

Date: 2026-09-08
Status: Accepted

## Context

An automated upgrade left an uncommitted change in the working tree raising Java 17 to 21 in
four places at once: `maven.compiler.source`, `target` and `release`, the compiler plugin's
`<release>`, the javadoc `<source>`, the Enforcer's `requireJavaVersion`, and the
`java-version` in both GitHub Actions workflows.

Treating that as one decision is the mistake. It is two, and they point in opposite
directions.

**The release target is a floor imposed on every consumer.** A jar compiled with
`<release>21</release>` carries class file version 65 and will not load on a Java 17 JVM. For
a library, raising it takes something away from users and gives them nothing back unless the
code actually uses what the newer version offers.

It does not. Checked across all three modules: no `record`, no `sealed`, no `permits`, no
pattern-matching `switch`, no `Thread.ofVirtual`, no `SequencedCollection`, no
`StructuredTaskScope`. Nothing in this codebase needs anything later than 17. The bump would
have bought precisely nothing while narrowing who can use the result.

That the library is test-scoped softens the cost without removing it — a consumer would need
JDK 21 to build and run their tests, not to run their service in production. It is a lower bar
than a runtime library, and it is still a bar erected for no gain.

**The build JDK is a different question entirely.** Nothing about compiling on a newer JDK
reaches the consumer. It gets current tooling and a current security posture, and it proves the
library loads and runs correctly on the newer runtime — which is worth having, since consumers
on 21 and 25 exist. This session's own local `mvn -B verify` runs on Temurin 25 against a
release target of 17, which is the arrangement working as intended.

## Decision

Separate the two.

- **Release target stays 17.** `maven.compiler.release`, the compiler plugin's `<release>`,
  and the javadoc `<source>` remain at 17. Raise it only when something in the code needs a
  later language or library feature — never as housekeeping.
- **CI builds on 21.** Both workflows use `java-version: '21'`. This is the half of the
  automated change that was right, and it is kept.
- **`requireJavaVersion` stays `[17,)`.** That rule constrains the JDK a contributor builds
  with, not the bytecode shipped, so there is no reason to shut out a contributor still on 17.

This supersedes nothing in ADR 0005, which named Java 17 as the baseline; it records what
"baseline" means when the build JDK and the target diverge.

## Consequences

+ The library remains usable by anyone on 17 or later, which is most of the enterprise Java
  the project is aimed at.
+ CI exercises the code on a newer runtime than it targets, so a JDK incompatibility surfaces
  in the build rather than in a user's project.
+ Raising the floor later stays available and cheap. Lowering it after a release would not be.
- Two numbers now differ, and someone will eventually "fix" the inconsistency by making them
  match. This ADR is the answer to that.
- The build no longer proves the code compiles on a 17 JDK, only that it targets 17. Those are
  not the same claim, and `release` is what makes the second one sound.

## Note

Nothing here is an argument that 21 is worse than 17. It is an argument that a library's target
is a promise to its users and should only move when moving it buys them something.

---
