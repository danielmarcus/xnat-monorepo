# Release Guide

This document describes the branching model, release process, artifact signing,
and post-release steps for the XNAT monorepo.

---

## Branching Conventions

| Branch | Purpose |
|--------|---------|
| `main` | Latest stable release — protected, no direct pushes |
| `develop` | Integration branch — all feature and fix PRs target here |
| `release/X.Y` | Release stabilization branch for version X.Y |
| `hotfix/description` | Emergency fixes branched from `main`; merged back to both `main` and `develop` |
| `feat/*`, `fix/*`, `chore/*` | Feature, fix, and maintenance branches — deleted after merge |

---

## Version Naming

| Version pattern | Example | Description |
|----------------|---------|-------------|
| `X.Y.Z-SNAPSHOT` | `1.10.0-SNAPSHOT` | Published automatically on every merge to `develop` |
| `X.Y.Z-RCn` | `1.10.0-RC2` | Release candidate — published from a `release/X.Y` branch |
| `X.Y.Z` | `1.10.0` | Final release — published only via the manual release workflow |

The version is declared in `build-logic/gradle/libs.versions.toml` under the
`xnat` key.  Change it in exactly that one place; all modules inherit it.

---

## Release Process

### 1. Create the release branch

```sh
git checkout develop
git pull --ff-only origin develop
git checkout -b release/1.10
git push -u origin release/1.10
```

### 2. Update the version

Edit `build-logic/gradle/libs.versions.toml`:

```toml
[versions]
xnat = "1.10.0"          # drop -SNAPSHOT or -RCn suffix
```

Commit:
```sh
git commit -am "chore(release): bump version to 1.10.0"
```

### 3. Update CHANGELOG.md

Add a dated section for the new release with all notable changes.

### 4. Open a PR from `release/1.10` into `main`

- CI must be fully green (build + tests + smoke tests).
- At least one approver required.

### 5. Tag the merge commit

After the PR merges:

```sh
git checkout main
git pull --ff-only origin main
git tag -s v1.10.0 -m "Release 1.10.0"
git push origin v1.10.0
```

The `release.yml` CI workflow triggers on the `v*.*.*` tag and publishes
artifacts to XNAT Artifactory.

### 6. Merge the release branch back to `develop`

```sh
git checkout develop
git merge --no-ff release/1.10
git push origin develop
git branch -d release/1.10
git push origin --delete release/1.10
```

### 7. Bump the snapshot version on `develop`

```toml
[versions]
xnat = "1.11.0-SNAPSHOT"
```

### 8. Announce the release

- Post to the [XNAT discussion group](https://groups.google.com/forum/#!forum/xnat_discussion).
- Update the XNAT website release page.
- Update the GitHub release notes (the `release.yml` workflow creates a draft;
  edit and publish it).

---

## Hotfix Process

```sh
# Branch from the tag or from main
git checkout -b hotfix/critical-bug-fix main
# ... make the fix ...
git commit -am "fix: describe the fix"

# PR into main
# After merge, tag it
git tag -s v1.10.1 -m "Hotfix 1.10.1"
git push origin v1.10.1

# Also merge back into develop
git checkout develop
git merge --no-ff hotfix/critical-bug-fix
git push origin develop
```

---

## Artifact Signing

Release artifacts (WAR, JARs, POMs) are signed with a GPG key managed by the
release team.  Signing is enforced for all non-snapshot publications.

Required secrets (configured in GitHub Actions):

| Secret | Purpose |
|--------|---------|
| `GPG_PRIVATE_KEY` | ASCII-armored GPG private key (`gpg --armor --export-secret-keys <KEY_ID>`) |
| `GPG_PASSPHRASE` | Passphrase protecting the GPG private key |
| `XNAT_ARTIFACTORY_USER` | Artifactory publish credentials |
| `XNAT_ARTIFACTORY_PASSWORD` | Artifactory publish credentials |

Snapshot builds skip signing for build speed.

See [CI_SECRETS.md](CI_SECRETS.md) for the full secrets reference.

---

## Snapshot Publication

Snapshots are published automatically on every merge to `develop` by the
`main-build.yml` CI workflow.  The snapshot version string follows the
pattern `X.Y.Z-SNAPSHOT` and is published to the XNAT Artifactory
snapshots repository:

```
https://nrgxnat.jfrog.io/nrgxnat/libs-snapshot
```

Downstream projects that consume snapshots add this repository to their
dependency resolution configuration.

---

## Cloud Teardown After Release

After a release is published and the staging cloud environment is no longer
needed:

```sh
cd deploy/cloud/scripts
./teardown.sh
```

Or from the CI machine:

```sh
./teardown.sh --auto-approve -e staging
```

Additional post-release steps:

- [ ] Archive or delete the `release/X.Y` branch.
- [ ] Update the XNAT website with the new release link.
- [ ] Post release notes to the XNAT discussion group.
- [ ] Decommission or repurpose the staging cloud instance.
