#!/usr/bin/env bash
# =============================================================================
# teardown.sh — Destroy the XNAT cloud infrastructure via Terraform
#
# Usage:
#   ./teardown.sh [options]
#
# Options:
#   -d <tf-dir>   Path to the Terraform directory (default: deploy/cloud/terraform)
#   -e <env>      Terraform workspace / environment label (informational only)
#   --auto-approve  Skip the confirmation prompt (DANGEROUS — use in CI only)
#   -h            Show this help and exit
#
# Environment variables:
#   TF_DIR            Override the Terraform directory
#   AWS_REGION        AWS region (used for backend config)
#   TF_BACKEND_BUCKET S3 bucket for Terraform state
#   TF_BACKEND_KEY    S3 key for Terraform state
#   TF_BACKEND_REGION Region of the state bucket
#
# Examples:
#   # Interactive teardown (prompts for confirmation)
#   ./teardown.sh
#
#   # Teardown a staging environment, auto-approved (CI/CD use)
#   ./teardown.sh --auto-approve -e staging
#
#   # Point at a non-default Terraform directory
#   ./teardown.sh -d infra/terraform/staging
#
# WARNING: This script permanently destroys all cloud resources managed by
# Terraform, including the EC2 instance, Elastic IP, and security group.
# Any XNAT data stored only on the instance will be LOST.
# =============================================================================
set -euo pipefail

# ---- Defaults ---------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
TF_DIR="${TF_DIR:-${REPO_ROOT}/deploy/cloud/terraform}"
ENVIRONMENT="${ENVIRONMENT:-staging}"
AUTO_APPROVE=false

# ---- Colours ----------------------------------------------------------------
if [[ -t 1 ]]; then
  RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'
  BOLD='\033[1m'; RESET='\033[0m'
else
  RED=''; GREEN=''; YELLOW=''; CYAN=''; BOLD=''; RESET=''
fi

info()    { echo -e "${CYAN}[INFO ]${RESET}  $*"; }
success() { echo -e "${GREEN}[OK   ]${RESET}  $*"; }
warn()    { echo -e "${YELLOW}[WARN ]${RESET}  $*"; }
error()   { echo -e "${RED}[ERROR]${RESET}  $*" >&2; }
die()     { error "$*"; exit 1; }

# ---- Argument parsing -------------------------------------------------------
while [[ $# -gt 0 ]]; do
  case "$1" in
    -d) TF_DIR="$2";        shift 2 ;;
    -e) ENVIRONMENT="$2";   shift 2 ;;
    --auto-approve) AUTO_APPROVE=true; shift ;;
    -h|--help)
      sed -n '/^# Usage:/,/^# =/{ /^#/{ s/^# \{0,1\}//; p } }' "$0"
      exit 0
      ;;
    *) die "Unknown argument: $1  (run with -h for usage)" ;;
  esac
done

# ---- Pre-flight checks ------------------------------------------------------
[[ -d "$TF_DIR" ]] || die "Terraform directory not found: ${TF_DIR}"
command -v terraform &>/dev/null || die "terraform not found in PATH.  Install from https://developer.hashicorp.com/terraform/downloads"

# ---- Banner -----------------------------------------------------------------
echo ""
echo -e "${RED}${BOLD}╔══════════════════════════════════════════════════════════╗${RESET}"
echo -e "${RED}${BOLD}║  WARNING: XNAT CLOUD INFRASTRUCTURE TEARDOWN             ║${RESET}"
echo -e "${RED}${BOLD}╚══════════════════════════════════════════════════════════╝${RESET}"
echo ""
warn "This will PERMANENTLY DESTROY all resources managed by Terraform:"
warn "  • EC2 instance (xnat-monorepo-${ENVIRONMENT})"
warn "  • Elastic IP (if allocated)"
warn "  • Security group"
warn ""
warn "Any XNAT data stored only on the EC2 instance will be LOST."
warn "Terraform directory: ${TF_DIR}"
echo ""

# ---- Confirmation prompt (skipped with --auto-approve) ----------------------
if [[ "$AUTO_APPROVE" == "false" ]]; then
  echo -e "${YELLOW}${BOLD}Type 'yes' to confirm teardown, or anything else to cancel:${RESET}"
  read -r -p "> " CONFIRM
  echo ""
  if [[ "$CONFIRM" != "yes" ]]; then
    info "Teardown cancelled."
    exit 0
  fi
else
  warn "Auto-approve is enabled — skipping confirmation prompt."
fi

# ---- Terraform init ---------------------------------------------------------
info "Initialising Terraform in ${TF_DIR}..."
cd "$TF_DIR"

INIT_ARGS=(-input=false -reconfigure)

# Append backend config if environment variables are set
[[ -n "${TF_BACKEND_BUCKET:-}" ]] && INIT_ARGS+=("-backend-config=bucket=${TF_BACKEND_BUCKET}")
[[ -n "${TF_BACKEND_KEY:-}"    ]] && INIT_ARGS+=("-backend-config=key=${TF_BACKEND_KEY}")
[[ -n "${TF_BACKEND_REGION:-}" ]] && INIT_ARGS+=("-backend-config=region=${TF_BACKEND_REGION}")

terraform init "${INIT_ARGS[@]}"
success "Terraform initialised."
echo ""

# ---- Show what will be destroyed before proceeding --------------------------
info "Generating destroy plan..."
terraform plan -destroy -input=false -out=destroy.tfplan
echo ""

# ---- Terraform destroy -------------------------------------------------------
if [[ "$AUTO_APPROVE" == "true" ]]; then
  DESTROY_ARGS=(-input=false)
else
  DESTROY_ARGS=(-input=false)
fi

info "Running terraform destroy..."
terraform apply -input=false destroy.tfplan

echo ""
echo "============================================================"
success "Teardown complete — all Terraform-managed resources destroyed."
info "The Terraform state in S3 has been updated to reflect the empty environment."
info "To re-provision, run: terraform apply from ${TF_DIR}"
echo "============================================================"

# ---- Cleanup plan file -------------------------------------------------------
rm -f destroy.tfplan
