# Plan: Test Suite + EKS + Tomcat 10 / Jakarta

**Repository:** `danielmarcus/xnat-monorepo`
**Base:** `main` (XNAT 1.10.0-RC2-SNAPSHOT, Java 21, Gradle 8.14.4)
**Branch:** `feat/tomcat10-eks-tests`
**Scope:** All three changes land on a single feature branch; merge as one PR.

This plan supersedes the draft in `XNATMonoRepoV2_PLAN.md`. It reorders phases for risk, drops time estimates (irrelevant when Claude is the executor), and promotes the Tomcat bytecode transformer to the primary Tomcat-10 path based on the actual Restlet version in the tree.

---

## Locked decisions

| Decision | Value |
| --- | --- |
| AWS region | Configurable via `var.aws_region`, default `us-east-1` |
| EKS cluster name | Configurable via `var.eks_cluster_name`, default `xnat-${var.environment}` |
| Cloud database | RDS Postgres 15 (managed) |
| Local database | Postgres 15-alpine in Docker Compose (existing — must keep working) |
| Ingress strategy | Both supported in `values.yaml`: (a) `LoadBalancer` Service → ELB hostname (no domain needed); (b) ALB Ingress with host + ACM cert. Default to (a). |
| Domain | None this iteration — ELB hostname is sufficient |
| Tomcat 10 migration approach | **Apache `tomcat-jakartaee-migration` bytecode transformer at class-load time** (not source-level rewrite of Restlet). See Phase C. |

---

## Phase ordering and rationale

1. **Phase A — Test suite expansion** (was Phase 1)
2. **Phase B — EKS / Helm / RDS** (was Phase 3)
3. **Phase C — Tomcat 10 / Jakarta** (was Phase 2)
4. **Phase D — Integration + merge**

**Why reorder.** The original plan put Tomcat 10 before EKS. That couples two independent risk surfaces — if Tomcat 10 stalls, EKS stalls with it. The actual constraints:

- Phase A (tests) gates everything; without ~50 smoke tests, neither Phase B nor Phase C can verify it didn't break the app.
- Phase B (EKS) is **independent of the Tomcat/Jakarta version**. It can land on the existing Tomcat 9 + `javax.*` WAR, which is known-good. If Phase C never lands, Phase B still ships a working K8s deploy.
- Phase C (Tomcat 10 / Jakarta) is the riskiest item. Restlet 1.1.10 is the bottleneck; the bytecode-transformer path makes it tractable but needs validation against the smoke suite (Phase A) and ideally against a deployable target (Phase B). It belongs last.

Each phase has a verification gate. Do not start the next phase until the current gate passes.

---

# Phase A — REST Test Suite Expansion

## Goal

Expand `smoke-tests/` from 12 tests to ~50, covering DICOM upload, sessions, search, prearchive, container service, permissions, and resource files. Wire into `cloud-deploy.yml` (existing) and `eks-deploy.yml` (new in Phase B).

## Current state (verified)

- `smoke-tests/conftest.py` has fixtures: `base_url`, `admin_credentials`, `admin_session`, `unique_id`, `test_username`, `test_project_id`. Auth via `JSESSIONID` cookie on a `requests.Session`.
- `test_smoke.py` has 12 tests in 7 classes: health, admin login, user CRUD, project CRUD, project listing, cleanup.
- `requirements.txt` pins `pytest>=8.0`, `requests`.
- No `pytest.ini`. No marks.

## Files

### `smoke-tests/conftest.py` — extend

Add fixtures:
- `dicom_fixture_dir` (session-scoped) — generate synthetic DICOM via `pydicom.dataset.Dataset` with valid SOPClassUIDs into `tmp_path_factory`. Avoid committing binary fixtures unless generation proves too slow; if needed, commit one ~5 MB anonymized CT zip at `smoke-tests/fixtures/tiny_ct_study.zip`.
- `isolated_project` (function-scoped) — creates a unique project (UUID4 suffix), yields its ID, deletes on teardown.
- `container_service_available` (session-scoped) — checks `GET /xapi/docker/server` returns connected; tests using it skip if not.
- `user_session` — analogue of `admin_session` for non-admin user. Create the user via admin, log in as that user, yield, clean up.

Keep existing fixtures unchanged.

### `smoke-tests/pytest.ini` — new

```ini
[pytest]
markers =
    slow: long-running tests (>5s)
    dicom: requires DICOM fixtures
    container_service: requires container service configured
addopts = -v --strict-markers
```

