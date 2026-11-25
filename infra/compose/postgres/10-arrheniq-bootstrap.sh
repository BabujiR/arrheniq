#!/usr/bin/env bash
# Runs once on first container start (docker-entrypoint-initdb.d). Applies the same bootstrap
# script used in every environment, with local passwords from the compose environment.
# Flyway (run by the app) then creates the schema as arrheniq_owner.
set -euo pipefail
psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d postgres \
  -v owner_password="$ARQ_OWNER_PASSWORD" \
  -v app_password="$ARQ_APP_PASSWORD" \
  -v platform_password="$ARQ_PLATFORM_PASSWORD" \
  -f /arrheniq/00_roles_and_database.sql
echo "ArrheniQ bootstrap complete: roles arrheniq_owner/app/platform, database arrheniq"
