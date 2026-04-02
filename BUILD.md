# Build Guide

This document covers every Gradle command you need to build XNAT, describes
the module structure, explains common failure modes, and documents Java 21 and
Gradle 8 toolchain details.

---

## Quick Start

```sh
# Build everything: compile, generate sources, run unit tests, assemble artifacts
./gradlew build

# Build but skip tests (faster feedback loop)
./gradlew build -x test

# Build only the deployable WAR
./gradlew :apps:web:war
# Output: apps/web/build/libs/web-1.10.0-RC2-SNAPSHOT.war
```

---

## Root-Level Commands

| Command | Description |
|---------|-------------|
| `./gradlew clean` | Delete all `build/` directories across every module |
| `./gradlew assemble` | Compile and assemble artifacts without running tests |
| `./gradlew check` | Run all verification tasks: unit tests, Checkstyle, and any other `check` hooks |
| `./gradlew test` | Run unit tests across all modules |
| `./gradlew build` | `assemble` + `check` — the standard full build |
| `./gradlew :apps:web:war` | Produce only the deployable WAR at `apps/web/build/libs/` |
| `./gradlew publishToMavenLocal` | Publish all artifacts to the local Maven cache (`~/.m2/repository`) |
| `./gradlew dependencies` | Print the full dependency tree for the root project |
| `./gradlew :apps:web:dependencies` | Dependency tree for the WAR module |
| `./gradlew dependencyUpdates` | Report outdated dependencies (Ben Manes plugin) |

---

## Module-Specific Commands

All commands accept a module path prefix (`:group:module:task`).

```sh
# Build and test one module in isolation
./gradlew :libs:framework:build

# Run only tests for a specific module
./gradlew :libs:xdat:test

# Checkstyle for a single module
./gradlew :libs:xdat:checkstyleMain :libs:xdat:checkstyleTest

# Generate XSD-derived sources for xnat-data-models
./gradlew :build-tools:xnat-data-models:generateSources

# Build the convention plugin included build
./gradlew :build-logic:build
```

---

## Build Performance

```sh
# Enable the Gradle build cache and parallel execution (already set in gradle.properties)
./gradlew build --build-cache --parallel

# Incremental build after changing one module — Gradle will skip unchanged modules
./gradlew :apps:web:war

# Force a full re-run (ignores UP-TO-DATE checks)
./gradlew build --rerun-tasks

# Refresh the dependency cache (pick up new snapshot versions)
./gradlew --refresh-dependencies build
```

---

## Clean-Room Build

Reproducing a build from scratch is useful for debugging caching issues:

```sh
# Remove all build output
./gradlew clean

# Remove the Gradle build cache for this project
rm -rf .gradle/

# Nuclear option: remove the global Gradle cache (affects all local projects)
rm -rf ~/.gradle/caches/

# Re-run with detailed logging
./gradlew build --info 2>&1 | tee build.log
```

---

## Common Failures

### Dependency resolution failures

| Symptom | Likely Cause | Fix |
|---------|--------------|-----|
| `Could not resolve org.nrgxnat:...` | XNAT Artifactory credentials missing or expired | Set `xnatArtifactoryUser` / `xnatArtifactoryPassword` in `~/.gradle/gradle.properties` |
| `Could not resolve ...SNAPSHOT` | Snapshot not in cache and Artifactory is unreachable | Run `./gradlew --refresh-dependencies build` with a valid network connection |

### Java version failures

| Symptom | Likely Cause | Fix |
|---------|--------------|-----|
| `Unsupported class file major version 65` | Compiled with JDK 21 but being run on JRE < 21 | Ensure `JAVA_HOME` points to JDK 21 |
| `error: invalid source release 21` | `JAVA_HOME` points to an older JDK | Install JDK 21 and update `JAVA_HOME` |
| Gradle toolchain auto-provisioning hangs | Foojay provisioning API unavailable | Install JDK 21 manually and set `org.gradle.java.installations.paths` in `~/.gradle/gradle.properties` |

### Reactor version conflict

**Symptom:** Build fails with:
```
Could not resolve io.projectreactor:reactor-core:... (conflict between versions X and Y)
```

