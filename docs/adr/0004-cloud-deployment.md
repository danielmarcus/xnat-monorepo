# ADR 0004: AWS EC2 with Docker Compose for Cloud Deployment

**Status:** Accepted
**Date:** 2026-02-05
**Authors:** XNAT Core Team / DevOps
**Deciders:** XNAT Steering Committee

---

## Context

The XNAT monorepo needs a cloud deployment target for two purposes:

1. **CI/CD staging environment** — After the smoke test suite passes on `main`,
   the WAR is automatically deployed to a running XNAT instance in the cloud so
   that integration and acceptance tests can run against a realistic deployment.

2. **Reference deployment** — Provide a working reference architecture for
   institutions that want to self-host XNAT on a cloud provider.

The key requirements for the solution:

- Must be automatable from GitHub Actions with minimal operator intervention.
- Must run the same `docker-compose.yml` stack used for local development and
  CI smoke tests (no environment-specific configuration divergence).
- Must be simple to provision, update, and tear down.
- Should minimize cost for the staging use case (not a production-scale
  deployment).
- Infrastructure must be reproducible and version-controlled.

---

## Decision

Deploy XNAT to a **single AWS EC2 instance** running **Amazon Linux 2023**
with **Docker** and **Docker Compose v2** installed.  The instance runs the
same `docker-compose.yml` stack (`xnat-db` PostgreSQL container + `xnat-web`
Tomcat container) that is used in local development.

Infrastructure is provisioned and managed by **Terraform** with S3 remote
state.  Deployment automation is handled by two shell scripts:
`deploy/cloud/scripts/deploy.sh` (WAR copy + stack start) and
`deploy/cloud/scripts/teardown.sh` (Terraform destroy).

---

## Alternatives Considered

### AWS ECS (Elastic Container Service) with Fargate

ECS Fargate would eliminate the need to manage EC2 instance patching and
Docker installation.

**Rejected because:**

1. ECS introduces significant additional complexity: task definitions, service
   definitions, ECS clusters, IAM task roles, ECR image repositories, and
   ALBs.  This complexity is not justified for a staging environment.
2. XNAT requires a persistent data volume for the archive, build directory, and
   XNAT home.  ECS Fargate supports EFS mounts but this adds cost and
   configuration overhead.
3. The `docker-compose.yml` parity requirement is harder to achieve on ECS —
   the Compose file format maps imperfectly to ECS task definitions.

### Kubernetes (EKS or self-managed)

Kubernetes offers the most flexible and scalable deployment model.

**Rejected because:** The operational overhead of Kubernetes (cluster
management, node groups, RBAC, ingress controllers, persistent volume claims)
is far beyond what is needed for a staging environment.  XNAT does not
currently require horizontal scaling.  Kubernetes is retained as a future
option if XNAT adopts a microservices architecture.

### AWS Lightsail

AWS Lightsail provides simpler VM-based deployments with a fixed monthly price.

**Not selected:** Lightsail lacks the Terraform provider maturity and feature
set of EC2.  Terraform's `aws_lightsail_instance` resource has fewer lifecycle
management options.  EC2 provides better integration with VPC, security groups,
IAM instance profiles, and EBS volumes.

### Single Docker host on a non-AWS cloud (GCP, Azure, DigitalOcean)

Alternative cloud providers were considered.

**AWS selected because:** XNAT's existing CI infrastructure (Artifactory,
some internal tooling) already uses AWS.  Using AWS for the cloud deployment
avoids introducing a second cloud provider's IAM model and billing.

---

## Architecture

```
                    ┌─────────────────────────────────────────┐
                    │  AWS VPC                                 │
                    │                                         │
  Internet ─── SG ──►  EC2 (Amazon Linux 2023, t3.medium)    │
   :80, :443       │  │                                       │
                    │  ├── Docker: xnat-db (postgres:15)      │
                    │  │           port 5432 (internal only)  │
                    │  │                                       │
                    │  └── Docker: xnat-web (Tomcat + WAR)    │
                    │              port 80 (public)            │
                    │              /data/xnat/* (EBS volume)  │
                    │                                         │
                    └─────────────────────────────────────────┘

  Terraform state ──► S3 bucket (encrypted, versioned)
```

### Terraform resources provisioned

| Resource | Type | Purpose |
|----------|------|---------|
| `aws_instance.xnat` | EC2 instance | Runs the Docker Compose stack |
| `aws_security_group.xnat` | Security group | Allows ports 22, 80, 443 inbound |
| `aws_eip.xnat` | Elastic IP | Stable public IP across stop/start cycles |
| `data.aws_ami.amazon_linux_2023` | Data source | Latest AL2023 x86_64 AMI |

### CI/CD flow

```
main-build.yml  ->  smoke-test.yml  ->  cloud-deploy.yml
                                         |
                                         1. Terraform init/plan/apply
                                         2. deploy.sh (SCP WAR + docker-compose up)
                                         3. Wait for XNAT readiness
                                         4. Run smoke tests against cloud endpoint
```

---

## Consequences

### Positive

- The same `docker-compose.yml` runs in development, CI, and the cloud staging
  environment — no environment-specific configuration divergence.
- Simple to understand and operate: one EC2 instance, two Docker containers.
- Terraform state in S3 enables reproducible infrastructure management by any
  team member with AWS credentials.
- `teardown.sh` destroys all resources cleanly when the staging environment is
  not needed, avoiding idle costs.

### Negative / Accepted Trade-offs

- **Single point of failure** — if the EC2 instance fails, XNAT is unavailable.
  This is acceptable for a staging environment but not for production.
- **No auto-scaling** — XNAT on a single instance cannot scale beyond the
  capacity of the chosen instance type.  This is appropriate for a staging
  environment; production deployments should size the instance appropriately.
- **Manual SSH key management** — the EC2 key pair and `CLOUD_SSH_KEY` secret
  must be provisioned manually before the first deployment.

---

## Related Decisions

- ADR 0001: Monorepo structure
- ADR 0002: Gradle multi-project build
- ADR 0003: Java 21 target