### `smoke-tests/test_smoke.py` — keep

Existing 12 tests stay as-is. If `conftest.py` fixture names change, update references; otherwise leave alone.

### `smoke-tests/test_subjects_experiments.py` — new

- `test_create_subject` — `PUT /data/projects/{p}/subjects/{label}` → 200/201
- `test_list_subjects` — subject in `GET /data/projects/{p}/subjects`
- `test_subject_metadata_roundtrip` — set demographics via PUT, GET back
- `test_create_mr_session` — `PUT /data/projects/{p}/subjects/{s}/experiments/{e}?xsiType=xnat:mrSessionData`
- `test_create_ct_session` — same with `xnat:ctSessionData`
- `test_session_appears_in_subject` — listed under subject
- `test_delete_session_cascade` — DELETE session, expect 404 on follow-up GET
- `test_delete_subject_cascade` — DELETE subject removes its sessions

### `smoke-tests/test_dicom_upload.py` — new (mark `dicom`, `slow`)

- `test_dicom_upload_to_prearchive` — `POST /data/services/import?import-handler=DICOM-zip&dest=/prearchive` with the synthetic CT zip; expect 200 + session URL
- `test_prearchive_listing` — uploaded session in `GET /data/prearchive/projects/{p}`
- `test_prearchive_archive` — `POST /data/services/archive` moves prearchive → archive
- `test_archived_session_has_scans` — `GET /data/.../experiments/{e}/scans` returns non-empty list
- `test_archived_session_has_files` — `GET /data/.../scans/{s}/resources/DICOM/files` lists files
- `test_dicom_file_download` — single-file GET returns a `pydicom.dcmread`-parseable response
- `test_prearchive_delete` — DELETE on prearchive URL succeeds

This module is the **canary for Phase C**. If DICOM upload survives Tomcat 10 / Jakarta, the persistence + servlet stack survived. Run it first in Phase C verification.

### `smoke-tests/test_search.py` — new

- `test_stored_search_create` — `PUT /data/search/saved/{id}` with stored search XML
- `test_stored_search_execute` — `GET /data/search/saved/{id}/results?format=json` returns rows
- `test_search_by_xsiType` — `POST /data/search` with inline criteria for `xnat:mrSessionData`

### `smoke-tests/test_resources.py` — new

- `test_create_resource_collection` — `PUT /data/projects/{p}/resources/{label}`
- `test_upload_resource_file` — `PUT /data/projects/{p}/resources/{label}/files/{filename}` binary
- `test_download_resource_file` — GET returns identical bytes
- `test_list_resource_files` — file in collection listing
- `test_delete_resource_file` — DELETE → 404 on subsequent GET

### `smoke-tests/test_permissions.py` — new

- `test_member_can_read_owned_project` — added member sees project
- `test_nonmember_cannot_read_private_project` — 403 for outsider
- `test_owner_can_add_member` — `PUT /data/projects/{p}/users/Members/{username}`
- `test_member_cannot_delete_project` — non-owner DELETE → 403
- `test_collaborator_can_read_but_not_write` — role enforcement

### `smoke-tests/test_container_service.py` — new (mark `container_service`)

Skip module if `container_service_available` is False.

- `test_docker_server_configured`
- `test_pull_hello_world`
- `test_register_command` — POST minimal command JSON
- `test_launch_command`
- `test_container_completes` — poll `/xapi/containers/{id}` until Complete or 60s timeout

### `smoke-tests/test_site_config.py` — new

- `test_site_config_get` — move from `test_smoke.py`
- `test_site_config_update_admin_email` — PUT, GET back, restore
- `test_site_preferences_get` — `GET /xapi/siteConfig/buildInfo`
- `test_buildinfo_unauth` — `GET /xapi/siteConfig/buildInfo` works without auth (used by K8s readiness probe in Phase B). NOTE: original plan named `/xapi/siteConfig/initialized`; empirically that endpoint requires auth in this release, so `/buildInfo` is the actual unauth health endpoint.

### `smoke-tests/requirements.txt` — extend

Add: `pydicom>=2.4`, `pytest-xdist`, `pytest-rerunfailures`.

## CI integration

### `.github/workflows/smoke-test.yml` — modify

- Replace single `pytest smoke-tests/` invocation with two jobs:
  - `fast-smoke-test`: `pytest smoke-tests/ -m "not slow and not container_service" --reruns 2`
  - `full-smoke-test`: `pytest smoke-tests/ -m "slow or dicom" --reruns 2`
