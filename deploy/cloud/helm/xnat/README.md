# XNAT Helm Chart

Deploys XNAT into an EKS cluster provisioned by `deploy/cloud/terraform/eks/`.
The defaults match the matching terraform module's outputs, so the typical
flow is:

```bash
# 1. Provision infra
cd deploy/cloud/terraform/eks
terraform init -backend-config=eks-backend.conf
terraform apply -var "db_password=$EKS_DB_PASSWORD"

# 2. Wire kubeconfig
$(terraform output -raw kubeconfig_command)

# 3. Build + push image, install chart
cd ../../scripts
./eks-deploy.sh
```

## What it deploys

A single XNAT instance with:
- `Deployment` (1 replica, `strategy: Recreate` — see "Why one replica" below)
- `Service` (defaults to `LoadBalancer` so AWS assigns an ELB hostname)
- `ConfigMap` for `xnat-conf.properties`
- Two `PersistentVolumeClaim`s:
  - **archive** on `efs-sc` (RWX) — session data, future-proof for scale-out
  - **config** on `gp3` (RWO) — `XNAT_HOME` (logs, plugins, working files)
- Optional `Ingress` (ALB) when `ingress.enabled=true`
- Optional in-cluster Postgres `StatefulSet` when `inClusterPostgres.enabled=true`
- `ServiceAccount` with optional IRSA annotation

## Required values

When using the matching terraform module, `eks-deploy.sh` injects these via `--set`:

| Value | Source |
| --- | --- |
| `image.repository` | `terraform output -raw ecr_repository_url` |
| `image.tag` | Git SHA of the commit being deployed |
| `database.host` | `terraform output -raw rds_endpoint` |

The DB password is **not** rendered into the chart manifest. `eks-deploy.sh`
creates a Secret named `xnat-db-credentials` outside Helm using the
`EKS_DB_PASSWORD` GitHub secret; the Deployment references it via env.

## Finding the assigned ELB hostname

With `service.type: LoadBalancer` (default), AWS provisions a classic ELB
and reports the hostname back to the Service status:

```bash
kubectl get svc -l app.kubernetes.io/instance=xnat -o wide
# Or directly:
kubectl get svc xnat-xnat-web -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'
```

Point your DNS or a curl test at that hostname:
```bash
curl http://$(kubectl get svc xnat-xnat-web -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')/xapi/siteConfig/buildInfo
```

## Switching from RDS to in-cluster Postgres

Useful for dev / CI environments that don't have RDS access.

```bash
helm upgrade --install xnat . \
  --set image.repository=$ECR_URL --set image.tag=$GIT_SHA \
  --set database.mode=inCluster \
  --set inClusterPostgres.enabled=true \
  --set database.existingSecretName=
```

Clearing `existingSecretName` lets the chart auto-generate the password and
materialise it as a Secret. Reusing the password across upgrades is handled
via `lookup`, so subsequent `helm upgrade` calls don't rotate it.

## Switching to ALB Ingress

Pre-requisite: terraform applied with `enable_ingress_alb=true` (installs
the AWS Load Balancer Controller).

```bash
helm upgrade --install xnat . \
  --set image.repository=$ECR_URL --set image.tag=$GIT_SHA \
  --set service.type=ClusterIP \
  --set ingress.enabled=true \
  --set ingress.host=xnat.example.com \
  --set ingress.acmCertificateArn=arn:aws:acm:us-east-1:123:cert/abc
```

Service flips to ClusterIP because the ALB targets pods directly via
`target-type=ip`; a LoadBalancer Service alongside the ALB would just waste
an ELB.

## Why one replica

XNAT is not designed for active-active. Two pods writing to the same archive
will produce duplicate session entries, conflicting prearchive flushes, and
race conditions in the workflow engine. `replicaCount` is intentionally `1`;
`strategy: Recreate` is intentional too — `RollingUpdate` with one replica +
RWO config volume stalls (new pod can't bind the volume the old pod still
holds). Don't change either without designing a session-affinity story first.

## Probes

The chart probes `/xapi/siteConfig/buildInfo` rather than `/initialized`. The
`/initialized` endpoint requires authentication in the current XNAT release,
which would break the readiness probe. `/buildInfo` returns 200 with no auth
and exercises the same servlet stack.

The liveness probe has a 5-minute initial delay because Tomcat warmup +
Spring context init + first-time DB schema migration can each take a minute
or more on a cold pod. A tighter probe would kill pods mid-startup.

## Verifying without applying

```bash
helm lint .
helm template xnat . --set image.repository=foo --set image.tag=bar | less
```
