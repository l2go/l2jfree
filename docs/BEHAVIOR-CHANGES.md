# Behavior changes since the 2.x line

Platform 3.0 keeps the game rules and the client protocol. Moving the data to PostgreSQL forced a few changes that a player, an operator, or a script author can notice. This is the complete list for the world data. A change that is not here was not made on purpose.

## Features that are gone

A feature is removed when the accepted data design has no table for it ([ADR-0010](adr/0010-game-content-as-catalog-data.md)).

| Feature | What is gone |
|---|---|
| Factions | Joining a faction, faction points, and faction quests. The faction managers load nothing and keep empty lists. NPCs of the faction types do nothing when clicked |
| Community board item auction | Listing, bidding, buying, and creating item lots on the board. The board shows that the auction is not available. The clan hall auction is not affected |
| Archive of deleted mail | Deleted letters are no longer copied to an archive. The option `MailStoreDeletedLetters` is removed |
| Old crest files | Crests are read from and written to the `crest` table, not to `.bmp` files. The command `admin_cache_crest_fix` is removed |
| Optional content mods | The thirteen mods of the 2.x line, which copied files into the server tree and ran SQL by hand |
| Database installer, SQL update scripts, table optimizer, and the dump-based backup | A backup is a `pg_dump` of the `login` and `world` schemas, taken by the operator |
| Id factory options `Compaction` and `Rebuild` | The schema's foreign keys make rewriting ids impossible. `BitSet`, `Stack`, and `Increment` remain |
| Admin command `admin_process_auction` | It processed the removed item auction |

## What a player can notice

- A character name, a clan name, and an account name match without regard to case everywhere, because the columns are case-insensitive. A second character whose name differs only in case cannot be created.
- Saving a character is one transaction. If any part fails, nothing is saved, instead of a part-saved character.
- Deleting a character removes everything that belongs to it, including its pet and what the pet carries. Deleting a pet's control item removes the pet and what it carries.
- A mail subject is cut to 12 characters, the recipient list to 200, and the message to 3,000, because the columns have those limits. The old client field allowed a longer subject.
- A pet name is limited to 16 characters.
- The alliance crest of a clan is kept across a restart. Before, joining an alliance lost it.
- A large clan crest can be replaced and deleted. Before, the delete usually failed.
- A friend pair is stored once. A war between clans is recorded once.
- A raid boss or a grand boss that has no saved state spawns with full health.

## What an operator can notice

- The server needs PostgreSQL 18, and a 2.x database is not read ([ADR-0004](adr/0004-postgresql-and-the-accepted-data-design.md)). A second server on the same world database stops at start.
- Game content comes from the catalog files of the release and is loaded when their revision changes. An edit made by an admin command to a catalog row lasts until the next catalog load.
- A merchant restock interval is kept in seconds. A negative price set by an admin command for a shop item means the item's reference price.
- A statement that fails inside a transaction rolls back the whole block and the method continues after it, with the error in the log.
- The telnet GM check, the jail command, and the item give command use the new names. An unknown character name is reported instead of raising an error.

## What a script author can notice

- Python scripts that read the database use the new table and column names. The three scripts that did are converted.
- The order of rows that a query returns can differ where the old query had no `ORDER BY`. The loaders that need an order now ask for it.
- A moment in the database is a `timestamptz`. The Java code still uses epoch milliseconds, and `0` in the code is `NULL` in the table.

## Checked by tests

Each repository has an integration test on a real PostgreSQL 18. A check prepares every SQL statement of the compiled server against the real schema. The schema test checks the conventions in [DATABASE-CONVENTIONS.md](DATABASE-CONVENTIONS.md), and the data dictionary in [docs/database](database/world.md) is generated from the migrated database and compared in CI.
