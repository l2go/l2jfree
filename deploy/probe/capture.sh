#!/usr/bin/env bash
# Collects what is needed to look at an incident of a running stack into one directory: the state of the containers,
# the log of the server and of the database, the ten statements that took the most time, what the database sessions
# are doing, and a thread dump of the server.
#
# Usage: deploy/probe/capture.sh [directory]
#
# The thread dump does not stop the server: SIGQUIT makes the JVM print every thread to its standard output, which is
# the log of the container, and the JVM goes on.
set -euo pipefail

compose="docker compose -f $(dirname "$0")/../compose.yaml"
out="${1:-l2jfree-capture-$(date +%Y%m%dT%H%M%S)}"
mkdir -p "$out"

$compose ps > "$out/ps.txt"
$compose logs --no-color --timestamps server > "$out/server.log" 2>&1
$compose logs --no-color --timestamps db > "$out/db.log" 2>&1

$compose exec -T db psql -U postgres -d l2jfree \
  -c 'SELECT calls, round(total_exec_time) AS ms, left(query, 100) AS query FROM pg_stat_statements ORDER BY total_exec_time DESC LIMIT 10' \
  > "$out/pg_stat_statements.txt"
$compose exec -T db psql -U postgres -d l2jfree \
  -c "SELECT pid, usename, state, wait_event_type, wait_event, now() - xact_start AS transaction_age, left(query, 100) AS query FROM pg_stat_activity WHERE datname = 'l2jfree' ORDER BY xact_start NULLS LAST" \
  > "$out/pg_stat_activity.txt"

mark="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
$compose kill -s QUIT server
for _ in 1 2 3 4 5 6 7 8 9 10; do
  $compose logs --no-color --since "$mark" server > "$out/thread-dump.txt" 2>&1 || true
  if grep -q 'Full thread dump' "$out/thread-dump.txt"; then
    break
  fi
  sleep 1
done

echo "$out"
