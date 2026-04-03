#!/usr/bin/env bash
# =============================================================================
# deploy.sh — Deploy XNAT WAR to the cloud EC2 instance
#
# Usage:
#   ./deploy.sh <war-file> <host> [options]
#
# Arguments:
#   <war-file>   Path to the built WAR file, e.g. apps/web/build/libs/web-1.10.0-RC2-SNAPSHOT.war
#   <host>       Public IP or hostname of the EC2 instance
#
# Options:
#   -u <user>      SSH user (default: ec2-user)
#   -i <identity>  SSH identity file / key path
#   -p <port>      SSH port (default: 22)
#   -d <dir>       Remote deploy directory (default: /opt/xnat-deploy)
#   -t <timeout>   Seconds to wait for XNAT readiness (default: 300)
#   -h             Show this help message
#
# Environment variables (alternative to flags):
#   SSH_USER          SSH username
#   SSH_KEY_FILE      Path to SSH private key
#   DEPLOY_DIR        Remote directory on the instance
#   XNAT_READY_TIMEOUT  Seconds to wait for XNAT to become ready
#
# Examples:
#   # Basic deploy using SSH agent
#   ./deploy.sh apps/web/build/libs/web-1.10.0-RC2-SNAPSHOT.war 1.2.3.4
#
#   # With explicit key file
#   ./deploy.sh apps/web/build/libs/web-1.10.0-RC2-SNAPSHOT.war 1.2.3.4 \
#     -i ~/.ssh/xnat-staging.pem
#
#   # CI usage (key passed via environment variable decoded from secret)
#   echo "$CLOUD_SSH_KEY" > /tmp/deploy_key && chmod 600 /tmp/deploy_key
#   ./deploy.sh path/to/web.war "$CLOUD_HOST" -i /tmp/deploy_key
# =============================================================================
set -euo pipefail

# ---- Defaults ---------------------------------------------------------------
SSH_USER="${SSH_USER:-ec2-user}"
SSH_KEY_FILE="${SSH_KEY_FILE:-}"
SSH_PORT="${SSH_PORT:-22}"
DEPLOY_DIR="${DEPLOY_DIR:-/opt/xnat-deploy}"
XNAT_READY_TIMEOUT="${XNAT_READY_TIMEOUT:-300}"
XNAT_READY_INTERVAL=10

# ---- Colours (suppressed when not a terminal) -------------------------------
if [[ -t 1 ]]; then
  RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; RESET='\033[0m'
else
  RED=''; GREEN=''; YELLOW=''; CYAN=''; RESET=''
fi

info()    { echo -e "${CYAN}[INFO ]${RESET}  $*"; }
success() { echo -e "${GREEN}[OK   ]${RESET}  $*"; }
warn()    { echo -e "${YELLOW}[WARN ]${RESET}  $*"; }
error()   { echo -e "${RED}[ERROR]${RESET}  $*" >&2; }
die()     { error "$*"; exit 1; }

# ---- Argument parsing -------------------------------------------------------
usage() {
  sed -n '/^# Usage:/,/^# =/{ /^#/{ s/^# \{0,1\}//; p } }' "$0"
  exit 0
}

WAR_FILE=""
HOST=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    -u) SSH_USER="$2";        shift 2 ;;
    -i) SSH_KEY_FILE="$2";    shift 2 ;;
    -p) SSH_PORT="$2";        shift 2 ;;
    -d) DEPLOY_DIR="$2";      shift 2 ;;
    -t) XNAT_READY_TIMEOUT="$2"; shift 2 ;;
    -h|--help) usage ;;
    -*) die "Unknown option: $1" ;;
    *)
      if [[ -z "$WAR_FILE" ]]; then WAR_FILE="$1"
      elif [[ -z "$HOST" ]]; then  HOST="$1"
      else die "Unexpected argument: $1"; fi
      shift
      ;;
  esac
done

[[ -n "$WAR_FILE" ]] || die "WAR file argument is required.  Run with -h for usage."
[[ -n "$HOST" ]]     || die "Host argument is required.  Run with -h for usage."
[[ -f "$WAR_FILE" ]] || die "WAR file not found: $WAR_FILE"

# ---- Build SSH / SCP options ------------------------------------------------
SSH_OPTS=(-o StrictHostKeyChecking=no -o ConnectTimeout=30 -p "$SSH_PORT")
[[ -n "$SSH_KEY_FILE" ]] && SSH_OPTS+=(-i "$SSH_KEY_FILE")

# ---- Helper: run a command on the remote host -------------------------------
remote() {
  ssh "${SSH_OPTS[@]}" "${SSH_USER}@${HOST}" "$@"
}

