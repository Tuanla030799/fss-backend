#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ROOT_DIR}/.env.production"
COMPOSE_FILE="${ROOT_DIR}/docker-compose.prod.yml"

if [[ ! -f "${ENV_FILE}" ]]; then
    echo "Missing ${ENV_FILE}. Copy .env.production.example and fill in production secrets." >&2
    exit 1
fi

PUBLIC_NETWORK="$(grep -E '^BACKEND_PUBLIC_NETWORK=' "${ENV_FILE}" | tail -n 1 | cut -d '=' -f 2-)"
PUBLIC_NETWORK="${PUBLIC_NETWORK:-thepocketshoes_net}"

mkdir -p "${ROOT_DIR}/uploads" "${ROOT_DIR}/logs"

if ! docker network inspect "${PUBLIC_NETWORK}" >/dev/null 2>&1; then
    docker network create "${PUBLIC_NETWORK}"
fi

docker compose \
    --env-file "${ENV_FILE}" \
    -f "${COMPOSE_FILE}" \
    up -d --build
