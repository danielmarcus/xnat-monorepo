# Monorepo Migration Report

This document records all decisions, deviations, and findings from the
migration of 24 individual XNAT repositories into this monorepo, performed
against the `releases/1.10.0-rc` branch of each source repository.

---

## Imported Repositories

All 24 repositories were imported at their `releases/1.10.0-rc` branch tip.
Git history was not preserved in the monorepo (flat import of source tree).

| Source Repository | Import Date | Target Path in Monorepo |
|-------------------|-------------|------------------------|
| xnat-web | 2026-02-10 | `apps/web` |
| xnat-framework | 2026-02-10 | `libs/framework` |
| xdat | 2026-02-10 | `libs/xdat` |
| xnat-transaction | 2026-02-11 | `libs/transaction` |
| xnat-extattr | 2026-02-11 | `libs/extattr` |
| xnat-mail | 2026-02-11 | `libs/mail` |
| xnat-notify | 2026-02-12 | `libs/notify` |
| xnat-prefs | 2026-02-12 | `libs/prefs` |
| xnat-config | 2026-02-12 | `libs/config` |
| xnat-automation | 2026-02-13 | `libs/automation` |
| xnat-spawner | 2026-02-13 | `libs/spawner` |
| dicomtools | 2026-02-14 | `libs/dicomtools` |
| DicomEdit4 | 2026-02-14 | `libs/dicom-edit4` |
| DicomEdit6 | 2026-02-14 | `libs/dicom-edit6` |
| dicom-image-utils | 2026-02-15 | `libs/dicom-image-utils` |
| ecat4xnat | 2026-02-15 | `libs/ecat4xnat` |
| xnat-dicom | 2026-02-17 | `libs/dicom-xnat` (split into sop/util/mx subprojects) |
| xnat-session-builders | 2026-02-18 | `libs/session-builders` |
| xnat-prearc-importer | 2026-02-18 | `libs/prearc-importer` |
| xnat-data-models | 2026-02-20 | `build-tools/xnat-data-models` |
| xdat-data-builder | 2026-02-20 | `build-tools/xdat-data-builder` (Gradle plugin included build) |
| xnat-data-builder | 2026-02-20 | `build-tools/xnat-data-builder` (Gradle plugin included build) |
| platform-bom | 2026-02-21 | `platform/bom` |
| xnat-test-utils | 2026-02-22 | `libs/test` |

---

## Final Module Mapping

| Old Repository | Old Artifact | New Module Path | New Artifact |
|----------------|-------------|----------------|-------------|
| xnat-web | `org.nrgxnat:xnat-web` | `apps/web` | `org.nrgxnat:xnat-web` |
| xnat-framework | `org.nrgxnat:xnat-framework` | `libs/framework` | `org.nrgxnat:xnat-framework` |
| xdat | `org.nrgxnat:xdat` | `libs/xdat` | `org.nrgxnat:xdat` |
| xnat-transaction | `org.nrgxnat:xnat-transaction` | `libs/transaction` | `org.nrgxnat:xnat-transaction` |
| xnat-extattr | `org.nrgxnat:xnat-extattr` | `libs/extattr` | `org.nrgxnat:xnat-extattr` |
| xnat-mail | `org.nrgxnat:xnat-mail` | `libs/mail` | `org.nrgxnat:xnat-mail` |
| xnat-notify | `org.nrgxnat:xnat-notify` | `libs/notify` | `org.nrgxnat:xnat-notify` |
| xnat-prefs | `org.nrgxnat:xnat-prefs` | `libs/prefs` | `org.nrgxnat:xnat-prefs` |
| xnat-config | `org.nrgxnat:xnat-config` | `libs/config` | `org.nrgxnat:xnat-config` |
| xnat-automation | `org.nrgxnat:xnat-automation` | `libs/automation` | `org.nrgxnat:xnat-automation` |
| xnat-spawner | `org.nrgxnat:xnat-spawner` | `libs/spawner` | `org.nrgxnat:xnat-spawner` |
| dicomtools | `org.nrgxnat:dicomtools` | `libs/dicomtools` | `org.nrgxnat:dicomtools` |
| DicomEdit4 | `org.nrgxnat:DicomEdit4` | `libs/dicom-edit4` | `org.nrgxnat:dicom-edit4` (artifact name normalized) |
| DicomEdit6 | `org.nrgxnat:DicomEdit6` | `libs/dicom-edit6` | `org.nrgxnat:dicom-edit6` (artifact name normalized) |
| dicom-image-utils | `org.nrgxnat:dicom-image-utils` | `libs/dicom-image-utils` | `org.nrgxnat:dicom-image-utils` |
| ecat4xnat | `org.nrgxnat:ecat4xnat` | `libs/ecat4xnat` | `org.nrgxnat:ecat4xnat` |
| xnat-dicom | `org.nrgxnat:xnat-dicom` | `libs/dicom-xnat/{sop,util,mx}` | `org.nrgxnat:dicom-xnat-{sop,util,mx}` |
| xnat-session-builders | `org.nrgxnat:xnat-session-builders` | `libs/session-builders` | `org.nrgxnat:xnat-session-builders` |
| xnat-prearc-importer | `org.nrgxnat:xnat-prearc-importer` | `libs/prearc-importer` | `org.nrgxnat:xnat-prearc-importer` |
| xnat-data-models | `org.nrgxnat:xnat-data-models` | `build-tools/xnat-data-models` | `org.nrgxnat:xnat-data-models` |
| xdat-data-builder (plugin) | `org.nrgxnat.gradle:xdat-data-builder` | `build-tools/xdat-data-builder` | `org.nrgxnat.gradle:xdat-data-builder` |
| xnat-data-builder (plugin) | `org.nrgxnat.gradle:xnat-data-builder` | `build-tools/xnat-data-builder` | `org.nrgxnat.gradle:xnat-data-builder` |
| platform-bom | `org.nrgxnat:platform-bom` | `platform/bom` | `org.nrgxnat:platform-bom` |
| xnat-test-utils | `org.nrgxnat:xnat-test-utils` | `libs/test` | `org.nrgxnat:xnat-test-utils` |

