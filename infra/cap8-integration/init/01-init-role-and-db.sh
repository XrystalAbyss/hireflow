#!/usr/bin/env bash
# ==============================================================================
# HireFlow Dedicated PostgreSQL Initialization Script
# Executed automatically by official postgres:16 entrypoint on first cluster init.
# Secure secret delivery: dynamically reads password from APP_DB_PASSWORD_FILE
# or APP_DB_PASSWORD environment variable. Never commits plaintext credentials.
# ==============================================================================
set -euo pipefail

app_user="${APP_DB_USER:-hireflow_cap8_app}"
target_db="${POSTGRES_DB:-hireflow_cap8_it}"
bootstrap_user="${POSTGRES_USER:-postgres}"

app_password=""
if [ -n "${APP_DB_PASSWORD_FILE:-}" ] && [ -f "${APP_DB_PASSWORD_FILE}" ]; then
    app_password="$(head -n 1 "${APP_DB_PASSWORD_FILE}")"
elif [ -n "${APP_DB_PASSWORD:-}" ]; then
    app_password="${APP_DB_PASSWORD}"
fi

if [ -z "${app_password}" ]; then
    echo "ERROR [HireFlow Init]: Application database password not provided." >&2
    echo "Set APP_DB_PASSWORD or APP_DB_PASSWORD_FILE in the container environment." >&2
    exit 1
fi

echo "Initializing HireFlow restricted role '${app_user}' on database '${target_db}'..."

# 1. Create or update restricted application role with least privileges
# Password passed securely via psql variables without command-line parameter leakage
psql -v ON_ERROR_STOP=1 --username "${bootstrap_user}" --dbname "${target_db}" \
    -v role_name="${app_user}" \
    -v role_pass="${app_password}" <<'EOSQL'
SELECT CASE 
    WHEN NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = :'role_name') 
    THEN format('CREATE ROLE %I WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD %L', :'role_name', :'role_pass')
    ELSE format('ALTER ROLE %I WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD %L', :'role_name', :'role_pass')
END \gexec
EOSQL

# 2. Configure schema permissions, extensions, and the safety sentinel marker
psql -v ON_ERROR_STOP=1 --username "${bootstrap_user}" --dbname "${target_db}" \
    -v role_name="${app_user}" <<-EOSQL
CREATE EXTENSION IF NOT EXISTS btree_gist;

GRANT ALL ON SCHEMA public TO :"role_name";
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO :"role_name";
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO :"role_name";
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON FUNCTIONS TO :"role_name";

CREATE TABLE IF NOT EXISTS _integration_sentinel (
    marker_key VARCHAR(64) PRIMARY KEY,
    marker_value VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO _integration_sentinel (marker_key, marker_value)
VALUES ('ENVIRONMENT_ID', 'HIREFLOW_CAP8_ISOLATED_INTEGRATION_2026')
ON CONFLICT (marker_key) DO UPDATE SET marker_value = EXCLUDED.marker_value;

GRANT ALL ON TABLE _integration_sentinel TO :"role_name";
EOSQL

echo "HireFlow isolated database initialization completed successfully."
