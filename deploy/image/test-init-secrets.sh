#!/usr/bin/env bash
# Checks bin/init-secrets. Needs only bash and coreutils.
#
#   deploy/image/test-init-secrets.sh
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

failures=0
expect() {
	local description=$1 expected=$2 actual=$3
	if [[ $actual != "$expected" ]]; then
		printf 'FAIL %s\n--- expected\n%s\n--- actual\n%s\n' "$description" "$expected" "$actual"
		failures=$((failures + 1))
	fi
}

secrets=$work/secrets
db_secrets=$work/secrets-db
mkdir -p "$secrets" "$db_secrets"
run() { L2JFREE_SECRETS_DIR=$secrets L2JFREE_DB_SECRETS_DIR=$db_secrets "$here/bin/init-secrets"; }

run > "$work/first.log"
expect "the module passwords are in the secrets volume" "login_password world_password" "$(ls "$secrets" | paste -sd' ')"
expect "the superuser password is in its own volume" "postgres_password" "$(ls "$db_secrets" | paste -sd' ')"
expect "every password has 32 letters and digits" "ok ok ok" "$(for f in "$secrets"/* "$db_secrets"/*; do
	[[ $(cat "$f") =~ ^[A-Za-z0-9]{32}$ ]] && echo ok || echo "bad $(cat "$f")"; done | paste -sd' ')"
expect "the three passwords differ" "3" "$(for f in "$secrets"/* "$db_secrets"/*; do cat "$f"; echo; done | sort -u | wc -l | tr -d ' ')"
expect "the server can read the passwords" "644 644 644" "$(stat -c %a "$secrets"/* "$db_secrets"/* | paste -sd' ')"
expect "no temporary file is left behind" "" "$(ls "$secrets" "$db_secrets" | grep '\.tmp$' || true)"

before=$(cat "$secrets"/* "$db_secrets"/*)
run > "$work/second.log"
expect "a second start keeps the passwords" "$before" "$(cat "$secrets"/* "$db_secrets"/*)"
expect "a second start says so" "3" "$(grep -c 'already exists' "$work/second.log")"

# An empty file is a secret that was never written, so it is created again.
: > "$secrets/login_password"
run > /dev/null
expect "an empty password file is replaced" "ok" "$([[ $(cat "$secrets/login_password") =~ ^[A-Za-z0-9]{32}$ ]] && echo ok || echo bad)"

if ((failures)); then
	echo "$failures check(s) failed" >&2
	exit 1
fi
echo "init-secrets: all checks passed"
