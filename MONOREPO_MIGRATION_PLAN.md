# XNAT Monorepo Migration Plan

**Target release:** `releases/1.10.0-rc`
**Plan date:** 2026-04-02
**Prepared for:** XNAT Platform Engineering

---

## 1. In-Scope Repos

All 24 repositories are pinned to branch `releases/1.10.0-rc`. Each will be imported into the monorepo using `git subtree` or `git filter-repo` to preserve full commit history under the target path.

| Repo Name | Source URL | Target Monorepo Path | Resolved Ref | Commit SHA |
|---|---|---|---|---|
| xnat-web | https://bitbucket.org/xnatdev/xnat-web | apps/web | releases/1.10.0-rc | d5ffd7ddabcca76467759433a40c7e1f6ad56c52 |
| parent | https://bitbucket.org/xnatdev/xnat-build-parent | build/parent | releases/1.10.0-rc | 7a2e9f37a33531c85f0d48740fbb6e9ee6528d04 |
| framework | https://bitbucket.org/xnatdev/xnat-framework | libs/framework | releases/1.10.0-rc | 7e485941c31ef68be6a8f4441cc0f205f32e2220 |
| prefs | https://bitbucket.org/xnatdev/xnat-prefs | libs/prefs | releases/1.10.0-rc | 35559bb09786ebb03127e293997dba16932c16b3 |
| automation | https://bitbucket.org/xnatdev/xnat-automation | libs/automation | releases/1.10.0-rc | 4b2255e8f997f44796bd7a2bf6239f8919b66e10 |
| config | https://bitbucket.org/xnatdev/xnat-config | libs/config | releases/1.10.0-rc | 0a078a1446b9b60bae62d462d16a8d7950c07de8 |
| transaction | https://bitbucket.org/xnatdev/xnat-transaction | libs/transaction | releases/1.10.0-rc | 8ac9844f9f0c072a6329beda9022e613859e992b |
| test | https://bitbucket.org/xnatdev/xnat-test | libs/test | releases/1.10.0-rc | 37ccb1ff3391ac8c1bd48101bdbd0ae4c9199811 |
| mail | https://bitbucket.org/xnatdev/xnat-mail | libs/mail | releases/1.10.0-rc | d19382b232aadefe84a6c7325218c79b75d2849d |
| notify | https://bitbucket.org/xnatdev/xnat-notify | libs/notify | releases/1.10.0-rc | 03ec8267751e45f459918cd3aaccd6a25c676b2c |
| dicomtools | https://bitbucket.org/xnatdev/xnat-dicomtools | libs/dicomtools | releases/1.10.0-rc | 0c80f29f93596758bf6720e2c5f113d44bdd32a9 |
| xdat | https://bitbucket.org/xnatdev/xdat | libs/xdat | releases/1.10.0-rc | f29b303ebd1f5de0be6702d9789b23189dcdd1f4 |
| spawner | https://bitbucket.org/xnatdev/xnat-spawner | libs/spawner | releases/1.10.0-rc | d0d5334ba693ae69755a165336deca9c9e22b0ea |
| xnat-data-models | https://bitbucket.org/xnatdev/xnat-data-models | codegen/xnat-data-models | releases/1.10.0-rc | 29cd799fa3b1c84c6f5790253842cd1a21b8b2ac |
| xnat-data-builder | https://bitbucket.org/xnatdev/xnat-data-builder | codegen/xnat-data-builder | releases/1.10.0-rc | 1ceb9df7ea472f306edbd712c39fad7327bb60de |
| xdat-data-builder | https://bitbucket.org/xnatdev/xdat-data-builder | codegen/xdat-data-builder | releases/1.10.0-rc | 0ceaebaed7ea98d8af355efdf877637911dee120 |
| extattr | https://bitbucket.org/xnatdev/xnat-extattr | libs/extattr | releases/1.10.0-rc | 97e439ce4010b638e8c244fdd4dfd2c3dc0cd7a0 |
| dicom-edit4 | https://bitbucket.org/xnatdev/dicom-edit4 | libs/dicom-edit4 | releases/1.10.0-rc | 322a1f955bf3d15b784bc46f840b16242151393f |
| dicom-edit6 | https://bitbucket.org/xnatdev/dicom-edit6 | libs/dicom-edit6 | releases/1.10.0-rc | 9e25a0fb87aa40e7324dbbc58d1c02fba5056c38 |
| dicom-image-utils | https://bitbucket.org/xnatdev/dicom-image-utils | libs/dicom-image-utils | releases/1.10.0-rc | 9b49959e1e407ec6181820d3ff6d872f9d2195a5 |
| ecat4xnat | https://bitbucket.org/xnatdev/ecat4xnat | libs/ecat4xnat | releases/1.10.0-rc | e641284ae4c28d454298c88cd1a37b726586d506 |
| session-builders | https://bitbucket.org/xnatdev/xnat-session-builders | libs/session-builders | releases/1.10.0-rc | a0b41cecc041d5ac1885b6403c201def34881dae |
| dicom-xnat | https://bitbucket.org/xnatdev/dicom-xnat | libs/dicom-xnat | releases/1.10.0-rc | 74c75e5c2b4e8d3056d47b2d80560238d13be084 |
| prearc-importer | https://bitbucket.org/xnatdev/xnat-prearc-importer | libs/prearc-importer | releases/1.10.0-rc | 758c0448b6b0e6b4a9d055d8129c322a89a1d764 |

