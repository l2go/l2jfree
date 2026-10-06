#!/bin/bash
# Creates the roles and schemas of Platform 3.0 on the first start of an empty
# database. The official image runs this script once, as the database superuser.
# Each module owns one schema and connects with its own role, so a module cannot
# read the data of another.
set -euo pipefail

secrets=/run/l2jfree-secrets
login_password="$(< "$secrets/login_password")"

psql -v ON_ERROR_STOP=1 -v login_password="$login_password" \
	--username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
-- Case-insensitive text for login names. The migrations rely on it and the
-- module roles do not need the right to create extensions.
CREATE EXTENSION IF NOT EXISTS citext SCHEMA public;

-- The login module: accounts and session data. Owns the schema "login".
CREATE ROLE l2jfree_login LOGIN PASSWORD :'login_password';
CREATE SCHEMA login AUTHORIZATION l2jfree_login;
ALTER ROLE l2jfree_login SET search_path = login, public;
SQL
