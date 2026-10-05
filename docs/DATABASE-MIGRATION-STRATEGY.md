# Database migration strategy for 2.0.0

## Purpose

Define safe, forward-only SQL changes from the 1.5.1 schema for the 2.0
line. v2.5.0 keeps this boundary. This strategy applies to both the
LoginServer database and the
GameServer world database. It preserves established account and world data.

## 2.0 release boundary

- New schema changes are numbered, immutable SQL files. Login and world schema
  changes remain separately identifiable.
- MySQL-specific persistence syntax stays in the repository layer.
- Liquibase 4.33 remains a test-only compatibility probe. Liquibase and Flyway
  are not part of the 2.0 production migration path or release runtime.
- Do not replay the historical installer updates or infer their completion from
  machine-local Java Preferences. Upgrade instructions must identify the exact
  forward SQL files for the target baseline and preserve established data.
- Flyway is introduced with PostgreSQL in Platform 3.0, not backported into
  the MySQL 2.0 line.

## Current-state findings

The datapack contains 214 SQL files: 134 at the SQL root, 69 historical
updates, 9 custom scripts, 1 add-on script, and 1 optional script. The login
module contains the `accounts.sql` and `gameservers.sql` definitions.

The legacy installer in `l2j-commons` has two important properties:

- Applied update filenames are stored in Java Preferences on the machine and
  OS user that ran the installer, rather than in the database. Moving the
  deployment or running under another Windows account loses that history.
- The legacy clean-install path drops every table in the selected database.
  GUI and interactive console use explicit confirmation. The non-interactive
  CLI now additionally requires `-confirm-clean` with the exact selected
  database name; `-m c` alone is refused. Use clean mode only for a newly
  created, verified-empty installation database, never for an upgrade.

The update scripts date from the historical release sequence and are not a
linear, checksum-tracked migration chain. They must not be replayed against a
current installation merely to create a migration ledger. The installer also
continues after SQL errors in its update path, so a local Preferences entry is
not reliable proof that an update completed successfully.

## Target model for numbered SQL changes

- Keep login and world changes separately identifiable because each database
  has its own schema and release lifecycle.
- Use numbered, immutable SQL files for changes introduced after the 2.0.0
  baseline. Do not edit a released change; add a new version.
- Keep bootstrap/reference data separate from schema migrations where it can
  be safely reloaded. Never reseed mutable player, account, economy, or world
  state during an upgrade.
- Make CI create clean MySQL 8.4 databases, apply the supported baseline and
  each numbered SQL change, then start both servers against those databases.
- Back up both production databases and rehearse the exact upgrade against a
  restorable copy before a release is accepted.

Liquibase 4.33.0 is pinned as a test-only compatibility candidate. The official
Liquibase 4.33 integration guide lists MySQL Server 8.4, and this release is
published under Apache License 2.0. A Testcontainers scenario in GitHub Actions
applies two formatted-SQL changesets to MySQL 8.4, validates the applied
history, confirms a second update adds no changesets, and checks that validation
rejects a tampered checksum. The probe ran in the successful Microsoft JDK 25
GitHub Actions build on 2026-09-30. This proves basic engine/database
compatibility for those probe changesets; it does not qualify the historic
L2JFree schema, prove an upgrade of populated databases, or authorize
production migrations. Liquibase 5.0 is not selected
because its license changed to Functional Source License, which is not the
Apache-licensed dependency line appropriate for this GPLv3 project. Production Liquibase use in 2.0 is not planned. Platform 3.0 adopts Flyway for
PostgreSQL; any production migration framework decision there is separate. See the [Liquibase 4.33 MySQL
guide](https://docs.liquibase.com/oss/integration-guide-4-33/connect-liquibase-with-mysql-server)
and the [Liquibase 4.33 license](https://central.sonatype.com/artifact/org.liquibase/liquibase-core/4.33.0).

## Baseline transition

### Fresh installations

1. Construct and review a canonical schema for each database from the supported
   2.0.0 install definitions.
2. Apply it to empty MySQL 8.4 databases in CI and verify server startup and
   required repository queries.
3. Record the verified database baseline and apply only numbered SQL changes
   released after it.
4. Classify seed rows as immutable reference data or mutable operational data;
   encode only safe reference data in repeatable, reviewed steps.

### Existing installations

1. Take and restore-test backups of both databases before any change.
2. Capture schema metadata and compare it with the canonical 1.5.1 schema.
   Include tables, columns, indexes, constraints, character sets, SQL mode,
   and stored routines where present.
3. Reconcile differences with a reviewed, installation-specific upgrade plan.
   Do not infer migration state from Java Preferences or table names alone.
4. Baseline the verified schema without executing historical updates. Stop if
   schema drift or an unknown legacy update state remains unresolved.
5. Apply only reviewed SQL changes newer than the agreed baseline. Preserve
   account rows and all player, item, character, clan, siege, and event state.
6. Run post-upgrade invariants and a representative restart/persistence check
   against the restored copy before cutover.

The first migration rollout must be rehearsed on the actual target MySQL 8.4
version with a copy from the Windows host. CI coverage on a clean schema does
not qualify a populated production database.

## Adoption gates

Do not replace the legacy installer or automatically upgrade the target host
until all of these are true:

- The canonical clean-install schema is deterministic and reproducible in CI.
- Login and world databases each have explicit baseline versions.
- MySQL 8.4 integration tests cover baseline application, forward migration,
  checksum mismatch, failed migration reporting, and restart after migration.
- Any future production migration runner is a separate decision with its own
  support, locking, licensing, checksum, and baseline review. It is not part of
  the 2.0 release scope.
- Existing-host schema differences have been inventoried and the upgrade has
  been rehearsed on a restorable database copy.
- The installer cannot invoke a destructive database reset through the normal
  upgrade path.
- Backup restoration and rollback instructions are documented and exercised.

Until these gates pass, retain the 1.5.1 installer as a manual legacy tool,
require verified backups, and do not describe its Java Preferences history as a
database migration ledger. Platform 3.0 introduces Flyway for PostgreSQL as a
separate migration system.
