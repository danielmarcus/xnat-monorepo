# ADR 0001: Monorepo over Polyrepo

**Status:** Accepted
**Date:** 2026-01-15
**Authors:** XNAT Core Team
**Deciders:** XNAT Steering Committee

---

## Context

XNAT core was historically maintained as 24 separate Git repositories, each
containing one or a small number of related modules.  This structure reflected
the way the codebase grew organically over many years, with different teams
owning different subsystems.

As the project matured, a number of recurring problems emerged:

1. **Cross-cutting changes require coordinated multi-repo PRs.** A single
   user-facing feature might touch `xnat-framework`, `xdat`, `xnat-web`, and
   one or more imaging libraries.  Coordinating and reviewing changes across
   four repositories — ensuring compatible version tags, updating dependency
   declarations, and merging in the right order — consumed significant
   engineering time and was error-prone.

2. **Dependency version skew.** Each repository declared its own versions of
   shared libraries.  The same transitive dependency appeared at different
   versions across repos, causing integration failures discovered late in the
   release cycle.

3. **Lack of atomic refactoring.** Renaming a public API, moving a class, or
   extracting a utility required sequential PRs across multiple repos with no
   way to make the change atomically.

4. **CI fragmentation.** Each repository had its own CI configuration, often
   diverging in subtle ways (different Java versions, different Checkstyle
   configs, different test frameworks).

5. **Onboarding friction.** New contributors had to clone and configure up to
   24 repositories to run a full local build.

---

## Decision

Consolidate all 24 XNAT core repositories into a single Gradle multi-project
monorepo (this repository) targeting the `releases/1.10.0-rc` branch of each
source repository as the migration baseline.

---

## Alternatives Considered

### Option A: Polyrepo with stricter tooling (rejected)

Introduce shared CI templates and a centralized BOM repository to enforce
consistency across the 24 repos without consolidating them.

**Rejected because:** This addresses the tooling inconsistency but not the
coordination overhead of cross-cutting changes.  Multi-repo atomic refactoring
remains impossible.  The dependency skew problem is only partially mitigated by
a shared BOM unless every consumer upgrades in lock-step.

### Option B: Monorepo with separate build systems per sub-module (rejected)

Import all repositories but preserve each repo's existing build system
(Maven for some, Gradle for others), stitching them together at the CI level.

**Rejected because:** This approach yields none of the build-time benefits of
a unified build graph.  Cross-module incremental compilation and shared
convention plugins would not be possible.

### Option C: Monorepo with Gradle composite build (considered, not adopted)

Use Gradle's [composite build](https://docs.gradle.org/current/userguide/composite_builds.html)
feature to include each module as a separate included build rather than as
subprojects of a single multi-project build.

**Not adopted** — see ADR 0002 for the detailed rationale.

---

## Consequences

### Positive

- Single `./gradlew build` command builds the entire XNAT core stack.
- Cross-cutting changes can be made atomically in one PR with a single CI run.
- A unified version catalog (`libs.versions.toml`) eliminates dependency
  version skew across all modules.
- New contributors clone one repository and get a working build environment.
- Shared Checkstyle, Spotless, and test configuration applied consistently via
  convention plugins.

### Negative / Accepted Trade-offs

- Git history from the 24 source repositories was not preserved in the
  monorepo (flat import).  Annotated git blame for pre-migration history
  requires consulting the archived source repositories.
- The monorepo is larger than any individual source repository.  Shallow clones
  (`git clone --depth=1`) are recommended for CI runners.
- Developers who work on only one subsystem must clone the entire monorepo.
  Git sparse checkout can mitigate this if needed.

---

## Related Decisions

- ADR 0002: Gradle multi-project build (not composite build)
- ADR 0003: Java 21 target
