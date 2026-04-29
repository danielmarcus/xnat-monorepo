# ADR 0006: EKS as a Second Deployment Target

**Status:** Accepted
**Date:** 2026-04-27
**Authors:** XNAT Core Team / DevOps
**Deciders:** XNAT Steering Committee

---

## Context

ADR 0004 records the decision to use a **single AWS EC2 instance with
Docker Compose** as the cloud deployment. That decision still holds for
its stated purpose (CI staging + reference architecture). Two new
requirements emerged that single-EC2 cannot answer:

1. **Managed control plane for the database.** The EC2 deploy runs
   PostgreSQL in a container on the same instance. A volume failure or
   instance loss takes the database with it, and there is no native
   point-in-time recovery story. Institutions running production-ish
   pilots want RDS — automated backups, multi-AZ failover, parameter
   groups, and patching managed outside our Terraform.

2. **Independent scaling of compute and storage.** The single-EC2 setup
   couples instance size to both XNAT's JVM heap and the archive volume.
   Growing the archive past the local disk limit forces either an
   instance-type change (and a downtime window) or a manual EBS resize.
   Workloads that grow archive faster than CPU need them decoupled.

We rejected EKS in ADR 0004 on operational-overhead grounds. The shape
of "operational overhead" has shifted: EKS-managed addons, IRSA, the
AWS Load Balancer Controller, and the EFS CSI driver are now mature and
manageable from Terraform without hand-rolling RBAC or ingress configs.

---

## Decision

Add an **Amazon EKS** deployment target alongside the existing single-EC2
target. Both targets coexist permanently; neither replaces the other.

| Target | Purpose | Database | Archive | Failover |
|---|---|---|---|---|
| Single EC2 (ADR 0004) | Reference / dev-staging | Postgres in container, EBS-backed | Local EBS | None |
| **EKS (this ADR)** | Managed K8s with persistent layer | **RDS Postgres 15** (managed) | **EFS** (multi-AZ NFS) | RDS Multi-AZ optional, EKS reschedules pod |

The EKS implementation is in `deploy/cloud/terraform/eks/` (infra) and
`deploy/cloud/helm/xnat/` (application). A single deploy script
`deploy/cloud/scripts/eks-deploy.sh` handles `aws eks update-kubeconfig`,
ECR login, image build + push, the out-of-band DB Secret refresh, and
`helm upgrade --install`. CI integration is `.github/workflows/eks-deploy.yml`.

### Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│  AWS account                                                         │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  VPC (10.10.0.0/16, 2 AZs)                                  │   │
│  │                                                              │   │
│  │  Public subnets (.0/24, .1/24)                              │   │
│  │   ├── Internet Gateway                                       │   │
│  │   ├── NAT Gateway(s)                                         │   │
│  │   └── ELB / ALB (front door)                                 │   │
│  │                                                              │   │
│  │  Private subnets (.10/24, .11/24)                           │   │
│  │   ├── EKS managed node group (t3.large × 2..4)              │   │
│  │   │     └── xnat pod (1 replica, Recreate strategy)         │   │
│  │   │            ├── ConfigMap   xnat-conf.properties         │   │
│  │   │            ├── PVC archive  efs-sc / RWX                │   │
│  │   │            └── PVC config   gp3 / RWO                   │   │
│  │   ├── RDS Postgres 15  (subnet group spans both AZs)        │   │
│  │   └── EFS mount targets (one per AZ)                        │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ECR repo (xnat-web) ──► xnat-web image (Dockerfile.k8s)            │
│  S3 (terraform state) ──► xnat/eks/terraform.tfstate                │
└─────────────────────────────────────────────────────────────────────┘
```

### Key implementation choices

**EFS for the archive (RWX)** — XNAT writes session data to
`/data/xnat/archive`. EBS is RWO; the moment a future scale-out or a
recreate-rolling deployment wants the new pod to start before the old
one releases its volume, RWO blocks it. EFS with the AWS EFS CSI driver
gives RWX without redesign. The chart references the
terraform-provisioned EFS file system through a static `efs-sc`
StorageClass.

**Single replica + `strategy: Recreate`** — XNAT is not designed for
active-active. Two pods writing to the same archive will produce
duplicate session entries, conflicting prearchive flushes, and race
conditions in the workflow engine. `replicaCount: 1` is intentional;
`strategy: Recreate` is intentional too — `RollingUpdate` with one
replica + RWO config volume stalls (new pod can't bind the volume the
old pod still holds). The RWX archive PVC could in principle support
multiple pods, but the application layer can't.

**RDS by default; in-cluster Postgres optional** — `database.mode: rds`
is the default and the only path the eks-deploy workflow exercises. The
chart also supports `database.mode: inCluster` (a single-replica
StatefulSet) for environments without RDS access (dev/CI). The toggle
swaps the configmap's JDBC URL between the RDS endpoint and the
in-cluster `Service`.

**Probes hit `/xapi/siteConfig/buildInfo`** — Phase A finding: the
`/xapi/siteConfig/initialized` endpoint requires authentication in this
XNAT release (returns 401 to anonymous K8s probes). `/buildInfo` is the
genuine unauth endpoint and exercises the same servlet stack.

**LoadBalancer Service by default** — Setting `service.type:
LoadBalancer` makes AWS provision a classic ELB and return a hostname
in the Service status. No domain or ACM cert needed. ALB Ingress is
optional behind `var.enable_ingress_alb` (terraform) +
`ingress.enabled: true` (chart values).

**DB password never in chart manifest** — `eks-deploy.sh` creates the
`xnat-db-credentials` Secret via `kubectl apply --dry-run=client | kubectl apply -f -`
using the `EKS_DB_PASSWORD` GitHub secret. The chart references it by
name (`database.existingSecretName`) and `valueFrom.secretKeyRef`. Helm
never sees the password and `helm get manifest` never contains it.

---

## Alternatives Considered

### Stay single-EC2 only

**Rejected because:** the requirements (managed RDS, decoupled storage)
genuinely cannot be met without a second target. Single-EC2 has not
changed; we are not relitigating ADR 0004.

### ECS Fargate

ECS Fargate would skip the cluster-ops overhead of EKS.

**Rejected because:** XNAT's archive volume is the dominant scaling
concern, not the compute. ECS Fargate's EFS support is workable but the
persistent-volume + Service + readiness-probe primitives are less
expressive than Kubernetes; the chart we'd need to build for ECS would
end up as Helm-shaped CloudFormation, not simpler.

### Self-managed K8s on EC2 (kubeadm)

**Rejected because:** every reason listed in ADR 0004 for not adopting
self-managed Kubernetes still applies. EKS specifically removes the
control-plane operations from the table.

### Helm-only (no Terraform)

A pure-Helm deployment that assumes the cluster already exists is
simpler.

**Rejected because:** the cluster, RDS, and EFS are co-evolving
infrastructure. Splitting their lifecycle across two tools adds the
"who owns the security group" problem. Terraform owns the AWS surface;
Helm owns the application surface; the boundary is clean.

---

## Operational Notes

- **Cost.** The default `single_nat_gateway: true` shares one NAT
  Gateway across both AZs to keep the staging cost under control.
  Production-grade deployments should set it false (one NAT per AZ).
  RDS at `db.t3.medium` and node group at `t3.large × 2` is staging-tier
  sizing.
- **Cluster bootstrap.** `terraform apply` takes ~15 minutes (control
  plane creation dominates). After that, `eks-deploy.sh` runs in 2-3
  minutes for image-only redeploys (the `apply_terraform: false` path
  in the workflow).
- **Database password rotation.** Update the GitHub secret + re-run the
  workflow. The script re-applies the Secret object; pods get the new
  value on next restart. Terraform doesn't manage the in-cluster Secret
  so no state drift.
- **Tomcat 10.** The image used is `Dockerfile.k8s`, which inherits
  from `tomcat:10.1-jdk21-temurin` and runs the Phase C.1
  Jakarta-migration entrypoint at container start (see ADR 0005).

---

## Consequences

### Positive

- Production-grade managed database (RDS) and archive (EFS) without
  hand-rolling either.
- Standard cluster operations (rolling images, rollback, kubectl logs)
  without giving up the single-EC2 reference deploy.
- The chart is reusable: institutions running their own EKS clusters
  can install it without taking the Terraform module too.

### Negative / Accepted Trade-offs

- **Two deploy paths to keep working.** Each release must be smoke-tested
  on both targets. The matrix in `smoke-test.yml` and the duplicate
  fast/full split in `cloud-deploy.yml` and `eks-deploy.yml` exist for
  this reason.
- **NAT Gateway cost** even at staging tier (~$32/mo per AZ at
  us-east-1 list). The single-NAT default keeps it to one.
- **Single-replica constraint** is documented but enforceable only by
  social contract — the `replicaCount` value can be raised by anyone
  reading the chart. A future ADR may pin it via a chart hook or admission
  policy if accidental scale-up causes incidents.
- **EBS gp3 vs EFS choice** is per-PVC. The chart's `config` PVC is
  EBS-RWO; the `archive` PVC is EFS-RWX. Operators changing storage
  classes via `--set` should understand the implication for rolling
  updates.

---

## Bring-up Notes / Common Pitfalls

This ADR was written before the deploy was exercised end-to-end. The first
live bring-up surfaced ~10 bugs across IAM, the chart, and the Dockerfile —
captured here so the next environment doesn't re-discover them.

### IAM — what AWS-managed policies don't cover

The IAM role `AWS_ROLE_TO_ASSUME` (the one assumed via OIDC by the GitHub
Actions workflow) needs more than the obvious managed policies imply.

| Policy / permission | Why | Found by |
|---|---|---|
| `AmazonEC2FullAccess` (not `AmazonVPCFullAccess` alone) | `ec2:DescribeAddressesAttribute` was added to EC2 in 2022; older `AmazonVPCFullAccess` doesn't include it. Terraform's EIP refresh hits it. | First live `terraform apply` got `UnauthorizedOperation: ec2:DescribeAddressesAttribute`. |
| `eks:*` (inline) | **No AWS-managed policy grants `eks:CreateCluster` to a caller.** `AmazonEKSClusterPolicy` is for the cluster *service* role (assumed by EKS itself), not the IAM principal that creates the cluster. | First `eks_create_cluster` call returned `AccessDeniedException`. |
| `iam:PassRole`, `iam:CreateOpenIDConnectProvider`, etc. | Terraform creates IRSA roles, OIDC providers, and passes service roles to EKS. `IAMFullAccess` covers it. | Implied. |
| **S3** on the Terraform state bucket | The state bucket is read on every plan/apply (`HeadObject` on the state key). 403s here look like NoSuchBucket because S3 returns 403 when the caller lacks `s3:ListBucket`. | First `terraform init` succeeded but plan failed with `403 Forbidden: HeadObject xnat/eks/terraform.tfstate`. |

The minimum-managed-policy set we converged on:
`AmazonEC2FullAccess`, `AmazonRDSFullAccess`, `AmazonElasticFileSystemFullAccess`,
`AmazonEC2ContainerRegistryFullAccess`, `AmazonS3FullAccess`, `IAMFullAccess`,
plus an inline `eks-management` policy granting `eks:*`.

`AmazonEKSWorkerNodePolicy`, `AmazonEKSServicePolicy`, `AmazonEKSClusterPolicy`,
`AmazonEKS_CNI_Policy` are attached **on the resources Terraform creates** (the
node-group role, the cluster service role) — they don't go on the deployer role.

### Node IAM role — `AmazonEBSCSIDriverPolicy`

Without IRSA, the EBS CSI driver inherits credentials from the node's IAM role.
The standard `AmazonEKSWorkerNodePolicy` + `AmazonEKS_CNI_Policy` +
`AmazonEC2ContainerRegistryReadOnly` set does **not** include
`AmazonEBSCSIDriverPolicy`. The CSI controller's `CreateVolume` /
`AttachVolume` calls fail silently — no `addon.health.issues` reported, the
add-on stays in `CREATING` until Terraform's 20-minute timeout. (`eks.tf:108`
attaches the policy.)

### IRSA for EBS CSI driver — IMDS hop-limit

Even with the node-role policy attached, the AWS-managed
`aws-ebs-csi-driver` add-on still failed to reach `ACTIVE` on first try.
Root cause: EKS managed-node-group launch templates default to
`http-put-response-hop-limit = 1`, so **pods cannot reach IMDSv2** (only
the host can). The CSI controller pods got *no* AWS credentials at all,
not even AccessDenied — the add-on stayed in `CREATING` indefinitely.

Fix: bind the addon's controller service account to a dedicated IRSA role
via `service_account_role_arn` so it gets credentials from STS via the
OIDC token, bypassing IMDS entirely. This is the same pattern `efs_csi.tf`
already uses for the EFS CSI driver. See `eks.tf:179-237`.

This is the canonical "addon stuck in CREATING with empty health.issues"
failure mode, and it took 3 PRs to land cleanly.

### Helm chart — fullname helper vs `HELM_RELEASE_NAME=xnat`

`xnat.fullname` (the standard Helm helper in `_helpers.tpl`) collapses to
just `Release.Name` when the release name contains the chart name. With
the default `HELM_RELEASE_NAME=xnat` and `chart.name=xnat`:

| Template expression | Renders to |
|---|---|
| `{{ include "xnat.fullname" . }}` | `xnat` (not `xnat-xnat`) |
| `{{ ... }}-web` | `xnat-web` |

`eks-deploy.sh` originally looked for `${HELM_RELEASE_NAME}-xnat` —
`xnat-xnat`, which doesn't exist — so the post-`helm upgrade` `kubectl
rollout status` and Service hostname capture both 404'd. The script now
mirrors the helper's logic in shell to compute the actual `FULLNAME`.

### ConfigMap property keys — `xnat.` prefix is wrong

XNAT looks for `datasource.driver`, `datasource.url`, etc. — without the
`xnat.` prefix. The chart's ConfigMap originally rendered `xnat.datasource.X`
keys (matching the `xnat.home` key, which IS the right shape). Result:
XNAT loaded the file, found no DB config, Spring's DataSource bean wiring
failed, the WAR's listeners failed to start with the unhelpful "One or
more listeners failed to start" message.

Compare against the working docker-compose `xnat-conf.properties`: every
datasource key has no prefix.

### ConfigMap password — env-var placeholder doesn't work

The chart's first design rendered `# password is set at runtime via
XNAT_DATASOURCE_PASSWORD env var` (a comment, not a property) on the
assumption Spring's property loader would resolve `${ENV_VAR}` placeholders
in `xnat-conf.properties`. It does not. XNAT's property loader reads the
file as literal text. The password has to be rendered into the file.
`eks-deploy.sh` passes `--set "database.password=${EKS_DB_PASSWORD}"`;
helm releases are stored as Secrets since v3, so this doesn't widen the
blast radius.

### PVC overlay clobbering

`Dockerfile.k8s` runs `RUN mkdir -p ${XNAT_HOME}/{config,logs,plugins,work}`
at image build time. The chart mounts a fresh empty `gp3` PVC at
`/data/xnat/home`, which **overlays** the image's contents — the four
build-time dirs vanish on first cold boot. Tomcat's `StandardRoot` then
fails the WAR with:

```
The directory specified by base and internal path
[/data/xnat/home/plugins]/[] does not exist.
```

Fix: a `bootstrap-xnat-home` init container that `mkdir -p`s the four
dirs on the PVC before the main container starts. Idempotent; only first
boot pays the cost.

### Dockerfile.k8s build context

`eks-deploy.sh` builds with `docker build --file Dockerfile.k8s .` from
the repo root, so `COPY ${WAR_PATH}` (`apps/web/build/libs/...`) works.
But the helper-script COPYs (`make-xnat-config.sh`, `wait-for-postgres.sh`,
`jakarta-migrate-and-start.sh`) used **bare filenames**, only valid when
the docker-compose context is `./xnat`. Fix: prefix them with
`deploy/docker-compose/xnat/`.

### Runtime entrypoint — bypass on K8s

The image's default `CMD ["jakarta-migrate-and-start.sh"]` chains into
`wait-for-postgres.sh` which has docker-compose-isms baked in:

```sh
psql -h xnat-db ...   # docker-compose service name; doesn't resolve in K8s
```

…plus reads `PGPASSWORD` baked at *image build time*. Both wrong on K8s.
The `wait-for-db` init container already gates on Postgres TCP via
`nc -z`, so the runtime check is doubly redundant. Chart overrides
`command: ["/usr/local/tomcat/bin/catalina.sh"]` to skip both shell scripts.

### jakartaee-migration timing

The runtime entrypoint script does `jakartaee-migration` against ~250 jars
in the WAR on every cold start. On a t3.medium with no CPU limits this is
38s; on EKS gp3 + a 2-CPU pod limit it ran for 5–10 min, long enough that
the liveness probe killed the container before Tomcat ever bound 8080.

Fix: pre-migrate at image *build* time (`Dockerfile.k8s` runs the migration
tool against `webapps/ROOT.war` and touches the runtime sentinel). The
runtime sentinel-skip then fires immediately. Cost shifts from per-pod-
cold-start to per-image-build.

### Probe initial delays

Even with build-time migration, fresh deploys still pay Hibernate
`hbm2ddl=update` schema-sync on RDS (1–3 min), Spring context init (1–2
min), and EFS PVC first-mount latency. Defaults of `livenessProbe.initialDelaySeconds=300`
were too tight; bumped to **600s**. Helm `--wait --timeout` was 15m;
bumped to **25m**. Both are first-deploy ceilings — subsequent rolling
deploys don't need either.

### kubectl access from a laptop

The IAM principal that calls `CreateCluster` (the GitHub Actions OIDC
role) gets cluster-admin via `system:masters` automatically. Your local
IAM user doesn't, by default. To debug from a laptop, either add an EKS
**Access Entry** (the modern API-based path) or update the cluster's
`aws-auth` ConfigMap (the legacy path):

```sh
aws eks update-cluster-config --region us-east-2 --name xnat-staging \
  --access-config authenticationMode=API_AND_CONFIG_MAP

aws eks create-access-entry --region us-east-2 --cluster-name xnat-staging \
  --principal-arn $(aws sts get-caller-identity --query Arn --output text)

aws eks associate-access-policy --region us-east-2 --cluster-name xnat-staging \
  --principal-arn $(aws sts get-caller-identity --query Arn --output text) \
  --access-scope type=cluster \
  --policy-arn arn:aws:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy
```

This is worth doing **before** the first dispatch — much easier to debug
a stuck pod with kubectl than via diagnostic-artifact archaeology.

---

## Related Decisions

- ADR 0001: Monorepo structure
- ADR 0003: Java 21 target
- ADR 0004: Single-EC2 deploy target (coexists with this one)
- ADR 0005: Tomcat 10 / Jakarta migration (image used by this deploy)
