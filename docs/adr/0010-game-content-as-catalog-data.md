# 0010. Game content ships as catalog data

- Status: Accepted
- Date: 2026-10-06

## Context

The 2.x line kept game content in about 214 SQL files that an installer ran against MySQL, plus a folder of optional mods. A mod was a set of files that an operator copied into the server tree and a SQL script that the operator ran by hand. The 3.0 image has a read-only application tree and no installer, so neither mechanism can work as it did. The accepted data design ([ADR-0004](0004-postgresql-and-the-accepted-data-design.md)) already holds the content in a `catalog` schema that is rebuilt from the image.

## Decision

1. Game content (item, NPC, and skill templates, spawns, drops, shops, teleports) is one CSV file per catalog table in the datapack. The server loads the files into the `catalog` schema at start when their SHA-256 revision differs from the one in the database.
2. A change to game content is a change to a CSV file or to the datapack, reviewed in a pull request like any other change.
3. The optional mods of the 2.x line are not shipped. A mod that should return is ported into the catalog files and the datapack.
4. A feature of the old server whose data has no home in the accepted schema is removed rather than kept without persistence. Each removal is listed in the behavior-change list.

## Alternatives considered

- Keep the mods and their SQL. They need a writable tree and a MySQL-specific script, which the image does not have.
- Add schema for every old table. It repeats the data design work that was declined.

## Consequences

- Operators cannot add content by copying files. Content is part of the image and the release.
- The installer, the update scripts, and the MySQL dump files are gone. The `catalog` schema is unlogged and never backed up.
- Edits that admin commands make to catalog rows last until the next catalog load.

## Revisit when

Operators need to add content to a running server without a new image.
