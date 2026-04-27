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

## Related Decisions

- ADR 0001: Monorepo structure
- ADR 0003: Java 21 target
- ADR 0004: Single-EC2 deploy target (coexists with this one)
- ADR 0005: Tomcat 10 / Jakarta migration (image used by this deploy)
