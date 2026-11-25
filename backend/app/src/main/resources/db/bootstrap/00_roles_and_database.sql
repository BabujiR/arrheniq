-- =============================================================================
-- ArrheniQ database bootstrap (run ONCE per environment by an administrator,
-- NOT by Flyway). Cloud: executed by Terraform/ops with the RDS master user.
-- Private: executed by the customer DBA following the installation guide.
--
-- Roles (ADR 0003, multi-tenancy.md §3):
--   arrheniq_owner    owns all objects; used only by Flyway migrations
--   arrheniq_app      runtime role of arrheniq-app; NO BYPASSRLS, not owner
--   arrheniq_platform operator tooling (tenant provisioning, platform tables)
-- Passwords are injected from the secrets store; never commit real values.
-- =============================================================================

CREATE ROLE arrheniq_owner    LOGIN PASSWORD :'owner_password'    NOSUPERUSER NOCREATEROLE NOBYPASSRLS;
CREATE ROLE arrheniq_app      LOGIN PASSWORD :'app_password'      NOSUPERUSER NOCREATEROLE NOBYPASSRLS;
CREATE ROLE arrheniq_platform LOGIN PASSWORD :'platform_password' NOSUPERUSER NOCREATEROLE NOBYPASSRLS;

CREATE DATABASE arrheniq OWNER arrheniq_owner ENCODING 'UTF8' TEMPLATE template0;

\connect arrheniq

-- pgcrypto: digest() for the audit genesis hash and tenant provisioning.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

REVOKE ALL ON DATABASE arrheniq FROM PUBLIC;
GRANT CONNECT ON DATABASE arrheniq TO arrheniq_app, arrheniq_platform;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
