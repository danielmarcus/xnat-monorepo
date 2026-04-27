# ADR 0008: Typed Stubs in an Internal `stubs` Source Set Replace `Object`-Typed Facades for xnat-data-models Codegen Boundary

**Status:** Accepted
**Date:** 2026-04-27
**Authors:** XNAT Core Team
**Deciders:** XNAT Core Team

---

## Context

The monorepo uses a stub-and-exclude pattern for the `apps/web` ↔
`libs/xdat` circular compile dependency: `libs/xnat-api` ships
compile-time facade classes (e.g. `org.nrg.xnat.utils.CatalogUtils`,
`org.nrg.xnat.turbine.utils.ArchivableItem`) whose FQNs collide with
real implementations in `apps/web` or generated code in
`build-tools/xnat-data-models`. The xnat-api JAR excludes these
facades, so at runtime only the real implementations are loaded.

Several facade methods declared parameters typed as `Object` because
the real types live in `xnat-data-models` codegen output (e.g.
`org.nrg.xdat.model.XnatResourcecatalogI`,
`org.nrg.xdat.model.XnatImagescandataI`,
`org.nrg.xdat.model.CatEntryI`, `org.nrg.xdat.bean.CatCatalogBean`),
and `xnat-api` cannot depend on `xnat-data-models` (that would close
the circular dependency Gradle already refuses to compile).

This produced runtime descriptor mismatches:

```
java.lang.NoSuchMethodError:
  'org.nrg.xnat.utils.CatalogUtils$CatalogData
   org.nrg.xnat.utils.CatalogUtils$CatalogData.getOrCreate(
     java.lang.String, java.lang.Object, java.lang.String)'
  at BaseXnatResourcecatalog.getCorrespondingFilesWithCatEntries(:93)
```

`xnat-data-models` (in `BaseXnatResourcecatalog`, `BaseXnatImagescandata`,
`BaseXnatImagesessiondata`) compiled against the `Object`-typed stubs
and baked descriptors like `(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/String;)…`
into bytecode. At runtime apps/web's `CatalogUtils` only declared
`(Ljava/lang/String;Lorg/nrg/xdat/model/XnatResourcecatalogI;Ljava/lang/String;)…`,
and the JVM rejected the call. The same descriptor-mismatch class
applied to `CatalogData.catBean` (field type `CatBeanProxy` vs runtime
`CatCatalogBean` → `NoSuchFieldError`), `CatalogUtils.getFile` /
`getFileStats` / `Stats(...)` constructor, and the scan-type facades
(`ScanTypeMappingI`, `AbstractScanTypeMapping`, `ImageScanTypeMapping`).

Eight smoke tests (`smoke-tests/test_resources.py` x4 with
`@pytest.mark.xfail(strict=True)`, plus four DICOM tests in
`test_dicom_upload.py` gated by an `archived_experiment` fixture
that detected the error signature and called `pytest.xfail`) pinned
the failure.

---

## Decision

Adopt a **typed-stub + internal `stubs` source set** model for the
`xnat-api` → `xnat-data-models` boundary:

1. Add minimal stub interfaces / classes for the codegen FQNs that
   appear in xnat-api facade signatures. The current set:
   `org.nrg.xdat.model.XnatResourcecatalogI`,
   `org.nrg.xdat.model.XnatImagescandataI`,
   `org.nrg.xdat.model.CatCatalogI`,
   `org.nrg.xdat.model.CatEntryI`,
   `org.nrg.xdat.bean.CatCatalogBean`,
   `org.nrg.xdat.om.XnatResourcecatalog`.
2. Place these stubs in a dedicated `stubs` Gradle source set at
   `libs/xnat-api/src/stubs/java/`. Output goes to
   `build/classes/java/stubs/`, NOT `build/classes/java/main/`.
3. xnat-api's main `compileJava` includes `stubs.output` on its
   classpath so facade signatures (`CatalogUtils`, `ScanTypeMappingI`,
   etc.) compile against the typed stubs.
4. Downstream consumers (`xnat-data-models`, `apps/web`) see only
   `xnat-api/main`'s outgoing variants. They never observe the empty
   stubs and resolve the FQNs to the codegen / hand-written runtime
   versions, which carry the full method/field surface that consumer
   code depends on.
