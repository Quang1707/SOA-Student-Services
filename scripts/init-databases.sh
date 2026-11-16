#!/usr/bin/env bash
set -Eeuo pipefail

: "${PROFILE_DB_NAME:?PROFILE_DB_NAME is required}"
: "${PROFILE_DB_USER:?PROFILE_DB_USER is required}"
: "${PROFILE_DB_PASSWORD:?PROFILE_DB_PASSWORD is required}"
: "${DOCUMENT_DB_NAME:?DOCUMENT_DB_NAME is required}"
: "${DOCUMENT_DB_USER:?DOCUMENT_DB_USER is required}"
: "${DOCUMENT_DB_PASSWORD:?DOCUMENT_DB_PASSWORD is required}"
: "${AUDIT_DB_NAME:?AUDIT_DB_NAME is required}"
: "${AUDIT_DB_USER:?AUDIT_DB_USER is required}"
: "${AUDIT_DB_PASSWORD:?AUDIT_DB_PASSWORD is required}"

psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v ON_ERROR_STOP=1 \
  -v profile_db_name="$PROFILE_DB_NAME" -v profile_db_user="$PROFILE_DB_USER" -v profile_db_password="$PROFILE_DB_PASSWORD" \
  -v document_db_name="$DOCUMENT_DB_NAME" -v document_db_user="$DOCUMENT_DB_USER" -v document_db_password="$DOCUMENT_DB_PASSWORD" \
  -v audit_db_name="$AUDIT_DB_NAME" -v audit_db_user="$AUDIT_DB_USER" -v audit_db_password="$AUDIT_DB_PASSWORD" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'profile_db_user', :'profile_db_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'profile_db_user')
\gexec
SELECT format('CREATE DATABASE %I OWNER %I', :'profile_db_name', :'profile_db_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'profile_db_name')
\gexec

SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'document_db_user', :'document_db_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'document_db_user')
\gexec
SELECT format('CREATE DATABASE %I OWNER %I', :'document_db_name', :'document_db_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'document_db_name')
\gexec

SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'audit_db_user', :'audit_db_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'audit_db_user')
\gexec
SELECT format('CREATE DATABASE %I OWNER %I', :'audit_db_name', :'audit_db_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'audit_db_name')
\gexec

SELECT format('GRANT ALL PRIVILEGES ON DATABASE %I TO %I', :'profile_db_name', :'profile_db_user')\gexec
SELECT format('GRANT ALL PRIVILEGES ON DATABASE %I TO %I', :'document_db_name', :'document_db_user')\gexec
SELECT format('REVOKE CONNECT ON DATABASE %I FROM PUBLIC', :'profile_db_name')\gexec
SELECT format('REVOKE CONNECT ON DATABASE %I FROM PUBLIC', :'document_db_name')\gexec
SELECT format('REVOKE CONNECT ON DATABASE %I FROM PUBLIC', :'audit_db_name')\gexec
SELECT format('GRANT CONNECT, TEMPORARY ON DATABASE %I TO %I', :'profile_db_name', :'profile_db_user')\gexec
SELECT format('GRANT CONNECT, TEMPORARY ON DATABASE %I TO %I', :'document_db_name', :'document_db_user')\gexec
SELECT format('GRANT CONNECT, TEMPORARY ON DATABASE %I TO %I', :'audit_db_name', :'audit_db_user')\gexec
SQL

# Apply schema permissions while connected to each service database.
for item in "${PROFILE_DB_NAME}:${PROFILE_DB_USER}" "${DOCUMENT_DB_NAME}:${DOCUMENT_DB_USER}" "${AUDIT_DB_NAME}:${AUDIT_DB_USER}"; do
  db_name="${item%%:*}"
  db_user="${item#*:}"
  psql --username "$POSTGRES_USER" --dbname "$db_name" -v ON_ERROR_STOP=1 -v app_user="$db_user" <<'SQL'
SELECT format('GRANT USAGE, CREATE ON SCHEMA public TO %I', :'app_user')\gexec
SQL
done
