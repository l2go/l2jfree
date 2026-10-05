# L2JFree Infrastructure Stack

> **Retired.** This document describes the 2.x line. v2.5.0 is its final release ([ADR-0008](adr/0008-retire-the-2x-line.md)). See the [documentation index](index.md).

This is the implementation map for the 2.0 infrastructure vision. v2.5.0 is
the current release. Platform constraints are fixed. A row's status cell says
whether that choice is in v2.5.0 or still waits for Platform 3.0.

The Python runtime is Jython 2.7.5b1. Platform 3.0 moves to the final Jython
2.7 release. GraalPy and a Python 3 port are excluded. Jython 2.2.1 is the
baseline this line replaced.

## Fixed platform

| Area | Approved value |
|---|---|
| Operating system | Windows 10 x64 |
| Java runtime and build JDK | Microsoft Build of OpenJDK 25 |
| Java compiler target | Java 25 bytecode; application code uses standard JDK APIs without vendor-specific dependencies |
| Database | MySQL Server 8.4 |
| JDBC driver | MySQL Connector/J 26.7.x |
| Application topology | Separate LoginServer and GameServer processes; scripts ship with the GameServer revision and checksum manifest |
| Build and verification | Maven Wrapper and GitHub Actions; no workstation builds, tests, or server runs |

## Approved modernization stack and qualification

| Area | Current baseline | Target or candidate | Promotion evidence | Status |
|---|---|---|---|---|
| Java platform | Java 8-era source and runtime assumptions | Java 25 source and bytecode on Microsoft OpenJDK 25 | Linux production build, Windows packaging, and target-host runtime qualification | Java 25 compilation is released in v2.5.0. CI compiles and runs the unit tests. The maintainer ran this tree on Windows 10. |
| Persistence | JDBC in both servers after issue #52 | Explicit JDBC repositories and bounded transactions | Operator qualification on MySQL 8.4 for account, world persistence, reconnect, shutdown, and restart; automated integration coverage | JDBC baseline is released. MySQL 8.4 integration tests run in the CI integration job. The maintainer ran v2.5.0 on Windows 10. |
| Connection pool | c3p0 was removed | HikariCP 7.1.x | Pool exhaustion, reconnect, idle validation, lifecycle, metrics, and shutdown checks | Implemented; full target-host workload qualification remains |
| Schema evolution | Historical SQL installer and existing databases | Numbered forward SQL files; MySQL syntax stays in the repository layer; Liquibase remains test-only | Operator qualification uses disposable MySQL 8.4 and a copy of an existing schema | No production migration framework is selected. Liquibase stays a test-only probe. Flyway belongs to PostgreSQL in Platform 3.0 |
| Java datapack scripts | Runtime Java compilation through ECJ 4.4.2 | Compile datapack sources during CI packaging; carry compiled bytecode with the matching GameServer revision; remove ECJ from runtime archives | CI verifies source-to-bytecode coverage, revision match, merged archive contents, and absence of ECJ | v2.5.0 ships the bytecode inside the GameServer archive and does not compile Java scripts at startup. ECJ is not a dependency. |
| Python datapack scripts | Jython 2.2.1 and Python 2 scripts | Jython 2.7.5b1 only for 2.0; final Jython 2.7 release for Platform 3.0 | Operator qualification of Java interop, embedded bridge, and representative quest/AI/event/task behavior on Windows | Jython 2.7.5b1 is pinned and ships in the GameServer archive. The `jython-syntax` CI job compiles every datapack Python script with that runtime and checks Java interop. No quest or AI behavior suite exists, so script behavior is accepted on the target host. |
| Network I/O | Custom MMO transport and protocol implementation | Retain the existing core in 2.0; Netty 4.2 with `io_uring` and epoll is Platform 3.0 work | Preserve packet encryption, ordering, and disconnect behavior on the Windows acceptance host | No network replacement experiment is planned for 2.0 |
| Concurrency | Existing executor, FIFO, scheduled task, and platform-thread model | Retain in 2.0; evaluate virtual threads only on the Platform 3.0 Linux process | Repeat target-host gameplay and deadlock scenarios | 2.0 fixes known world-lock issues and does not change thread model |
| Collections | Javolution 5.4.1 and Trove4j 2.1.0 | Replace with JDK collections and fastutil on measured paths in Platform 3.0 | Linux JFR and allocation/throughput measurements for affected code, including hot world loops | Deferred until Platform 3.0 Linux qualification |
| Logging | Mixed JUL and SLF4J handlers during transition | SLF4J 2.0.20, Logback 1.5, one-way JUL bridge; no Commons Logging bridge | Preserve named audit/gameplay channels; verify rotation, severity, exceptions, file permissions, and secret redaction | Shipped in v2.5.0: unified Logback backend, named channels, and `config/logback.xml` in both archives. CI checks the packaged runtime jars. Rotation and file permissions remain an operator check on the host |
| IRC integration | irclib 1.10 | The admin IRC bridge and irclib are removed | The archive check rejects an irclib jar | Removed |
| JVM diagnostics | Existing logs and deadlock detector | JFR plus optional OpenTelemetry Java agent 2.32.0 with OTLP metrics and traces | Capture JVM, JDBC, HikariCP pool, startup, script, scheduler, and packet paths; record instrumentation overhead and incident artifacts | Agent is packaged separately and stays disabled until an operator enables it. Version 2.32.0 replaces 2.26.1, which had the v2.0.0 scan findings. |
| Test stack | Existing Maven tests and legacy qualification checks | Refactored and expanded JUnit 6.1.x, AssertJ, Mockito, Testcontainers 2.0.x, MySQL 8.4 | Deterministic GitHub Actions unit and integration runs | v2.5.0 runs the unit suite on Linux and Windows. MySQL tests run in a separate Linux integration job. v2.0.0 skipped tests. |
| Dependency governance | Pinned Maven dependencies | Automated update proposals, license review, CycloneDX SBOM, vulnerability report | Review runtime closure and archive contents for every release | v2.0.0 findings are upgraded in v2.5.0. A later Medium-or-higher finding is upgraded or explicitly accepted before the next stable tag. |
| Release integrity | GitHub Actions-built distributions | Checksummed archives, attestations, version/source provenance, private Windows assembly | Verify every artifact against source revision and checksum | v2.5.0 is the current release. v2.0.0 remains published with checksums and attestations. The maintainer accepted the v2.5.0 tree on Windows 10. |

## Experiment policy

Pre-production status permits the selected beta Jython 2.7.5b1 in the 2.0
qualification path. GraalPy is excluded and its experiment is removed. Linux,
PostgreSQL, Temurin, Flyway, Netty, virtual threads, ZGC, the AOT cache, and
collection replacements belong to Platform 3.0, not 2.0. Do not place an
unapproved dependency in public release archives.
Protect established world data with backups and disposable copies even while
the server itself has no production users.

Attach the target-host qualification evidence for Jython 2.7.5b1 to the pull
request and dependency inventory. Keep each selected stack component
pinned and review licensing and vulnerability results before release.
