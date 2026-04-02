# Changelog

All notable changes to the XNAT monorepo will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

_Changes that are merged but not yet included in a versioned release._

---

## [2026-04-02] — Monorepo Creation

### Added

- Initial monorepo structure created by migrating XNAT component repositories
  into a single Gradle multi-project build.
- Root-level community and governance files: `LICENSE`, `SECURITY.md`,
  `CODEOWNERS`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md` (placeholder),
  `CHANGELOG.md`, `README.md`.
- Editor and tooling configuration: `.editorconfig`, `.gitignore`.
- GitHub community health files: PR template, bug report form, feature request
  form (`/.github/`).
- Developer documentation skeletons: `DEVELOPMENT.md`, `BUILD.md`,
  `RELEASE.md`, `CI_SECRETS.md`, `MIGRATION_REPORT.md`.
- Repository layout established with top-level directories:
  `apps/`, `libs/`, `build-tools/`, `build-logic/`, `platform/`,
  `deploy/`, `smoke-tests/`.

### Changed

_Nothing changed — this is the initial commit._

### Deprecated

_Nothing deprecated._

### Removed

_Nothing removed._

### Fixed

_Nothing fixed._

### Security

_No security changes._

---

[Unreleased]: https://github.com/NrgXnat/xnat/compare/HEAD...HEAD
