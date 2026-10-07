# Changelog

All notable changes are recorded here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Platform 3.0 ships as one release ([ADR-0007](docs/adr/0007-release-policy.md)), so this file collects the changes toward `v3.0.0` under Unreleased.

## [Unreleased]

### Added

- Decision records for Platform 3.0: delivery, process model, data, network core, data access, release policy, and retirement of 2.x.
- Roadmap, risk register, and documentation index.
- Platform 3.0 project board and issue forms for work packages, decisions, and features.
- Security policy, contributing guide, code of conduct, support guide, and code owners.
- Code scanning, dependency review, OpenSSF Scorecard, and a documentation link check.
- A CI check of the datapack Python scripts with Jython.
- The Platform 3.0 walking skeleton: a `linux/amd64` image on a pinned Arch Linux base with a pinned Temurin 25 JRE, a Docker Compose stack that publishes ports 2106 and 7777, a content check, and a first-packet probe of the login port.
- Publication of `edge` and `sha-*` images with a cosign signature, an SBOM, and build provenance, and a daily job that deletes `sha-*` versions older than 30 days.
- Architecture views as diagrams in `docs/architecture.md`.
- PostgreSQL 18 for the login module: the `login` schema and its Flyway migration, a shared migration runner, a pool that sends text untyped, and integration tests against a real database.
- A decision record for database roles, schemas, and migration at start, and the report of milestone M1.
- The world data on PostgreSQL 18: the `world` schema with ten migrations, the `catalog` schema loaded from 57 CSV files with a SHA-256 revision, the report views, and a lock that keeps a second server off the database.
- A transaction scope: everything that runs on a thread inside `WorldTransaction.run` joins one database transaction, so saving a player is atomic.
- Repositories for the player, the items, and the clans, each with integration tests on a real PostgreSQL 18.
- A check that prepares every SQL statement of the compiled server (688 today) against the real schema, a schema test for the database conventions, and a data dictionary with entity diagrams generated from the migrated database and compared in CI.
- Decision record ADR-0010 for game content as catalog data, the database conventions, and a list of the behavior changes since the 2.x line.
- Monthly Dependabot updates for the image base and the compose images.
- One process for login and world: a `contract` module with `LoginPort` and `WorldPort`, admission and status as Java calls, and a `platform` module that starts both and assembles the one distribution. Architecture rules fail the build when the login module and the world module depend on each other.
- A check that compares every JDBC setter and getter of the game server with the PostgreSQL type of its parameter or column, a test that keeps the number of classes with direct database access from growing, source and bytecode rules that refuse the removed libraries and the update of a map by remove and put, and tests for code that came over without them.
- A check in the pipeline that no datapack script fails to load, tests of the script that creates the passwords, and a stand profile (an overlay, three configuration files, and a read-only database role) for an external simulator.
- The database counts and explains its statements (`pg_stat_statements`, `auto_explain`), and the deployment guide names the backup and the restore.
- A check in the pipeline that an account and its character survive a restart of the server, and the report of milestone M2.
- A repeatable start-up measurement under G1 and ZGC, with and without the AOT cache, a record of the decision for G1 without a cache ([ADR-0012](docs/adr/0012-garbage-collector-and-aot-cache.md)), and a ready line that reports the start by step.
- A configuration model with the defaults in the image and the operator's changes in one directory, and `L2JFREE_*` environment variables for the database, the bind address, and the announced address ([ADR-0011](docs/adr/0011-configuration-model.md)).

### Changed

- The vision describes a Linux image delivered with Docker Compose on Docker Desktop, Colima, or Docker Engine, with ports 2106 and 7777.
- The documentation archive carries the whole `docs/` tree.
- The Docker Compose stack runs PostgreSQL 18 instead of MySQL, with passwords generated on the first start.
- The image carries the login and the world in one service. The world connects with its own database role and the login with its own.
- The age limit of the world is applied to a world that is online. The old code returned no limit for an online world.

### Fixed

- A Python class that a datapack script stored in a global ran without its constructor, so no script prepared its state and some AI scripts failed to load. Jython is the final 2.7.4, and the pipeline fails when a script does not load.
- The save of the raid bosses on shutdown never ended, and the heroes and the Olympiad threw an exception while a map was walked and changed; the maps are updated with `put` alone.
- The collections that are walked and changed at once (the decay and attack-stance tasks, the chat definitions, the status filters, the geo editor list, the items on the ground, the warehouse and script caches) are safe to do both.
- The failed logins of one address and the announced connections of the world were counted with lost updates; a non-player character without skills failed when it got its first skill, and one with skills got an error.
- The fort siege clans were deleted with two bound values for one parameter, the dates of the changelog and of the online record were read as text, and the Hellbound variables, which are text, as numbers.
- The log of the server reached the container output only for its section headers.

### Removed

- The faction mod and the file of start values of the Olympiad.
- The dead plain-HTTP distribution repository in the root build file.
- The Windows packaging job and the 2.x release job of the pipeline.
- MySQL from the login module, its SQL files and installer scripts, and the Liquibase compatibility test.
- MySQL from the game server, the build, and the repository: the database installer, the 214 SQL files of the datapack, the update scripts, the dump-based backup, and the table optimizer.
- The optional content mods of the 2.x line.
- The internal login-to-world socket and its protocol, the registration of a game server, the `game_server` table, `servername.xml`, the hex id file, and the `register_gameserver` tool.
- The telnet status server of the login module and its four commands; only the world has a telnet status.
- The separate distributions of the login, the world, and the datapack, and their launcher scripts.
- Features whose data has no home in the schema: the factions, the community board item auction, and the archive of deleted mail.
- The experimental Rebuild id factory and the Compaction option.

## [2.5.0] - 2026-10-05

Final release of the 2.x line. Java 25 bytecode, HikariCP, Jython 2.7.5b1, Logback loaded from `config/logback.xml`, the datapack inside the GameServer archive, unit tests in CI, and the dependency upgrades named in the v2.0.0 vulnerability report. See the [release](https://github.com/l2go/l2jfree/releases/tag/v2.5.0).

[Unreleased]: https://github.com/l2go/l2jfree/compare/v2.5.0...HEAD
[2.5.0]: https://github.com/l2go/l2jfree/releases/tag/v2.5.0