- Both jobs use the existing Compose stack pattern.

### `.github/workflows/cloud-deploy.yml` — modify

Same expansion: fast suite gates the deploy, full suite runs post-deploy as the validation step.

## Phase A verification gate

- [ ] `pytest smoke-tests/ -v` passes locally against `docker compose up -d`
- [ ] Every test has at least one assertion
- [ ] No test depends on execution order (run with `pytest --random-order` to confirm)
- [ ] `smoke-test.yml` and `cloud-deploy.yml` both green on the branch
- [ ] DICOM upload test passes — this is the Phase C canary

---

# Phase B — EKS / Helm / RDS

## Goal

New deployment target. Builds the **existing Tomcat 9 / javax WAR** — does NOT depend on Phase C. The single-EC2 + docker-compose path stays working; both deploy targets coexist.

## Files

### `deploy/cloud/terraform/eks/` — new module

#### `versions.tf`
```hcl
terraform {
  required_version = ">= 1.6.0"
  required_providers {
    aws        = { source = "hashicorp/aws", version = "~> 5.0" }
    kubernetes = { source = "hashicorp/kubernetes", version = "~> 2.30" }
    helm       = { source = "hashicorp/helm", version = "~> 2.13" }
  }
}
```

#### `variables.tf`
- `aws_region` (string, required)
- `environment` (string, default `"staging"`)
- `eks_cluster_name` (string, default `"xnat-${var.environment}"`)
- `eks_node_instance_type` (default `"t3.large"`)
- `eks_node_min_size` (default 2), `eks_node_max_size` (default 4)
- `db_password` (sensitive)
- `db_instance_class` (default `"db.t3.medium"`)
- `db_allocated_storage_gb` (default 50)
- `enable_ingress_alb` (default false)
- `domain_name` (default `""`), `acm_certificate_arn` (default `""`)

#### `vpc.tf`
Reuse pattern from existing `deploy/cloud/terraform/main.tf`. Require ≥2 AZs (EKS). Add private subnets for the node group + RDS subnet group.

#### `eks.tf`
- `aws_eks_cluster`, control plane in private subnets
- `aws_eks_node_group` — managed, autoscaling per variables
- `aws_eks_addon` for `vpc-cni`, `coredns`, `kube-proxy`, `aws-ebs-csi-driver`
- IAM roles + IRSA OIDC provider

#### `rds.tf`
- `aws_db_subnet_group` across private subnets
- `aws_db_instance`: postgres 15.x, gp3 encrypted, `multi_az=false` for staging (configurable), 7-day backup retention, `skip_final_snapshot=true` for staging
- `aws_security_group` — 5432 from EKS node SG only
- Output `rds_endpoint`

#### `efs.tf`
- `aws_efs_file_system` for the XNAT archive (RWX)
- `aws_efs_mount_target` per AZ
- Security group — 2049 from EKS node SG
- Output `efs_id`

#### `alb_controller.tf`
- IAM policy from official LB controller policy JSON, IAM role with IRSA trust
- `helm_release` for `aws-load-balancer-controller` in `kube-system`

#### `efs_csi.tf`
- `helm_release` for `aws-efs-csi-driver` in `kube-system`
- `kubernetes_storage_class` named `efs-sc`

#### `ecr.tf`
- `aws_ecr_repository` `xnat-web` with lifecycle policy: keep last 10 images

#### `outputs.tf`
- `eks_cluster_endpoint`, `eks_cluster_name`, `rds_endpoint`, `efs_id`, `ecr_repository_url`

### `deploy/cloud/helm/xnat/` — new chart

#### `Chart.yaml`
```yaml
apiVersion: v2
name: xnat
description: XNAT imaging informatics platform
type: application
version: 0.1.0
appVersion: "1.10.0-RC2"
```

#### `values.yaml` (key sections)