---

## Key Deviations

| Deviation | Module(s) Affected | Reason | Follow-up Required |
|-----------|-------------------|--------|-------------------|
| `web-stubs` introduced as new module | `build-tools/web-stubs`, `apps/web`, `libs/xdat` | Circular compile-time dependency between `xnat-web` and `xdat` prevented a direct multi-project build.  `web-stubs` provides the minimal interfaces that `apps/web` needs to compile without pulling in the full `libs/xdat` compile classpath. | Resolve the full circular dependency so `web-stubs` can be removed |
| ANTLR grammar directory renamed | `libs/dicom-edit6`, `libs/automation` | The source directories in the original repos used `src/main/antlr4` but Gradle's standard ANTLR convention expects `src/main/antlr`.  Directories were renamed during import. | None — resolved |
| Reactor version forced in root build | All modules via root `build.gradle.kts` | Multiple transitive paths disagreed on `reactor-core` version, causing resolution failures.  A `resolutionStrategy.force` was added to the root build. | Review when upgrading Spring/Reactor — may be removable |
| `DicomEdit4` / `DicomEdit6` artifact names normalized | `libs/dicom-edit4`, `libs/dicom-edit6` | Original artifact IDs used PascalCase (`DicomEdit4`), inconsistent with all other XNAT artifacts.  Normalized to kebab-case. | Downstream consumers must update coordinates in their dependency declarations |

---

## Java 21 Changes

| Module | Change Description |
|--------|--------------------|
| All modules | Added `javax.annotation:javax.annotation-api` to platform BOM — `@PostConstruct`/`@PreDestroy` removed from JDK |
| All modules | Added `javax.inject:javax.inject` to platform BOM — CDI annotations not bundled in JDK 9+ |
| `libs/mail` | Replaced JDK-bundled `javax.mail` with `com.sun.mail:javax.mail` (Jakarta Mail) |
| `libs/xdat`, `build-tools/xnat-data-models` | Added `jakarta.xml.bind:jakarta.xml.bind-api` + `org.glassfish.jaxb:jaxb-runtime` — JAXB removed from JDK 11 |
| `apps/web` | Updated `web.xml` servlet API from `javax.servlet` to `jakarta.servlet` namespace |
| `libs/framework` | Replaced deprecated `SecurityContextHolder.getContext().getAuthentication()` usage pattern with injected `SecurityContext` bean (Java 21 strict null-safety enforcement) |
| `libs/prefs` | Replaced reflection-based access to private fields (blocked by Java 9 module system, enforced in Java 21) with explicit accessor methods |

---

## Gradle 8 Changes

