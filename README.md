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
# Start a local XNAT instance (PostgreSQL + Tomcat 10)
docker compose -f deploy/docker-compose/docker-compose.yml up -d

# Tail logs
docker compose -f deploy/docker-compose/docker-compose.yml logs -f xnat-web
```

Once XNAT finishes initializing (~2 minutes; first boot adds ~20s for the
Jakarta-EE migration step described under "Tomcat 10" below), open
[http://localhost](http://localhost) and log in with `admin` / `admin`.

See [DEVELOPMENT.md](DEVELOPMENT.md) for full local setup instructions.

---

## Cloud Deployment Targets

Two deployment targets coexist; pick the one that matches your environment.

| Target | Path | Status | When to use |
|---|---|---|---|
| **Single AWS EC2** | `deploy/cloud/terraform/` + `deploy/cloud/scripts/deploy.sh` | Live; manual-dispatch (`Cloud Deploy` workflow) | Reference / dev-staging. One instance, runs the same Compose stack as local dev. See [ADR 0004](docs/adr/0004-cloud-deployment.md). |
| **Amazon EKS** | `deploy/cloud/terraform/eks/` + `deploy/cloud/helm/xnat/` + `deploy/cloud/scripts/eks-deploy.sh` | Live; manual-dispatch (`EKS Deploy` workflow) | Managed Kubernetes with RDS Postgres + EFS-backed archive. See [ADR 0006](docs/adr/0006-eks-deployment-target.md) and the [Helm chart README](deploy/cloud/helm/xnat/README.md). |

Both deploy the same WAR artifact; they're independent (one stalling doesn't
block the other). Pick EC2 for reference / dev-staging; pick EKS when you need
a managed control plane and the option to scale RDS / EFS independently.

### EKS quick start

First run (cluster does not yet exist):

```sh
# Prereqs (one-time, AWS account-level): an OIDC IdP for GitHub trusted by IAM,
# an IAM role assumable by repo:danielmarcus/xnat-monorepo:* with EKS + RDS +
# EFS + EC2 + IAM + S3 permissions. See ADR 0006 "Bring-up Notes" for the
# minimum policy set we landed on.

# Set the GitHub secrets listed in CI_SECRETS.md → "EKS Deploy":
#   AWS_ROLE_TO_ASSUME, EKS_DB_PASSWORD, TF_BACKEND_BUCKET, TF_BACKEND_REGION

# Trigger the workflow with apply_terraform=true (≈ 25 min cold provision).
gh workflow run eks-deploy.yml -f apply_terraform=true
```

Subsequent deploys (image rebuild + helm upgrade only, ≈ 5–7 min):

```sh
gh workflow run eks-deploy.yml          # apply_terraform defaults to false
```

After the run, the public hostname is on the LoadBalancer Service:

```sh
aws eks update-kubeconfig --region us-east-2 --name xnat-staging
kubectl get svc -n default xnat-web -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'
```

ADR 0006's "Bring-up Notes" captures the bugs encountered during first live
deploy — IRSA for the EBS CSI driver, IMDS hop-limit interactions, ConfigMap
property-key prefix mismatches, PVC overlay clobbering — worth skimming before
adapting the chart for new environments.

---

## Tomcat 10 / Jakarta EE 9

XNAT runs on Tomcat 10.1 with Jakarta EE 9 servlet APIs as of the
`feat/tomcat10-eks-tests` work. The codebase still imports `javax.*` at
compile time (Spring 5.3.x, Hibernate 5.6.x, Restlet 1.1.10). The container
entrypoint runs Apache's
[`jakartaee-migration`](https://tomcat.apache.org/migration-10.html) tool
against the WAR before Tomcat sees it, rewriting `javax.*` → `jakarta.*`
references at deploy time.

This adds about 20 seconds to first-container-boot. A sentinel file under
`webapps/.jakarta-migrated` skips the rewrite on subsequent restarts of the
same container.

Source-level migration to `jakarta.*` (and the corresponding Spring 6 /
Hibernate 6 / Spring Security 6 bumps) is deferred — see
[ADR 0005](docs/adr/0005-tomcat-10-jakarta-migration.md) for rationale and
[`docs/plan-tomcat10-eks-tests.md`](docs/plan-tomcat10-eks-tests.md) for the
Phase C.1 / C.2 scope split.

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
│       │   └── eks/              <- AWS EKS infrastructure (Terraform)
│       ├── helm/
│       │   └── xnat/             <- Helm chart for the EKS deployment
│       └── scripts/              <- deploy.sh, teardown.sh, eks-deploy.sh
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
| `deploy/cloud/terraform/` | Terraform module for the single-EC2 deploy target |
| `deploy/cloud/terraform/eks/` | Terraform module for the EKS deploy target (cluster, node group, RDS Postgres, EFS, ECR) |
| `deploy/cloud/helm/xnat/` | Helm chart deployed onto the EKS cluster |
| `deploy/cloud/scripts/` | Shell scripts: `deploy.sh` (EC2 path), `teardown.sh`, `eks-deploy.sh` |
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

Copyright (c) 2026 Washington University

Distributed under the **Simplified BSD (2-Clause) License**. See [LICENSE](LICENSE) for details.
