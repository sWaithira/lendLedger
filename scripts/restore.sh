#!/usr/bin/env bash
# Replaces the LIVE database with a backup. Stop the backend first.
set -euo pipefail
CONTAINER="${LL_DB_CONTAINER:-lendledger-postgres-1}"
DB="${LL_DB_NAME:-lendledger}"
DB_USER="${LL_DB_USER:-lendledger_app}"
FILE="${1:?Usage: scripts/restore.sh <backup-file.dump>}"

echo "This will DELETE the current '$DB' database and replace it with $FILE."
read -r -p "Type RESTORE to continue: " answer
[ "$answer" = "RESTORE" ] || { echo "Cancelled."; exit 1; }

docker exec "$CONTAINER" dropdb -U "$DB_USER" --force --if-exists "$DB"
docker exec "$CONTAINER" createdb -U "$DB_USER" "$DB"
docker exec -i "$CONTAINER" pg_restore -U "$DB_USER" -d "$DB" --no-owner --no-privileges < "$FILE"
echo "Restored. Start the backend again."