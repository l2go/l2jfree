# Platform 3.0 Vision

## Purpose

This document defines Platform 3.0, the infrastructure generation after the
2.0.0 line. Release 2.0.0 still ships on Windows 10, the Microsoft Build of
OpenJDK 25, and MySQL 8.4. Platform 3.0 replaces that platform. It keeps the
Lineage II client protocol and the gameplay rules, and it changes the operating
system, the JDK distribution, the database, and the shape of the deployment.

The name 3.0 marks a platform change. It is a new host, a new JDK build, and a
new database, assembled as one process.

Read this document in two layers. The next section is what release 2.0.0
finishes. Everything after it is the 3.0 vision and starts only from that
finished 2.0 release.

## What 2.0.0 finishes

Release 2.0.0 stays on its current platform. It does not become Platform 3.0.

| Layer | 2.0.0 stable |
|---|---|
| Acceptance | Windows 10 x64, Microsoft Build of OpenJDK 25, MySQL 8.4 |
| Processes | Login and Game remain two processes. The monolith is not moved here |
| Code | Bytecode 25. Standard JDK APIs only. No dependency on the JDK vendor |
| Data | JDBC and HikariCP 7.1. New schema changes are numbered SQL files. The MySQL dialect stays inside the repository layer. Liquibase is not the release journal. Flyway is not added |
| Scripts | Java scripts are compiled in CI into the Game delivery, and startup loads bytecode. Python is Jython 2.7.5b1 in that same delivery, one checksum manifest with Game |
| Logging | SLF4J 2.0.20 and Logback 1.5 to stdout and named operational files. JUL uses a one-way bridge; Commons Logging is removed |
| IRC | The admin bridge is not a 2.0 feature, and irclib is not a dependency |
| Launch | A `java` command, with configuration outside the archive |

A 2.0 release candidate is blocked only by qualification of what is already
landed: startup, save, restart, database loss, and shutdown on Windows,
Microsoft JDK 25, and MySQL 8.4. Stable waits for confirmation and for the
table above.

Release 2.0.0 does not include Linux as the acceptance host, Temurin as the
acceptance JDK, PostgreSQL, one process, Netty, Generational ZGC, the AOT
cache, a Javolution or Trove replacement, GraalPy, or a datapack revision
separate from Game.

## Fixed platform

| Layer | Decision |
|---|---|
| Operating system | Linux x64. aarch64 is built the same way and is not the acceptance host. |
| JDK | Eclipse Temurin 25, the current long-term support release. The next LTS is JDK 29, expected in September 2027. |
| Database | PostgreSQL 18, current minor of that major line. On 30 September 2026 the minor is 18.6. Community support runs until 14 November 2030. |
| Deployment | One image, one process, two client ports. Modules `login` and `world`. The datapack is source that the image contains. |
| Verification | GitHub Actions and the acceptance Linux host. Developer workstations do not build, test, or run the server. |

PostgreSQL does not publish a separate long-term-support edition. The project
supports each major version for five years. PostgreSQL 18 is the newest line
inside that window. PostgreSQL 19 was still a beta on 30 September 2026 and is
outside this platform. Moving to a later major version is a separate decision
near the end of PostgreSQL 18 support.

## Why this platform

Linux, Temurin 25, and PostgreSQL 18 belong together. Each piece is a version
move. The combination changes networking, startup, concurrency, and data
integrity.

- Linux provides `io_uring`. A Netty 4.2 transport can use it. That path does
  not exist on Windows, which is why replacing the current network core was a
  weak trade on the 2.0 host.
- JDK 25 provides virtual threads, scoped values, and the Project Leyden AOT
  cache. One process warms once, without a native image. Generational ZGC
  holds pauses on a long-lived process with allocation spikes.
- PostgreSQL 18 provides transactional DDL, `INSERT ... ON CONFLICT`, advisory
  locks, `SKIP LOCKED`, exclusion constraints, partitioned logs, and `COPY`.
  These are data properties. They are not a wrapper around the MySQL schema.

Eclipse Temurin and the Microsoft Build of OpenJDK are both OpenJDK 25 HotSpot
builds that pass the JCK. Neither includes the Graal compiler. The Microsoft
build matched the Windows 2.0 host. On Linux, Adoptium publishes archives,
container images, deb and rpm packages, x64 and aarch64, including Alpine.
Server behavior does not change with the vendor string. `java.vendor` becomes
Eclipse Adoptium. Oracle JDK 25 updates move to a different license after
autumn 2028. Temurin does not have that split.

The server stays a Java MMORPG server. Quarkus, Spring, and a reactive database
stack are not added. Virtual threads plus JDBC cover the workload that used to
justify a database event loop. Kubernetes is not part of the platform. One
systemd unit and one compose file run the image and PostgreSQL 18.

## Modular monolith

The Lineage II client opens two connections. It speaks to the login port,
receives the world list and a session key, then opens a second connection to
the world port. That is a property of the client protocol. It does not require
two programs, two images, or two JVMs.

