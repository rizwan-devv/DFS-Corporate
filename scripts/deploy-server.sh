#!/usr/bin/env bash
# Run on server: cd /opt/dfs-corporate && bash scripts/deploy-server.sh
set -euo pipefail

cd "$(dirname "$0")/.."

BRANCH="${DEPLOY_BRANCH:-Prod}"

echo "==> Stashing local docker-compose.yml overrides (if any)..."
git stash push -m "server-compose-$(date +%Y%m%d)" -- docker-compose.yml 2>/dev/null || true

echo "==> Pulling latest ${BRANCH}..."
git fetch origin "${BRANCH}"
git checkout "${BRANCH}"
git pull origin "${BRANCH}"

echo "==> HEAD:"
git log -1 --oneline

echo "==> Re-applying compose stash (resolve conflicts manually if needed)..."
git stash pop 2>/dev/null || true

echo "==> Building & restarting..."
docker compose build --no-cache dfs-corporate-frontend dfs-corporate-backend
docker compose up -d --force-recreate dfs-corporate-frontend dfs-corporate-backend

echo "==> Done. Verify:"
docker compose ps
echo "Portal UI:  http://<server>:8060"
echo "Backend:    http://<server>:8050/actuator/health"
echo "CMS status: curl -H \"Authorization: Bearer \$TOKEN\" http://127.0.0.1:8050/api/cms/cards/status"