---

## 2. Out-of-Scope Repos

The following repositories are explicitly excluded from this migration. They are not required to build and test `xnat-web` at the `releases/1.10.0-rc` boundary, or they introduce risks that outweigh the benefit of including them in the initial monorepo cut.

| Repo / Category | Rationale |
|---|---|
| **mizer** | Internal metrics and monitoring utility. Not a build-time dependency of `xnat-web`. Operates as a standalone service; including it would pull in unrelated technology choices and widen the blast radius of the migration. |
| **dicomedit-pixels** | A specialized pixel-editing extension to the DicomEdit family. Ships as a separately versioned optional plugin and does not appear in the `xnat-web` compile or runtime dependency graph. |
| **all** (aggregator POM) | A release-aggregator artifact only. It references other modules but provides no source code. Its purpose (triggering a mass release via the old Maven release plugin) is superseded by the monorepo build itself. |
| **ecat** (legacy standalone) | The original, pre-XNAT ECAT library. `ecat4xnat` (in scope) is the repackaged XNAT-specific wrapper; the raw `ecat` repo is a transitive upstream that is published to the XNAT Nexus and consumed as a binary. Pulling it into the monorepo would require building it from source without a clear integration point. |
| **xnat-dicom** (disambiguation) | Distinct from `dicom-xnat` (in scope). `xnat-dicom` is an older, largely superseded DICOM routing module retained only for historical reference. It is not referenced in current `xnat-web` dependency declarations. |
| **Pipeline-adjacent repos** (xnat-pipeline-engine, pipeline-installer, xnat-pipeline-integration) | The legacy pipeline system is treated as a separately deployed subsystem. It communicates with XNAT over HTTP and is not a compile-time dependency. Migrating it would require resolving a parallel set of Spring/Hibernate version conflicts and is deferred to a follow-on workstream. |
| **Deployment-only repos** (xnat-docker-compose, xnat-helm, xnat-ansible) | Contain no Java source. They consume the WAR artifact produced by this monorepo build and are better maintained alongside release packaging automation, not inside the build graph. |
| **Container-only repos** (xnat-jupyterhub, xnat-notebook-api) | Purpose-built container integration services that expose their own REST APIs. They are optional infrastructure components, not library dependencies. They depend on `xnat-web` artifacts (not the reverse). |
| **Optional plugins** (ohif-viewer, xnat-ohif-viewer, xnat-roi, xnat-ldap) | Ship as separately versioned plugin JARs deployed into a running XNAT instance via the plugin mechanism. Including them would add scope with no benefit to validating the core `xnat-web` build. |

---

## 3. Target Directory Layout

The layout follows a three-tier convention:

- `apps/` — deployable applications (WAR, executable JAR)
- `libs/` — reusable library modules consumed by apps and other libs
- `codegen/` — code-generation modules that must run before compilation of any consumer
- `build/` — shared Gradle build logic, BOMs, and convention plugins
- `gradle/` — Gradle wrapper files (shared across all subprojects)

