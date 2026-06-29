#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ROOT_DIR}/.env.production"
COMPOSE_FILE="${ROOT_DIR}/docker-compose.prod.yml"
BACKUP_ROOT="${ROOT_DIR}/backups"
STAMP="$(date +%F-%H%M%S)"
STOP_API=true

usage() {
    cat <<'EOF'
Usage: scripts/backup-prod.sh [options]

Options:
  --no-stop-api         Keep the API running during backup.
  --output-dir <dir>    Directory where backup folders are written.
  --name <value>        Override timestamp-based backup folder name.
  -h, --help            Show this help.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --no-stop-api)
            STOP_API=false
            shift
            ;;
        --output-dir)
            [[ $# -ge 2 ]] || { echo "Missing value for $1" >&2; exit 1; }
            BACKUP_ROOT="$2"
            shift 2
            ;;
        --name)
            [[ $# -ge 2 ]] || { echo "Missing value for $1" >&2; exit 1; }
            STAMP="$2"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1" >&2
            usage >&2
            exit 1
            ;;
    esac
done

if [[ ! -f "${ENV_FILE}" ]]; then
    echo "Missing ${ENV_FILE}. Copy .env.production.example and fill in production secrets." >&2
    exit 1
fi

if [[ ! -f "${COMPOSE_FILE}" ]]; then
    echo "Missing ${COMPOSE_FILE}." >&2
    exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
    echo "docker command not found." >&2
    exit 1
fi

COMPOSE=(docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}")
TARGET_DIR="${BACKUP_ROOT}/${STAMP}"
DB_DUMP_FILE="${TARGET_DIR}/fss_db.dump"
UPLOADS_ARCHIVE="${TARGET_DIR}/uploads.tar.gz"
ENV_COPY_FILE="${TARGET_DIR}/.env.production"
MANIFEST_FILE="${TARGET_DIR}/backup-info.txt"

mkdir -p "${TARGET_DIR}"

API_RUNNING=false
if "${COMPOSE[@]}" ps --status running --services | grep -qx 'api'; then
    API_RUNNING=true
fi

restart_api_if_needed() {
    if [[ "${STOP_API}" == true && "${API_RUNNING}" == true ]]; then
        "${COMPOSE[@]}" start api >/dev/null
    fi
}

trap restart_api_if_needed EXIT

if [[ "${STOP_API}" == true && "${API_RUNNING}" == true ]]; then
    echo "Stopping api for a consistent backup window..."
    "${COMPOSE[@]}" stop api >/dev/null
fi

echo "Dumping PostgreSQL to ${DB_DUMP_FILE}..."
"${COMPOSE[@]}" exec -T db \
    sh -lc 'PGPASSWORD="$POSTGRES_PASSWORD" pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' \
    > "${DB_DUMP_FILE}"

echo "Archiving uploads to ${UPLOADS_ARCHIVE}..."
if [[ -d "${ROOT_DIR}/uploads" ]]; then
    tar -czf "${UPLOADS_ARCHIVE}" -C "${ROOT_DIR}" uploads
else
    tar -czf "${UPLOADS_ARCHIVE}" --files-from /dev/null
fi

cp "${ENV_FILE}" "${ENV_COPY_FILE}"

cat > "${MANIFEST_FILE}" <<EOF
backup_name=${STAMP}
created_at=$(date -Iseconds)
project_root=${ROOT_DIR}
db_dump=$(basename "${DB_DUMP_FILE}")
uploads_archive=$(basename "${UPLOADS_ARCHIVE}")
env_file=$(basename "${ENV_COPY_FILE}")
api_stop_requested=${STOP_API}
api_was_running=${API_RUNNING}
EOF

trap - EXIT
restart_api_if_needed

echo "Backup completed: ${TARGET_DIR}"
