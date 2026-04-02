# Contributing to XNAT

Thank you for your interest in contributing to XNAT! This document provides
guidelines and information for contributors. Please read it before opening
issues or submitting pull requests.

---

## Code of Conduct

By participating in this project you agree to abide by the XNAT Code of Conduct
(see `CODE_OF_CONDUCT.md`). Please report unacceptable behavior to
security@xnat.org.

---

## Getting Started

1. **Search existing issues** before opening a new one — someone may already be
   working on it.
2. **Open an issue** to discuss significant changes before starting work.
   This ensures effort is not duplicated and the change aligns with project
   direction.
3. **Fork the repository**, create a feature branch, and submit a pull request
   against the `develop` branch (never directly against `main`).

---

## Development Setup

Full environment setup instructions are in [DEVELOPMENT.md](DEVELOPMENT.md).

Quick summary:

- **Java 21** (Temurin recommended) — enforced by the Gradle toolchain.
- **Docker & Docker Compose** — required for local XNAT and database containers.
- **Gradle 8** — use the included Gradle wrapper (`./gradlew`); do not install
  Gradle globally.

```sh
# Clone
git clone https://github.com/NrgXnat/xnat.git
cd xnat

# Verify toolchain
./gradlew --version

# Build everything
./gradlew build
```

See [BUILD.md](BUILD.md) for common build commands and troubleshooting.

---

## Coding Standards

| Topic | Standard |
|-------|----------|
| Language | Java 21 (language level) |
| Build scripts | Gradle Kotlin DSL (`.gradle.kts`) |
| Formatting | Enforced via `.editorconfig` (UTF-8, LF, 4-space indent for Java/Kotlin) |
| Nullability | Prefer `@NonNull` / `@Nullable` annotations; avoid raw nullable returns |
| Logging | SLF4J only — no `System.out.println` or `java.util.logging` in production code |
| Dependencies | Declare in the version catalog (`gradle/libs.versions.toml`); no hard-coded versions in build scripts |

Please run the style checker before submitting:

```sh
./gradlew checkstyleMain checkstyleTest
```

---

## Pull Request Process

1. Create a branch from `develop` with a descriptive name:
   ```
   feat/add-dicom-router
   fix/session-timeout-npe
   chore/upgrade-spring-6
   ```
2. Write or update tests to cover your changes.
3. Ensure all CI checks pass locally before pushing:
   ```sh
   ./gradlew build test integrationTest
   ```
4. Fill in the pull request template completely.
5. Request a review from at least one code owner (see `CODEOWNERS`).
6. Address all review comments before merging.
7. Squash-merge when the branch is approved (CI enforces this).

---

## Test Requirements

- **Unit tests** are required for all new logic.
- **Integration tests** (`src/integrationTest/`) are required for any code that
  touches the database, filesystem, or external services.
- Test coverage must not decrease for the modified module(s).
- Run the smoke test suite against a local XNAT instance for user-facing
  features (see _Running Smoke Tests Locally_ below).

---

## Commit Conventions

This project uses [Conventional Commits](https://www.conventionalcommits.org/).

```
<type>(<scope>): <short description>

[optional body]

[optional footer(s)]
```

**Types:**

| Type | When to use |
|------|-------------|
| `feat` | A new feature |
| `fix` | A bug fix |
| `docs` | Documentation only |
| `style` | Formatting, whitespace — no logic change |
| `refactor` | Code restructure — no feature or bug |
| `perf` | Performance improvement |
| `test` | Adding or correcting tests |
| `build` | Build system or dependency changes |
| `ci` | CI pipeline changes |
| `chore` | Maintenance tasks |
| `revert` | Reverting a previous commit |

**Examples:**

```
feat(dicom): add SCP routing for multi-site projects

fix(auth): prevent session fixation after login redirect

build(deps): upgrade Spring Boot to 3.3.0
```

Breaking changes must include `BREAKING CHANGE:` in the footer or `!` after
the type/scope:

```
feat(api)!: remove deprecated v1 REST endpoints
```

---

## Running Smoke Tests Locally

The smoke test suite validates end-to-end behavior against a running XNAT
instance. See [DEVELOPMENT.md](DEVELOPMENT.md) for how to stand up a local
XNAT with Docker Compose, then:

```sh
# Run smoke tests against local XNAT (default: http://localhost:8080)
./gradlew :smoke-tests:smokeTest \
  -Pxnat.host=http://localhost:8080 \
  -Pxnat.admin.user=admin \
  -Pxnat.admin.password=admin
```

> **Note:** Smoke tests require a fully initialized XNAT instance. Allow 2–3
> minutes after container startup for XNAT to finish initializing.

---

## Questions?

- XNAT Discussion Group: https://groups.google.com/g/xnat_discussion
- XNAT JIRA: https://issues.xnat.org
- General info: https://www.xnat.org/
