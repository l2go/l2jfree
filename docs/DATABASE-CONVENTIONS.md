# Database Conventions (PostgreSQL 18)

These rules define how the Platform 3.0 database looks. They are the accepted data
design of [ADR-0004](adr/0004-postgresql-and-the-accepted-data-design.md). How a module
connects and migrates is in [ADR-0009](adr/0009-database-roles-schemas-and-migration.md).
`SchemaQualityPostgresTest` checks the rules marked **[checked]** against a freshly
migrated database. A new table that breaks a rule fails the build.

The goal: a developer, an operator, or a game designer who opens the database for
the first time understands what every table and column means without reading
the server code.

## 1. Schemas

| Schema | Owner | Content | Lifecycle |
|---|---|---|---|
| `login` | login module | Accounts | Flyway, forward only |
| `world` | world module | Game state: players, items, clans, sieges, events, plus the small reference tables the state points to (race, class, item location) | Flyway, forward only. This schema is the backup |
| `catalog` | world module | Game content from the image: item, NPC and skill templates, spawns, drops, shops, teleports | Rebuilt from the image when its revision changes. `UNLOGGED`. Never backed up |
| `report` | nobody writes | Read-only views for people: `player_overview`, `player_inventory`, `clan_overview`, … | Recreated after every catalog load |

- No object lives in `public` except extension objects (`citext`) **[checked]**.
- Every schema has a `COMMENT ON SCHEMA` **[checked]**.
- Code uses unqualified names. The pool sets `search_path` (`world, catalog, public` for
  the world module, `login, public` for the login module; `public` holds only `citext`).
- `world` never has a foreign key into `catalog`: the catalog is replaced
  wholesale. Such columns end in `_template_id` or name the catalog table
  (`item_template_id`, `npc_template_id`, `skill_id`). Their comment names the target
  table **[checked]**.

## 2. Names

- `snake_case`, lower case, ASCII **[checked]**.
- Tables are singular nouns: a row is one thing (`player`, `item`, `clan_war`) **[checked]**.
- Child tables start with the parent name: `player_skill`, `player_shortcut`,
  `clan_skill`.
- Words are spelled out. Allowed abbreviations are established game or technical
  terms only: `id`, `hp`, `mp`, `cp`, `sp`, `exp`, `pvp`, `pk`, `npc`, `gm`, `x`, `y`, `z`,
  `crest`, `url`, `ip` **[checked]** (forbidden list: `char`, `loc`, `cur`, `privs`, `obj`,
  `lvl`, `num`, `cnt`, `tmp`, `desc`, `info`).
- No typos carried over from MySQL: `privilleges` → `privileges`, `maried` → `married`,
  `nobless` → `is_noble`.
- A reserved word is never a name, so no identifier needs quotes **[checked]**.

## 3. Keys and references

- Every table has a primary key **[checked]**.
- An entity table has `id` as its key. A link or child table has a composite key
  of its reference columns (`player_skill (player_id, class_index, skill_id)`).
- A reference column is `<entity>_id` and has a foreign key to that entity **[checked]**,
  except catalog references (section 1).
