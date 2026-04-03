# XNAT

  <img src="docs/assets/xnat.png" alt="XNAT Logo" width="300"/>

[![License](https://img.shields.io/badge/License-BSD_2--Clause-blue.svg)](LICENSE)
[![Build](https://github.com/NrgXnat/xnat/actions/workflows/main-build.yml/badge.svg)](https://github.com/NrgXnat/xnat/actions)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/)

**XNAT** is an open-source neuroimaging informatics platform developed by the Neuroinformatics Research Group at
Washington University in St. Louis. It facilitates common management, productivity, and
quality assurance tasks for neuroimaging and associated data.

This repository is the **monorepo for XNAT core**, consolidating all
first-party XNAT modules into a single Gradle multi-project build.
It was migrated from 24 individual repositories at the `releases/1.10.0-rc`
branch point and targets **Java 21** and **Gradle 8**.

> For general information, hosted instances, and community resources, visit
> [xnat.org](https://www.xnat.org/). XNAT is supported through generous funding from the National Institutes of Health, [Yosemite](https://yosemite.co/), and the [Mallinckrodt Institute of Radiology](https://www.mir.wustl.edu/)  

---

## Quick Start

### Prerequisites

| Tool | Minimum Version | Notes |
|------|----------------|-------|
| Java (JDK) | 21 (LTS) | [Eclipse Temurin](https://adoptium.net/) recommended |
| Docker | 24+ | For local XNAT containers |
| Docker Compose | 2.20+ | Bundled with Docker Desktop |
| Git | 2.40+ | |

> The Gradle wrapper (`./gradlew`) downloads and manages the correct Gradle
> version automatically.  **Do not install Gradle globally.**

### Clone and Build

```sh
# Clone the repository
git clone https://github.com/NrgXnat/xnat.git
cd xnat

# Verify your Java toolchain
java -version   # must be 21+

# Build all modules — compiles, generates sources, and runs unit tests
./gradlew build

# Build only the deployable WAR
./gradlew :apps:web:war

# Run unit tests across all modules
./gradlew test
```

The WAR is written to `apps/web/build/libs/web-1.10.0-RC2-SNAPSHOT.war`.

### Deploy Locally with Docker Compose

```sh
# Start a local XNAT instance (PostgreSQL + Tomcat)
docker compose -f deploy/docker-compose/docker-compose.yml up -d

# Tail logs
docker compose -f deploy/docker-compose/docker-compose.yml logs -f xnat-web
```

Once XNAT finishes initializing (allow ~2 minutes), open
[http://localhost](http://localhost) and log in with `admin` / `admin`.

See [DEVELOPMENT.md](DEVELOPMENT.md) for full local setup instructions.

---

## Repository Layout

```
xnat-monorepo/                    <- repo root
├── apps/
│   └── web/                      <- XNAT Web Application (WAR) — primary artifact
├── libs/                         <- Library modules (26 subprojects)
│   ├── framework/                <- Spring wiring, core beans
│   ├── xdat/                     <- XDAT ORM layer
│   ├── automation/               <- Workflow automation
│   ├── config/                   <- Site configuration
│   ├── dicom-xnat/               <- DICOM integration (sop, util, mx)
│   ├── dicom-edit4/              <- DicomEdit4 scripting engine
│   ├── dicom-edit6/              <- DicomEdit6 scripting engine
│   ├── dicom-image-utils/        <- DICOM image utilities
│   ├── dicomtools/               <- DICOM toolkit wrappers
│   ├── ecat4xnat/                <- PET ECAT format support
│   ├── extattr/                  <- Extended attribute storage
│   ├── mail/                     <- Email service
│   ├── notify/                   <- Notification framework
│   ├── prearc-importer/          <- Pre-archive data importer
│   ├── prefs/                    <- User/site preferences
│   ├── session-builders/         <- Imaging session builders
│   ├── spawner/                  <- Background job spawner
│   ├── test/                     <- Shared test utilities
│   └── transaction/              <- Transaction management
├── build-tools/                  <- Non-plugin build-time tools
│   ├── web-stubs/                <- Compile-time stubs (breaks circular dep)
│   ├── xnat-data-models/         <- XSD → Java code generation (partial)
│   ├── xdat-data-builder/        <- Gradle plugin: XSD → XDATElement sources
│   └── xnat-data-builder/        <- Gradle plugin: schema registration
├── build-logic/                  <- Gradle convention plugins (Kotlin DSL)
│   └── src/main/kotlin/          <- *.gradle.kts convention plugins
├── platform/
│   ├── bom/                      <- Dependency BOM (version catalog alignment)
│   └── parent/                   <- Shared POM metadata for Maven publish
├── deploy/
│   ├── docker-compose/           <- Docker Compose stack for local + CI use
│   └── cloud/
│       ├── terraform/            <- AWS EC2 infrastructure (Terraform)
│       └── scripts/              <- deploy.sh, teardown.sh
├── smoke-tests/                  <- Black-box smoke tests (pytest)
└── docs/
    └── adr/                      <- Architecture Decision Records
```

| Directory | Purpose |
|-----------|---------|
| `apps/web` | The primary deployable XNAT WAR artifact |
| `libs/` | 19 reusable library modules shared across the web app |
| `build-tools/` | Source generators, annotation processors, and compile-time stubs |
| `build-logic/` | Convention plugins that standardize build config across all modules |
| `platform/bom` | Dependency BOM for version alignment via the Gradle version catalog |
| `deploy/docker-compose` | Docker Compose stack for local development and CI smoke tests |
| `deploy/cloud` | Terraform + shell scripts for AWS EC2 cloud deployment |
| `smoke-tests/` | Black-box smoke tests run against a live XNAT instance |
| `docs/adr` | Architecture Decision Records |

---

## Documentation

| Document | Description |
|----------|-------------|
| [DEVELOPMENT.md](DEVELOPMENT.md) | Local setup, IDE configuration, module dependency graph |
| [BUILD.md](BUILD.md) | All build commands, common failure modes, toolchain notes |
| [RELEASE.md](RELEASE.md) | Branching strategy, release process, artifact signing |
| [MIGRATION_REPORT.md](MIGRATION_REPORT.md) | Full account of the 24-repo monorepo migration |
| [CI_SECRETS.md](CI_SECRETS.md) | GitHub Actions secrets reference |
| [CONTRIBUTING.md](CONTRIBUTING.md) | How to contribute, coding standards, PR process |
| [CHANGELOG.md](CHANGELOG.md) | Notable changes per release |
| [SECURITY.md](SECURITY.md) | Vulnerability reporting policy |
| [docs/adr/](docs/adr/) | Architecture Decision Records |

---

## License

Copyright (c) 2026 Washington University and Radiological Society of North America (RSNA).

Distributed under the **Simplified BSD (2-Clause) License**. See [LICENSE](LICENSE) for details.