echo "============================================================"
info "XNAT Cloud Deployment"
info "WAR         : $WAR_FILE"
info "Host        : $HOST"
info "SSH user    : $SSH_USER"
info "Deploy dir  : $DEPLOY_DIR"
echo "============================================================"
echo ""

# ---- Step 1: Verify SSH connectivity ----------------------------------------
info "Step 1/5 — Verifying SSH connectivity to ${HOST}..."
remote 'echo "SSH OK — $(uname -n)"' || die "Cannot reach ${HOST} via SSH.  Check the host, key, and security group."
success "SSH connection established."
echo ""

# ---- Step 2: Copy the WAR to the EC2 instance --------------------------------
info "Step 2/5 — Copying WAR to ${HOST}:${DEPLOY_DIR}..."
WAR_FILENAME="$(basename "$WAR_FILE")"
remote "sudo mkdir -p ${DEPLOY_DIR} && sudo chown -R ${SSH_USER}:${SSH_USER} ${DEPLOY_DIR}"
scp "${SSH_OPTS[@]/#-p/-P}" "$WAR_FILE" "${SSH_USER}@${HOST}:${DEPLOY_DIR}/${WAR_FILENAME}"
# Also stage a generic name the docker-compose volume mount can reference
remote "cp ${DEPLOY_DIR}/${WAR_FILENAME} ${DEPLOY_DIR}/xnat.war"
success "WAR copied: ${WAR_FILENAME}"
echo ""

# ---- Step 3: Copy Docker Compose files --------------------------------------
info "Step 3/5 — Copying Docker Compose stack files..."
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
COMPOSE_SRC="${REPO_ROOT}/deploy/docker-compose/docker-compose.yml"

if [[ -f "$COMPOSE_SRC" ]]; then
  scp "${SSH_OPTS[@]/#-p/-P}" "$COMPOSE_SRC" "${SSH_USER}@${HOST}:${DEPLOY_DIR}/docker-compose.yml"
  success "docker-compose.yml copied from ${COMPOSE_SRC}"
else
  warn "docker-compose.yml not found at ${COMPOSE_SRC} — using the version already on the server."
fi

# Copy optional .env file if present alongside the compose file
COMPOSE_ENV="${REPO_ROOT}/deploy/docker-compose/.env"
if [[ -f "$COMPOSE_ENV" ]]; then
  scp "${SSH_OPTS[@]/#-p/-P}" "$COMPOSE_ENV" "${SSH_USER}@${HOST}:${DEPLOY_DIR}/.env"
  info ".env file staged."
fi
echo ""

# ---- Step 4: Start (or restart) the Docker Compose stack -------------------
info "Step 4/5 — Starting Docker Compose stack on ${HOST}..."
remote bash -s <<REMOTE
set -euo pipefail
cd "${DEPLOY_DIR}"

# Ensure Docker is running
if ! systemctl is-active --quiet docker; then
  echo "Starting Docker service..."
  sudo systemctl start docker
fi

# Pull the latest images before bringing the stack up
echo "Pulling Docker images..."
docker compose pull --quiet 2>/dev/null || true

# Bring up the stack (recreate xnat-web so the new WAR is picked up)
echo "Starting stack..."
docker compose up -d --force-recreate --no-deps xnat-web xnat-db

echo "Stack started."
docker compose ps
REMOTE
success "Docker Compose stack started."
echo ""

# ---- Step 5: Wait for XNAT readiness ----------------------------------------
info "Step 5/5 — Waiting for XNAT to become ready (timeout: ${XNAT_READY_TIMEOUT}s)..."
XNAT_URL="http://${HOST}/xnat"
elapsed=0

until curl --silent --fail --max-time 5 --output /dev/null "${XNAT_URL}/app/template/Login.vm" 2>/dev/null; do
  if (( elapsed >= XNAT_READY_TIMEOUT )); then
    die "XNAT did not become ready within ${XNAT_READY_TIMEOUT}s at ${XNAT_URL}.  Check logs with: ssh ${SSH_USER}@${HOST} 'docker compose -C ${DEPLOY_DIR} logs --tail=100 xnat-web'"
  fi
  printf "  waiting... (%ds elapsed)\r" "$elapsed"
  sleep "$XNAT_READY_INTERVAL"
  (( elapsed += XNAT_READY_INTERVAL ))
done

echo ""
echo ""
echo "============================================================"
success "Deployment complete!"
info "XNAT URL    : ${XNAT_URL}"
info "SSH access  : ssh ${SSH_USER}@${HOST}"
info "Stack logs  : ssh ${SSH_USER}@${HOST} 'cd ${DEPLOY_DIR} && docker compose logs -f'"
echo "============================================================"
