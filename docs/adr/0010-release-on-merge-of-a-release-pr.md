# 10. Release on the merge of a release PR, as culvert does

Date: 2026-10-03
Status: Accepted. Supersedes the trigger in ADR 0009; the bundle, the profile and the manual Publish stay.

## Context

ADR 0009 made a pushed `v*` tag the release trigger. Nothing has been tagged since. Releases here are
batched (CLAUDE.md), and `enrichmeai/culvert`, released the same way to the same Central Portal namespace,
has shipped 0.3.0 with a different trigger: merging the release PR uploads the bundle
(`culvert/.github/workflows/publish-maven.yml`, `scripts/release/gate.py`). On 2026-10-03 Joseph asked for
the first enrich-test-api release, copying from culvert what is needed.

Under ADR 0009 two things are separate acts: the PR that writes the version and the CHANGELOG section, and
the tag on some commit. A tag on the wrong commit, or a tag whose version disagrees with the POMs, is
possible. Tags are not protected here (ADR 0009 § Consequences). Culvert's secrets also have different
names from the ones ADR 0009 asked for, so one key and one token could not serve both repositories.

## Decision

**The release PR is the release.** It sets the root POM and every module POM to the release version and
turns `[Unreleased]` into that version's `CHANGELOG.md` section. When it merges, `release.yml` runs
`scripts/release/gate.py`, ported from culvert. The gate uploads only when the push changed the root POM's
version, the version is `X.Y.Z` or `X.Y.Z-qualifier` (never `-SNAPSHOT`), it is not already in Maven
Central's `maven-metadata.xml` for `test-core`, `CHANGELOG.md` has its `## [<version>]` heading, and all
four POMs agree. Otherwise it is a no-op, or a failure that names what is wrong. A later `pom.xml` change
while the bundle waits in the Portal does not upload again.

Unlike culvert, the gate accepts a qualifier, because ADR 0009 made the first version `0.3.0-alpha1`. A
404 from Central's metadata means nothing has been published yet and counts as "not published". Any other
answer that cannot be read stops the release.

**Then `verify`, without secrets:** the full `mvn -B verify`, then the `release` profile without signing,
asserting that `test-core` and `test-cloud-aws` each carry main, sources and javadoc jars. **Then `deploy`**
in the `maven-central` environment: sign, upload to the Portal's validation stage, keep the signed jars as
a workflow artifact, and create the `v<version>` tag and GitHub release on the merged commit. Tags are
outputs of a release now, not inputs.

**Publishing stays manual.** `autoPublish` is `false`. A person presses Publish on central.sonatype.com.

**Secrets use culvert's names:** `MAVEN_GPG_PRIVATE_KEY`, `MAVEN_GPG_PASSPHRASE`, `CENTRAL_USERNAME` and
`CENTRAL_PASSWORD`, as repository or organisation secrets. ADR 0009's names are read as a fallback.

**A manual run** with the confirm phrase `publish-maven` re-uploads the release version on `main`, for a
retry after a Portal-side failure. It refuses a `-SNAPSHOT` and any ref other than `main`.

## Consequences

+ One reviewed PR decides the version, the CHANGELOG and the release, and the gate checks they agree.
+ The same key and Portal token serve culvert and enrich-test-api.
+ A failed bundle fails in `verify` before any secret is read.
- Merging a release PR now starts a release. That merge is Joseph's, as it is in culvert.
- `main` carries the release version after the release, until a follow-up PR sets the next `-SNAPSHOT`.
  The site's `release-sync` reads the version on `main` and handles both.
- `gh release create` makes the tag with the workflow's token, so a tag push does not trigger other
  workflows. Nothing here listens for one any more.
