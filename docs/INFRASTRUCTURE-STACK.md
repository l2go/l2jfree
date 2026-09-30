# L2JFree Infrastructure Stack

This is the implementation map for the 2.0.0 infrastructure vision. Platform
constraints are fixed; the remaining rows identify the incumbent, approved
target, experiment, and evidence required before the target enters a release.
Candidate experiments run through GitHub Actions and do not alter the release
runtime by themselves.

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
| Java compiler target | Java 25 |
| Database | MySQL Server 8.4 |
| JDBC driver | MySQL Connector/J 26.7.x |
| Application topology | Separate LoginServer and GameServer processes; versioned datapack artifact |
| Build and verification | Maven Wrapper and GitHub Actions; no workstation builds, tests, or server runs |

## Approved modernization stack and qualification

| Area | Current baseline | Target or candidate | Promotion evidence | Status |
|---|---|---|---|---|
| Java platform | Java 8-era source and runtime assumptions | Java 25 source, tests, and bytecode on Microsoft OpenJDK 25 | Linux CI build/tests, Windows packaging and runtime qualification | Java 25 compilation is implemented; target-host qualification remains |
| Persistence | JDBC in both servers after issue #52 | Explicit JDBC repositories and bounded transactions | MySQL 8.4 integration coverage for account, world persistence, reconnect, shutdown, and restart | JDBC baseline is released; modernization coverage continues |
| Connection pool | c3p0 was removed | HikariCP 7.1.x | Pool exhaustion, reconnect, idle validation, lifecycle, metrics, and shutdown checks | Implemented; full target-host workload qualification remains |
| Schema evolution | Historical SQL installer and existing databases | Versioned, non-destructive migration runner; compare Liquibase 4.33 with a direct SQL runner | Empty install, baseline existing schema, forward migration, checksum drift, concurrent startup, interruption, restore, and restart on disposable MySQL 8.4 | Liquibase test probe exists; production migration choice is open |
| Java datapack scripts | Runtime Java compilation through ECJ 4.4.2 | ECJ 3.44.x, validated and versioned datapack output, stable script API | Compile all scripts in CI; representative load and gameplay scenarios | Compiler upgrade is implemented; script API and scenario coverage remain |
| Python datapack scripts | Jython 2.2.1 and Python 2 scripts | Jython 2.7.5b1 for 2.0; final Jython 2.7 release for Platform 3.0 | Full inventory parse/compile, Java interop, embedded JSR-223 bridge, representative quest, AI, event, and task behavior, and Windows qualification | Candidate CI checks pass; gameplay behavior and target-host qualification remain |
| Network I/O | Custom MMO transport and protocol implementation | Compare with Netty 4.2.18.Final, checking newer security patches before adoption | Protocol fixture replay, encryption/order, disconnects, recovery, throughput, p95/p99 latency, and allocations on Linux and Windows | Prototype and measurements remain |
| Concurrency | Existing executor, FIFO, and scheduled task model | Evaluate JDK 25 virtual threads only for blocking login, administrative, and background I/O | Compare throughput, tail latency, thread count, pinning, cancellation, and shutdown; leave world ticks out until separately proven | Benchmark remains |
| Collections | Javolution 5.4.1 and Trove4j 2.1.0 | Replace selectively with JDK collections | Allocation and throughput measurements for affected code, including hot world loops | Selective migration remains |
| Logging | JUL and Commons Logging, with SLF4J 2.x migration in progress | SLF4J 2.x API and Logback backend | Preserve audit/gameplay channels; verify rotation, structured fields, severity, exceptions, and secret redaction | SLF4J migration is partial; Logback is not yet adopted |
| JVM diagnostics | Existing logs and deadlock detector | JFR plus OpenTelemetry Java agent / SDK metrics and traces | Capture startup, GC, pool, script, scheduler, and packet paths; record instrumentation overhead and incident artifacts | JFR recordings are added to the active CI change; OpenTelemetry export and broad runtime coverage remain |
| Test stack | Maven tests | JUnit 6.1.x, AssertJ, Mockito, Testcontainers 2.0.x, MySQL 8.4 | Deterministic unit and integration runs on GitHub Actions | Present in project; scenario coverage is being expanded |
| Dependency governance | Pinned Maven dependencies | Automated update proposals, license review, CycloneDX SBOM, vulnerability report | Review runtime closure and archive contents for every release | SBOM and scan exist; vulnerability threshold is still a triage policy |
| Release integrity | GitHub Actions-built distributions | Checksummed archives, attestations, version/source provenance, private Windows assembly | Verify every artifact against source revision and checksum; qualify RC on target host | CI provenance is implemented; 2.0.0 RC/host acceptance remains |

## Experiment policy

Pre-production status permits beta dependencies only when they are part of the
approved target. Jython 2.7.5b1 is the selected 2.0 runtime and must pass the
bridge, script, and target-host gates before release. GraalPy is excluded and
its experiment is removed. Database and network prototypes remain isolated
until their promotion evidence is complete. Do not place an unapproved
dependency in public release archives.
Protect established world data with backups and disposable copies even while
the server itself has no production users.

For every experiment, attach the CI run, candidate version, Windows and Linux
results where applicable, performance measurements, incompatibility list, and
adoption decision to the pull request and dependency inventory. Keep each
selected stack component pinned and review licensing and vulnerability results
before release.
