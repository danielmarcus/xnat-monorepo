# CI Secrets Reference

This document is the authoritative reference for secrets required by GitHub
Actions workflows in this repository.  **Do not store actual secret values
here.**

---

## Overview

All secrets are stored in GitHub Actions secrets at the repository or
organization level.  Workflows reference them via `${{ secrets.SECRET_NAME }}`.

Secrets should be rotated at least annually or immediately upon suspected
compromise.  Contact the repository administrator to add or rotate secrets.

---

## Secrets Table

All secrets are optional — workflows skip the relevant step gracefully when a
secret is absent, posting a `::notice::` annotation in the run log.

### Artifactory (snapshot + release publish)

| Secret Name | Purpose | Required By | Format |
|-------------|---------|-------------|--------|
| `XNAT_ARTIFACTORY_USER` | Artifactory username for snapshot and release publish | `main-build.yml`, `release.yml` | Plain string (your Artifactory username) |
| `XNAT_ARTIFACTORY_PASSWORD` | Artifactory API token or password | `main-build.yml`, `release.yml` | Plain string (API token recommended over password) |

> **Note:** The old secret name `XNAT_ARTIFACTORY_TOKEN` was renamed to
> `XNAT_ARTIFACTORY_PASSWORD` for consistency.  Update any external tooling
> that references the old name.

### GPG Artifact Signing

| Secret Name | Purpose | Required By | Format |
|-------------|---------|-------------|--------|
| `GPG_PRIVATE_KEY` | ASCII-armored GPG private key for signing release artifacts | `release.yml` | Output of `gpg --armor --export-secret-keys <KEY_ID>` |
| `GPG_PASSPHRASE` | Passphrase protecting the GPG private key | `release.yml` | Plain string |

Signing is enforced for all non-snapshot (`release.yml`) publications.
Snapshot builds skip signing.

### AWS / Cloud Deploy

| Secret Name | Purpose | Required By | Format |
|-------------|---------|-------------|--------|
| `AWS_ACCESS_KEY_ID` | AWS IAM access key for Terraform and WAR deployment | `cloud-deploy.yml` | Plain string (20-character AWS key ID) |
| `AWS_SECRET_ACCESS_KEY` | AWS IAM secret access key | `cloud-deploy.yml` | Plain string (40-character AWS secret) |
| `AWS_REGION` | Target AWS region for the EC2 instance | `cloud-deploy.yml` | e.g. `us-east-1` |
| `TF_BACKEND_BUCKET` | S3 bucket name for Terraform remote state storage | `cloud-deploy.yml` | Plain string (S3 bucket name, no `s3://` prefix) |
| `TF_BACKEND_KEY` | S3 object key for the Terraform state file | `cloud-deploy.yml` | e.g. `xnat/terraform.tfstate` |
| `TF_BACKEND_REGION` | AWS region of the Terraform state S3 bucket (defaults to `AWS_REGION`) | `cloud-deploy.yml` | e.g. `us-east-1` |
| `XNAT_CLOUD_HOST` | Public IP or hostname of the deployed XNAT cloud instance | `cloud-deploy.yml` | e.g. `1.2.3.4` or `xnat.example.com` |
| `XNAT_CLOUD_ADMIN_PASS` | XNAT admin password configured on the cloud instance | `cloud-deploy.yml` | Plain string |

### EKS Deploy

| Secret Name | Purpose | Required By | Format |
|-------------|---------|-------------|--------|
| `AWS_ROLE_TO_ASSUME` | OIDC-assumable IAM role used by `eks-deploy.yml`. Must have permissions for EKS describe + token, ECR push, RDS describe, EFS describe, optionally ALB controller Helm install. | `eks-deploy.yml` | ARN, e.g. `arn:aws:iam::123456789012:role/github-actions-xnat-eks` |
| `EKS_DB_PASSWORD` | Postgres password for the RDS instance provisioned by `deploy/cloud/terraform/eks/`. Same value is used to create the in-cluster `xnat-db-credentials` Secret that the chart references. Must be ≥8 chars, no `/@" '` per RDS rules. | `eks-deploy.yml` | Plain string |
| `EKS_XNAT_ADMIN_PASS` | Optional. XNAT site-admin password to use for the post-deploy smoke tests. Defaults to `admin` (the first-time-init credentials a fresh XNAT bootstraps with). Set this once you've changed the admin password through the UI. | `eks-deploy.yml` | Plain string |

The existing `TF_BACKEND_BUCKET` / `TF_BACKEND_REGION` secrets are reused for
the EKS module's state file (different `key`: `xnat/eks/terraform.tfstate`).
`AWS_REGION` is reused, and may be overridden per-run via the workflow input.

### SSH Access to Cloud Instance

| Secret Name | Purpose | Required By | Format |
|-------------|---------|-------------|--------|
| `CLOUD_SSH_KEY` | PEM-encoded private key for SSH access to the EC2 instance | `cloud-deploy.yml` (via `deploy.sh`) | Contents of the `.pem` file generated when the EC2 key pair was created |

Usage in CI:

```yaml
- name: Write SSH key
  run: |
    echo "${{ secrets.CLOUD_SSH_KEY }}" > /tmp/deploy_key
    chmod 600 /tmp/deploy_key

- name: Deploy WAR
  run: |
    bash deploy/cloud/scripts/deploy.sh \
      apps/web/build/libs/web-1.10.0-RC2-SNAPSHOT.war \
      "${{ secrets.XNAT_CLOUD_HOST }}" \
      -i /tmp/deploy_key
```

### Notifications (optional)

| Secret Name | Purpose | Required By | Format |
|-------------|---------|-------------|--------|
| `SLACK_WEBHOOK_URL` | Post build success/failure notifications to Slack | `smoke-test.yml` (future) | Slack incoming webhook URL |

### Legacy / Planned (not yet used by active workflows)

| Secret Name | Purpose | Notes |
|-------------|---------|-------|
| `NEXUS_USERNAME` | Publish to Nexus repository | Superseded by Artifactory secrets — do not add |
| `NEXUS_PASSWORD` | Publish to Nexus repository | Superseded by Artifactory secrets — do not add |
| `DOCKER_HUB_USERNAME` | Push Docker images to Docker Hub | Reserved for a future `docker.yml` workflow |
| `DOCKER_HUB_TOKEN` | Authenticate to Docker Hub | Reserved for a future `docker.yml` workflow |
| `SONAR_TOKEN` | SonarCloud static analysis | Reserved for a future `sonar.yml` workflow |

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