**Cause:** Multiple transitive dependency paths disagree on the Reactor version.

**Fix:** The root `build.gradle.kts` enforces a specific Reactor version via a
dependency constraint (force resolution strategy).  If you see this error after
updating a dependency, update the forced version in the root build file:

```kotlin
// build.gradle.kts (root)
configurations.all {
    resolutionStrategy.force("io.projectreactor:reactor-core:3.6.x")
}
```

### Circular dependency / `web-stubs`

**Symptom:** Build fails with a circular dependency error between `libs:xdat`
and `apps:web` (or similar).

**Cause:** The original polyrepo had a circular compile-time dependency between
the web application and the XDAT data layer.

**Fix:** `build-tools/web-stubs` provides compile-time interface stubs that
break the cycle.  Ensure `apps/web/build.gradle.kts` declares a dependency on
`build-tools:web-stubs` rather than the full `libs:xdat` for the affected
classes.

### ANTLR grammar directory

**Symptom:** ANTLR source generation fails with `grammar file not found`.

**Cause:** During migration the ANTLR grammar directory was renamed from
`src/main/antlr4` to `src/main/antlr` for consistency.

**Fix:** The grammar files live in `src/main/antlr`.  The Gradle `antlr`
configuration in the affected modules points there.  If you add a new grammar
file, place it under `src/main/antlr/`.

### `xnat-data-models` partial compilation

**Symptom:** `build-tools:xnat-data-models` compiles partially but some
generated classes are missing.

**Cause:** Known issue — the full XSD schema graph for `xnat-data-models`
references classes that create a dependency cycle between the data-models
module and `libs/xdat`.  This is tracked as a known issue in
`MIGRATION_REPORT.md`.

**Workaround:** `build-tools/web-stubs` supplies the required stub interfaces so
that `apps/web` can compile fully.  A complete resolution of the
`xnat-data-models` cycle is deferred to a follow-up task.

---

## Java 21 Toolchain Notes

This project targets **Java 21** and uses the
[Gradle toolchain](https://docs.gradle.org/current/userguide/toolchains.html)
feature to ensure the correct JDK is used regardless of the system default.

The toolchain is declared in the root convention plugin
(`build-logic/src/main/kotlin/xnat.java-conventions.gradle.kts`):

```kotlin
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
```

Gradle will auto-provision JDK 21 via the Foojay Disco resolver if it is not
found locally.

### Java 21 Dependency Additions

Several APIs that were bundled with older JDKs were removed or modularized in
Java 11+ and are now provided as explicit dependencies.  The following were
added to `platform/bom` as part of the migration:

| Dependency | Reason Added |
|-----------|--------------|
| `javax.annotation:javax.annotation-api` | `@PostConstruct`, `@PreDestroy` removed from JDK |
| `javax.inject:javax.inject` | CDI inject annotations not bundled in JDK 9+ |
| `com.sun.mail:javax.mail` | `javax.mail` removed from JDK; Jakarta EE package |
| `jakarta.xml.bind:jakarta.xml.bind-api` + `org.glassfish.jaxb:jaxb-runtime` | JAXB removed from JDK 11 |

---

## Gradle 8 Migration Notes

The build was migrated to Gradle 8 with the following changes:

- **Kotlin DSL throughout** — all `build.gradle` files converted to
  `build.gradle.kts`.
- **Version catalog** — all dependency versions declared in
  `build-logic/gradle/libs.versions.toml`; referenced via the `libs.*` alias
  accessors.
- **Convention plugins** — shared build logic extracted into
  `build-logic/src/main/kotlin/` as `*.gradle.kts` convention scripts applied
  with `plugins { id("xnat.java-library-conventions") }`.
- **Included builds** — `build-logic`, `build-tools/xdat-data-builder`, and
  `build-tools/xnat-data-builder` are included builds declared in
  `settings.gradle.kts` via `pluginManagement { includeBuild(...) }`.
- **Configuration cache** — enabled in `gradle.properties`; do not use
  imperative `afterEvaluate` blocks or non-serializable configuration objects.
