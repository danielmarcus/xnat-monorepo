# Development Guide

This guide covers everything you need to build XNAT locally, run it with Docker
Compose, configure your IDE, and understand how the monorepo is structured.

---

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Java (JDK) | 21 (LTS) | [Eclipse Temurin](https://adoptium.net/) recommended |
| Docker | 24+ | Required for the local compose stack and integration tests |
| Docker Compose | 2.20+ | Bundled with Docker Desktop; or install the v2 CLI plugin |
| Git | 2.40+ | |
| IntelliJ IDEA | 2024.1+ | Community or Ultimate — both work |

The Gradle wrapper (`./gradlew`) downloads and manages the correct Gradle
version automatically.  Do **not** install Gradle globally.

---

## Local Setup

### 1. Clone

```sh
git clone https://github.com/NrgXnat/xnat.git
cd xnat
```

### 2. Verify Java toolchain

```sh
java -version
# openjdk version "21.x.x" ...
```

If `java -version` reports anything older than 21, set `JAVA_HOME` to a JDK 21
installation or install [Eclipse Temurin 21](https://adoptium.net/).

### 3. Build

```sh
# Full build: compiles all modules, generates sources, runs unit tests
./gradlew build

# Faster first-time build that skips tests
./gradlew build -x test

# Build only the deployable WAR
./gradlew :apps:web:war
# Output: apps/web/build/libs/web-1.10.0-RC2-SNAPSHOT.war
```

The first build downloads all dependencies from the XNAT Artifactory instance
and from Maven Central.  This can take several minutes on a cold cache.
Subsequent builds are incremental and much faster.

### 4. Start a local XNAT with Docker Compose

```sh
# Start PostgreSQL + Tomcat (detached)
docker compose -f deploy/docker-compose/docker-compose.yml up -d

# Stream logs
docker compose -f deploy/docker-compose/docker-compose.yml logs -f xnat-web
```

Once Tomcat finishes initializing (~2 minutes for a fresh database), open
[http://localhost](http://localhost) and log in with `admin` / `admin`.

To rebuild and redeploy the WAR into the running stack:

```sh
./gradlew :apps:web:war
docker compose -f deploy/docker-compose/docker-compose.yml \
  up -d --force-recreate --no-deps xnat-web
```

### 5. Stop / clean up

```sh
docker compose -f deploy/docker-compose/docker-compose.yml down
# To also remove the PostgreSQL volume (wipes the database):
docker compose -f deploy/docker-compose/docker-compose.yml down -v
```

---

## Environment Variables

The Docker Compose stack accepts these environment variables, either exported in
your shell or placed in `deploy/docker-compose/.env`:

| Variable | Default | Purpose |
|----------|---------|---------|
| `XNAT_PORT` | `80` | Host port that maps to Tomcat's 8080 |
| `XNAT_SITE_URL` | `http://localhost` | XNAT's advertised site URL |
| `XNAT_ADMIN_USER` | `admin` | Admin username (first-run only) |
| `XNAT_ADMIN_PASS` | `admin` | Admin password (first-run only) |
| `XNAT_ADMIN_EMAIL` | `admin@example.com` | Admin email |

For Gradle builds, Artifactory credentials are read from
`~/.gradle/gradle.properties`:

```properties
xnatArtifactoryUser=your-username
xnatArtifactoryPassword=your-token-or-password
```

Without these credentials, Gradle falls back to the public release repository
and cannot resolve snapshot or internal dependencies.

---

## IDE Setup (IntelliJ IDEA)

1. **File > Open** — select the repo root directory (or `build.gradle.kts`).
2. Choose **Open as Project** when prompted.
3. IntelliJ will import all 30+ Gradle subprojects automatically.  This takes a
   minute or two on the first import.
4. Set **Project SDK** to Java 21: **File > Project Structure > Project >
   SDK**.
5. Enable annotation processing: **Settings > Build, Execution, Deployment >
   Compiler > Annotation Processors > Enable annotation processing**.
6. Install recommended plugins (optional but helpful):
   - **Kotlin** (bundled — keep it up to date)
   - **CheckStyle-IDEA** — surfaces Checkstyle violations inline
   - **SonarLint** — local static analysis

### Known IntelliJ Import Issue

The `build-tools/xdat-data-builder` and `build-tools/xnat-data-builder`
projects are Gradle plugin included builds.  IntelliJ sometimes fails to
resolve their sources on first import.  If you see red symbols for classes from
those plugins, run:

```sh
./gradlew --refresh-dependencies
```

Then re-sync the Gradle project in IntelliJ (**Reload All Gradle Projects**).

---

## Common Workflows

```sh
# Build a single module
./gradlew :libs:framework:build

# Run tests for one module
./gradlew :apps:web:test

# Run all checks (tests + Checkstyle)
./gradlew check

# Generate XDAT sources for xnat-data-models
./gradlew :build-tools:xnat-data-models:generateSources

# Run all Checkstyle checks
./gradlew checkstyleMain checkstyleTest

# Refresh stale dependency cache
./gradlew --refresh-dependencies build

# Print the full dependency graph for the web app
./gradlew :apps:web:dependencies

# Publish all artifacts to local Maven cache (for downstream plugin testing)
./gradlew publishToMavenLocal
```

---

## Generated Sources

The `build-tools/xnat-data-models` module uses the `xdat-data-builder` Gradle
plugin to generate Java source files from XSD schemas at build time.  The
generated files are placed under `build-tools/xnat-data-models/build/generated-sources/`
and are **not committed** to the repository.

The code generation is triggered automatically by `./gradlew build` but can be
run in isolation:

```sh
./gradlew :build-tools:xnat-data-models:generateSources
```

**Note:** Due to a circular dependency between `xnat-data-models` and
`libs/xdat`, code generation for the full schema set is not yet complete in
the monorepo.  The `build-tools/web-stubs` module provides compile-time stubs
that allow `apps/web` to compile while this is resolved.  See
`MIGRATION_REPORT.md` for details.

---

## Module Dependency Graph

Dependency direction: `A -> B` means A depends on B.

```
platform:bom          (no internal deps — defines the version catalog BOM)
  ^
  |
libs:transaction      (core transaction management, no XNAT deps)
libs:extattr          (extended attributes)
libs:test             (shared test utilities, test scope only)
libs:mail             (email service) -> platform:bom
libs:notify           -> libs:mail
libs:prefs            -> platform:bom
libs:config           -> libs:prefs
libs:automation       -> libs:config
libs:dicomtools       -> platform:bom
libs:dicom-edit4      -> libs:dicomtools
libs:dicom-edit6      -> libs:dicomtools
libs:dicom-image-utils
libs:ecat4xnat
libs:xdat             -> libs:transaction, libs:extattr, libs:prefs, libs:config, libs:mail
libs:framework        -> libs:xdat, libs:automation, libs:notify
libs:spawner          -> libs:framework
libs:dicom-xnat:*     -> libs:framework, libs:dicomtools, libs:dicom-edit6
libs:prearc-importer  -> libs:dicom-xnat:*, libs:session-builders
libs:session-builders -> libs:framework
build-tools:web-stubs -> libs:framework (stub implementations — breaks circ dep)
apps:web              -> libs:framework, libs:spawner, libs:prearc-importer,
                         libs:session-builders, libs:dicom-xnat:*,
                         build-tools:web-stubs
```

---

## Adding a New Module

1. **Create the directory** under the appropriate top-level folder:
   - Library shared across multiple apps → `libs/<module-name>/`
   - Standalone deployable → `apps/<module-name>/`
   - Build-time code generator → `build-tools/<module-name>/`

2. **Add a `build.gradle.kts`** and apply the appropriate convention plugin:
   ```kotlin
   // For a plain library module
   plugins {
       id("xnat.java-library-conventions")
   }
   ```

3. **Register in `settings.gradle.kts`**:
   ```kotlin
   include(":libs:my-new-module")
   ```

4. **Add to `CODEOWNERS`** if the module has distinct ownership.

5. Open a PR — CI runs `./gradlew build` across all modules and will catch
   any dependency issues introduced by the new module.
