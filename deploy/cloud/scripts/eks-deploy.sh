#!/usr/bin/env bash
# =============================================================================
# eks-deploy.sh
#
# End-to-end deploy of the XNAT WAR onto the EKS cluster provisioned by
# deploy/cloud/terraform/eks/. Builds an image (Dockerfile.k8s, WAR baked
# in), pushes to ECR, creates/refreshes the DB-credentials Secret out of
# band, and helm-upgrades the chart.
#
# Required environment:
#   AWS_REGION              AWS region (e.g. us-east-1)
#   ECR_REPOSITORY_URL      e.g. 123456789012.dkr.ecr.us-east-1.amazonaws.com/xnat-web
#                           — `terraform output -raw ecr_repository_url`
#   EKS_CLUSTER_NAME        e.g. xnat-staging
#                           — `terraform output -raw eks_cluster_name`
#   RDS_ENDPOINT            e.g. xnat-staging-xnat.abc.us-east-1.rds.amazonaws.com
#                           — `terraform output -raw rds_endpoint`
#   EKS_DB_PASSWORD         Same value passed to terraform's db_password var.
#   GIT_SHA                 Image tag. Defaults to the current `git rev-parse --short HEAD`.
#   WAR_PATH                Optional; defaults to apps/web/build/libs/web-1.10.0-RC2-SNAPSHOT.war
#   HELM_RELEASE_NAME       Optional; defaults to xnat
#   HELM_NAMESPACE          Optional; defaults to default
#   ENABLE_INGRESS_ALB      Optional; "true" to render the Ingress
#   INGRESS_HOST            Required when ENABLE_INGRESS_ALB=true
#   ACM_CERTIFICATE_ARN     Required when ENABLE_INGRESS_ALB=true
#
# Usage:
#   AWS_REGION=us-east-1 \
#   ECR_REPOSITORY_URL=$(terraform -chdir=deploy/cloud/terraform/eks output -raw ecr_repository_url) \
#   EKS_CLUSTER_NAME=$(terraform -chdir=deploy/cloud/terraform/eks output -raw eks_cluster_name) \
#   RDS_ENDPOINT=$(terraform -chdir=deploy/cloud/terraform/eks output -raw rds_endpoint) \
#   EKS_DB_PASSWORD=... \
#   ./deploy/cloud/scripts/eks-deploy.sh
# =============================================================================

set -euo pipefail

# -----------------------------------------------------------------------------
# Inputs
# -----------------------------------------------------------------------------
: "${AWS_REGION:?AWS_REGION is required}"
: "${ECR_REPOSITORY_URL:?ECR_REPOSITORY_URL is required (terraform output)}"
: "${EKS_CLUSTER_NAME:?EKS_CLUSTER_NAME is required (terraform output)}"
: "${RDS_ENDPOINT:?RDS_ENDPOINT is required (terraform output)}"
: "${EKS_DB_PASSWORD:?EKS_DB_PASSWORD is required}"

GIT_SHA="${GIT_SHA:-$(git rev-parse --short HEAD)}"
WAR_PATH="${WAR_PATH:-apps/web/build/libs/web-1.10.0-RC2-SNAPSHOT.war}"
HELM_RELEASE_NAME="${HELM_RELEASE_NAME:-xnat}"
HELM_NAMESPACE="${HELM_NAMESPACE:-default}"
ENABLE_INGRESS_ALB="${ENABLE_INGRESS_ALB:-false}"

# Mirror the chart's `xnat.fullname` helper (templates/_helpers.tpl): if the
# release name already contains the chart name, fullname == release; otherwise
# fullname == "<release>-<chart>". With the default HELM_RELEASE_NAME=xnat
# this collapses to "xnat", so kubectl resource names like
# `${HELM_RELEASE_NAME}-xnat` would refer to the non-existent "xnat-xnat".
if [[ "${HELM_RELEASE_NAME}" == *"xnat"* ]]; then
  FULLNAME="${HELM_RELEASE_NAME}"
else
  FULLNAME="${HELM_RELEASE_NAME}-xnat"
fi

# Resolve the script's own directory so relative paths work whether invoked
# from the repo root, from CI, or from `deploy/cloud/scripts/`.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../../" && pwd)"
cd "${REPO_ROOT}"

CHART_DIR="${REPO_ROOT}/deploy/cloud/helm/xnat"
DOCKERFILE_K8S="${REPO_ROOT}/deploy/docker-compose/xnat/Dockerfile.k8s"

if [[ ! -f "${WAR_PATH}" ]]; then
  echo "::error::WAR not found at ${WAR_PATH}. Run './gradlew :apps:web:war' first."
  exit 1
fi

echo "==> Deploying XNAT to EKS cluster ${EKS_CLUSTER_NAME} (region ${AWS_REGION})"
echo "    image:     ${ECR_REPOSITORY_URL}:${GIT_SHA}"
echo "    db host:   ${RDS_ENDPOINT}"
echo "    release:   ${HELM_RELEASE_NAME} (namespace ${HELM_NAMESPACE})"
echo "    war:       ${WAR_PATH}"
echo