| Module / Build File | Change Description |
|--------------------|--------------------|
| All modules | Converted all `build.gradle` files to `build.gradle.kts` (Kotlin DSL) |
| `build-logic/` (new) | Extracted all common build configuration into convention plugins: `xnat.java-conventions`, `xnat.java-library-conventions`, `xnat.war-conventions` |
| `build-logic/gradle/libs.versions.toml` (new) | All dependency versions centralized in a Gradle version catalog; accessed via `libs.*` alias accessors |
| `settings.gradle.kts` | Converted to Kotlin DSL; added `includeBuild()` entries for plugin included builds |
| All modules | Removed `compile` configuration usage (removed in Gradle 7); replaced with `implementation` / `api` / `compileOnly` |
| All modules | Replaced `testCompile` with `testImplementation` |
| `build-tools/xdat-data-builder` | Migrated from `groovy` DSL to Kotlin DSL plugin project; published as an included build |
| `platform/bom` | Migrated to `java-platform` Gradle plugin for BOM publication |
| Root `build.gradle.kts` | Replaced `allprojects { ... }` configuration with convention plugin application per module (configuration avoidance) |
| All modules | Added `group` and `version` declarations consistent with the version catalog `xnat` version entry |

---

## Known Issues

| Issue | Module(s) | Severity | Status |
|-------|-----------|----------|--------|
| `xnat-data-models` partial compilation — not all XSD-derived Java classes are generated | `build-tools/xnat-data-models` | Medium | Open — `web-stubs` provides workaround |
| `web-stubs` are manually maintained stub interfaces | `build-tools/web-stubs` | Low | Open — requires resolving xdat circular dep |
| DicomEdit4/6 artifact coordinate change may break downstream consumers | `libs/dicom-edit4`, `libs/dicom-edit6` | Medium | Communication sent to known downstream maintainers |
| Integration tests disabled in CI (require running XNAT instance) | `apps/web`, smoke-tests | Low | Tracked — smoke tests run against Docker Compose stack |
| XNAT runtime SLF4J/logback mismatch — fixed by forcing SLF4J 1.7.36 | `apps/web` | **Resolved** | Fixed — SLF4J 2.x was pulled in transitively; forced to 1.7.36 to match logback-classic 1.2.13 |
| Missing Build-Date manifest attribute caused NullPointerException in XnatAppInfo — fixed | `apps/web` | **Resolved** | Fixed — added Build-Date and other XNAT-expected attributes to WAR manifest |
| BouncyCastle cyclic inheritance causes StackOverflowError during Tomcat annotation scanning | `apps/web` | Medium | Mitigated — BouncyCastle JARs added to catalina.properties jarsToSkip |

---

## Temporary Exceptions

| Exception | Module(s) | Reason | Resolution Deadline | Owner |
|-----------|-----------|--------|--------------------:|-------|
| Checkstyle violations suppressed in `libs/xdat` | `libs/xdat` | Large legacy codebase; violation count too high for a single pass | 2026-Q3 | Core team |
| `@SuppressWarnings("deprecation")` retained in `build-tools/xnat-data-models` | `build-tools/xnat-data-models` | Generated code references deprecated JAXB internals; fix requires updating the XSD generator | 2026-Q3 | Data models team |

---

## Follow-up Work

| Task | Module(s) | Priority | Target Date |
|------|-----------|----------|-------------|
| Resolve circular dependency between `apps/web` and `libs/xdat`; retire `web-stubs` | `build-tools/web-stubs`, `apps/web`, `libs/xdat` | High | 2026-Q2 |
| Complete `xnat-data-models` XSD code generation for all schemas | `build-tools/xnat-data-models` | High | 2026-Q2 |
| Re-enable Checkstyle in `libs/xdat` and fix violations | `libs/xdat` | Medium | 2026-Q3 |
| Preserve git history for each imported module (using `git filter-repo` or `git subtree`) | All | Low | 2026-Q4 |
| Add Renovate / Dependabot for automated dependency updates | Root | Low | 2026-Q2 |
| Upgrade SLF4J to 2.x + logback to 1.4+ (currently forced to SLF4J 1.7.36 for compatibility) | `apps/web`, all libs | Medium | 2026-Q3 |

---

## Out-of-Scope Repositories

| Repository | Reason Excluded | Future Migration Candidate |
|------------|----------------|--------------------------|
| xnat-pipeline-engine | Complex standalone Perl/Java hybrid pipeline; requires separate migration planning | Yes — 2027 |
| xnat-marketplace | Plugin marketplace web application with separate deployment requirements | Yes — 2026-Q4 |
| xnat-docker-compose | Replaced in the monorepo by `deploy/docker-compose/`; archived | N/A — superseded |
| xnat-cs-config | Container service configuration; depends on non-migrated container service plugin | Yes — when container service is migrated |
| xnat-jupyterhub | JupyterHub integration plugin; maintained by a separate team | Out of scope — third-party integration |

---

_Last updated: 2026-04-02_
