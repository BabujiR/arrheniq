#!/usr/bin/env bash
# Runs the database lint + security behaviour tests against a freshly migrated DB.
# Usage: PGHOST=... PGPORT=... ./run_db_tests.sh   (passwords via PGPASSWORD or ~/.pgpass)
# In CI this runs inside a Testcontainers/Docker PostgreSQL 16 after Flyway migrate.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
DB="${DB:-arrheniq}"
PSQL="psql -X -v ON_ERROR_STOP=1 -d $DB"

echo "== schema lint (expect no violations)"
out=$($PSQL -U arrheniq_owner -A -t -f "$DIR/schema_lint.sql" | grep -v '^\[LINT' | grep -v '^$' || true)
if [ -n "$out" ]; then echo "LINT VIOLATIONS:"; echo "$out"; exit 1; fi
echo "lint OK"

echo "== security behaviour (platform role)"
$PSQL -U arrheniq_platform -v part=platform -v part_is_platform=1 -f "$DIR/security_behaviour_test.sql"
echo "== security behaviour (app role)"
$PSQL -U arrheniq_app -v part=app -v part_is_platform=0 -f "$DIR/security_behaviour_test.sql"
echo "ALL DB TESTS PASSED"
