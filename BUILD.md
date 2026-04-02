# Build Guide

> **Status:** Skeleton — to be completed during migration.
> Sections marked _[TODO]_ require content once the monorepo build is stabilized.

---

## Quick Start

```sh
# Build everything (compiles, runs unit tests, assembles artifacts)
./gradlew build

# Skip tests for a faster build
./gradlew build -x test

# Build a specific module
./gradlew :apps:web:build
```

---

## Root Commands

| Command | Description |
|---------|-------------|
| `./gradlew build` | Compile, test, and assemble all modules |
| `./gradlew test` | Run unit tests across all modules |
| `./gradlew integrationTest` | Run integration tests (requires Docker) |
| `./gradlew check` | Run all verification tasks (tests, style, etc.) |
| `./gradlew clean` | Delete all `build/` directories |
| `./gradlew dependencies` | Print the full dependency tree |
| `./gradlew :apps:web:war` | Produce the deployable WAR file |
| `./gradlew publishToMavenLocal` | Publish all artifacts to local Maven cache |
| `./gradlew dependencyUpdates` | Report outdated dependencies (Ben Manes plugin) |

_[TODO: Add commands for publishing to Nexus/Artifactory once the release pipeline is configured.]_

---

## Module-Specific Tips

_[TODO: Expand with quirks and optimizations per module after migration.]_

### `apps/web`

_[TODO]_

### `libs/core`

_[TODO]_

### `build-logic`

Convention plugins live here. Changes to convention plugins affect all modules
that apply them. Always rebuild after modifying:

```sh
./gradlew :build-logic:build
```

---

## Common Failures

_[TODO: Document common build errors and their resolutions.]_

| Symptom | Likely Cause | Fix |
|---------|--------------|-----|
| `Could not resolve ...` | Nexus/Artifactory credentials missing | Set credentials in `~/.gradle/gradle.properties` |
| `Unsupported class file major version` | Wrong JDK version | Ensure `JAVA_HOME` points to JDK 21 |
| `GenerateJaxb task failed` | Schema files missing or stale | Run `./gradlew clean generateSources` |
| _[TODO]_ | _[TODO]_ | _[TODO]_ |

---

## Clean-Room Build

To reproduce a build from scratch (useful for debugging caching issues):

```sh
# Remove all Gradle caches for this project
./gradlew clean

# Remove the global Gradle cache (nuclear option — affects all projects)
rm -rf ~/.gradle/caches/

# Re-run with info logging
./gradlew build --info
```

_[TODO: Document any required seed data or local repository mirrors.]_

---

## Java 21 Toolchain Notes

_[TODO: Complete after Java 21 migration is verified across all modules.]_

This project targets **Java 21** and uses the
[Gradle toolchain](https://docs.gradle.org/current/userguide/toolchains.html)
feature to ensure the correct JDK is used regardless of the system default.

Key points (to be expanded):

- Toolchain spec is declared in the root convention plugin (`build-logic/`).
- Gradle will auto-provision JDK 21 via Foojay resolver if not found locally.
- Record classes, pattern matching, and sealed types are used — do not lower
  the language level without discussion.
- Modules that were previously compiled at Java 11 may require code changes;
  see `MIGRATION_REPORT.md` for the list.
