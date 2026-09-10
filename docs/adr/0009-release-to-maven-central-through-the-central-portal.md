# 9. Release to Maven Central through the Central Portal, from a tag, on JDK 17

Date: 2026-09-09
Status: Accepted

## Context

The PRD listed "packaging for Central is out of scope" among its non-goals and the architecture
spine carried Maven Central publishing in its Deferred list. On 2026-09-09 the maintainer asked
for a first release to Maven Central. This ADR records how, and what had to change for the
request to be honest.

**The POM described a publishing path that no longer exists.** It carried OSSRH
`distributionManagement` pointing at `oss.sonatype.org`, a `nexus-staging-maven-plugin` bound
as a build extension, a `wagon-ssh` extension, a `sonatype.org` plugin repository, and a
`maven-release-plugin` configured to activate a `release` profile that was not defined. Sonatype
decommissioned OSSRH in 2025; releases now go through the Central Portal at
`central.sonatype.com` and its publisher API. Story 6.3 had planned to delete all of this on
the grounds that publishing was out of scope. With publishing in scope, deletion alone would
leave nothing, so the same seven items are replaced rather than removed.

**Central has requirements the POM meets and requirements it does not.** Met: `name`,
`description`, `url`, `licenses`, `developers`, `scm`. Not met until this change: a sources jar
and a javadoc jar for every published jar, a GPG signature on every file, and a verified
namespace for the `groupId`. `test-feature` has no main sources, so it cannot meet the first of
those and must not be in the bundle.

**Nothing is published and nobody consumes the library**, so the first version can say what the
code is. Coverage is below the stated target, `CloudMode.LIVE` and the `SECRETS`/`KMS` enum
values are declared but unimplemented, and the second-provider claim is unproven. That is an
alpha.

## Decision

**Publish through the Central Portal with `central-publishing-maven-plugin`, inside a `release`
profile that a plain build never activates.** The profile attaches sources and javadoc, signs
with `maven-gpg-plugin` in loopback mode, and hands the bundle to the plugin. The default
lifecycle is untouched: `mvn -B verify` still needs no key and no token (AD-9).

**The release is tag-driven and runs on JDK 17.** Pushing `vX.Y.Z` or `vX.Y.Z-qualifier` runs
`.github/workflows/release.yml`, which sets the version from the tag with `versions:set`, runs
`mvn -B -Prelease -pl '!test-feature' deploy` on Temurin 17 (ADR 0007), uploads the signed jars
as a workflow artifact, and creates a GitHub release. `main` keeps a `-SNAPSHOT` version; the
tag is the source of truth for what is released.

**Publishing is a manual step for now.** `autoPublish` is `false` and `waitUntil` is
`VALIDATED`: the workflow succeeds when the Portal has validated the bundle, and a person presses
Publish on `central.sonatype.com`. Once the flow has been seen to work end to end, flipping
`autoPublish` is a one-line change.

**What is published:** the parent POM `com.enrichmeai:enrich-test-api`, `test-core` and
`test-cloud-aws`. **What is not:** `test-feature`, excluded from the release reactor with
`-pl '!test-feature'`. It has no main sources, so it cannot produce the sources and javadoc jars
Central requires of every published jar, and there is nothing in it to publish. The exclusion is
on the command line rather than through the plugin's `skipPublishing` parameter for a second
reason: the reactor builds `test-feature` last, because it depends on both other modules
(regardless of the order in `<modules>`, which lists it second), and the plugin uploads from the
last module it runs in. `skipPublishing` is undocumented, and skipping the last module could skip
the upload. Leaving it out of the reactor makes `test-cloud-aws` last, and that one is published.

**The first version is `0.3.0-alpha1`.** The `-alpha1` qualifier is the honest description above,
and it lets the enum removals in Stories 4.1 and 6.4 land in a later alpha without pretending
they were never shipped.

## What must be true before the first tag

None of these can be done from the repository; all of them are the maintainer's.

1. The `com.enrichmeai` namespace is verified on `central.sonatype.com`. Verification for a
   domain-based groupId is a TXT record on `enrichmeai.com`; the domain already carries a
   ten-character TXT record of the shape the Portal issues, which suggests this has been done,
   but only the Portal account can confirm it.
2. A Portal **user token** (not the account password) exists, stored as the repository secrets
   `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD`.
3. A GPG key exists for signing, its public half is on `keyserver.ubuntu.com` (Central checks
   signatures against public keyservers), and the ASCII-armoured private key and its passphrase
   are the repository secrets `GPG_PRIVATE_KEY` and `GPG_PASSPHRASE`.
4. The tag is on a commit that is green on both existing workflows.

The README's "Releasing" section carries the commands.

## Consequences

+ A plain `mvn -B verify` is unchanged: no profile, no secret, no network beyond dependency
  resolution. SM-4 holds.
+ The POM describes a path that exists. Story 6.3's seven items are each replaced or removed,
  and the `release` profile the release plugin used to reference is now real, so the plugin is
  gone rather than fixed.
+ The rename in ADR 0008 lands before the first publish, so the one irreversible thing on the
  backlog is settled before it can be missed.
- The PRD's non-goal and the spine's Deferred entry are reversed by this ADR; both documents now
  point here.
- A tag publishes, so a tag is a release decision. Tags are not protected by anything in this
  repository; whoever can push a tag can start a release, and the manual Publish step on the
  Portal is the only gate after that.
- `CloudMode.LIVE`, `SECRETS` and `KMS` ship declared and unimplemented in the first alpha.
  Stories 4.1 and 6.4 remain free work and are the first things to land after it.
- A `-SNAPSHOT` version sends `central-publishing-maven-plugin` down a different path: it uploads
  each artifact directly to the Portal's snapshot repository and ignores `centralBaseUrl`. Found
  the hard way during the local rehearsal, which reached `central.sonatype.com` once with dummy
  credentials and was rejected with 401 before anything was stored. The release workflow therefore
  refuses any version containing `SNAPSHOT`, in addition to setting the version from the tag.
- The release path was verified against a local stand-in for the Portal API, not against
  Sonatype: with the version set to `0.3.0-alpha1` the plugin built and uploaded a bundle
  containing the published modules' jars, sources, javadoc, POMs, signatures and checksums. The
  Portal's own validation runs for the first time on the first real tag.

---