```yaml
image:
  repository: ""        # ECR URL injected by deploy workflow
  tag: latest
  pullPolicy: IfNotPresent

database:
  mode: rds             # rds | inCluster
  host: ""
  port: 5432
  name: xnat
  username: xnat
  existingSecretName: xnat-db-credentials

persistence:
  archive:
    storageClass: efs-sc
    size: 100Gi
    accessMode: ReadWriteMany
  config:
    storageClass: gp3
    size: 5Gi
    accessMode: ReadWriteOnce

resources:
  requests: { cpu: "1", memory: "3Gi" }
  limits:   { cpu: "2", memory: "4Gi" }

javaOpts: "-Xms1g -Xmx3g -Xss512k"

service:
  type: LoadBalancer    # LoadBalancer | ClusterIP
  port: 80
  targetPort: 8080

ingress:
  enabled: false
  className: alb
  host: ""
  acmCertificateArn: ""

inClusterPostgres:
  enabled: false
  image: postgres:15-alpine
  storageSize: 20Gi
```

#### Templates
- `_helpers.tpl` — `xnat.fullname`, `xnat.labels`, `xnat.selectorLabels`
- `secret-db.yaml` — conditional, generates random password when `existingSecretName` empty AND in-cluster Postgres enabled
- `configmap-xnat-conf.yaml` — `xnat-conf.properties` mounted at `/data/xnat/home/config/xnat-conf.properties`
- `postgres-statefulset.yaml`, `postgres-service.yaml` — conditional on `inClusterPostgres.enabled`; Service named `xnat-db` so `jdbc:postgresql://xnat-db:5432/xnat` works without code changes
- `archive-pvc.yaml` — `efs-sc`, RWX
- `config-pvc.yaml` — `gp3`, RWO
- `xnat-web-deployment.yaml`:
  - 1 replica, `strategy: type: Recreate` (single replica + RWO PVC)
  - Init container: wait for Postgres TCP via `nc -z $DB_HOST 5432`
  - Main container env: `XNAT_DATASOURCE_URL`, `XNAT_DATASOURCE_PASSWORD` from secret
  - Volume mounts: archive PVC at `/data/xnat/archive`, config PVC at `/data/xnat/home`, configmap into `/data/xnat/home/config/xnat-conf.properties`
  - Readiness probe: `httpGet /xapi/siteConfig/buildInfo:8080`, initialDelay 60s, period 10s, failureThreshold 30
  - Liveness probe: same path, initialDelay 300s (Tomcat warmup), period 30s, failureThreshold 3
- `xnat-web-service.yaml` — type from values; LoadBalancer assigns ELB hostname automatically (`kubectl get svc xnat-web -o wide`)
- `xnat-web-ingress.yaml` — conditional on `ingress.enabled`
- `serviceaccount.yaml` — optional IRSA annotation for S3 backups

#### `deploy/cloud/helm/xnat/README.md`
Document: prerequisites (Terraform first), values setup, finding the assigned ELB hostname, switching RDS ↔ in-cluster Postgres.

### `deploy/cloud/scripts/eks-deploy.sh` — new

```
1. aws eks update-kubeconfig --region $REGION --name $CLUSTER_NAME
2. aws ecr get-login-password | docker login ...
3. docker build -t $ECR_URL:$GIT_SHA -f deploy/docker-compose/xnat/Dockerfile.k8s .
4. docker push $ECR_URL:$GIT_SHA
5. helm upgrade --install xnat ./deploy/cloud/helm/xnat \
     --set image.repository=$ECR_URL --set image.tag=$GIT_SHA \
     --set database.host=$RDS_ENDPOINT \
     --wait --timeout 15m
6. kubectl rollout status deployment/xnat -n default --timeout 10m
7. kubectl get svc xnat-web -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'
```

### `deploy/docker-compose/xnat/Dockerfile.k8s` — new

Variant of the existing Dockerfile that **bakes the WAR into the image**:
```dockerfile
FROM tomcat:9.0-jdk21-temurin     # NOTE: still Tomcat 9 — Phase C bumps to 10
ARG WAR_PATH=apps/web/build/libs/web-1.10.0-RC2-SNAPSHOT.war
COPY ${WAR_PATH} /usr/local/tomcat/webapps/ROOT.war
# ... same setup steps as deploy/docker-compose/xnat/Dockerfile ...
```

Keep existing `Dockerfile` unchanged for Compose path.

### `.github/workflows/eks-deploy.yml` — new

Triggered by `workflow_dispatch` and on tags. Steps:
1. Checkout
2. Download WAR from latest `main-build` artifact (or rebuild)
3. Configure AWS credentials (OIDC)
4. Run `eks-deploy.sh`
5. Capture ELB hostname as job output
6. Run full pytest suite against `http://$ELB_HOSTNAME`
7. Upload test report

## Phase B verification gate

