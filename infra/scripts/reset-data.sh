#!/usr/bin/env sh
# Reset the LOCAL database to a fresh start with sample data.
#   sh infra/scripts/reset-data.sh        (asks first)
#   sh infra/scripts/reset-data.sh -y     (no question)
#
# What it does: empties the local database, then restarts the backend. On start the backend
# re-creates every table (Flyway) and loads the sample data (sample data loader, from P1-01).
# Only touches the local Docker Compose database (project "gemshop") – never a server.
set -eu

cd "$(dirname "$0")/../.."
DB_USER="${DB_USER:-gemshop}"
DB_NAME="${DB_NAME:-gemshop}"
HEALTH_URL="http://127.0.0.1:8081/api/health"

if [ "${1:-}" != "-y" ]; then
  printf "This deletes ALL data in the local database (%s) and loads fresh sample data. Continue? [y/N] " "$DB_NAME"
  read -r answer
  case "$answer" in y|Y|yes|YES) ;; *) echo "Cancelled."; exit 1 ;; esac
fi

echo "1/4 Starting the local database…"
docker compose up -d --wait db

echo "2/4 Stopping the backend…"
docker compose stop server >/dev/null 2>&1 || true

echo "3/4 Emptying the database…"
docker compose exec -T db psql -v ON_ERROR_STOP=1 -q -U "$DB_USER" -d "$DB_NAME" \
  -c "drop schema public cascade; create schema public authorization \"$DB_USER\";"

echo "4/4 Starting the backend (tables + sample data)…"
docker compose up -d server

i=0
until curl -sf "$HEALTH_URL" >/dev/null 2>&1; do
  i=$((i + 1))
  if [ "$i" -gt 60 ]; then
    echo "The backend did not start in 2 minutes. Check: docker compose logs server" >&2
    exit 1
  fi
  sleep 2
done
echo "Done. Fresh local data is ready – $(curl -s "$HEALTH_URL")"
