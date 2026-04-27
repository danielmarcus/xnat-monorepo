# CLAUDE.md — XNAT Monorepo

XNAT server platform consolidated into a single Gradle multi-project. The on-disk artifact is the `apps/web` WAR deployed onto Tomcat. **Distinct from XNAT Workstation** (the Electron desktop viewer at `../XNAT Workstation/`).

Migration of the 24 source repos into this monorepo is **complete** (see `MIGRATION_REPORT.md`). Active work targets test-suite expansion, EKS deployment, and Tomcat 10 / Jakarta migration — see [`docs/plan-tomcat10-eks-tests.md`](docs/plan-tomcat10-eks-tests.md) for the working plan and phase ordering.

## Stack (versions that matter)

- **Java 21** (toolchain enforced by convention plugins)
- **Gradle 8.14.4** with Kotlin DSL, included builds for `build-logic` and `build-tools/{xdat,xnat}-data-builder`
- **Spring Framework 5.3.39 / Spring Security 5.7.13** (still on Spring 5 — Phase 2 moves to Spring 6)
- **Hibernate 5.6.15 / Tomcat 9.0.93 (embedded) / `javax.servlet-api:3.1.0`** (still `javax.*` — 207 `javax.servlet` and 340 `javax.persistence` imports across the tree)
- **Restlet 1.1.10** — used by `libs/xnat-api`, `libs/xdat`, `apps/web`. **This is the migration's longest pole** (see Gotchas).
- Version catalog: `gradle/libs.versions.toml`. BOM: `platform/bom`. **Never bump versions outside these two files.**
- **Forced versions** (don't touch without coordination — see `apps/web/build.gradle.kts` resolutionStrategy): `reactor-core:2.0.8.RELEASE`, `slf4j-api:1.7.36`, logback 1.2.x. All three are blockers for Spring 6.

## Layout

- `apps/web/` — the deployable WAR (the only app)
- `libs/` — 20 modules: `xdat`, `xnat-api`, `framework`, `config`, `prefs`, `automation`, DICOM stack (`dicom-xnat/{sop,util,mx}`, `dicom-edit4`, `dicom-edit6`, `dicom-image-utils`, `dicomtools`, `ecat4xnat`, `session-builders`, `prearc-importer`), plus `mail`, `notify`, `spawner`, `transaction`, `extattr`, `test`
- `platform/{bom,parent}` — BOM and parent build conventions
- `build-logic/` — included build for the `xnat-java-library` and `xnat-war-application` convention plugins
- `build-tools/` — `xnat-data-models` (XSD→Java codegen, also exposes "shared API classes" that replaced the old `web-stubs`), `xdat-data-builder`, `xnat-data-builder`
- `deploy/docker-compose/` — local stack: Postgres 15 + custom Tomcat-9-on-JDK21 image
- `deploy/cloud/terraform/` — single-EC2 cloud deploy (existing path — keep working alongside the new EKS path)
- `smoke-tests/` — pytest suite hitting a live XNAT (~12 tests today; Phase 1 of plan expands to ~50)
- `.github/workflows/` — `main-build`, `pr-validation`, `cloud-deploy`, `smoke-test`, `release`

## Workflows

- Build: `./gradlew build` — see `BUILD.md` for the full set
- WAR only: `./gradlew :apps:web:war`
- Local stack: `cd deploy/docker-compose && docker compose up -d`
- Smoke tests against local stack: `cd smoke-tests && pip install -r requirements.txt && pytest -v` (env vars `XNAT_HOST`, `XNAT_PORT`, `XNAT_ADMIN_USER`, `XNAT_ADMIN_PASS`)
- Branching: `develop` is integration; feature branches → PR to `develop`; releases on `release/X.Y` (see `RELEASE.md`)

## Gotchas

- **Restlet 1.1.10 is the entire REST API.** It predates the Restlet 2.x line — there is no Restlet 2.5+ "drop-in" upgrade path from 1.1.10. Jakarta migration almost certainly requires either (a) replacing Restlet endpoints with Spring MVC controllers (multi-week, not weekend-scope), or (b) running on Tomcat 10 with the `tomcat-jakartaee-migration` runtime bytecode transformer to rewrite `javax.servlet` → `jakarta.servlet` at load time. Validate (b) actually starts before committing to it; Restlet 1.1.10's servlet glue is unusual.
- **`apps/web` ↔ `libs/xdat` circular compile dependency** is still open. Worked around by `build-tools/xnat-data-models` exposing a "shared API" surface (the replacement for the old `web-stubs` module). Don't reintroduce `web-stubs`; do plan to retire the workaround as part of Phase 2.
- **Java 21 `--add-opens`** flags are set in the convention plugins. Removing them breaks deep-reflection paths in xdat / Hibernate. Audit per-flag, don't bulk-delete.
- **ANTLR sources** live in `src/main/antlr/` (renamed from `antlr4/` during import — see `MIGRATION_REPORT.md`). `libs/dicom-edit6` and `libs/automation` use this layout.
- **Reactor / SLF4J / logback locks** (above) will all need to move together when Spring 6 lands. Treat as one atomic change in `libs.versions.toml`.
- **DB schema**: Hibernate 6 changes implicit naming strategies. After any Hibernate bump, diff generated DDL against the live schema before promoting past staging.
- **Don't commit** Artifactory creds, GPG keys, AWS keys, `RDS_DB_PASSWORD`, or anything else listed in `CI_SECRETS.md`. They flow through GitHub Actions secrets and Terraform vars.

## Existing docs (don't duplicate)

- `README.md` — quick start
- `BUILD.md` — full Gradle command reference and perf tips
- `DEVELOPMENT.md` — local dev environment setup
- `CONTRIBUTING.md` — PR workflow
- `RELEASE.md` — branching, version naming, location
- `CI_SECRETS.md` — every secret the CI uses, with purpose
- `MONOREPO_MIGRATION_PLAN.md` / `MIGRATION_REPORT.md` — record of how this monorepo was assembled
- `SECURITY.md` — security policy
