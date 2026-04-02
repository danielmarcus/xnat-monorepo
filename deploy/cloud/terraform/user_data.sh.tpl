#!/bin/bash
# =============================================================================
# EC2 User-Data Bootstrap Script — XNAT Monorepo
#
# Rendered by Terraform templatefile().  Variables supplied by main.tf.
# Runs as root on first boot on Amazon Linux 2023.
# =============================================================================
set -euo pipefail

LOGFILE="/var/log/xnat-bootstrap.log"
exec > >(tee -a "$LOGFILE") 2>&1
echo "[$(date -u +%FT%TZ)] Starting XNAT bootstrap"

# --------------------------------------------------------------------------
# 1. System updates
# --------------------------------------------------------------------------
dnf update -y --quiet

# --------------------------------------------------------------------------
# 2. Install Docker (Amazon Linux 2023 ships the docker package directly)
# --------------------------------------------------------------------------
dnf install -y docker git awscli
systemctl enable --now docker
usermod -aG docker ec2-user

# --------------------------------------------------------------------------
# 3. Install Docker Compose v2 plugin
# --------------------------------------------------------------------------
COMPOSE_VERSION="2.27.1"
COMPOSE_DEST="/usr/local/lib/docker/cli-plugins/docker-compose"
mkdir -p "$(dirname "$COMPOSE_DEST")"
curl -fsSL \
  "https://github.com/docker/compose/releases/download/v${COMPOSE_VERSION}/docker-compose-linux-x86_64" \
  -o "$COMPOSE_DEST"
chmod +x "$COMPOSE_DEST"
# Symlink for bare `docker-compose` invocations
ln -sf "$COMPOSE_DEST" /usr/local/bin/docker-compose
docker compose version

# --------------------------------------------------------------------------
# 4. Prepare XNAT data directories
# --------------------------------------------------------------------------
XNAT_HOME="/data/xnat"
mkdir -p "$XNAT_HOME"/{home,build,archive,prearchive,cache,ftp,inbox,pipeline}
chmod -R 755 "$XNAT_HOME"
chown -R ec2-user:ec2-user "$XNAT_HOME"

# --------------------------------------------------------------------------
# 5. Pull the deploy script + docker-compose files from S3 (if available)
#    The CI cloud-deploy workflow uploads these before running Terraform.
# --------------------------------------------------------------------------
DEPLOY_DIR="/opt/xnat-deploy"
mkdir -p "$DEPLOY_DIR"

%{ if deploy_s3_bucket != "" }
echo "[$(date -u +%FT%TZ)] Pulling deploy artefacts from S3..."
aws s3 sync "s3://${deploy_s3_bucket}/deploy/" "$DEPLOY_DIR/" --quiet || \
  echo "[WARN] S3 sync failed — continuing without pre-staged artefacts"
%{ endif }

# --------------------------------------------------------------------------
# 6. Write a minimal docker-compose.yml if none was staged
# --------------------------------------------------------------------------
if [[ ! -f "$DEPLOY_DIR/docker-compose.yml" ]]; then
cat > "$DEPLOY_DIR/docker-compose.yml" <<'COMPOSE'
version: "3.8"
services:
  xnat-db:
    image: postgres:15-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: xnat
      POSTGRES_USER: xnat
      POSTGRES_PASSWORD: xnat
    volumes:
      - xnat-db-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U xnat -d xnat"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 20s
  xnat-web:
    image: xnat/xnat-web:latest
    restart: unless-stopped
    depends_on:
      xnat-db:
        condition: service_healthy
    ports:
      - "80:8080"
    environment:
      XNAT_DATASOURCE_URL: jdbc:postgresql://xnat-db:5432/xnat
      XNAT_DATASOURCE_DRIVER: org.postgresql.Driver
      XNAT_DATASOURCE_USERNAME: xnat
      XNAT_DATASOURCE_PASSWORD: xnat
      XNAT_SITE_URL: http://localhost
      XNAT_HOME: /data/xnat/home
      XNAT_ADMIN_USERNAME: admin
      XNAT_ADMIN_PASSWORD: ${xnat_admin_pass}
    volumes:
      - /data/xnat/home:/data/xnat/home
      - /data/xnat/archive:/data/xnat/archive
      - /data/xnat/build:/data/xnat/build
      - /data/xnat/cache:/data/xnat/cache
volumes:
  xnat-db-data:
COMPOSE
fi

# --------------------------------------------------------------------------
# 7. Start XNAT stack (if a WAR is already present)
# --------------------------------------------------------------------------
if ls "$DEPLOY_DIR"/*.war 2>/dev/null | head -1 | grep -q '.'; then
  echo "[$(date -u +%FT%TZ)] WAR found — starting XNAT stack"
  WAR_FILE=$(ls "$DEPLOY_DIR"/*.war | head -1)
  cp "$WAR_FILE" "$DEPLOY_DIR/xnat.war"
  cd "$DEPLOY_DIR"
  docker compose up -d
else
  echo "[$(date -u +%FT%TZ)] No WAR found yet — stack will start after deploy.sh runs"
fi

echo "[$(date -u +%FT%TZ)] Bootstrap complete.  XNAT environment: ${environment}"
