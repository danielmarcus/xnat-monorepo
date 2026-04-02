# CI Secrets Reference

> **Status:** Skeleton — to be completed during migration.
> This document serves as the authoritative reference for secrets required by
> GitHub Actions workflows. Do **not** store actual secret values here.

---

## Overview

All secrets are stored in GitHub Actions secrets at the repository or
organization level. Workflows reference them via `${{ secrets.SECRET_NAME }}`.

Secrets should be rotated at least annually or immediately upon suspected
compromise. Contact the repository administrator to add or rotate secrets.

---

## Secrets Table

_[TODO: Complete this table as workflows are created during migration.]_

| Secret Name | Purpose | Required By | Format | Where to Obtain |
|-------------|---------|-------------|--------|----------------|
| `NEXUS_USERNAME` | Publish artifacts to Nexus | `publish.yml` | Plain string | _[TODO]_ |
| `NEXUS_PASSWORD` | Publish artifacts to Nexus | `publish.yml` | Plain string | _[TODO]_ |
| `GPG_PRIVATE_KEY` | Sign release artifacts | `release.yml` | ASCII-armored GPG key | _[TODO]_ |
| `GPG_PASSPHRASE` | Decrypt GPG private key | `release.yml` | Plain string | _[TODO]_ |
| `DOCKER_HUB_USERNAME` | Push Docker images | `docker.yml` | Plain string | _[TODO]_ |
| `DOCKER_HUB_TOKEN` | Authenticate to Docker Hub | `docker.yml` | Docker access token | _[TODO]_ |
| `SONAR_TOKEN` | Publish analysis to SonarCloud | `sonar.yml` | SonarCloud token | _[TODO]_ |
| `SLACK_WEBHOOK_URL` | Post build notifications | `notify.yml` | Slack webhook URL | _[TODO]_ |
| _[TODO]_ | _[TODO]_ | _[TODO]_ | _[TODO]_ | _[TODO]_ |

---

## Adding a New Secret

1. Navigate to **Settings > Secrets and variables > Actions** in the GitHub UI.
2. Click **New repository secret**.
3. Enter the secret name exactly as listed in this document.
4. Paste the value and save.
5. Update this document with the new secret entry and open a PR.

---

## Rotating a Secret

1. Generate the new credential value from the source system.
2. Update the secret value in GitHub Actions secrets (overwrite the old value).
3. Verify that the next CI run succeeds.
4. Revoke the old credential in the source system.
5. Note the rotation date in your team's secret-management tracker.

---

## Notes

- Secrets are **not** available to pull requests from forks — this is a GitHub
  safety restriction. Fork-based PRs will see empty strings for secrets.
- Use `${{ secrets.SECRET_NAME || '' }}` with a fallback only when a secret is
  truly optional.
- Never print secret values in workflow logs (`echo "$SECRET"` will expose it).
  Use `::add-mask::` if you must derive a value from a secret.
