# ADR 0002: Gradle Multi-Project Build (not Composite Build)

**Status:** Accepted
**Date:** 2026-01-22
**Authors:** XNAT Core Team
**Deciders:** Build Infrastructure Working Group

---

## Context

Once the decision to consolidate into a monorepo was made (ADR 0001), a key
implementation question remained: **how should the Gradle build be structured?**

Gradle provides two main mechanisms for organizing a large build:

1. **Multi-project build** — a single Gradle build with one root
   `settings.gradle.kts` that declares all subprojects via `include()`.  All
   subprojects share a single build evaluation cycle and dependency graph.

2. **Composite build** — a Gradle build that `includeBuild()` other Gradle
   builds.  Each included build is an independent build with its own
   settings, version catalog, and configuration lifecycle.  The root build
   can depend on artifacts from included builds, and Gradle substitutes
   project dependencies for published artifact coordinates at build time.

---

## Decision

Use a **Gradle multi-project build** (single `settings.gradle.kts` with
`include()` declarations for all application and library subprojects).

Gradle composite builds (`includeBuild()`) are used **only** for Gradle
plugins and code-generation tools that must be available during the
configuration phase of the main build:

- `build-logic` — convention plugin project
- `build-tools/xdat-data-builder` — XSD-to-Java Gradle plugin
- `build-tools/xnat-data-builder` — schema registration Gradle plugin

These are included builds because Gradle requires that a plugin be in a
separate build from the project that applies it.

---

## Alternatives Considered

### Full composite build (all modules as separate included builds)

Split every module into its own Gradle included build, linked via
`includeBuild()` in the root `settings.gradle.kts`.

**Rejected because:**

1. **No shared dependency resolution graph.** Each included build resolves
   dependencies independently.  Version catalog entries declared in the root
   build are not automatically available to included builds without explicit
   cross-build sharing.  This would re-introduce the dependency skew problem
   that the monorepo was intended to solve.

2. **Configuration complexity.** Each included build needs its own
   `settings.gradle.kts`, `build.gradle.kts`, and potentially its own version
   catalog.  Applying the same convention plugins to 24 separate included builds
   requires additional indirection.

3. **Build cache efficiency.** The Gradle build cache works best within a
   single multi-project build.  Composite builds have separate cache namespaces
   which reduces cache hit rates across modules.

4. **IDE support.** IntelliJ IDEA's Gradle integration handles multi-project
   builds well but has historically had issues resolving composite build
   dependency graphs for code navigation.

### Hybrid: multi-project for libs, composite for apps

Keep library modules as subprojects but treat `apps/web` as a separate
composite included build.

**Rejected because:** This adds complexity without meaningful benefit.  The
`apps/web` module benefits from the same incremental compilation and shared
dependency graph as the library modules.

---

## Consequences

### Positive

- Single unified dependency resolution graph — the version catalog in
  `build-logic/gradle/libs.versions.toml` governs all module versions.
- Incremental compilation works across module boundaries — Gradle only
  recompiles modules whose sources or dependencies have changed.
- Convention plugins in `build-logic/` are applied consistently to all
  subprojects with a single `plugins { id("xnat.java-library-conventions") }`
  declaration.
- `./gradlew :apps:web:war` follows the dependency graph and only rebuilds
  what is necessary.

### Negative / Accepted Trade-offs

- The root `settings.gradle.kts` must list every subproject explicitly.  Adding
  a new module requires editing this file.  This is a minor inconvenience
  compared to the benefits.
- The initial Gradle configuration phase evaluates all subprojects, which adds
  a few seconds to cold configuration time on large machines.  This is
  acceptable given the configuration cache.

---

## Related Decisions

- ADR 0001: Monorepo structure
- ADR 0003: Java 21 target
