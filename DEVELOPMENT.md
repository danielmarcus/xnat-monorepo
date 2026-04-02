# Development Guide

> **Status:** Skeleton — to be completed during migration.
> Sections marked _[TODO]_ require content once the monorepo structure is finalized.

---

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Java (JDK) | 21 (LTS) | [Eclipse Temurin](https://adoptium.net/) recommended |
| Docker | 24+ | |
| Docker Compose | 2.20+ | |
| Git | 2.40+ | |
| IntelliJ IDEA | 2024.1+ | Community or Ultimate |

The Gradle wrapper (`./gradlew`) downloads and manages the correct Gradle
version automatically. Do **not** install Gradle globally.

---

## Local Setup

_[TODO: Expand with step-by-step instructions once all modules are migrated.]_

```sh
# 1. Clone
git clone https://github.com/NrgXnat/xnat.git
cd xnat

# 2. Verify Java toolchain
java -version   # must be 21+

# 3. Build (downloads dependencies on first run — may take several minutes)
./gradlew build

# 4. Start local XNAT with Docker Compose
docker compose -f deploy/docker-compose.local.yml up -d
```

### Environment Variables

_[TODO: Document required environment variables and where to set them.]_

Copy `.env.example` to `.env` and fill in local values before running Docker
Compose.

---

## IDE Setup (IntelliJ IDEA)

_[TODO: Complete once project structure is stabilized.]_

Recommended steps (placeholder):

1. **File > Open** — select the repo root `build.gradle.kts`.
2. Choose **Open as Project** when prompted.
3. IntelliJ will import all Gradle modules automatically.
4. Set **Project SDK** to Java 21 (**File > Project Structure > Project**).
5. Enable annotation processing (**Settings > Build > Compiler > Annotation Processors**).
6. Install recommended plugins:
   - Kotlin (bundled)
   - CheckStyle-IDEA
   - SonarLint (optional)

_[TODO: Add `.idea/` run configurations for common tasks.]_

---

## Common Workflows

_[TODO: Flesh out after migration is complete.]_

```sh
# Build a single module
./gradlew :libs:core:build

# Run tests for one module
./gradlew :apps:web:test

# Run all integration tests
./gradlew integrationTest

# Check code style
./gradlew checkstyleMain checkstyleTest

# Generate sources (JAXB, etc.)
./gradlew generateSources

# Refresh dependencies (clear cache)
./gradlew --refresh-dependencies build
```

---

## Generated Sources

_[TODO: Document which modules generate sources, which tasks trigger generation, and where output lands.]_

Some modules use annotation processors or JAXB schema compilation to produce
Java sources at build time. These are placed under `build/generated-sources/`
and should **not** be committed.

---

## Module Relationships

_[TODO: Add a dependency graph or table once all modules are imported.]_

High-level dependency order (to be confirmed):

```
platform  ->  (no internal deps — defines BOM)
libs/core ->  platform
libs/web-api -> libs/core
apps/web  ->  libs/web-api, libs/core
```

---

## Adding a New Module

_[TODO: Write a step-by-step guide with the convention plugin to apply.]_

Placeholder steps:

1. Create the directory under the appropriate top-level folder (`apps/`, `libs/`, etc.).
2. Add a `build.gradle.kts` and apply the appropriate convention plugin.
3. Register the new module in `settings.gradle.kts`.
4. Add it to `CODEOWNERS` if it has distinct ownership.
5. Open a PR — CI will validate the new module on all supported platforms.
