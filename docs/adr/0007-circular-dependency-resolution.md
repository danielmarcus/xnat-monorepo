# ADR 0007: `apps/web` ↔ `libs/xdat` Circular Dependency — Decision Deferred

**Status:** Deferred
**Date:** 2026-04-27
**Authors:** XNAT Core Team
**Deciders:** XNAT Core Team

---

## Context

The monorepo migration (`MIGRATION_REPORT.md`) records a circular
compile-time dependency between `apps/web` and `libs/xdat`. The original
24-repo build broke the cycle by publishing distinct artefacts in a
specific build order; collapsing them into one Gradle multi-project
re-introduced the cycle.

The current workaround: `build-tools/xnat-data-models` exposes a small
"shared API" surface — minimal interfaces and types — that
`apps/web` compiles against. The full `libs/xdat` impl is loaded only at
runtime. This replaced an earlier `build-tools/web-stubs` module
that did the same thing.

The Phase C plan (`docs/plan-tomcat10-eks-tests.md`) flagged this as a
pre-flight decision because Spring 6's stricter classloader can surface
latent cycles that Spring 5 silently tolerates. Two options were
identified:

1. **Keep the workaround.** Document why; move on.
2. **Retire it.** Resolve the circular by relocating shared interfaces
   into a proper `libs/xnat-shared-api` module, taking on the cross-module
   refactor.

---

## Decision

**Defer the decision.** Phase C.1 (Tomcat 10 via deploy-time Jakarta
migration; ADR 0005) does not bump Spring or Hibernate, so the
classloader behaviour that motivated the pre-flight check did not
change. The existing `xnat-data-models` workaround compiled and ran
against the Tomcat 10 + Jakarta-rewritten WAR with no observable issues
across the full smoke suite (29 fast + 6 full passes; the 8 xfails are
all attributable to a separate `CatalogUtils` signature mismatch
unrelated to this circular).

The decision returns when **Phase C.2** lands (Spring 6 / Hibernate 6
source-level migration). At that point one of the two options must be
chosen:

- If Spring 6 + the OpenRewrite-rewritten code compiles cleanly with the
  current workaround, **option 1 stays** and this ADR is amended to
  Status: Accepted with the workaround documented.
- If the Spring 6 build surfaces classloader issues — the most likely
  failure mode is `LinkageError` or `IllegalAccessError` at startup —
  **option 2 is forced** and this ADR is amended to record the refactor.

---

## What's at the workaround today

`build-tools/xnat-data-models` exposes a flat list of interfaces under
`org.nrg.xdat.model.*` plus a small set of base classes under
`org.nrg.xdat.om.base.*` that `apps/web` compiles against. The full
implementations live in `libs/xdat`. At classpath assembly time the
implementations win (same FQNs); at compile time only the lightweight
interfaces are visible.

This pattern depends on `apps/web` and `libs/xdat` agreeing on the
interface signatures. The Phase A smoke run surfaced one place where
they don't:
`org.nrg.xnat.utils.CatalogUtils$CatalogData.getOrCreate(String, Object, String)`
exists in `libs/xnat-api`'s stub but not in `apps/web`'s impl, causing a
`NoSuchMethodError` for resource uploads and DICOM archive commits. That
specific incident is tracked separately and is unrelated to the
`apps/web` ↔ `libs/xdat` circular — it's a stub/impl drift in a sibling
module — but it illustrates the failure class the workaround is exposed
to.

---

## Why deferring is reasonable now

- **C.1 is non-disruptive.** No Spring/Hibernate code paths changed; the
  classloader behaviour is identical to pre-Phase-C. Forcing a refactor
  decision now would be solving a problem the branch didn't reproduce.
- **The right time is when C.2's build forces it.** Either Spring 6 will
  expose a real linkage problem and we'll know definitively, or it won't
  and we keep the workaround with confidence.
- **The refactor is bigger than its pre-emption.** Relocating shared
  interfaces touches `libs/xdat`, `libs/xnat-api`, `apps/web`, and the
  generated code in `build-tools/xnat-data-models/build/xnat-generated/`.
  Doing it speculatively for "Spring 6 might not like this" inverts
  effort-to-confidence ratios.

---

## Consequences

### Positive

- Phase C.1 ships with no scope creep into module restructuring.
- The decision is recorded explicitly so future contributors don't
  re-litigate it.

### Negative / Accepted Trade-offs

- **The workaround is still load-bearing.** Anyone reading
  `build-tools/xnat-data-models` should understand that the directory's
  purpose is "break a circular dep" rather than "contain shared models" —
  the name doesn't fully reflect that.
- **Phase C.2 inherits this decision.** That branch will need to validate
  the Spring 6 classloader behaviour explicitly before doing any other
  Spring 6 work; if it surfaces issues, the refactor scope expands.

---

## Re-open Conditions

This ADR moves to "Accepted" or to "Superseded" when any of the
following:

1. Phase C.2 (Spring 6 / Hibernate 6) ships and the workaround is
   either kept (this ADR amended) or removed in favour of a proper
   shared-types module (a new ADR replaces this one).
2. A class-loading incident in production traces to the workaround.
3. A future contributor proposes the refactor independently.

---

## Related Decisions

- ADR 0001: Monorepo structure (records the migration that created the
  cycle)
- ADR 0005: Tomcat 10 / Jakarta migration (Phase C.1 — chose not to force
  this decision)
