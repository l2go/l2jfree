#!/usr/bin/env bash
# Creates or updates the read-only role simulator_ro of the stand and sets its
# password. Runs in the database container of the stand (see README.md), after
# the server has started once and migrated the world schema:
#
#   docker compose -f compose.yaml -f stand/simulator/compose.yaml \
#     exec db /opt/stand/readonly-role.sh
#
# The password comes from STAND_DB_PASSWORD or from the file named by
# STAND_DB_PASSWORD_FILE; it never appears on a command line.
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
if [[ -n ${STAND_DB_PASSWORD_FILE:-} ]]; then
	STAND_DB_PASSWORD=$(cat -- "$STAND_DB_PASSWORD_FILE")
fi
[[ -n ${STAND_DB_PASSWORD:-} ]] || {
	echo "stand: set STAND_DB_PASSWORD or STAND_DB_PASSWORD_FILE" >&2
	exit 1
}
export STAND_DB_PASSWORD

{
	cat "$here/readonly-role.sql"
	printf '%s\n' '\getenv password STAND_DB_PASSWORD' "ALTER ROLE simulator_ro PASSWORD :'password';"
} | psql -v ON_ERROR_STOP=1 --single-transaction --quiet \
	--username "$POSTGRES_USER" --dbname "${POSTGRES_DB:-l2jfree}"
echo "stand: role simulator_ro is ready"
