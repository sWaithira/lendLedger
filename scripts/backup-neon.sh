#!/usr/bin/env bash
# Back up the live Neon database: dump, verify, encrypt, copy off the laptop, and prune old files.
set -euo pipefail
umask 077
CONF="${LL_CONF:-$HOME/.config/lendledger/backup.env}"
if [ -f "$CONF" ]; then . "$CONF"; fi          # may set NEON_URL, LL_PASSFILE, LL_COPY_DIR, LL_KEEP, LL_MAX_DAYS
DIR="${LL_BACKUP_DIR:-$HOME/lendledger-neon-backups}"
KEEP="${LL_KEEP:-14}"
MAX_DAYS="${LL_MAX_DAYS:-90}"
mkdir -p "$DIR"
if [ -z "${NEON_URL:-}" ]; then read -rs -p "Neon connection string (postgresql://...): " NEON_URL; echo; fi
export PGURL="$NEON_URL"

PLAIN="$DIR/neon-$(date +%Y%m%d-%H%M%S).dump"
docker run --rm -e PGURL postgres:18 sh -c 'pg_dump "$PGURL" --format=custom --no-owner --no-privileges' > "$PLAIN"
docker run --rm -i postgres:18 pg_restore --list < "$PLAIN" > /dev/null     # fails if the dump is unreadable

OUT="$PLAIN"
if [ -n "${LL_PASSFILE:-}" ]; then
  gpg --batch --yes --pinentry-mode loopback --passphrase-file "$LL_PASSFILE" --symmetric --cipher-algo AES256 -o "$PLAIN.gpg" "$PLAIN"
  shred -u "$PLAIN" 2>/dev/null || rm -f "$PLAIN"
  OUT="$PLAIN.gpg"
else
  echo "WARNING: LL_PASSFILE is not set, so this backup is NOT encrypted" >&2
fi
if [ -n "${LL_COPY_DIR:-}" ]; then mkdir -p "$LL_COPY_DIR"; cp "$OUT" "$LL_COPY_DIR"/; fi

# keep the newest KEEP files and drop anything older than MAX_DAYS
ls -t "$DIR"/neon-*.dump* 2>/dev/null | tail -n +$((KEEP + 1)) | xargs -r rm --
find "$DIR" -name 'neon-*.dump*' -mtime +"$MAX_DAYS" -delete
echo "OK  $(du -h "$OUT" | cut -f1)  $OUT"