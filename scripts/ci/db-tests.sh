#!/usr/bin/env bash
# =============================================================================
# Database CI: bootstrap roles, apply Flyway migrations in version order with
# psql (no Java needed), then run the schema linter + security behaviour tests.
#
# Usage (CI or local):
#   PGHOST=localhost PGPORT=5432 PGSUPERPASS=postgres scripts/ci/db-tests.sh
# Requires: psql 16+, a PostgreSQL 16 server where PGSUPERUSER (default postgres)
# is a superuser. The script creates the arrheniq_* roles and database.
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DB_DIR="$ROOT/backend/app/src/main/resources/db"
TEST_DIR="$ROOT/backend/app/src/test/resources/db"

export PGHOST="${PGHOST:-localhost}"
export PGPORT="${PGPORT:-5432}"
SUPERUSER="${PGSUPERUSER:-postgres}"
OWNER_PW="${ARQ_CI_OWNER_PW:-ci-owner}"
APP_PW="${ARQ_CI_APP_PW:-ci-app}"
PLATFORM_PW="${ARQ_CI_PLATFORM_PW:-ci-platform}"

# Per-role passwords via a temporary pgpass file (never echoed).
PGPASSFILE="$(mktemp)"
export PGPASSFILE
trap 'rm -f "$PGPASSFILE"' EXIT
chmod 600 "$PGPASSFILE"
{
  echo "*:*:*:$SUPERUSER:${PGSUPERPASS:-postgres}"
  echo "*:*:*:arrheniq_owner:$OWNER_PW"
  echo "*:*:*:arrheniq_app:$APP_PW"
  echo "*:*:*:arrheniq_platform:$PLATFORM_PW"
} > "$PGPASSFILE"

PSQL="psql -X -q -v ON_ERROR_STOP=1"

echo "::group::Reset (idempotent for local runs)"
$PSQL -U "$SUPERUSER" -d postgres \
  -c "DROP DATABASE IF EXISTS arrheniq" \
  -c "DROP ROLE IF EXISTS arrheniq_app" \
  -c "DROP ROLE IF EXISTS arrheniq_platform" \
  -c "DROP ROLE IF EXISTS arrheniq_owner"
echo "::endgroup::"

echo "::group::Bootstrap roles and database"
$PSQL -U "$SUPERUSER" -d postgres \
  -v owner_password="$OWNER_PW" -v app_password="$APP_PW" -v platform_password="$PLATFORM_PW" \
  -f "$DB_DIR/bootstrap/00_roles_and_database.sql"
echo "::endgroup::"

echo "::group::Apply migrations (Flyway version order) as arrheniq_owner"
count=0
for f in $(ls "$DB_DIR/migration" | grep -E '^V[0-9]+__.*\.sql$' | sort -t V -k2 -n); do
  PGOPTIONS='-c client_min_messages=warning' $PSQL -U arrheniq_owner -d arrheniq -1 -f "$DB_DIR/migration/$f"
  echo "applied $f"
  count=$((count + 1))
done
echo "$count migrations applied"
echo "::endgroup::"

echo "::group::Schema lint + security behaviour tests"
DB=arrheniq bash "$TEST_DIR/run_db_tests.sh"
echo "::endgroup::"
