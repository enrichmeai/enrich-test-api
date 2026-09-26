---
name: Claude task
about: A task for the autonomous build loop (CLAUDE.md § "Autonomous build loop"). Creating it does not start anything. Review it, then add the `claude` label (Joseph's account only).
labels: []
---

## Goal

<!-- One sentence: the behaviour that will be true when this is done. -->

## Measure

<!-- Every task names its number, or says why it has none (tests, per-module line/branch coverage, a build time). -->
- Metric:
- Baseline, measured on main (command or run ID):
- Target:
- How it is read after merge:

## Evidence of done

<!-- Each line is checkable by someone who only reads the PR and its runs. -->
- [ ] Red proof: the test or guard that fails on main, quoted
- [ ] Green proof: the same test passing on the branch, quoted (the exact `mvn` command and counts)
- [ ] `mvn -B verify` green (locally with Docker, or CI's `build` run on the PR)
- [ ] `STATUS.md`, `CHANGELOG.md` and `README.md` updated for every fact this changes

## Risk

<!-- Exactly one. It decides who runs what. -->
- [ ] docs: only *.md, not CLAUDE.md
- [ ] code: library code, tests
- [ ] build-release: the build, quality gates, versions, `release.yml` or publishing (Joseph tags and publishes)

## Surface

<!-- Modules (test-core, test-cloud-aws, test-feature) or files expected to change. -->

## Out of scope

<!-- What must NOT change in this task. -->

## Stop and ask if

<!-- Conditions where Claude replies with a question instead of continuing. -->
- a test would have to be relaxed or a permission widened
- the change needs a real cloud account, a publish or a tag
- a capability interface in `test-core` would change shape, or `test-core` would need a cloud SDK
- the surface grows beyond what is listed above
