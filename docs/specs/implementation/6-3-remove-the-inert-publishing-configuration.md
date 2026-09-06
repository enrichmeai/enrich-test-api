---
title: 'Story 6.3: Remove the inert publishing configuration'
epic: 6
status: ready-for-dev
created: '2026-09-06'
source: docs/specs/epics.md
---

# Story 6.3: Remove the inert publishing configuration

## Story

As a maintainer, I can read the POM without inferring capabilities the project does not have.

## Scope boundary, stated up front

This story **deletes dead configuration**. It does not set up publishing, does not prepare for Maven
Central, and does not make the release plugin work. Publishing is explicitly out of scope for this
project. If a reviewer asks "shouldn't we just fix it instead", the answer is that fixing it is a
different, larger, unrequested piece of work — and this story exists because a half-configured
release path reads as a working one.

## What is actually there

Six pieces in `pom.xml`, all verified present at these lines:

| Lines | What | Why it is dead |
|---|---|---|
| 73–75 | `<pluginRepository>` for `oss.sonatype.org/content/repositories/releases` | The host is decommissioned. This is the one entry with a live cost: it sits in the plugin resolution path. |
| 85–94 | `<distributionManagement>` with OSSRH snapshot and staging repositories | Nothing runs `deploy`. |
| 167–195 | `sign-source-javadoc` profile attaching sources and javadoc jars | Never activated; exists to satisfy Central's requirements. |
| 424–426 | `nexus-staging-maven-plugin` 1.6.7 in `<pluginManagement>` | Version pin for a plugin nothing invokes. |
| 443–452 | `nexus-staging-maven-plugin` in `<plugins>` with `<extensions>true</extensions>` | `extensions` replaces the default deploy lifecycle, so it is resolved on every build. |
| 617–620 | `wagon-ssh` 2.10 build extension | Build extensions resolve on every build. Nothing deploys over ssh. |

And one that is not merely dead but broken:

`maven-release-plugin` at 432–441 is configured with `<releaseProfiles>release</releaseProfiles>`.
**There is no profile with id `release` in this POM.** Confirmed:
`grep -n '<id>release</id>' pom.xml` returns nothing; the profiles present are `sign-source-javadoc`,
`errorprone` and `owasp`. Anyone running a release would activate a profile that does not exist and
get no signing, no sources and no javadoc.

## Why the pluginRepository is the one to prioritise

Maven consults `<pluginRepositories>` when a plugin is not already resolved locally. `oss.sonatype.org`
no longer serves that content, so on a cold local repository or a CI runner with an empty cache,
every plugin resolution that misses Central also attempts a dead host. That is latency at best and a
build failure at worst, and it is invisible until it happens.

## Acceptance criteria

**AC-1.** All seven items are either removed, or retained with a comment on each saying it is
deliberately kept and why. Mixed outcomes are fine; silence on any one of them is not.

**AC-2.** `mvn -B verify` green with Docker running, no skip flags.

**AC-3.** `mvn -B -Powasp ...` and the `errorprone` profile still activate — confirm the profile
block was not damaged by the edits around it.

**AC-4.** `mvn -B help:effective-pom` is captured before and after, and the diff contains nothing
beyond the intended removals. This is the check that catches an accidentally deleted closing tag
that still parses.

**AC-5.** The CHANGELOG records the removal under `[Unreleased]`, since anyone who had been relying
on `deploy` would be affected. Nobody is, as far as this repository knows, which is worth writing
down rather than assuming.

## Definition of done

- One commit, `pom.xml` and `CHANGELOG.md`.
- The effective-POM diff from AC-4 summarised in the commit message.
- No new publishing configuration introduced. Deletion only.