```
xnat-monorepo/
├── settings.gradle                  # root settings; includes all subprojects
├── build.gradle                     # root build: common repositories, allprojects config
├── gradle.properties                # shared version catalog entries, JVM args
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties   # points to Gradle 8.x distribution
│
├── build/
│   └── parent/                      # formerly: xnat-build-parent
│       ├── build.gradle
│       └── src/
│
├── apps/
│   └── web/                         # formerly: xnat-web
│       ├── build.gradle
│       └── src/
│
├── libs/
│   ├── framework/                   # formerly: xnat-framework
│   │   ├── build.gradle
│   │   └── src/
│   ├── prefs/                       # formerly: xnat-prefs
│   │   ├── build.gradle
│   │   └── src/
│   ├── automation/                  # formerly: xnat-automation
│   │   ├── build.gradle
│   │   └── src/
│   ├── config/                      # formerly: xnat-config
│   │   ├── build.gradle
│   │   └── src/
│   ├── transaction/                 # formerly: xnat-transaction
│   │   ├── build.gradle
│   │   └── src/
│   ├── test/                        # formerly: xnat-test
│   │   ├── build.gradle
│   │   └── src/
│   ├── mail/                        # formerly: xnat-mail
│   │   ├── build.gradle
│   │   └── src/
│   ├── notify/                      # formerly: xnat-notify
│   │   ├── build.gradle
│   │   └── src/
│   ├── dicomtools/                  # formerly: xnat-dicomtools
│   │   ├── build.gradle
│   │   └── src/
│   ├── xdat/                        # formerly: xdat
│   │   ├── build.gradle
│   │   └── src/
│   ├── spawner/                     # formerly: xnat-spawner
│   │   ├── build.gradle
│   │   └── src/
│   ├── extattr/                     # formerly: xnat-extattr
│   │   ├── build.gradle
│   │   └── src/
│   ├── dicom-edit4/                 # formerly: dicom-edit4
│   │   ├── build.gradle
│   │   └── src/
│   ├── dicom-edit6/                 # formerly: dicom-edit6
│   │   ├── build.gradle
│   │   └── src/
│   ├── dicom-image-utils/           # formerly: dicom-image-utils
│   │   ├── build.gradle
│   │   └── src/
│   ├── ecat4xnat/                   # formerly: ecat4xnat
│   │   ├── build.gradle
│   │   └── src/
│   ├── session-builders/            # formerly: xnat-session-builders
│   │   ├── build.gradle
│   │   └── src/
│   ├── dicom-xnat/                  # formerly: dicom-xnat
│   │   ├── build.gradle
│   │   └── src/
│   └── prearc-importer/             # formerly: xnat-prearc-importer
│       ├── build.gradle
│       └── src/
│
└── codegen/
    ├── xnat-data-models/            # XML schemas and data model descriptors
    │   ├── build.gradle
    │   └── src/
    ├── xnat-data-builder/           # Java class generator; consumes xnat-data-models
    │   ├── build.gradle
    │   └── src/
    └── xdat-data-builder/           # XDAT persistence layer generator; consumes xnat-data-models
        ├── build.gradle
        └── src/
```

---

## 4. Dependency Closure for xnat-web

The diagram below represents the **compile-time dependency graph** rooted at `apps/web`. All nodes are now subprojects within the monorepo; external Maven coordinates that previously pointed to separately deployed snapshots/releases must be replaced with project-path references (e.g., `implementation project(':libs:framework')`).

```
apps/web (xnat-web)
│
├── libs/framework (xnat-framework)          <-- primary framework aggregate
│   ├── libs/xdat                            <-- XDAT ORM layer
│   │   └── codegen/xnat-data-models        <-- schema inputs (transitive, codegen phase)
│   ├── libs/prefs                           <-- preference system
│   ├── libs/automation                      <-- workflow automation
│   ├── libs/config                          <-- server configuration
│   ├── libs/transaction                     <-- transaction management
│   ├── libs/mail                            <-- mail subsystem
│   ├── libs/notify                          <-- notification subsystem
│   ├── libs/test                            <-- shared test utilities
│   ├── libs/extattr                         <-- extended attribute support
│   ├── libs/dicomtools                      <-- DICOM utilities
│   ├── libs/spawner                         <-- UI widget spawner
│   ├── libs/dicom-edit4                     <-- DicomEdit v4 anonymization
│   ├── libs/dicom-edit6                     <-- DicomEdit v6 anonymization
│   ├── libs/dicom-image-utils              <-- DICOM image processing utilities
│   ├── libs/ecat4xnat                       <-- ECAT file format support
│   ├── libs/session-builders                <-- session import builders
│   ├── libs/dicom-xnat                      <-- DICOM-to-XNAT bridge
│   └── libs/prearc-importer                 <-- prearchive importer
│
└── codegen/xnat-data-models                 <-- code generation input (direct dep)
    ├── codegen/xnat-data-builder            <-- generates Java POJOs from XML schemas
    └── codegen/xdat-data-builder            <-- generates XDAT persistence code
```