- [ ] `terraform apply` in `deploy/cloud/terraform/eks/` succeeds
- [ ] `kubectl get nodes` shows ≥2 ready nodes
- [ ] `kubectl get sc` shows `efs-sc` and `gp3`
- [ ] RDS endpoint reachable from a node: `kubectl run psql --image=postgres:15-alpine --rm -it -- psql -h $RDS_ENDPOINT -U xnat`
- [ ] `helm install` succeeds; pod Ready within 5 min
- [ ] ELB hostname resolves; `curl http://$ELB/xapi/siteConfig/buildInfo` returns 200
- [ ] Full Phase A smoke suite passes against EKS
- [ ] DICOM upload test passes (canary for EFS + persistence)
- [ ] Existing `cloud-deploy.yml` still passes — both deploy targets coexist

---

# Phase C — Tomcat 10 / Jakarta EE Migration

## Goal

Run on Tomcat 10.1 with Jakarta EE 9+ namespaces. Stay on JDK 21. **Keep Restlet 1.1.10 working via runtime bytecode transformation** — no source rewrite of Restlet itself.

## Approach: bytecode transformer as primary path

The repo's Restlet is **1.1.10**, not the 2.4.x the original plan assumed. There is no Jakarta-compatible upgrade path from 1.1.10. Replacing Restlet with Spring MVC controllers is a multi-week effort that doesn't belong in this branch.

Instead, use Apache Tomcat's `tomcat-jakartaee-migration` listener to rewrite `javax.*` → `jakarta.*` references at class-load time. The WAR on disk stays `javax.*`; the running classes are `jakarta.*`. This handles Restlet AND XNAT's own 547 `javax.servlet`/`javax.persistence` imports in one mechanism.

## Scope split

Phase C is split into two deliveries:

- **Phase C.1 — Transformer-only (this branch):** Tomcat 10.1 base image + `JakartaTransformerListener` + `web.xml` schema bump + Dockerfile updates. **No source changes. No Spring/Hibernate version bumps.** Spring 5.3.39, Spring Security 5.7.13, Hibernate 5.6.15, and Restlet 1.1.10 stay exactly as-is — the transformer rewrites their `javax.*` references at class-load time so they run on Tomcat 10. This is the primary deliverable for Phase C.
- **Phase C.2 — Spring 6 / Hibernate 6 / OpenRewrite (deferred):** Source-level Jakarta migration via OpenRewrite, plus Spring 6.x / Hibernate 6.x / Spring Security 6.x bumps. This is multi-day work involving `WebSecurityConfigurerAdapter` removal, `antMatchers→requestMatchers` rewrites, Hibernate naming-strategy schema audit, and unlocking the forced version pins. Schedule as a separate branch after C.1 ships and the transformer is proven stable.

The "Pre-flight" sections, "Spring config — manual updates", and the "Files / `platform/bom`" version-bump table below all belong to **C.2** and are kept here for the follow-up branch's reference. The remaining sections (`context.xml`, `web.xml`, `Dockerfile*`, `docker-compose.yml` health check, verification gate) are **C.1** scope.

## Pre-flight notes (C.2)

The forced versions live in **`build-logic/src/main/kotlin/xnat-{war-application,java-library}.gradle.kts`** (the convention plugins), not in `apps/web/build.gradle.kts` as the original plan stated:

| Locked | Reason it's locked | Spring 6 needs |
| --- | --- | --- |
| `io.projectreactor:reactor-core:2.0.8.RELEASE` | XNAT uses reactor 2 (`reactor-bus`); reactor 2 is not source-compatible with 3 | reactor 3.x |
| `org.slf4j:slf4j-api:1.7.36` | logback 1.2.x compat | slf4j 2.x |
| `ch.qos.logback:logback-classic:1.2.x` | NOT actually forced via resolutionStrategy — only pinned in `gradle/libs.versions.toml` (`logback = "1.2.13"`) | logback 1.4.x+ |

The catalog also pins `slf4j = "1.7.30"` while the convention plugins force `1.7.36`; the force overrides. Step 1 of C.2 is dropping the forces and bumping the catalog values together, then verifying the build still passes on Tomcat 9 / Spring 5 with the new versions before touching Spring/Hibernate.

## Pre-flight: circular dependency decision (C.2)

`apps/web` ↔ `libs/xdat` is currently broken by `build-tools/xnat-data-models` exposing shared interfaces (replaced the old `web-stubs`). Spring 6's stricter classloader can expose this. Two options:

