---
title: 'Story 6.2: Correct the misleading .gitignore patterns'
epic: 6
status: ready-for-dev
created: '2026-09-06'
source: docs/specs/epics.md
---

# Story 6.2: Correct the misleading .gitignore patterns

## Story

As a contributor, I can trust that a file I add appears in `git status`.

## Read this first: the previous framing of this story was wrong

An earlier draft of the backlog recorded this as a live footgun, on the reasoning that
`src/main/resources/` and `src/test/resources/` have no leading slash and therefore match at every
depth, silently swallowing any newly added resource under any module.

That is not how gitignore works. From `gitignore(5)`: a pattern containing a separator anywhere
other than at its end is matched relative to the directory of the `.gitignore` file itself. Both
patterns contain mid-pattern separators, so both are anchored to the repository root.

Verified, not reasoned:

```
$ git check-ignore -v --no-index test-feature/src/test/resources/features/new.feature
(no output — not ignored)
$ git check-ignore -v --no-index test-core/src/main/resources/x.txt
(no output — not ignored)
$ git check-ignore -v --no-index src/test/resources/x.txt
.gitignore:43:src/test/resources/    src/test/resources/x.txt
```

So the patterns are **inert in this layout**, not dangerous. This repository has no root-level
`src/`; every module has its own. Nothing is being swallowed today.

Two things follow. The patterns should still go, because they are dead lines describing a
single-module layout this project does not have, and because they were convincing enough on a
casual read to be written into a backlog as a defect — which is itself the cost. And the correction
should be visible: this story exists partly as the record that the claim was checked.

## The line that is genuinely odd

`.gitignore:41` is the single entry `.gitignore`. The file ignores itself:

```
$ git check-ignore -v --no-index .gitignore
.gitignore:41:.gitignore    .gitignore
```

Inert today only because the file is already tracked, and gitignore has no effect on tracked files.
It would matter to anyone who removed it from the index, and it makes no sense in any case.

While in the file: lines 1–39 are inherited from a Spring-project template. `spring-test/test-output/`,
`/spring-*/build`, `spring-*/src/main/java/META-INF/MANIFEST.MF`, `activemq-data/`, `derby.log`,
`ivy-cache`, `jxl.log`. None of it relates to this project. Removing it is optional; if it stays,
it should be under a comment saying it is inherited and unaudited, so the next reader does not
assume it was chosen.

## Acceptance criteria

**AC-1.** The `src/main/resources/` and `src/test/resources/` entries are removed, or anchored with
a leading slash and carry a comment naming the layout they are for.

**AC-2.** Line 41's self-ignore is removed, or a comment explains it.

**AC-3.** The `git check-ignore -v --no-index` output for a new file under each module's resources
directory is pasted into the commit message as evidence, both before and after.

**AC-4.** `git status` behaviour is unchanged for every currently tracked file:
`git ls-files | wc -l` matches before and after.

**AC-5.** `mvn -B verify` stays green. This should be a no-op for the build; if it is not, something
has been misunderstood.

## Definition of done

- One commit, `.gitignore` only.
- The commit message carries the check-ignore evidence, not a claim about it.