**Key rule:** No module in `libs/` or `apps/` may depend on a `codegen/` subproject at runtime. Codegen subprojects are `buildSrc`-style dependencies or Gradle task dependencies whose outputs (generated source directories) are consumed as `compileOnly` source sets or via `sourceSets.main.java.srcDirs`.

---

## 5. Build-Generation / Codegen Dependencies

### 5.1 codegen/xnat-data-models

**What it contains:**
- XML Schema Definition (`.xsd`) files describing XNAT data entities (experiments, sessions, assessors, etc.)
- `.xml` data model descriptor files used by both `xnat-data-builder` and `xdat-data-builder`
- No Java source that ships in the runtime WAR

**What it produces:**
- A JAR of schema resources consumed by downstream generators
- Published schema artifacts (for IDE support and external plugin development)

**When it runs:**
- Phase 1 of the build: must be assembled (`./gradlew :codegen:xnat-data-models:jar`) before any generator can execute.
- In CI, a dedicated `codegenPhase` lifecycle task should depend on all `codegen/*` `jar` tasks before any `libs/` compilation begins.

### 5.2 codegen/xnat-data-builder

**What it contains:**
- A Gradle plugin / standalone tool that reads `xnat-data-models` schema files
- Velocity or FreeMarker templates for Java POJO generation