1. **Keep the workaround.** Document why; move on.
2. **Retire it.** Resolve the circular by relocating shared interfaces into a proper `libs/xnat-shared-api` module.

Pick (1) for the C.2 branch unless the build forces (2). Either choice goes into an ADR.

## Files

### `platform/bom/build.gradle.kts` (or `gradle/libs.versions.toml`)

Update versions:
- Spring Framework: 5.3.39 → 6.1.x
- Spring Security: 5.7.13 → 6.3.x
- Spring Data JPA: 2.x → 3.x
- Hibernate ORM: 5.6.15 → 6.4.x
- Tomcat embed: 9.0.93 → 10.1.x
- Servlet API: `javax.servlet:javax.servlet-api:3.1.0` → `jakarta.servlet:jakarta.servlet-api:6.0.0` *(only used at compile time — runtime classes provided by Tomcat 10 + transformer)*
- JSP/JSTL: → `jakarta.servlet.jsp:jakarta.servlet.jsp-api:3.1.1`, `jakarta.servlet.jsp.jstl:jakarta.servlet.jsp.jstl-api:3.0.0`
- Validation API: → `jakarta.validation:jakarta.validation-api:3.0.2`
- Persistence API: → `jakarta.persistence:jakarta.persistence-api:3.1.0`
- Restlet: **stays at 1.1.10** — transformer rewrites it at runtime
- Reactor / slf4j / logback: from preflight above

### `apps/web/src/main/webapp/META-INF/context.xml` — new (or modify)

Enable the transformer per-context:
```xml
<Context>
  <Listener className="org.apache.tomcat.jakartaee.JakartaTransformerListener" />
</Context>
```

(Alternative: enable globally in `server.xml`. Per-context is cleaner — only the XNAT WAR gets transformed.)

### `apps/web/src/main/webapp/WEB-INF/web.xml` — bump schema

```xml
<web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee
                             https://jakarta.ee/xml/ns/jakartaee/web-app_6_0.xsd"
         version="6.0">
```

### `deploy/docker-compose/xnat/Dockerfile` — modify

```dockerfile
FROM tomcat:10.1-jdk21-temurin
```

Plus:
- Remove the `bcpkix-*.jar` `jarsToSkip` patch — Tomcat 10's scanner handles BC differently. Re-add only if startup fails with stack overflow.
- Audit `JDK_JAVA_OPTIONS` add-opens flags. Tomcat 10 needs fewer. Remove one at a time, keep what's actually needed.
- Health check path: `/xapi/siteConfig/buildInfo` (200, no auth).

### `deploy/docker-compose/xnat/Dockerfile.k8s` — modify

Same Tomcat 10 base. The Phase B EKS path picks this up automatically on next image build.

### `deploy/docker-compose/docker-compose.yml` — health check

```yaml
healthcheck:
  test: ["CMD-SHELL", "wget --quiet --spider http://localhost:8080/xapi/siteConfig/buildInfo || exit 1"]
```

### Spring config — manual updates