Today the login server and the game server are two products. Each has its own
`main`, and they admit a player through an internal socket protocol. On a
single-world host that socket connects a process to itself over loopback. Two
heaps and two deliveries exist for a topology this platform does not ship: one
login fronting several worlds.

Platform 3.0 is a modular monolith of one world.

One Temurin 25 process listens on both client ports. The `login` module owns
the login port, accounts in the `login` schema, and session-key issue. The
`world` module owns the world port, the simulation, and the `world` and
`catalog` schemas. Admission is a Java call in the same process: login marks
the account busy and passes the key, and world checks that key in memory. The
internal login-to-world TCP connection leaves the release. The client still
sees two addresses.

The module boundary holds. `world` does not read passwords and does not write
the `login` schema. `login` does not see the catalog, characters, or world
packets. The shared session-key and world-list types live in a small contract
module.

One process fits because there is one world. A restart already drops play.
A login process that stays up during that restart does not keep the world
session. A shared heap and a shared collector are an accepted cost: ZGC and the
AOT cache are configured once, and a world deadlock is visible in one JFR
recording. A second world on the same login is a later product. The same Java
admission interface could then grow a socket adapter, and the second world
would be another process of the same image. Platform 3.0 does not build that
adapter in advance.

## Stack

| Area | Choice | Reason |
|---|---|---|
| Memory | Generational ZGC | The process lives for a long time. Pause time matters more than G1 peak throughput. On JDK 25, ZGC runs in generational mode. |
| Startup | JDK 25 AOT cache | Warmup of the single process is stored in the image. A native image is the wrong tool for a long-running HotSpot server. |
| JDBC driver | pgjdbc 42.7.13 | BSD license, ordinary JDBC, current PostgreSQL coverage. |
| Driver candidate | pg-java | August 2026 pre-release. Blocking API that avoids pinning virtual threads. It enters a release only after full JDBC coverage and a comparison with pgjdbc. |
| Pool | HikariCP 7.1 | The pool stays small. Virtual threads wait for a connection. One connection per thread overloads PostgreSQL. |
| Migrations | Flyway 13, PostgreSQL module | Plain SQL in the repository. The PostgreSQL module is Apache 2.0. Transactional DDL makes a migration atomic. Liquibase 5 is declined on license terms. |
| Network | Own protocol codec on Netty 4.2. Transport is `io_uring`, with epoll as the fallback | A replay of real packets accepts the port: encryption, packet order, disconnect, p99, allocations. A losing table is fixed in the Netty port. The previous core is not in the 3.0 release. |
| Logging | SLF4J 2.0.20 and Logback 1.5 to stdout and named operational files | The 2.0 line completes the backend migration. Platform 3.0 retains the unified backend and named audit/gameplay channels. |
| IRC | Not included | The admin bridge and irclib are removed on the 2.0 line. Platform 3.0 does not add an IRC client. |
| Threads | Virtual threads for login, JDBC, admin, and background I/O. Scoped values replace `ThreadLocal` | World ticks, knownlist, and AI stay on platform threads. The promotion check includes a pinning report. |
| Scripts and catalog | Built into the image in CI | Datapack sources are not a release archive. Java scripts ship as bytecode. Python is one runtime inside the image. ECJ in the running process is a development tool. |
| Python | Jython 2.7 final inside the image | Same line as the 2.0.0 choice, Jython 2.7.5b1. The final release drops the beta. GraalPy is excluded: the datapack stays on Python 2, and GraalPy speed requires GraalVM. |
| Observability | Always-on JFR, OpenTelemetry Java agent, `pg_stat_statements`, `auto_explain` | An incident is captured without a restart: JVM recording, pool, task queue, slow SQL. |
| Delivery | Non-root image, SBOM, cosign signature, checksum | CI uses `eclipse-temurin:25` and `postgres:18.6`. PostgreSQL patch releases stay on the 18 line. |
| Tests | JUnit 6, AssertJ, Mockito, Testcontainers with PostgreSQL 18 | The same major line as the acceptance host. |

Javolution 5.4.1 leaves the release. Cold paths use the JDK collections.
Hot paths shown by JFR use the JDK as well: `ArrayList`, `ArrayDeque`, and
`ConcurrentHashMap`. No replacement structural library is added.

Trove 2.1.0 leaves the release. fastutil replaces it only where JFR shows
primitive maps and sets on world loops. Every other site uses the JDK
collections. Both replacements happen after the Linux flight recording, not
during the 2.0 Windows acceptance.

## Data

Data is split by lifetime. The old datapack archive mixed all of it: XML and
CSV stats, HTML, zones, geodata, 449 Python scripts, about 175 Java scripts,
dialog pages, catalog SQL for items, NPCs, and spawns, character tables, an
`updates/` history, and side mods under `optional/`. Operators had to match
that archive to the game-server build.

Platform 3.0 ships one image. The login, world, and catalog revisions are the
image digest. The datapack tree remains the place where content is written. CI
bakes it into the image. Operators do not unpack it.

