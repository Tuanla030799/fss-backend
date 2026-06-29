#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ROOT_DIR}/.env.production"
COMPOSE_FILE="${ROOT_DIR}/docker-compose.prod.yml"
CONFIRM=false
BACKUP_DIR=""
STAMP="$(date +%F-%H%M%S)"

usage() {
    cat <<'EOF'
Usage: scripts/restore-prod.sh --backup-dir <dir> --yes

Options:
  --backup-dir <dir>    Backup directory containing fss_db.dump and uploads.tar.gz.
  --yes                 Confirm destructive restore.
  --name <value>        Override suffix used for the preserved uploads directory.
  -h, --help            Show this help.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --backup-dir)
            [[ $# -ge 2 ]] || { echo "Missing value for $1" >&2; exit 1; }
            BACKUP_DIR="$2"
            shift 2
            ;;
        --yes)
            CONFIRM=true
            shift
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

if [[ "${CONFIRM}" != true ]]; then
    echo "Restore is destructive. Re-run with --yes." >&2
    exit 1
fi

if [[ -z "${BACKUP_DIR}" ]]; then
    echo "Missing --backup-dir." >&2
    usage >&2
    exit 1
fi

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

if [[ ! -d "${BACKUP_DIR}" ]]; then
    echo "Backup directory not found: ${BACKUP_DIR}" >&2
    exit 1
fi

BACKUP_DIR="$(cd "${BACKUP_DIR}" && pwd)"
DB_DUMP_FILE="${BACKUP_DIR}/fss_db.dump"
UPLOADS_ARCHIVE="${BACKUP_DIR}/uploads.tar.gz"
UPLOADS_DIR="${ROOT_DIR}/uploads"
UPLOADS_ROLLBACK_DIR="${ROOT_DIR}/uploads.before-restore-${STAMP}"

if [[ ! -f "${DB_DUMP_FILE}" ]]; then
    echo "Missing database dump: ${DB_DUMP_FILE}" >&2
    exit 1
fi

if [[ ! -f "${UPLOADS_ARCHIVE}" ]]; then
    echo "Missing uploads archive: ${UPLOADS_ARCHIVE}" >&2
    exit 1
fi

COMPOSE=(docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}")
API_RUNNING=false
RESTORE_OK=false

if "${COMPOSE[@]}" ps --status running --services | grep -qx 'api'; then
    API_RUNNING=true
fi

finish() {
    if [[ "${RESTORE_OK}" == true && "${API_RUNNING}" == true ]]; then
        "${COMPOSE[@]}" start api >/dev/null
        echo "API restarted."
    elif [[ "${RESTORE_OK}" != true && "${API_RUNNING}" == true ]]; then
        echo "Restore failed. API remains stopped for manual verification." >&2
    elif [[ "${RESTORE_OK}" != true ]]; then
        echo "Restore failed." >&2
    fi
}

trap finish EXIT

if [[ "${API_RUNNING}" == true ]]; then
    echo "Stopping api before restore..."
    "${COMPOSE[@]}" stop api >/dev/null
fi

echo "Restoring PostgreSQL from ${DB_DUMP_FILE}..."
"${COMPOSE[@]}" exec -T db \
    sh -lc 'PGPASSWORD="$POSTGRES_PASSWORD" pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner --no-privileges' \
    < "${DB_DUMP_FILE}"

if [[ -d "${UPLOADS_DIR}" ]]; then
    echo "Preserving current uploads at ${UPLOADS_ROLLBACK_DIR}..."
    mv "${UPLOADS_DIR}" "${UPLOADS_ROLLBACK_DIR}"
fi

echo "Restoring uploads from ${UPLOADS_ARCHIVE}..."
tar -xzf "${UPLOADS_ARCHIVE}" -C "${ROOT_DIR}"

RESTORE_OK=true

echo "Restore completed from ${BACKUP_DIR}"
if [[ -d "${UPLOADS_ROLLBACK_DIR}" ]]; then
    echo "Previous uploads were kept at ${UPLOADS_ROLLBACK_DIR}"
fi
