# Runbook

How to run Platform 3.0: the first start, the daily checks, a backup and a restore, an update of the image and the way back, and what to collect when something goes wrong. Each section names what must be true before it and how to check that it worked. The commands are the ones of the [deploy guide](../deploy/README.md); the pipeline runs the backup, the restore, the update, and the capture on every merge, and the first start and the stop on every pull request.

All commands run from the root of the repository, with `docker compose -f deploy/compose.yaml` shortened to `compose` below:

```sh
alias compose='docker compose -f deploy/compose.yaml'
```

## First start

Before: Docker Desktop, Colima, or Docker Engine with the Compose plugin, 4 GB of memory for the server, and the ports 2106 and 7777 free on the host. Set `L2JFREE_EXTERNAL_HOST` to the address that players use ([variables](../deploy/README.md#run-it)).

```sh
compose up -d --wait
```

The first start takes five minutes or more: the database is created with its two roles, each module migrates its schema, and the world loads the catalog.

Check: `compose ps` shows `server` and `db` healthy, and the log has the line `Platform ready`, which marks the moment the login port opens:

```sh
compose logs server | grep 'Platform ready'
```

A client that connects to the login port gets a first packet at once.

## Daily checks

```sh
compose ps
compose logs --since 24h server | grep -E '(^|\| )ERROR ' || echo "no errors"
```

- Every service is healthy and `server` has not restarted (`compose ps` shows its uptime).
- The log has no `ERROR` line. A `WARN` line is information.
- The disk has room for the volumes `pgdata` and `log`.

## Change a setting

Put the keys you change into one file of the operator directory and restart ([how](../deploy/README.md#configuration)). The server reads the files only at start.

Check: the log of the next start has no warning about an unknown key.

## Backup

Before: the stack runs. Take a backup before every update and on a schedule you can live with; a backup is two schemas, `login` and `world` ([why](../deploy/README.md#backup-and-restore)).

```sh
compose exec -T db pg_dump -U postgres -d l2jfree -n login -n world -Fc > l2jfree-$(date +%F).dump
```

Check: the file is not empty, and `compose exec -T db pg_restore -l < l2jfree-….dump` lists `TABLE DATA login account` and `TABLE DATA world player`. Keep the dump on another machine.

## Restore

Before: a dump from the section above. A restore replaces `login` and `world` entirely, so everything since the dump is lost.

```sh
compose stop server
compose exec -T db psql -U postgres -d l2jfree \
  -c 'DROP SCHEMA IF EXISTS report, catalog, world, login CASCADE' \
  -c 'CREATE SCHEMA catalog AUTHORIZATION l2jfree_world' \
  -c 'CREATE SCHEMA report AUTHORIZATION l2jfree_world'
compose exec -T db pg_restore -U postgres -d l2jfree --exit-on-error < l2jfree-2026-10-07.dump
compose start server
```

The tables of `catalog` have foreign keys into `world`, which is why all four schemas are dropped; the server builds `catalog` and `report` again at start.

On a new host, run the first start first, so that the database and its roles exist, then stop the server and restore.

Check: after `compose up -d --wait`, a player of the dump logs in and finds the character.

## Update the image

Before: a backup from the same day. Updates only go forward: the new image migrates the schemas at start, and an older image does not understand a newer schema.

```sh
compose pull
compose up -d --wait
```

`compose pull` fetches the image named by `L2JFREE_IMAGE` (default `ghcr.io/l2go/l2jfree:edge`); name a `sha-…` tag to pin the version. Compose replaces the `server` container and keeps the volumes. The pipeline rehearses this: a stack on the image that is published is replaced by the image of the commit, and an account with a character is still there.

Check: the log has `Platform ready` again, `compose ps` shows the new image, and a player logs in. [Verify the image](image-verification.md) before you run an image you did not build.

## Roll back

If the new image does not start or misbehaves, go back to the state before the update:

```sh
compose down
L2JFREE_IMAGE=ghcr.io/l2go/l2jfree:sha-<previous> compose up -d --wait db
```

then [restore](#restore) the backup taken before the update and start the server on the previous image. The database must be restored, because the migrations of the new image stay in it.

## Collect what an incident needs

Before: the stack runs, even badly. One command collects the container state, the logs, the slowest statements, the database sessions, and a thread dump of the server, without stopping it:

```sh
deploy/probe/capture.sh
```

Send the directory it prints with the report, after removing what you do not want to share (account names, addresses). The thread dump is the first thing to read when the server is up but does not answer.

## Stop

```sh
compose stop server
```

The server saves the world and the players, and stops within seconds; the pipeline fails if it needs more than 30 seconds or is killed. The compose file gives it 60 seconds before Docker kills it, because the save goes through the players one after the other.

Check: the exit code of the container is 0 (`docker inspect -f '{{.State.ExitCode}}' l2jfree-server-1`) and the log of the stop has `Data saved. All players disconnected`.
