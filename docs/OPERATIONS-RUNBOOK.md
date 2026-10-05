# L2JFree operations runbook

This runbook describes release acceptance and routine operation on the fixed
Windows 10 x64, Microsoft Build of OpenJDK 25, and MySQL Server 8.4 platform.
Use the exact release archives selected for the deployment; do not mix files
from different releases.

## Prepare and verify a release

1. Download the LoginServer, GameServer, and documentation archives from the
   same GitHub release. From v2.5.0 the datapack is inside the GameServer
   archive. v2.0.0 is the last release that publishes a separate datapack zip.
2. Verify `SHA256SUMS.txt` and GitHub artifact attestations using
   [the release verification guide](RELEASE-VERIFICATION.md).
3. Extract the LoginServer and GameServer archives into the deployment image.
   Keep Login and Game configuration separate from the public repository and
   restrict their Windows ACLs to the server operator and service identity.
   Both processes load `config/logback.xml` from their own directory.
4. Install Microsoft Build of OpenJDK 25 x64 and MySQL Server 8.4. Record exact
   versions and archive hashes in the private deployment record.
5. Create the `l2jfree_ls` and `l2jfree_gs` databases using the account setup
   appropriate to the installation. Grant each process only the permissions
   it requires. Do not use a MySQL administrative account for either server.

The LoginServer reads its JDBC driver, URL, username, and password from
`loginserver/config/loginserver.properties`. The GameServer reads its database
settings from `gameserver/config/server.properties`. Store real credentials
only in these private deployment files or a protected secret store. Never put
them in source control, public issue reports, command-line arguments, or
diagnostic bundles.

## Database safety

- Take a verified backup of both databases before installation, schema change,
  or release upgrade. Include routines, events, triggers, and binary values
  where used.
- If all tables are confirmed InnoDB, use a transaction-consistent dump. If
  any table uses a non-transactional engine, stop both servers or use a
  database-wide locking strategy so the dump is consistent.
- Restore each backup into a separate test database and confirm that the
  server can read representative account and world records before relying on
  it for rollback.
- Follow the [database migration strategy](DATABASE-MIGRATION-STRATEGY.md) for
  schema changes. The legacy installer has a destructive clean-install mode;
  never select that mode for a populated database.
- Do not allow old and new server processes to write to the same database
  during a cutover. Keep the pre-upgrade backup untouched until acceptance is
  complete.

Use a protected MySQL client option file rather than putting passwords on the
command line. Restrict that file with Windows ACLs. For example, after creating
such a private option file, a backup command can take this form:

```powershell
mysqldump --defaults-extra-file=C:\secure\l2jfree-client.cnf --routines --events --triggers --hex-blob l2jfree_ls > C:\backups\l2jfree_ls.sql
mysqldump --defaults-extra-file=C:\secure\l2jfree-client.cnf --routines --events --triggers --hex-blob l2jfree_gs > C:\backups\l2jfree_gs.sql
```

Use a different filename for each backup run and copy the completed dumps to
protected storage. Preserve the corresponding release checksum manifest and
private deployment record with the backup set.

## Start and stop

1. Start the MySQL 8.4 Windows service and confirm both target databases are
   available.
2. From the LoginServer directory, run `loginserver_launcher.bat` and wait for
   `Login Server ready`.
3. From the GameServer directory, run `gameserver_launcher.bat` and wait for
   successful registration with the LoginServer and `Server loaded`.
4. Confirm there are no database startup errors, `wrong hexid` registration
   errors, repeated reconnects, or script-load failures. The LoginServer and
   GameServer must use matching server registration data and hex ID.
5. Before maintenance, schedule a GameServer shutdown with the supported GM
   shutdown command. Wait for the shutdown log to report that data has been
   saved and players have been disconnected. The LoginServer `shutdown`
   command is available only through its optional status Telnet interface,
   which is disabled by default; if enabled, bind it privately and restrict
   access. Otherwise request the LoginServer process to stop through its
   supervising console or service manager and wait for the process to exit
   before stopping MySQL. Avoid force-terminating either JVM.

Keep the game client port (default 7777) reachable only as intended. Keep the
LoginServer/GameServer registration port (default 9014), MySQL, and any
administrative or status interfaces private. Confirm the configured bind
addresses and firewall rules before exposing the host.

## Optional OpenTelemetry export

Both runtime archives contain the pinned OpenTelemetry Java agent outside the
application classpath. Telemetry is disabled unless `L2JFREE_OTEL_ENABLED` is
set to `true` in the process environment. Before enabling it, configure
`OTEL_EXPORTER_OTLP_ENDPOINT` to a reachable OTLP collector and permit the
collector connection through the host firewall. The launchers set distinct
service names (`l2jfree-loginserver` and `l2jfree-gameserver`), export metrics
and traces, and disable the agent's log exporter so Logback remains the single
log path. The agent sanitizes database statements by default; keep that
sanitization enabled.

Use the collector's TLS and authentication settings for endpoints outside the
host. Keep credentials in protected environment variables or the service
secret store; do not add exporter headers or endpoints containing secrets to
repository files. Start with a short qualification window, inspect collector
data and JVM overhead, then keep the agent disabled until the server's normal
workload has been compared with and without instrumentation.

## Incident capture

When startup, combat, or persistence fails, record the release tag, source
revision, Windows build, Java runtime version, MySQL version, database name,
and local timestamps. Redact account names, passwords, session keys, public
addresses, and private file paths from any report shared publicly.

With Microsoft JDK 25 tools available, use `jcmd -l` to find the server PID,
then collect a thread dump and a bounded flight recording while the process is
still running. Replace `1234` with the PID reported for the target process:

```powershell
jcmd 1234 Thread.print -l > C:\incidents\gameserver-threads.txt
jcmd 1234 JFR.start name=l2jfree-incident settings=profile duration=60s filename=C:\incidents\gameserver.jfr
```

Capture LoginServer and GameServer separately. Preserve the original logs and
record the exact command and time for each capture. Treat dumps, recordings,
and database backups as private operational data.

## Upgrade acceptance

Before cutover, restore backups into a test environment, run the release
qualification checklist in [the Windows deployment notes](2026-Q4-UPGRADE.md),
and exercise LoginServer startup, account login, character selection, world
entry, representative combat, scheduled persistence, and a clean restart.
Complete target-host qualification on the exact Windows 10 / JDK 25 / MySQL
8.4 combination; GitHub Actions results alone do not qualify the deployment.

If an acceptance check fails, stop both new server processes, preserve logs,
restore both pre-upgrade database backups as a matching pair, and return to the
previous verified release image. Do not combine a database from one release
with binaries from another.
