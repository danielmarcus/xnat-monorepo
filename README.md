# XNAT

<!-- XNAT Logo Placeholder -->
<!--
  TODO: Replace with official XNAT logo once assets are added to this repo.
  <img src="docs/assets/xnat-logo.png" alt="XNAT Logo" width="300"/>
-->

[![License](https://img.shields.io/badge/License-BSD_2--Clause-blue.svg)](LICENSE)
[![Build](https://github.com/NrgXnat/xnat/actions/workflows/ci.yml/badge.svg)](https://github.com/NrgXnat/xnat/actions)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/)

**XNAT** is an open-source neuroimaging informatics platform developed at
Washington University in St. Louis and supported by the Radiological Society
of North America (RSNA). It facilitates common management, productivity, and
quality assurance tasks for neuroimaging and associated data.

This repository is the **monorepo for XNAT core**, consolidating all
first-party XNAT modules into a single Gradle multi-project build.

> For general information, hosted instances, and community resources, visit
> [xnat.org](https://www.xnat.org/).

---

## Quick Start

### Prerequisites

| Tool | Minimum Version | Notes |
|------|----------------|-------|
| Java | 21 (LTS) | [Eclipse Temurin](https://adoptium.net/) recommended |
| Docker | 24+ | For local XNAT containers |
| Docker Compose | 2.20+ | Bundled with Docker Desktop |
| Git | 2.40+ | |

> The Gradle wrapper (`./gradlew`) is included — **do not install Gradle
> globally**.

### Clone and Build

```sh
# Clone the repository
git clone https://github.com/NrgXnat/xnat.git
cd xnat

# Verify your Java toolchain
java -version   # must be 21+

# Build all modules (skips integration tests)
./gradlew build

# Run unit tests across all modules
./gradlew test
```

### Deploy Locally with Docker Compose

```sh
# Start a local XNAT instance (PostgreSQL + Tomcat)
docker compose -f deploy/docker-compose.local.yml up -d

# Tail logs
docker compose -f deploy/docker-compose.local.yml logs -f xnat
```

Once XNAT finishes initializing (allow ~2 minutes), open
[http://localhost:8080](http://localhost:8080) and log in with
`admin` / `admin`.

See [DEVELOPMENT.md](DEVELOPMENT.md) for full local setup instructions.

---

## Repository Layout

```
xnat/                         <- repo root
├── apps/
│   └── web/                  <- XNAT Web Application (WAR)
├── libs/                     <- Shared library modules
│   ├── core/                 <- xnat-data-models, core utilities
│   ├── web-api/              <- REST API framework
│   └── ...
├── build-tools/              <- Code-generation tools, processors
├── build-logic/              <- Gradle convention plugins, version catalog
│   ├── src/
│   └── gradle/
│       └── libs.versions.toml
├── platform/                 <- BOM and platform dependency management
├── deploy/                   <- Docker Compose configs, Helm charts
└── smoke-tests/              <- End-to-end smoke test suite
```

| Directory | Purpose |
|-----------|---------|
| `apps/web` | The primary deployable XNAT WAR artifact |
| `libs/` | Reusable library modules shared across apps |
| `build-tools/` | Annotation processors and source generators |
| `build-logic/` | Convention plugins that standardize build config |
| `platform/` | Dependency BOM for version alignment |
| `deploy/` | Deployment descriptors (Docker, Kubernetes) |
| `smoke-tests/` | Black-box smoke tests against a live XNAT instance |

---

## Documentation

| Document | Description |
|----------|-------------|
| [DEVELOPMENT.md](DEVELOPMENT.md) | Local setup, IDE configuration, common workflows |
| [BUILD.md](BUILD.md) | Build commands, module tips, toolchain notes |
| [RELEASE.md](RELEASE.md) | Branching strategy, release process, signing |
| [CONTRIBUTING.md](CONTRIBUTING.md) | How to contribute, coding standards, PR process |
| [CHANGELOG.md](CHANGELOG.md) | Notable changes per release |
| [SECURITY.md](SECURITY.md) | Vulnerability reporting policy |

---

## License

Copyright (c) 2026 Washington University and Radiological Society of North America (RSNA).

Distributed under the **Simplified BSD (2-Clause) License**. See [LICENSE](LICENSE) for details.
