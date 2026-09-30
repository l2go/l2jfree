# L2JFree Infrastructure Stack

This is the implementation map for the 2.0.0 infrastructure vision. Platform
constraints are fixed; the remaining rows identify the incumbent, approved
target, and evidence required before the target enters a release. Platform 3.0
replacements are explicitly deferred until the 2.0 acceptance gates pass.

The selected Python runtime for 2.0 is Jython 2.7.5b1; Platform 3.0 moves to
the final Jython 2.7 release. Jython 2.2.1 is the current compatibility
baseline until the selected bridge and supported scripts pass qualification.
GraalPy and a Python 3 port are excluded from the modernization goal. The
GraalPy prototype, dependencies, and CI job are being removed.

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
| Java platform | Java 8-era source and runtime assumptions | Java 25 source, tests, and bytecode on Microsoft OpenJDK 25 | Linux CI build/tests, Windows packaging and runtime qualification | Java 25 compilation is implemented; target-host qualification remains |
| Persistence | JDBC in both servers after issue #52 | Explicit JDBC repositories and bounded transactions | MySQL 8.4 integration coverage for account, world persistence, reconnect, shutdown, and restart | JDBC baseline is released; modernization coverage continues |
| Connection pool | c3p0 was removed | HikariCP 7.1.x | Pool exhaustion, reconnect, idle validation, lifecycle, metrics, and shutdown checks | Implemented; full target-host workload qualification remains |
| Schema evolution | Historical SQL installer and existing databases | Numbered forward SQL files; MySQL syntax stays in the repository layer; Liquibase remains test-only | Test each change against disposable MySQL 8.4 and a copy of an existing schema; verify interruption, backup, and restart behavior | No production migration framework is selected for 2.0; Flyway belongs to PostgreSQL in Platform 3.0 |
| Java datapack scripts | Runtime Java compilation through ECJ 4.4.2 | Compile datapack sources during CI packaging; carry compiled bytecode with the matching GameServer revision; remove ECJ from runtime archives | CI verifies source-to-bytecode coverage, revision match, merged archive contents, and absence of ECJ; target-host script loading and gameplay scenarios remain | CI packaging and bytecode checks are in progress; Windows/MySQL runtime acceptance remains |
| Python datapack scripts | Jython 2.2.1 and Python 2 scripts | Jython 2.7.5b1 only for 2.0; final Jython 2.7 release for Platform 3.0 | Full inventory parse, Java interop, embedded bridge, representative quest/AI/event/task behavior, and Windows qualification | Jython 2.7.5b1 CI check passes; gameplay behavior and target-host qualification remain |
| Network I/O | Custom MMO transport and protocol implementation | Retain the existing core in 2.0; Netty 4.2 with `io_uring` and epoll is Platform 3.0 work | Preserve packet encryption, ordering, and disconnect behavior on the Windows acceptance host | No network replacement experiment is planned for 2.0 |
| Concurrency | Existing executor, FIFO, scheduled task, and platform-thread model | Retain in 2.0; evaluate virtual threads only on the Platform 3.0 Linux process | Repeat target-host gameplay and deadlock scenarios | 2.0 fixes known world-lock issues and does not change thread model |
| Collections | Javolution 5.4.1 and Trove4j 2.1.0 | Replace with JDK collections and fastutil on measured paths in Platform 3.0 | Linux JFR and allocation/throughput measurements for affected code, including hot world loops | Deferred until Platform 3.0 Linux qualification |
| Logging | JUL and Commons Logging, with SLF4J 2.x migration in progress | SLF4J 2.0.20 and Logback 1.5 to stdout; remove Commons Logging and `slf4j-jdk14` | Preserve named audit/gameplay channels; verify rotation, structured fields, severity, exceptions, and secret redaction | Required before 2.0 stable |
| IRC integration | irclib 1.10 | Remove irclib; use Kitteh IRC Client Library only if the admin IRC bridge remains a product feature, otherwise remove the integration | Confirm feature use, qualify replacement if retained, and verify archives contain no irclib | Required before 2.0 stable |
| JVM diagnostics | Existing logs and deadlock detector | JFR plus OpenTelemetry Java agent / SDK metrics and traces | Capture startup, GC, pool, script, scheduler, and packet paths; record instrumentation overhead and incident artifacts | JFR recordings are added to the active CI change; OpenTelemetry export and broad runtime coverage remain |
| Test stack | Existing Maven tests and legacy qualification checks | Refactored and expanded JUnit 6.1.x, AssertJ, Mockito, Testcontainers 2.0.x, MySQL 8.4 | Deterministic unit and integration runs on GitHub Actions after the dedicated test modernization phase | Test execution and qualification jobs are disabled during the current build-and-package phase; no test evidence is claimed |
| Dependency governance | Pinned Maven dependencies | Automated update proposals, license review, CycloneDX SBOM, vulnerability report | Review runtime closure and archive contents for every release | SBOM and scan exist; vulnerability threshold is still a triage policy |
| Release integrity | GitHub Actions-built distributions | Checksummed archives, attestations, version/source provenance, private Windows assembly | Verify every artifact against source revision and checksum; qualify RC on target host | CI provenance is implemented; 2.0.0 RC/host acceptance remains |

## Experiment policy

Pre-production status permits the selected beta Jython 2.7.5b1 in the 2.0
qualification path; its compatibility job is a required check. GraalPy is
excluded and its experiment is removed. Linux,
PostgreSQL, Temurin, Flyway, Netty, virtual threads, ZGC, the AOT cache, and
collection replacements belong to Platform 3.0, not 2.0. Do not place an
unapproved dependency in public release archives.
Protect established world data with backups and disposable copies even while
the server itself has no production users.

Attach the Jython 2.7.5b1 CI result and target-host qualification evidence to
the pull request and dependency inventory. Keep each selected stack component
pinned and review licensing and vulnerability results before release.