5. Retype every xnat-api facade signature that crosses the
   xnat-data-models boundary. Concretely:

   - `CatalogData` field `catBean` → `CatCatalogBean`;
     constructor `(CatCatalogBean, File, String, String)`;
     constructors `(File, XnatResourcecatalog, String[, String[, boolean]])`.
   - `CatalogData.getOrCreate(String, XnatResourcecatalogI, String)` and
     `(ArchivableItem, XnatResourcecatalogI)`.
   - `CatalogData.getOrCreateAndClean(String, XnatResourcecatalogI, boolean, String)`
     and `(…, UserI, EventMetaI)`.
   - `Stats(CatCatalogI, String, String)`.
   - `getFile(CatEntryI, String, String)` and `(CatEntryI, String, String, String)`.
   - `getFileOnLocalFileSystem(String, String, String)` (was `Object` for
     `entry`, but the real impl takes `String uri`).
   - `getCatalog(String, XnatResourcecatalogI, String)` returning
     `CatCatalogBean`; `getCatalog(File, String)` returning `CatCatalogBean`.
   - `getCatalogFile(String, XnatResourcecatalogI)` and
     `(String, String, XnatResourcecatalogI)`.
   - `getCleanCatalog(String, String, XnatResourcecatalogI, boolean[, UserI, EventMetaI])`
     returning `CatCatalogBean`.
   - `formalizeCatalog(CatCatalogI, String, String, UserI, EventMetaI[, boolean, boolean])`.
   - `getCatalogProject(CatCatalogBean)`, `setCatalogProject(CatCatalogBean, String)`.
   - `getFileStats(CatCatalogI, String, String)`.
   - `deleteRemoteFile(String, String)` (was `(Object, String)`; real takes a URI string).
   - `ScanTypeMappingI.setType(XnatImagescandataI)`,
     `AbstractScanTypeMapping.getMappedType(XnatImagescandataI, Map)`,
     `ImageScanTypeMapping.getMappedType(XnatImagescandataI, Map)`.

   After this pass, no xnat-api facade signature reachable from
   xnat-data-models takes a generated/codegen type as `Object`. The only
   `Object` parameter that remains is `formatFileStats(String, long, Object)`,
   which matches the apps/web real impl exactly (the third arg is
   genuinely a heterogeneous size value), so it is not a drift.

The xnat-api JAR continues to exclude the facade `.class` files; the
runtime classpath is unchanged. The change is invisible to operators
and to apps/web's compile.

---

## Alternatives Considered

### A. Add `Object` overloads in apps/web's `CatalogUtils` that delegate to the typed methods

The smallest possible change — two `(String, Object, String)` and
`(Object, Object)` overloads in apps/web's `CatalogUtils` cast and
delegate to the typed signatures.

**Rejected because:** it patches the symptom rather than the contract
drift, leaves the same trap waiting for the next codegen type that
gets touched, and conflicts with the project's standing preference for
structural fixes over special-case patches. The same objection applies
to `getOrCreateAndClean`, `getFile`, `getFileStats`, `Stats(...)`,
`catBean`, and the scan-type triumvirate — Object-overload-and-cast
would have to be repeated for each.

### B (literal). Promote `XnatResourcecatalogI` / `ArchivableItem` into `xnat-api`, or create a new shared module

The originally-considered structural fix: hand-write the codegen
interfaces in xnat-api (or in a new `xnat-shared-types` module that
both xnat-api and xnat-data-models depend on), so xnat-api's
signatures use real types and the codegen no longer needs to produce
them.

