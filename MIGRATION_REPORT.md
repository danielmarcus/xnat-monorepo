# Monorepo Migration Report

> **Status:** In Progress — sections will be filled in as migration tasks complete.
> This document tracks all decisions, deviations, and findings from the migration
> of individual XNAT repositories into this monorepo.

---

## Imported Repos

_[TODO: List every source repository that was imported, along with the import date,
the target path in the monorepo, and the last commit SHA captured from the source repo.]_

| Source Repository | Import Date | Target Path in Monorepo | Last Commit SHA |
|-------------------|-------------|------------------------|----------------|
| _[TODO]_ | | | |

---

## Final Module Mapping

_[TODO: Map every Gradle subproject in the source repos to its new path in the monorepo.
Include the old artifact group:name and the new artifact group:name if changed.]_

| Old Repository / Module | Old Artifact Coordinates | New Module Path | New Artifact Coordinates |
|------------------------|-------------------------|----------------|------------------------|
| _[TODO]_ | | | |

---

## Deviations

_[TODO: Document any cases where the migration deviated from the original plan,
including the rationale for the deviation and any follow-up actions required.]_

| Deviation | Module(s) Affected | Reason | Follow-up Required |
|-----------|-------------------|--------|-------------------|
| _[TODO]_ | | | |

---

## Java 21 Changes

_[TODO: List all code changes required to compile and run under Java 21.
Include breaking API removals, deprecated API usage that was updated, and
any new language features adopted.]_

| Module | Change Description | JIRA / PR |
|--------|--------------------|-----------|
| _[TODO]_ | | |

---

## Gradle 8 Changes

_[TODO: List all build script changes required to migrate from the previous
Gradle version to Gradle 8, including API deprecations, plugin upgrades,
and convention plugin refactors.]_

| Module / Build File | Change Description | JIRA / PR |
|--------------------|--------------------|-----------|
| _[TODO]_ | | |

---

## Issues Discovered

_[TODO: Document bugs, misconfigurations, or technical debt uncovered during
migration that were not part of the original scope.]_

| Issue | Module(s) | Severity | Status | JIRA |
|-------|-----------|----------|--------|------|
| _[TODO]_ | | | | |

---

## Temporary Exceptions

_[TODO: List any rules, checks, or standards that are temporarily disabled or
relaxed for specific modules, with a deadline for resolution.]_

| Exception | Module(s) | Reason | Resolution Deadline | Owner |
|-----------|-----------|--------|--------------------:|-------|
| _[TODO]_ | | | | |

---

## Follow-up Work

_[TODO: Capture all tasks that were deferred out of the migration scope but
must be completed before the monorepo is considered fully stable.]_

| Task | Module(s) | Priority | Owner | Target Date |
|------|-----------|----------|-------|-------------|
| _[TODO]_ | | | | |

---

## Out-of-Scope Repos

_[TODO: List repositories that were explicitly excluded from this migration,
along with the reason they were excluded and whether they are candidates for
a future migration.]_

| Repository | Reason Excluded | Future Migration Candidate |
|------------|----------------|--------------------------|
| _[TODO]_ | | |

---

_Last updated: 2026-04-02_
