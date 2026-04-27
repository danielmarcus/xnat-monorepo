#!/usr/bin/env bash
# =============================================================================
# jakarta-migrate-and-start.sh — Phase C.1 entrypoint
#
# Runs Apache's jakartaee-migration tool against the inbound WAR to rewrite
# all javax.* references to jakarta.*, then chains to wait-for-postgres.sh
# to start Tomcat as before.
#
# The migration tool is a build/deploy-time rewriter — there is no runtime
# Listener class. So we transform the WAR before Tomcat scans it.
#
# Two input modes:
#   1. WAR mounted at /opt/xnat-input/ROOT.war (docker-compose path,
#      read-only mount). We transform to /usr/local/tomcat/webapps/ROOT.war.
#   2. WAR baked into the image at /usr/local/tomcat/webapps/ROOT.war
#      (Dockerfile.k8s path). The Dockerfile's COPY drops it directly at
#      the destination; this script then transforms it in place via a
#      tmp-then-mv dance.
#
# The transformation is idempotent in effect (re-running on an already-Jakarta
# WAR is a no-op for our purposes) but slow (~30s for a 230 MB WAR). To skip
# it on container restart of an already-transformed WAR, we sentinel-check
# /usr/local/tomcat/webapps/.jakarta-migrated.
# =============================================================================

set -euo pipefail

MIGRATION_JAR="/usr/local/tomcat/lib/jakartaee-migration-1.0.10-shaded.jar"
INPUT_WAR_MOUNTED="/opt/xnat-input/ROOT.war"
DEPLOY_WAR="/usr/local/tomcat/webapps/ROOT.war"
SENTINEL="/usr/local/tomcat/webapps/.jakarta-migrated"

# Profile EE = Java EE 8 -> Jakarta EE 9 namespace mapping. This is what
# we need for Spring 5 / Hibernate 5 / Restlet 1.1.10. TOMCAT profile
# is more aggressive (also rewrites tomcat-specific config); we want EE.
MIGRATION_PROFILE="EE"

if [[ -f "${SENTINEL}" ]]; then
  echo "[jakarta-migrate] Already migrated (sentinel present); skipping."
  exec wait-for-postgres.sh /usr/local/tomcat/bin/catalina.sh run
fi

# Resolve the input WAR.
if [[ -f "${INPUT_WAR_MOUNTED}" ]]; then
  INPUT_WAR="${INPUT_WAR_MOUNTED}"
  echo "[jakarta-migrate] Found mounted WAR at ${INPUT_WAR}"
elif [[ -f "${DEPLOY_WAR}" ]]; then
  INPUT_WAR="${DEPLOY_WAR}"
  echo "[jakarta-migrate] Using baked-in WAR at ${INPUT_WAR}"
else
  echo "[jakarta-migrate] ERROR: no WAR found at ${INPUT_WAR_MOUNTED} or ${DEPLOY_WAR}"
  exit 1
fi

# Migration tool can't write to the same path it reads, so output to tmp.
TMP_WAR="/tmp/ROOT-jakarta.war"
echo "[jakarta-migrate] Running migration (profile=${MIGRATION_PROFILE})..."
START_TS=$(date +%s)
java -jar "${MIGRATION_JAR}" \
  -profile="${MIGRATION_PROFILE}" \
  "${INPUT_WAR}" \
  "${TMP_WAR}"
END_TS=$(date +%s)
echo "[jakarta-migrate] Migration completed in $((END_TS - START_TS))s."

# Replace the deploy-target WAR. If the destination already exists (baked
# image case), this overwrites it; if not, this places the migrated copy.
mv "${TMP_WAR}" "${DEPLOY_WAR}"

# Sentinel so a container restart doesn't re-migrate (the migrated WAR is
# already Jakarta-typed; running migration again is harmless but slow).
touch "${SENTINEL}"

# Tomcat unpacks ROOT.war on first deploy; in case a previous run left an
# unpacked ROOT/ directory from before the migration, blow it away so the
# Jakarta-typed WAR drives the next deployment cleanly.
rm -rf /usr/local/tomcat/webapps/ROOT

echo "[jakarta-migrate] Handing off to wait-for-postgres + Tomcat."
exec wait-for-postgres.sh /usr/local/tomcat/bin/catalina.sh run