# -----------------------------------------------------------------------------
# 1. Wire kubeconfig
# -----------------------------------------------------------------------------
echo "==> 1/6 aws eks update-kubeconfig"
aws eks update-kubeconfig \
  --region "${AWS_REGION}" \
  --name "${EKS_CLUSTER_NAME}"

# -----------------------------------------------------------------------------
# 2. ECR login
# -----------------------------------------------------------------------------
echo "==> 2/6 ECR login"
ECR_REGISTRY="${ECR_REPOSITORY_URL%%/*}"
aws ecr get-login-password --region "${AWS_REGION}" \
  | docker login --username AWS --password-stdin "${ECR_REGISTRY}"

# -----------------------------------------------------------------------------
# 3. Build + push image
# -----------------------------------------------------------------------------
echo "==> 3/6 docker build + push"
IMAGE_TAG="${ECR_REPOSITORY_URL}:${GIT_SHA}"
docker build \
  --build-arg "WAR_PATH=${WAR_PATH}" \
  --tag "${IMAGE_TAG}" \
  --file "${DOCKERFILE_K8S}" \
  .

docker push "${IMAGE_TAG}"

# -----------------------------------------------------------------------------
# 4. Create/refresh DB credentials Secret out of band
#
# Done outside Helm so the password never lands in the chart manifest. Naming
# matches database.existingSecretName in values.yaml.
# -----------------------------------------------------------------------------
echo "==> 4/6 ensure DB credentials Secret"
kubectl create namespace "${HELM_NAMESPACE}" --dry-run=client -o yaml \
  | kubectl apply -f -

kubectl create secret generic xnat-db-credentials \
  --namespace "${HELM_NAMESPACE}" \
  --from-literal=password="${EKS_DB_PASSWORD}" \
  --dry-run=client -o yaml \
  | kubectl apply -f -

# -----------------------------------------------------------------------------
# 5. helm upgrade --install
# -----------------------------------------------------------------------------
echo "==> 5/6 helm upgrade --install"
HELM_ARGS=(
  upgrade --install "${HELM_RELEASE_NAME}" "${CHART_DIR}"
  --namespace "${HELM_NAMESPACE}"
  --set "image.repository=${ECR_REPOSITORY_URL}"
  --set "image.tag=${GIT_SHA}"
  --set "database.host=${RDS_ENDPOINT}"
  --set "database.mode=rds"
  --set "inClusterPostgres.enabled=false"
  --wait
  # 25 min — first-time deploy needs RDS schema sync inside the pod plus
  # all the cold-start stuff covered by the readiness/liveness initial
  # delays in values.yaml. Subsequent deploys finish in 2-3 min, but the
  # ceiling has to accommodate the worst case.
  --timeout 25m
)

if [[ "${ENABLE_INGRESS_ALB}" == "true" ]]; then
  : "${INGRESS_HOST:?INGRESS_HOST required when ENABLE_INGRESS_ALB=true}"
  : "${ACM_CERTIFICATE_ARN:?ACM_CERTIFICATE_ARN required when ENABLE_INGRESS_ALB=true}"
  HELM_ARGS+=(
    --set "service.type=ClusterIP"
    --set "ingress.enabled=true"
    --set "ingress.host=${INGRESS_HOST}"
    --set "ingress.acmCertificateArn=${ACM_CERTIFICATE_ARN}"
  )
fi

helm "${HELM_ARGS[@]}"

# -----------------------------------------------------------------------------
# 6. Resolve the externally-visible hostname
# -----------------------------------------------------------------------------
echo "==> 6/6 resolve external hostname"
kubectl rollout status \
  --namespace "${HELM_NAMESPACE}" \
  --timeout 10m \
  "deployment/${FULLNAME}"

if [[ "${ENABLE_INGRESS_ALB}" == "true" ]]; then
  EXTERNAL_HOST=$(kubectl get ingress \
    --namespace "${HELM_NAMESPACE}" \
    "${FULLNAME}" \
    -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')
  ENDPOINT_KIND="ALB"
else
  EXTERNAL_HOST=$(kubectl get svc \
    --namespace "${HELM_NAMESPACE}" \
    "${FULLNAME}-web" \
    -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')
  ENDPOINT_KIND="ELB"
fi

echo
echo "===================================================="
echo "  XNAT deployed."
echo "  ${ENDPOINT_KIND}: http://${EXTERNAL_HOST}"
echo "  Health: http://${EXTERNAL_HOST}/xapi/siteConfig/buildInfo"
echo "===================================================="

# Emit GitHub Actions output if running in CI.
if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  {
    echo "external_host=${EXTERNAL_HOST}"
    echo "endpoint_kind=${ENDPOINT_KIND}"
    echo "image_tag=${GIT_SHA}"
  } >> "${GITHUB_OUTPUT}"
fi