Spring 6 / Spring Security 6 changes that the transformer does NOT catch (they're API-level, not namespace-level):

- `WebSecurityConfigurerAdapter` is **removed**. Migrate to `SecurityFilterChain` bean style.
- `@EnableGlobalMethodSecurity` deprecated → `@EnableMethodSecurity`.
- `antMatchers()` → `requestMatchers()`. (Lambda DSL is mandatory in Spring Security 6.)
- Hibernate 6: `org.hibernate.dialect.PostgreSQLDialect` (versioned dialect classes removed).
- Hibernate 6: implicit naming strategy changed. **Highest schema risk in this phase.** Audit `@Table` and `@Column` annotations after migration; diff generated DDL against the live schema before promoting past staging.

These are best done by `libs/framework`, `libs/xdat`, `libs/config` maintainers — these three modules touch the most Spring/Hibernate configuration and need the most hand-resolution.

### JSP files — manual taglib URI update

```bash
# Search:
grep -rn 'uri="http://java.sun.com/jsp/jstl' apps/web/src/main/webapp/
# Replace with: uri="jakarta.tags.core", uri="jakarta.tags.fmt", etc.
```

The transformer doesn't rewrite JSP source (it operates on `.class` files).

### OpenRewrite — DEFERRED

The original plan ran `org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta` against the source. Defer this. Reasons:

1. The transformer makes it unnecessary for the build to succeed.
2. Source rewrite + transformer together = double-rewriting some classes (transformer detects `jakarta.*` and skips them, but the surface area for confusion is large).
3. Source rewrite touches every module — much bigger code review surface than transformer config.

Schedule OpenRewrite as a separate follow-up branch after Phase C ships and the transformer is proven stable.

## Phase C verification gate

- [ ] `./gradlew build` clean (the version unlock from preflight passed)
- [ ] `./gradlew :apps:web:war` produces a WAR
- [ ] Local Compose stack starts on Tomcat 10: `docker compose up -d`
- [ ] Container logs show transformer activity (look for `JakartaTransformerListener` startup messages) and no `ClassNotFoundException` / `NoClassDefFoundError`
- [ ] `/xapi/siteConfig/buildInfo` returns 200 within 3 minutes of startup (transformer adds ~5–30s to first class load)
- [ ] **Phase A DICOM upload test passes** — primary canary; if DICOM upload survives, persistence + servlet stack survived
- [ ] Phase A fast smoke suite passes
- [ ] Phase B EKS deploy succeeds with the new image (re-run, don't re-Terraform)
- [ ] Phase A full smoke suite passes against EKS deploy

If the gate fails, **stop and roll back**. Do not merge a half-migrated Phase C. Phase B's value (working K8s deploy on Tomcat 9) is preserved.

---

# Phase D — Integration + Merge

## Tasks

1. Confirm `cloud-deploy.yml` (single-EC2 path) still passes — both deploy targets coexist.
2. Confirm local Compose path works on Tomcat 10.
3. Update top-level `README.md`: link to `deploy/cloud/helm/xnat/README.md`, mention dual deploy targets, Tomcat 10 / Java 21 status.
4. Add `docs/adr/0005-tomcat-10-via-bytecode-transformer.md` — document the transformer choice and why source-level rewrite was deferred.
5. Add `docs/adr/0006-eks-deployment-target.md` — document EFS-for-archive choice, single-replica constraint, RDS-vs-in-cluster Postgres options.
6. Add `docs/adr/0007-circular-dep-resolution.md` — record the decision made in Phase C preflight (kept workaround vs. retired it).
7. Update `CI_SECRETS.md` with new EKS secrets.
8. Squash-merge to `develop` (per `RELEASE.md` branching).

## Phase D verification gate

- [ ] All workflows green: `main-build`, `pr-validation`, `smoke-test`, `cloud-deploy`, `eks-deploy`
- [ ] Local `docker compose up -d` works
- [ ] EC2 single-instance deploy works
- [ ] EKS deploy works
- [ ] Both deploys pass the full smoke suite

---

# Notes for Claude Code execution

- **Commit frequently.** After each file or logical group, commit with a clear message. The branch will have many commits — that's fine, squash on merge.
- **Run gates explicitly.** After each phase, run the verification gate commands and report results before starting the next phase.
- **If a gate fails: stop, surface the failure, propose a fix, do not proceed.** Especially Phase C — it's the only phase whose failure should be allowed to abort the rest. Phases A and B should be able to ship on their own.
- **Don't delete the existing single-EC2 path.** It's the fallback and must keep working.
- **Don't commit secrets.** RDS password, ACM ARNs, etc. flow through Terraform variables and GitHub Actions secrets.
- **Work on a long-lived branch.** Don't cherry-pick to main piecemeal — gates exist for a reason; partial Phase C states are unsafe.

## New GitHub Actions secrets

| Secret | Purpose |
| --- | --- |
| `AWS_ROLE_TO_ASSUME` | OIDC role for EKS deploy (already exists if cloud-deploy works) |
| `RDS_DB_PASSWORD` | Passed to Terraform on apply |
| `EKS_CLUSTER_NAME` | Or read from Terraform output |

---

# Out of scope (explicit)

- Multi-region deployment
- Active-active HA (XNAT does not support it)
- Existing customer data migration (greenfield deploy)
- Replacing the EC2 path with EKS — both coexist
- Container Service in K8s (XNAT's container service expects Docker socket; needs design work)
- JupyterHub on K8s
- Custom domain + ACM cert wiring
- Production monitoring (Prometheus, Grafana, alerting)
- **OpenRewrite source-level Jakarta migration** — deferred to follow-up branch after Phase C transformer is proven
- **Restlet replacement with Spring MVC controllers** — multi-week effort, separate initiative
- **Retiring `xnat-data-models` shared-API workaround** — only if forced by Phase C build failures
