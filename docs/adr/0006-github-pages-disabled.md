# 6. GitHub Pages is disabled for this repository

Date: 2026-09-06
Status: Accepted

## Context

This repository had GitHub Pages enabled. A repository Pages site publishes onto the
organisation's apex domain at the repository-name path, so `enrich-test-api` was being
served at `https://enrichmeai.com/enrich-test-api/` without anyone having chosen to
publish it. What it served was the rendered `README.md`.

Two problems followed.

First, the content was wrong for a public URL. Until the rebrand, the README still
carried references to the previous organisation and a build badge pointing at a
repository that no longer existed. A private alpha's internal README was the company's
public page for this project.

Second, and decisively, the path collided. `enrichmeai.github.io` publishes the
organisation site on the same apex domain. When an authored product page was added at
`enrich-test-api/index.html` in that repository, two Pages deployments claimed the same
path. The repository Pages site won, so the authored page was unreachable and the
homepage's link to it resolved to the rendered README instead. The collision is
structural, not a misconfiguration: an organisation site and a project site on one apex
domain cannot both own a given path.

## Decision

GitHub Pages is disabled for `enrichmeai/enrich-test-api`, by
`gh api -X DELETE repos/enrichmeai/enrich-test-api/pages`. It stays disabled.

The project's public page is authored content in the `enrichmeai.github.io` repository
at `enrich-test-api/index.html`, and is the only thing served at
`https://enrichmeai.com/enrich-test-api/`.

`README.md` is documentation for someone who has already reached the repository. It is
not a marketing page and is not published as one.

## Consequences

+ The authored product page is reachable, and the homepage link to it works.
+ The README can be written for contributors without also being public-facing copy.
+ A single owner for each path on the apex domain, so this collision cannot recur.
- The project page and the README can drift. They describe the same project from
  different angles, and nothing enforces agreement between them.
- Anyone re-enabling Pages on this repository silently breaks the product page again.
  That is the failure mode to watch for; this ADR is the record of why not to.

## Notes

This closes PRD open question 6 and Epic 6, Story 6.1.

---
