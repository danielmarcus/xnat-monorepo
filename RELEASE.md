# Release Guide

> **Status:** Skeleton — to be completed during migration.
> Sections marked _[TODO]_ require content once the release pipeline is configured.

---

## Branching Conventions

_[TODO: Confirm branching model with the team.]_

| Branch | Purpose |
|--------|---------|
| `main` | Latest stable release — protected, no direct pushes |
| `develop` | Integration branch — PRs target here |
| `release/X.Y` | Release stabilization branch for version X.Y |
| `hotfix/description` | Emergency fixes branched from `main` |
| `feat/*`, `fix/*`, `chore/*` | Feature and fix branches — deleted after merge |

---

## Release Process

_[TODO: Write step-by-step release runbook.]_

High-level placeholder:

1. Create a `release/X.Y` branch from `develop`.
2. Update `CHANGELOG.md` with the release date and all notable changes.
3. Bump version in `gradle/libs.versions.toml` (or version catalog property).
4. Open a PR from `release/X.Y` into `main` — CI must be fully green.
5. Tag the merge commit: `git tag -s vX.Y.Z -m "Release X.Y.Z"`.
6. Trigger the publish workflow to deploy to Nexus/Maven Central.
7. Merge `release/X.Y` back into `develop` to capture release commits.
8. Announce on the XNAT discussion group.

---

## Snapshot vs Release

_[TODO: Document version naming conventions and snapshot publication cadence.]_

- **Snapshot versions** follow the pattern `X.Y.Z-SNAPSHOT` and are published
  automatically on every merge to `develop`.
- **Release versions** follow `X.Y.Z` (SemVer) and are published only via the
  manual release workflow.
- Snapshot artifacts are stored in the snapshots repository; release artifacts
  go to the releases repository.

---

## Signing and Secrets

_[TODO: Document GPG key setup and where secrets live.]_

Release artifacts are signed with a GPG key managed by the release team.

Required secrets (see also `CI_SECRETS.md`):

| Secret | Purpose |
|--------|---------|
| `GPG_PRIVATE_KEY` | Artifact signing |
| `GPG_PASSPHRASE` | Passphrase for the GPG key |
| `NEXUS_USERNAME` | Nexus repository publish credentials |
| `NEXUS_PASSWORD` | Nexus repository publish credentials |

Signing is enforced for all release (non-snapshot) publications. Snapshot
publications skip signing for speed.

---

## Cloud Teardown

_[TODO: Document any cloud resources that must be cleaned up after a release
(e.g., temporary build environments, staging instances, DNS entries).]_

After a release is published:

- [ ] Decommission the release staging environment.
- [ ] Archive or delete the `release/X.Y` branch.
- [ ] Update the XNAT website with the new release link.
- [ ] Post the release notes to the XNAT discussion group.
