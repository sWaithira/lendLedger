#!/usr/bin/env bash
# Backs up the LendLedger database to a compressed dump and keeps the newest 14.
set -euo pipefail
CONTAINER="${LL_DB_CONTAINER:-lendledger-postgres-1}"
DB="${LL_DB_NAME:-lendledger}"
DB_USER="${LL_DB_USER:-lendledger_app}"
DIR="${LL_BACKUP_DIR:-$HOME/lendledger-backups}"

mkdir -p "$DIR"
FILE="$DIR/lendledger-$(date +%Y%m%d-%H%M%S).dump"
docker exec "$CONTAINER" pg_dump -U "$DB_USER" -d "$DB" -Fc > "$FILE"

# A backup you never checked is not a backup: make sure the dump can be read.
docker exec -i "$CONTAINER" pg_restore -U "$DB_USER" --list < "$FILE" > /dev/null
echo "Backup OK: $FILE ($(du -h "$FILE" | cut -f1))"

ls -1t "$DIR"/lendledger-*.dump | tail -n +15 | xargs -r rm --