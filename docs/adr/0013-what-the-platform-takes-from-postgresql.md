# 0013. What the platform takes from PostgreSQL, and what it leaves

- Status: Accepted
- Date: 2026-10-07

## Context

The vision of Platform 3.0 listed what PostgreSQL would change in the server: a transaction with `ON CONFLICT` and `RETURNING`, an advisory lock for a second login of an account, queues claimed with row locks, an exclusion constraint on the character name, partitioned logs, new identifiers as `uuidv7` or identities, `synchronous_commit` off for logs, and the catalog loaded with `COPY`. The list was written before the decision for one process ([ADR-0003](0003-one-process-two-modules.md)) and before the data design was accepted ([ADR-0004](0004-postgresql-and-the-accepted-data-design.md)). Some entries describe a server with several processes or with logs in the database, which this platform does not have.

The same review found two further statements that the code does not follow word for word: the default network transport, and the path of the catalog.

## Decision

Each entry is kept where it gives the platform a property, and left where it would add machinery for a case that does not exist.

| Entry | Decision | Why |
|---|---|---|
| A player, items, and adena in one transaction, with `ON CONFLICT` | Kept. `WorldTransaction` makes the save of a player one transaction, and statements upsert with `ON CONFLICT`. `RETURNING` is used where the code needs the stored row. | The property is atomic save. |
| Advisory lock for a second login of an account | Left. One process owns the world, the login session and the in-memory set of accounts in the world serialize a second login, and `WorldLock` (an advisory lock on the database) stops a second server on the same world database. | A lock per account protects against a second process that this platform does not run. |
| Queues claimed with `FOR UPDATE SKIP LOCKED` | Left. The manor, mail, and deferred tasks run in the one process, from memory. | Row locks matter when several consumers read one queue. |
| Exclusion constraint on the normalized name | Replaced by `citext` with a unique constraint. | The names compare without case, as MySQL compared them, and the constraint is simpler than an exclusion constraint for the same effect. |
| Logs as declarative partitions | Left. The logs are files of the logging configuration, and the one table that records GM actions (`gm_audit`) is small and durable. | There is no log table to partition. |
| `uuidv7` or identity for new identifiers | Identities where the table makes its own key. The ids of players, items, and clans stay integers from the in-memory allocator, because the client protocol carries them as 32-bit numbers. | The protocol fixes the type. |
| `synchronous_commit` off for logs only | Left. There are no log tables, so everything commits synchronously. | Nothing to relax. |
| Catalog loaded with `COPY` | Kept. | One transaction, in seconds. |

Two clarifications of earlier records, which this record supersedes where they differ:

1. **Transport.** The default value of `l2jfree.network.transport` is `auto`: the first of `io_uring`, `epoll`, and `nio` that the host permits. The default security profile of Docker blocks `io_uring`, so a container without a custom profile runs on `epoll`, as [ADR-0002](0002-linux-image-delivered-with-compose.md) states. Setting the value to `epoll` forces it.
2. **Catalog path.** The defaults of the image name the datapack and the catalog inside the image. The operator directory ([ADR-0011](0011-configuration-model.md)) can override any key, including these two. The platform does not offer the choice and does not support a catalog outside the image.

## Alternatives considered

- **Build each entry.** A lock per account, row-locked queues, and partitioned logs are real PostgreSQL features, but each needs a use that exists: a second process, several consumers, or log tables. Building them first would add schema and code that nothing reads.
- **Keep the vision as written.** The list would claim properties that the platform does not have.

## Consequences

- The vision lists what the platform does take from PostgreSQL, and this record holds what it leaves, so the two agree.
- A second world process, which the vision calls a later product, brings back the lock per account and the row-locked queues. That decision is its own record.

## Revisit when

A second process reads the same database, a table of logs is added, or the client protocol allows wider identifiers.