| Data | Owner | Where it lives | On a new image |
|---|---|---|---|
| Accounts | `login` | Schema `login`, Flyway | Forward only. World content never lands here. |
| Characters, items, clans, mail, quest progress | `world` | Schema `world`, Flyway | Forward only. A catalog load does not overwrite or delete it. |
| Item templates, NPCs, skills, spawns, zones, HTML, geodata, scripts | `world` | Schema `catalog` and files inside the image | Startup compares the catalog revision with the image. A different revision is replaced wholesale with `COPY`. Player state stays. |

The `catalog` schema can be rebuilt from the image. The `world` schema cannot:
it is the backup. The `login` backup is accounts. Geodata and HTML are layers
of the same image. A large layer is still addressed by the image digest.

Scripts are not compiled at server start. CI compiles Java scripts against the
server classpath, and the image loads bytecode. Python runs on Jython 2.7
final in that image. GraalPy is not on the classpath and not in CI. A mod under `optional/` is either part of
the image build or it is absent.

The historical `sql/updates` chain and the installer that records progress in
Java Preferences end. `login` and `world` move by numbered Flyway SQL. The
catalog does not replay years of incremental updates. The new image brings its
catalog whole. The process refuses players when the catalog revision does not
match the image. The `login` module does not read the catalog.

## What PostgreSQL changes in the server

The schema is not the MySQL schema with another driver.

- A character, an item, and adena are written in a transaction with
  `INSERT ... ON CONFLICT` and `RETURNING`.
- A second login of the same account is closed with an advisory lock, in
  addition to the in-memory session.
- Manor, mail, and deferred-task queues are claimed with
  `SELECT ... FOR UPDATE SKIP LOCKED`.
- Character-name uniqueness is an exclusion constraint on the normalized name.
- Chat and item logs are declarative partitions.
- New identifiers are `uuidv7` or `bigint GENERATED AS IDENTITY`. Existing
  integer ids remain while the client protocol requires them.
- `synchronous_commit` is off only for logs. Inventory and characters commit
  synchronously.
- The image loads its catalog with `COPY`.

The MySQL translation is its own work: `tinyint`, `datetime`, `enum`,
backticks, `ON DUPLICATE KEY UPDATE`, and `AUTO_INCREMENT`. It is not hidden
inside the pool change.

## Sequence

These steps start from a finished 2.0.0 stable. They do not repeat the logging,
IRC, Jython 2.7.5b1, or CI script work from that release.

1. Move CI and the image to Linux and Temurin 25. Remove Windows from the
   acceptance gate.
2. Bring up PostgreSQL 18.6 beside the existing database: Flyway,
   Testcontainers, and a vertical slice of account and character save/restart.
   MySQL stays until that slice passes.
3. Move the remaining JDBC paths to PostgreSQL. The pool has a wait ceiling
   and metrics.
4. Enable the AOT cache and ZGC in the image. Compare time-to-accept-players
   with the previous start.
5. Build the catalog and scripts into the image. Remove the separate datapack
   archive. Split `login`, `world`, and `catalog`.
6. Collapse login and world into one process and replace the internal
   admission socket with the in-process contract.
7. Move the transport to Netty 4.2 on `io_uring`, with epoll as the fallback.
   Keep the protocol codec. Accept the port with a replay of real packets. A
   losing table is fixed in the Netty port. The previous core is not released.
8. After that flight recording, replace Javolution with JDK collections and
   Trove with fastutil on the measured primitive hot paths. Move every other
   site to the JDK collections.
9. Release Platform 3.0 as one image plus PostgreSQL 18. The MySQL migration
   record covers `login` and `world`. The catalog arrives inside the image.
   The image no longer contains Commons Logging, irclib, Javolution, Trove,
   GraalPy, or the previous network core.

## Outside this platform

- PostgreSQL 19, until a later decision changes the major line.
- The Microsoft Build of OpenJDK and Oracle JDK.
- Windows as a delivery target.
- MySQL.
- GraalPy, GraalVM, and any second Python interpreter. Jython 2.7.5b1 is replaced by the Jython 2.7 final release.
- pg-java while it is a pre-release.
- Quarkus, Spring, R2DBC, and Kubernetes.
- Virtual threads on world ticks.
- The previous network core, Commons Logging, irclib, Javolution, and Trove.
- A separate datapack archive, a shared content volume, or a catalog path the
  operator chooses.
- A second world process and the socket adapter that would serve it.

## Relationship to 2.0.0

The 2.0.0 infrastructure vision remains the contract of the current release
line: Windows 10, Microsoft Build of OpenJDK 25, MySQL 8.4, two processes, and
a versioned datapack. Platform 3.0 does not rewrite that release. Work that
makes this vision cheaper lands on the 2.0 line only when it also satisfies
the 2.0 contract: JDK 25 APIs with no vendor dependency, SQL kept behind
repositories, numbered SQL files, Java scripts compiled in CI into the game
delivery, and Jython 2.7.5b1 as the only Python runtime. The 2.0 line uses
SLF4J 2.0.20 with Logback 1.5 instead of Commons Logging, and it does not
ship an admin IRC bridge or irclib. Platform 3.0 moves the Jython pin to the 2.7 final
release and completes the JDK, fastutil, and Netty replacements above.