**Rejected because:** it requires modifying the xdat-data-builder
codegen pipeline (so it doesn't duplicate the promoted interfaces),
or restructuring the module graph (a new top-level module that two
existing modules depend on, with corresponding settings.gradle / BOM
churn). Those changes are appropriate for the broader Phase 2
"retire the stub-and-exclude workaround" effort; they are out of
scope for an in-flight CI gate fix.

### C. Change `BaseXnatResourcecatalog` (codegen) to call differently-named methods

Force apps/web's CatalogUtils to expose method names that don't
collide with the stub's `Object` overloads.

**Rejected because:** doesn't address the underlying drift, drives
the contract from the codegen tail rather than from the API surface,
and would leave the next descriptor mismatch in the same condition.

### D. Configure Gradle to force apps/web to use xnat-api's JAR (with exclusions) instead of the classes directory

Add explicit consumer-side variant selection so apps/web reads the
xnat-api JAR (where stubs are excluded) rather than xnat-api's
`build/classes/java/main/` (where stubs would be present if kept in
the main source set).

**Rejected** as the primary mechanism because: forcing JAR usage
across the whole xnat-api dependency graph penalizes incremental
build time for every consumer, not just the consumers affected by
the codegen-FQN collision. The internal `stubs` source set approach
keeps the classes-directory variant fast for everyone and only
isolates the specific files that need isolation.

---

## Consequences

### Positive

- The `getOrCreate` `NoSuchMethodError`, the `catBean` `NoSuchFieldError`,
  and the latent scan-type descriptor mismatch all close in one pass.
- The pattern is reusable: future codegen-FQN parameter additions to
  any xnat-api facade follow the same recipe (stub class in
  `src/stubs/java/`, exclude from xnat-api JAR if the stub FQN
  collides with main-source classes — for the codegen FQNs the new
  source-set keeps them out of main's classes directory automatically).
- Smoke-test gate goes green: `test_resources.py` (5 tests, 0 xfail)
  and `test_dicom_upload.py` (7 tests, 0 xfail) all pass against the
  local Compose stack, unblocking Phase B/C verification.

### Negative

- The stubs are minimal and may drift from codegen output. If
  xnat-api's facade is later extended to call methods on a stubbed
  type, the stub must be enriched accordingly (compile error, not
  silent runtime bug). The current method-level couplings are
  `CatCatalogBean.getEntries_entry()` and `setId(String)`,
  `CatEntryI.getContent()` and `getUri()`. The interface stubs
  (`XnatResourcecatalogI`, `XnatImagescandataI`, `CatCatalogI`)
  and the class stub `XnatResourcecatalog` are intentionally empty —
  xnat-api never calls methods on them; only their FQNs are needed
  for descriptor matching.
- A separate `stubs` source set is non-standard and slightly raises
  the cognitive load of `libs/xnat-api/build.gradle.kts`. The build
  file carries comments tying back to this ADR.
- The whole stub-and-exclude pattern is itself a workaround for the
  `apps/web` ↔ `libs/xdat` circular compile dependency (CLAUDE.md
  Gotchas), and Phase 2 plans to retire it entirely. This ADR does
  not try to anticipate that work; it just removes the descriptor
  drift on the existing surface.

### Neutral

- The scan-type descriptor mismatch (`ScanTypeMappingI.setType`) is
  fixed in this ADR's pass even though `BaseXnatImagesessiondata`
  invokes `setType` reflectively at the actual runtime call site —
  the typed stubs match the apps/web impl regardless of how the call
  is dispatched, which is the correct contract.
- Tomcat 10's jakarta-migration step caches its output via a sentinel
  at `/usr/local/tomcat/webapps/.jakarta-migrated`; redeploying after
  changing classes inside the WAR requires removing the sentinel and
  the exploded `webapps/ROOT` directory. Documented in
  `deploy/docker-compose/xnat/jakarta-migrate-and-start.sh`.

---

## Files Touched

- `libs/xnat-api/src/main/java/org/nrg/xnat/utils/CatalogUtils.java` — full retype of `CatalogData` (`catBean` field, all constructors that take a catalog/resource type, `getOrCreate` / `getOrCreateAndClean`) plus all static helpers that take `XnatResourcecatalogI` / `CatCatalogI` / `CatCatalogBean` / `CatEntryI` (`getCatalog`, `getCatalogFile`, `getCleanCatalog`, `formalizeCatalog`, `getCatalogProject`, `setCatalogProject`, `getFile`, `getFileStats`, `getFileOnLocalFileSystem`, `deleteRemoteFile`); removed `CatBeanProxy`.
- `libs/xnat-api/src/main/java/org/nrg/xnat/helpers/scanType/{ScanTypeMappingI,AbstractScanTypeMapping,ImageScanTypeMapping}.java` — typed against `XnatImagescandataI`.
- `libs/xnat-api/src/stubs/java/org/nrg/xdat/model/{XnatResourcecatalogI,XnatImagescandataI,CatCatalogI,CatEntryI}.java` — new stub interfaces.
- `libs/xnat-api/src/stubs/java/org/nrg/xdat/bean/CatCatalogBean.java`, `libs/xnat-api/src/stubs/java/org/nrg/xdat/om/XnatResourcecatalog.java` — new stub classes.
- `libs/xnat-api/build.gradle.kts` — `stubs` source set declaration; revised JAR exclusion comments.
- `smoke-tests/test_resources.py` — removed `@pytest.mark.xfail(strict=True)` from 4 tests; trimmed obsolete bug-pin documentation.
- `smoke-tests/test_dicom_upload.py` — removed `pytest.xfail` detection block in `archived_experiment` fixture; added `columns=ID,label,subject_ID` to listing query so `subject_ID` is present in JSON (XNAT REST omits non-default fields without explicit `columns=`).

---

## Verification

Against `deploy/docker-compose` on branch `feat/tomcat10-eks-tests`:

```
$ pytest smoke-tests/test_resources.py -v       # 5 passed
$ pytest smoke-tests/test_dicom_upload.py -v    # 7 passed
```

Bytecode descriptor confirmation in the deployed
`xnat-data-models-1.10.0-RC2-SNAPSHOT.jar`:

```
$ javap -p -c BaseXnatResourcecatalog.class | grep getOrCreate:
21: invokestatic … getOrCreate:(Ljava/lang/String;Lorg/nrg/xdat/model/XnatResourcecatalogI;Ljava/lang/String;)Lorg/nrg/xnat/utils/CatalogUtils$CatalogData;
```