- Delete behavior:
  - `ON DELETE CASCADE` when the row is part of its parent (a player's skills,
    shortcuts, quests; a clan's skills);
  - `ON DELETE SET NULL` for an optional link (a player's clan);
  - `ON DELETE RESTRICT` (the default) otherwise.
- Every foreign key has an index that starts with its columns **[checked]**.
- Game object ids (players, items, clans, pets) share one id space with the
  runtime objects (spawned NPCs, dropped items), because the client protocol
  does. The in-memory `IdFactory` hands them out and reuses released ids; the
  database stores them as `integer` without a default. At startup the factory
  reads only the entity key columns (`player.id`, `item.id`, `clan.id`, `pet.item_id`,
  `ground_item.id`, `couple.id`) and the crest ids, which share the same range.
  Other surrogate keys are `bigint GENERATED ALWAYS AS IDENTITY`.
- Constraint names are the PostgreSQL defaults: `<table>_pkey`,
  `<table>_<column>_fkey`, `<table>_<column>_key`, `<table>_<column>_check`. Index
  names follow the same pattern, `<table>_<columns>_idx` **[checked]**.

## 4. Types

| Meaning | Type | Rule |
|---|---|---|
| Identifier | `integer` for game object ids (protocol is 32-bit), `bigint` for identities | |
| Count, amount, money | `bigint` or `integer` with `CHECK (… >= 0)` | No unsigned types exist in PostgreSQL. The check replaces them |
| Flag | `boolean NOT NULL` | Name starts with `is_`, `has_`, `can_`, or reads as a verb phrase (`wants_peace`) **[checked]**. No `'true'`/`'false'` text **[checked]** |
| Moment in time | `timestamptz` | Name ends with `_at` (`created_at`, `delete_at`, `respawn_at`) **[checked]**. "Not set" is `NULL`, never `0` |
| Calendar date | `date` | Name ends with `_on` (`birthday_on`) |
| Duration | `bigint` with a unit suffix | `_ms` or `_s` (`jail_remaining_ms`, `online_time_s`) **[checked]** |
| Fixed set of values | `text` + `CHECK (… IN (…))` | The values are the Java enum constant names, so `grep` finds them in code |
| Name compared without case (player, clan, alliance, account) | `citext` | With `UNIQUE` where the game requires it |
| Free text | `text`, or `varchar(n)` when the client limits the length | |
| Coordinates | `integer` | `x`, `y`, `z`, `heading` |
| Rate or percentage | `numeric(p,s)` or `real` | The comment gives the unit |

- `NOT NULL` is the default. A nullable column has a comment that says what `NULL`
  means **[checked]**.
- Defaults live in the table, not only in the code.

## 5. Documentation inside the database

- Every table and every column except `id` has `COMMENT ON` **[checked]**.
- A comment is one or two plain English sentences: what the value is, its unit,
  and what `NULL` means. For a catalog reference it also names the target, for
  example `Item template (catalog.item_template.id).`
- `docs/database/` holds the data dictionary and the ER diagrams (one per
  schema). A generator writes them from the migrated database. CI fails when
  the committed files differ from the generated ones.

## 6. Migrations

- Flyway 13, one history table per schema (`login.flyway_schema_history`,
  `world.flyway_schema_history`).
- The baseline is split by domain and ordered with sub-versions:
  `V1_01__reference.sql`, `V1_02__player.sql`, `V1_03__item.sql`, … Each file
  starts with a comment block that says what the domain is.
- A later change is a new file, `V2__<what_changes>.sql`. A released file is never
  edited.
- The catalog is not migrated. `catalog/schema.sql` and the CSV files in the image
  define it. The loader drops and recreates the schema in one transaction.

## 7. Queries in code

- SQL is written in upper-case keywords and lower-case names:
  `SELECT name, level FROM player WHERE id = ?`.
- `SELECT *` is not used in new or rewritten code.
- Moments are bound with `setObject(i, OffsetDateTime)` through the helper
  `Sql.setMoment`/`Sql.getMoment` (epoch milliseconds in Java, `NULL` for "not
  set"). Flags are bound with `setBoolean`/`getBoolean`.
- An operation that changes several rows that belong together (save a player,
  move an item, finish a trade) runs in one transaction.

## 8. Report views

- Schema `report`, one view per question an operator asks, columns in words:
  `report.player_overview (player_name, account_name, race, class, level, clan_name,
  is_online, last_login_at)`.
- Views never expose password hashes or other secrets.
- Role `l2_readonly` can read `report` and `catalog` only.
- The views are in `l2jfree-core/src/main/resources/db/report/views.sql`:
  `player_overview`, `player_inventory`, `clan_overview` and `item_name`. The
  server (`ReportViews`) creates them again on every start and after every
  catalog load, and grants them and the catalog to `l2_readonly` when that
  role exists. The operator creates the role; the views run with the rights of
  the world role, so `l2_readonly` needs no access to `world`.
