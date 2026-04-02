#!/usr/bin/env bash
# wait-for-xnat.sh
#
# Poll GET /xapi/siteConfig until it returns HTTP 200 or the timeout expires.
# Intended for use in CI pipelines and local development after `docker compose up`.
#
# Usage:
#   ./wait-for-xnat.sh [options]
#
# Options:
#   -h HOST      XNAT host  (default: http://localhost)
#   -p PORT      XNAT port  (default: 80)
#   -t TIMEOUT   Timeout in seconds (default: 300)
#   -i INTERVAL  Poll interval in seconds (default: 5)
#
# Environment variables (overridden by flags):
#   XNAT_HOST      e.g. http://localhost
#   XNAT_PORT      e.g. 80
#
# Exit codes:
#   0  XNAT responded with HTTP 200 within the timeout
#   1  Timeout reached without a successful response

set -euo pipefail

# ---------------------------------------------------------------------------
# Defaults
# ---------------------------------------------------------------------------
HOST="${XNAT_HOST:-http://localhost}"
PORT="${XNAT_PORT:-80}"
TIMEOUT=300   # 5 minutes
INTERVAL=5

# ---------------------------------------------------------------------------
# Argument parsing
# ---------------------------------------------------------------------------
while getopts "h:p:t:i:" opt; do
  case "$opt" in
    h) HOST="$OPTARG" ;;
    p) PORT="$OPTARG" ;;
    t) TIMEOUT="$OPTARG" ;;
    i) INTERVAL="$OPTARG" ;;
    *) echo "Unknown option: -$OPTARG" >&2; exit 1 ;;
  esac
done

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
log() { printf '[%s] %s\n' "$(date -u +%H:%M:%S)" "$*"; }

# Strip trailing slash then append port only when it isn't already in the host.
base_url="${HOST%/}"
if [[ ! "$base_url" =~ :[0-9]+$ ]]; then
  base_url="${base_url}:${PORT}"
fi

probe_url="${base_url}/xapi/siteConfig"

log "Waiting for XNAT at ${probe_url} (timeout: ${TIMEOUT}s, interval: ${INTERVAL}s) ..."

# ---------------------------------------------------------------------------
# Poll loop
# ---------------------------------------------------------------------------
elapsed=0
while true; do
  if http_code=$(curl --silent --output /dev/null --write-out "%{http_code}" \
        --max-time "$INTERVAL" \
        --location \
        "${probe_url}" 2>/dev/null); then
    if [[ "$http_code" == "200" ]]; then
      log "XNAT is ready (HTTP ${http_code})."
      exit 0
    fi
  fi

  if (( elapsed >= TIMEOUT )); then
    log "ERROR: Timed out after ${TIMEOUT}s waiting for XNAT." >&2
    exit 1
  fi

  log "Not ready yet (HTTP ${http_code:-???}). Retrying in ${INTERVAL}s ... (${elapsed}s elapsed)"
  sleep "$INTERVAL"
  elapsed=$(( elapsed + INTERVAL ))
done
