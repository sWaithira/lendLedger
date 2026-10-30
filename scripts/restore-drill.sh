#!/usr/bin/env bash
# Proves the newest backup restores: loads it into a scratch database and compares row counts with the live one.
set -euo pipefail
CONTAINER="${LL_DB_CONTAINER:-lendledger-postgres-1}"
DB="${LL_DB_NAME:-lendledger}"
DB_USER="${LL_DB_USER:-lendledger_app}"
DIR="${LL_BACKUP_DIR:-$HOME/lendledger-backups}"
SCRATCH="${DB}_restore_test"

FILE="${1:-$(ls -1t "$DIR"/lendledger-*.dump | head -n 1)}"
echo "Restoring $FILE into scratch database $SCRATCH"

psql_in() { docker exec -i "$CONTAINER" psql -U "$DB_USER" -d "$1" -tA -c "$2"; }

docker exec "$CONTAINER" dropdb -U "$DB_USER" --if-exists "$SCRATCH"
docker exec "$CONTAINER" createdb -U "$DB_USER" "$SCRATCH"
docker exec -i "$CONTAINER" pg_restore -U "$DB_USER" -d "$SCRATCH" --no-owner --no-privileges < "$FILE"

status=0
for t in tenant app_user borrower loan charge_event repayment payment_allocation audit_log; do
  live=$(psql_in "$DB" "SELECT count(*) FROM $t")
  copy=$(psql_in "$SCRATCH" "SELECT count(*) FROM $t")
  if [ "$live" = "$copy" ]; then echo "OK        $t: $live rows"; else echo "DIFFERENT $t: live $live, backup $copy"; status=1; fi
done

docker exec "$CONTAINER" dropdb -U "$DB_USER" "$SCRATCH"
[ "$status" = 0 ] && echo "Restore drill passed." || echo "Rows differ. If you added data after the backup, that is expected: back up again and re-run."