**What it produces:**
- Generated Java source files (`.java`) representing XNAT domain model classes
- Output directory: `libs/xdat/src/generated/java/` (or a configurable `buildDir` location wired into `libs/xdat`'s `sourceSets`)

**When it runs:**
- Phase 2a: after `codegen/xnat-data-models` is built, before `libs/xdat` compilation
- Implemented as a Gradle `generateSources` task that `compileJava` in `libs/xdat` depends on

### 5.3 codegen/xdat-data-builder

**What it contains:**
- A second generator targeting the XDAT persistence layer (database mapping metadata, ORMapping XML, search element registration)
- Historically produced both Java source and XML configuration consumed by Hibernate/Spring at runtime

**What it produces:**
- Generated Java classes for XDAT ORM mappers
- Generated XML bean definitions and database DDL fragments
- Output wired into `libs/xdat/src/generated/` and `apps/web/src/main/webapp/WEB-INF/`

**When it runs:**
- Phase 2b: concurrent with `xnat-data-builder` (both depend only on `xnat-data-models`)
- Must complete before `libs/xdat` and `apps/web` compile tasks

### 5.4 Recommended Build Phase Ordering

```
Phase 1:  :codegen:xnat-data-models:jar
Phase 2:  :codegen:xnat-data-builder:generateSources
          :codegen:xdat-data-builder:generateSources   (parallel with Phase 2a)
Phase 3:  :libs:xdat:compileJava  (consumes generated sources from Phases 2a + 2b)
Phase 4:  All other :libs:* compilation (depend on :libs:xdat)
Phase 5:  :apps:web:compileJava, :apps:web:war
```

---

## 6. Java 21 and Gradle 8 Compatibility Risks

### 6.1 Java 21 Risks

**SecurityManager removal (JEP 411, finalized in Java 17, hard-removed in Java 21)**

The `SecurityManager` and its associated APIs (`java.security.Policy`, `AccessController.doPrivileged`) were deprecated for removal in Java 17 and removed entirely in Java 21. XNAT's Spring Security and legacy code that calls `AccessController.doPrivileged` will throw `NoClassDefFoundError` or fail at compile time. All such call sites must be identified (grep for `AccessController`, `SecurityManager`, `doPrivileged`) and replaced with direct invocation or updated Spring Security constructs.

**Stricter module system access (JPMS encapsulation)**

Java 21 enforces strong encapsulation of JDK internals by default. Libraries that use reflection to access `sun.*` or `com.sun.*` classes (common in older versions of Spring, Hibernate, JAXB, and Xerces) will throw `InaccessibleObjectException` at runtime unless `--add-opens` JVM flags are explicitly configured. Mitigation: audit all transitive dependencies for known JPMS-unsafe patterns; add a `jvmArgs` block in `build.gradle` / `gradle.properties` as a temporary bridge while libraries are upgraded.

**Reflection issues with Spring 5.x and Hibernate 5.x on Java 21**

Spring Framework 5.x and Hibernate ORM 5.x were not designed with Java 21's virtual thread model or the finalized JPMS access rules in mind. Proxy generation (CGLIB, Javassist) may fail. The recommended path is to upgrade to Spring 6.x (which requires Jakarta EE 9+ namespace migration: `javax.*` -> `jakarta.*`) or to apply the set of `--add-opens` workarounds documented by the Spring team for running Spring 5 on Java 17/21. Assess which path is feasible within the 1.10.0-rc timeframe.

**Removed deprecated APIs**

APIs deprecated since Java 8 and removed in Java 21 include: `Thread.stop()`, `Thread.suspend()`, `Thread.resume()`, `Runtime.exec(String)` variants, and the Applet API. Codebase-wide scan required.

### 6.2 Gradle 8 Risks

**`Project.convention` and convention plugin deprecation**

Gradle 8 removes `Project.convention` and associated `Convention` object. Many older Gradle plugins (including some Groovy and Java convention plugins) relied on `project.convention.getPlugin(JavaPluginConvention)`. All such usages in `build.gradle` files and custom plugins must be replaced with `project.extensions.getByType(JavaPluginExtension)`.

**Task configuration avoidance (lazy API is now enforced)**

Gradle 8 issues deprecation warnings (which become errors in future versions) for eager task configuration patterns (`project.tasks.create(...)` vs. `project.tasks.register(...)`). Any custom Gradle plugins or `buildSrc` code in the migrated repos that uses eager configuration will need to be updated to the lazy `register`/`named` API.

**Groovy DSL compatibility**

Gradle 8's embedded Groovy version changed. Certain dynamic dispatch patterns in `build.gradle` Groovy DSL (especially around closures passed to `configurations` blocks and assignment to `ext` properties) may fail or behave differently. The safest remediation is to migrate critical build files to Kotlin DSL (`.gradle.kts`) incrementally, starting with the root build and `build/parent`.

**Deprecated dependency configurations**

The `compile` and `runtime` configurations were removed in Gradle 7 and must have been replaced by `implementation`, `api`, `runtimeOnly` in all `build.gradle` files. Any surviving `compile` usages from pre-Gradle-7 repos will cause build failure immediately.

**Incremental annotation processing compatibility**

`xnat-data-builder` and `xdat-data-builder` likely use annotation processors or custom `Exec` tasks. Gradle 8 requires processors to declare their incremental processing capability; undeclared processors trigger warnings and may disable incremental compilation globally.

---

## 7. Final Root Tasks That Define Success

The following commands, executed from the monorepo root, define the acceptance criteria for the migration. All must complete without error on a clean checkout.

| Task | Purpose | Notes |
|---|---|---|
| `./gradlew clean` | Deletes all `build/` output directories across all subprojects | Verifies the root build wires all subprojects into the lifecycle correctly |
| `./gradlew assemble` | Compiles all source sets and produces all non-test artifacts | Exercises the full codegen -> compile -> jar chain |
| `./gradlew check` | Runs all verification tasks: static analysis, checkstyle, spotbugs | Must pass with zero violations at the current ruleset threshold |
| `./gradlew test` | Executes all unit tests across all subprojects | Test results published to `build/reports/` per subproject |
| `./gradlew build` | Equivalent to `assemble` + `check`; full build including tests | The primary CI gate |
| `./gradlew :apps:web:war` | Produces the deployable `xnat-web-1.10.0-rc.war` artifact | The definitive deliverable; WAR must be deployable to Tomcat 9/10 |

**Suggested CI pipeline order:**

```
1. ./gradlew clean
2. ./gradlew :codegen:xnat-data-models:jar
   ./gradlew :codegen:xnat-data-builder:generateSources
   ./gradlew :codegen:xdat-data-builder:generateSources
3. ./gradlew assemble
4. ./gradlew test
5. ./gradlew check
6. ./gradlew :apps:web:war
```

Steps 2a and 2b may run in parallel. Steps 3-5 may be collapsed into `./gradlew build` once the pipeline is stable.

---

## 8. Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| **Circular dependencies between libs** | Medium | High | Run `./gradlew dependencies` on each subproject immediately after import. Use `dependency-analysis` Gradle plugin to detect cycles before wiring project references. Enforce a strict layering rule: `codegen` -> `libs` -> `apps`; no upward references. |
| **Codegen ordering failures** | High | High | Define an explicit `codegenPhase` lifecycle task in the root build that all `libs/*` compile tasks depend on. Add a CI check that fails if any `libs/` class file is older than the codegen output directory timestamp. |
| **Spring/Hibernate Java 21 incompatibility** | High | High | Establish a Java 21 compatibility matrix for all direct Spring and Hibernate dependencies before beginning the migration. Apply `--add-opens` flags as a temporary bridge. Target Spring 6 / Hibernate 6 upgrade in a parallel branch if 1.10.0-rc cannot absorb the Jakarta namespace migration. |
| **Large dependency graph complexity** | Medium | Medium | Use Gradle's `--scan` (Develocity) or the free `--profile` flag to identify slow subgraph evaluations. Consider splitting large `libs/framework` into smaller, more focused subprojects in a follow-on refactor. |
| **Divergent `gradle.properties` / version conflicts across repos** | Medium | High | Consolidate all dependency versions into a single `gradle/libs.versions.toml` version catalog at the monorepo root. Treat conflicting version declarations as a build error via Gradle's `strictly` version constraints. |
| **History rewriting during `git filter-repo` import** | Low | High | Tag each repo at the exact commit SHA listed in Section 1 before running any history rewrite. Keep the original Bitbucket repos read-only for 90 days post-migration. Validate SHA continuity with `git log --oneline` diff after import. |
| **Missing `releases/1.10.0-rc` branch on a repo** | Low | High | The inventory confirms all 24 repos resolve to this branch at the SHAs listed. Verify by scripted `git ls-remote` check before import begins. Abort the import script if any remote branch is absent or the tip SHA does not match. |
| **Test infrastructure assumptions (Tomcat, DB)** | Medium | Medium | Ensure the CI environment provides a Tomcat 9/10 instance and a PostgreSQL 14+ database for integration tests. Document required environment variables in a `.env.example` at the monorepo root. |

---

## 9. Assumptions

1. **Branch alignment:** All 24 in-scope repositories resolve to branch `releases/1.10.0-rc` at the commit SHAs listed in Section 1. No repository is pinned to a detached HEAD, tag, or differing branch.

2. **HTTPS public read access:** All source repositories are publicly readable over HTTPS at `https://bitbucket.org/xnatdev/<repo-name>`. No VPN or Bitbucket authentication token is required for `git clone` / `git fetch` during the import phase.

3. **GitHub SSH access for push:** The target monorepo on GitHub (or self-hosted GitHub Enterprise) is accessible via SSH. The CI runner and developer workstations have SSH keys registered with the appropriate GitHub organization.

4. **XNAT BSD license applies to all modules:** All 24 in-scope repositories are distributed under the XNAT BSD-style open-source license. No module carries a conflicting license (GPL, LGPL, AGPL) that would restrict aggregation into a single repository. A `LICENSE` file at the monorepo root covering all modules is legally sufficient; per-module `LICENSE` files will be preserved in their imported subtree paths.

5. **Gradle 8 is the target build tool:** The monorepo root wrapper will specify Gradle 8.x. Individual subproject `build.gradle` files that shipped with older wrapper specs will be updated; their local `gradle/wrapper/` directories will be removed in favor of the single root wrapper.

6. **Java 21 is the target JVM:** All compilation, testing, and WAR packaging will target Java 21 bytecode (`sourceCompatibility = JavaVersion.VERSION_21`, `targetCompatibility = JavaVersion.VERSION_21`). Java 21 LTS is required by the runtime environment.

7. **No Maven POM files in the monorepo build:** The migration converts all per-project `pom.xml` files to `build.gradle`. Maven is not used for the monorepo build. Publication to Nexus/Maven Central (if required) is handled by the Gradle `maven-publish` plugin using coordinates derived from the existing POM `groupId`/`artifactId`/`version` values.

8. **Nexus artifact proxy remains available:** Binary dependencies not in scope for this migration (e.g., `ecat` upstream, third-party DICOM libraries) continue to be resolved from the XNAT Nexus repository at `https://nrgxnat.org/nexus/`. The root `build.gradle` declares this repository for all subprojects.

9. **No monorepo tooling beyond Gradle:** This plan does not introduce Bazel, Buck, Nx, or Turborepo. Gradle's built-in multi-project support with composite builds (if needed for `buildSrc` isolation) is the only build orchestration layer.

10. **CI environment:** GitHub Actions (or equivalent) with Ubuntu 22.04 runners, Java 21 (Temurin distribution), and Gradle 8. Build cache is enabled. Dependency cache (`~/.gradle/caches`) is persisted between workflow runs.
