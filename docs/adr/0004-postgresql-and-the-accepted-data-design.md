# 0004. PostgreSQL 18 and the accepted data design

- Status: Accepted
- Date: 2026-10-06

## Context

The 2.x line stores data in MySQL 8.4. Platform 3.0 moves to PostgreSQL 18, which is supported by the community until 14 November 2030. A complete data design for PostgreSQL already exists as input to the implementation: separate schemas, forward-only migrations, a catalog that can be rebuilt from the image, and a documented data model. The maintainer decided not to redesign it.

## Decision

1. The database is PostgreSQL 18 with the pgjdbc driver and HikariCP.
2. Data is split by lifetime into three schemas. `login` holds accounts. `world` holds characters, items, clans, and other player state and is the only data that needs a backup. `catalog` holds templates, NPCs, skills, spawns, and zones and can be rebuilt from the image.
3. Flyway applies numbered forward-only migrations to `login` and `world`, one history table per schema. Each module migrates its own schema at start.
4. The catalog source is a set of CSV files in the repository. The catalog revision is the SHA-256 of those files. At start the server loads the catalog in one transaction when the revision differs, before it accepts players.
5. The `login` and `world` modules use separate database roles that cannot read each other's schema.
6. The data design is accepted as input and is carried over as it stands. Changes after that follow ordinary review and a new numbered migration.

## Alternatives considered

- Stay on MySQL 8.4. It limits transactional DDL, constraints, and the catalog loading path.
- Redesign the model from the old SQL files. It repeats finished work and was declined.

## Consequences

- There is no migration path from 2.x databases. A 3.0 installation starts clean (see decision 0008).
- Conversion from MySQL types is already reflected in the accepted design and is not redone.
- The `catalog` schema is disposable, so a backup is `pg_dump` of the `login` and `world` schemas only.

## Revisit when

PostgreSQL 18 nears the end of support, or a second world or a second database host is required.
