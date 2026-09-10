# 8. The Java packages move to com.enrichmeai

Date: 2026-09-09
Status: Accepted

## Context

The Maven coordinates became `com.enrichmeai:enrich-test-api` in the 0.3.0 rebrand. The Java
packages stayed `org.deveasy.*`, and the CHANGELOG recorded that as deliberate: renaming was a
breaking change across every source file, and it would also rename both `META-INF/services`
registration files, whose names derive from the SPI interface's fully qualified name. That was
PRD open question 1 and Story 2.1, held open as a decision.

Two facts settle it, and both were already written down.

**Nothing is published and nobody consumes the library.** A rename breaks nobody. Its cost is
the mechanical edit — 20 main sources, 26 test sources, two service files, and the documents
that name the packages — which is an afternoon. `docs/specs/implementation-readiness.md` reached
this conclusion on 2026-09-08 and reclassified the item from a decision to free work.

**Publishing makes the current names permanent.** Maven Central artifacts cannot be changed or
deleted. The first release pins every future consumer's `import` statements to whatever the
packages are called at that moment, and no later change rescues them. The readiness map calls
this the one item on the whole backlog with a hard deadline: free now, irreversible after the
first publish.

The maintainer has now asked for a first release to Maven Central, which turns the deadline
from a warning into a date.

## Decision

**The packages move to `com.enrichmeai.*`, before the first release.** The mapping keeps
everything after the organisation segment unchanged:

| Before | After |
| --- | --- |
| `org.deveasy.test.core.*` | `com.enrichmeai.test.core.*` |
| `org.deveasy.test.cloud.aws.*` | `com.enrichmeai.test.cloud.aws.*` |
| `org.deveasy.test.feature.cloud.*` | `com.enrichmeai.test.feature.cloud.*` |

Both service registration files are renamed with their contents:
`META-INF/services/com.enrichmeai.test.core.cloud.spi.CloudAdapter`, under
`test-cloud-aws/src/main/resources` and `test-core/src/test/resources`. The Cucumber glue
property in `CucumberQueueSuite` follows the glue package.

The `steps` package in `test-feature` test sources is not an `org.deveasy` package and is left
where it is; only its imports change.

### Why now and not at 1.0

Because "now" is the last moment it is free. Every consumer gained before a rename would have
to change every import, and the readiness map's whole argument is that there are none today.
Waiting for a milestone trades zero cost now for a real cost later, for nothing.

### Why not keep `org.deveasy` and document the mismatch

Story 2.1 allowed that answer, provided a written record said why. The only reason on offer was
the breaking-change cost, and that cost is zero. A permanent inconsistency between the
coordinates a consumer declares and the packages they import, kept for a reason that no longer
applies, is not a decision worth recording.

## Consequences

+ The packages match the groupId. A consumer who reads `com.enrichmeai` in their POM imports
  `com.enrichmeai` in their code.
+ The one hard deadline on the backlog is met before it can be missed.
+ The `ServiceLoader` failure mode Story 2.2 warned about — a renamed package with an unrenamed
  service file, resolving to nothing at runtime with no compile error — is guarded by the
  existing tests: `WithCloudInjectionTest` and every `*IT` in `test-cloud-aws` resolve the
  adapter through the registration file, so a stale name fails the build.
- Every document that named a package or a source path had to change with it. The historical
  CHANGELOG entry that recorded the packages as deliberately not renamed is kept and marked as
  superseded, so the record shows both the earlier reasoning and what replaced it.
- The `.junie/guidelines.md` and the expanded story files under `docs/specs/implementation/`
  now carry the new paths; their line numbers were measured against `ef907e1` and are
  unaffected, because the rename moves files without changing their length.

---
