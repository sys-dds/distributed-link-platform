#!/usr/bin/env sh
set -eu

API_BASE_URL="${PLATFORM_API_BASE_URL:-http://localhost:8080}"
WEBHOOK_SINK_URL="${PLATFORM_WEBHOOK_SINK_URL:-http://localhost:8090}"

# Standalone platform stack default:
# docker compose -f infra/docker-compose/docker-compose.platform.yml up --build
"$(dirname "$0")/wait-for-http.sh" "$API_BASE_URL/actuator/health/readiness"
curl -fsS "$API_BASE_URL/actuator/health" >/dev/null
"$(dirname "$0")/wait-for-http.sh" "$WEBHOOK_SINK_URL"

if [ -n "${PLATFORM_API_KEY:-}" ]; then
  if [ -n "${PLATFORM_WORKSPACE_SLUG:-}" ]; then
    curl -fsS \
      -H "X-API-Key: ${PLATFORM_API_KEY}" \
      -H "X-Workspace-Slug: ${PLATFORM_WORKSPACE_SLUG}" \
      "$API_BASE_URL/api/v1/workspaces/current" >/dev/null
  else
    curl -fsS \
      -H "X-API-Key: ${PLATFORM_API_KEY}" \
      "$API_BASE_URL/api/v1/workspaces/current" >/dev/null
  fi
fi
