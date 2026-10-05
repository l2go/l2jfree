# Dependency inventory and modernization status

This inventory records direct Maven dependencies and the infrastructure
decisions for the 2.0.0 program. The CycloneDX SBOM attached to each CI release
is the authoritative complete inventory, including transitive dependencies.
Version values marked as properties are maintained in
`l2jfree-module/pom.xml`.

## Current direct dependencies

| Module | Dependency | Current version | Scope | Purpose and status |
|---|---|---:|---|---|
| `l2j-commons`, GameServer, MMOCore | SLF4J API | 2.0.20 | Runtime | Application logging API; modules declare it directly where source code uses it and LoginServer receives it through `l2j-commons`. |
| Runtime distributions | JCL-to-SLF4J bridge | 2.0.20 | Runtime | Routes third-party Commons Logging calls to SLF4J pending a dependency and script audit. |
| `l2j-commons` | Apache Commons Lang | 3.20.0 | Runtime | General utility library. |
| `l2j-commons` | Apache Commons IO | 2.22.0 | Runtime | File and stream utilities. |
| `l2j-commons` | MySQL Connector/J | 26.7.0 | Runtime | JDBC driver for the fixed MySQL 8.4 platform. |
| `l2j-commons` | Javolution | 5.4.1 | Runtime | Legacy collections and utility structures; replace selectively after profiling. |
| LoginServer, GameServer | HikariCP | 7.1.0 | Runtime | JDBC connection pool replacing c3p0. |
| LoginServer, GameServer | OpenTelemetry Java agent | 2.26.1 | Optional agent archive | Provides opt-in JVM, JDBC, and HikariCP metrics and traces through OTLP; packaged outside the application classpath and disabled by default. |
| LoginServer, GameServer | SLF4J JUL provider | 2.0.20 | Runtime | Routes HikariCP SLF4J diagnostics into the existing JUL configuration during migration. |
| GameServer | ECJ | 3.44.0 | Provided compile dependency | Compiles datapack Java sources during the CI build; the release distribution must not include ECJ. |
| GameServer | Jython | 2.2.1 | Runtime baseline | Current compatibility baseline while the embedded Jython 2.7.5b1 bridge and supported scripts are qualified for the 2.0 target. |
| GameServer | Trove4j | 2.1.0 | Runtime | Legacy primitive collections; replace selectively after profiling. |
| LoginServer, GameServer | Testcontainers JUnit Jupiter | 2.0.5 | Test | MySQL integration infrastructure in GitHub Actions. |
| LoginServer | Liquibase Core | 4.33.0 | Test | MySQL 8.4 migration compatibility probe only; not included in runtime distributions and not approved for production migrations. |
| All test modules | JUnit Jupiter | 6.1.3 | Test | Unit and integration test framework. |
| All test modules | AssertJ | 3.27.7 | Test | Fluent test assertions. |
| All test modules | Mockito JUnit Jupiter | 5.24.0 | Test | Test doubles for unit tests. |

Internal modules are `l2j-commons`, `l2j-mmocore`, `l2jfree-scripting-engines`,
`l2jfree-login`, `l2jfree-core`, and `l2jfree-datapack`. LoginServer and
GameServer remain separate runtime processes. For 2.0, Java bytecode and Python
scripts share the GameServer revision and checksum manifest; there is no
independently versioned datapack release.

## Target decisions

- Keep Microsoft Build of OpenJDK 25, MySQL Server 8.4, and MySQL Connector/J
  as fixed platform choices.
- Keep Maven and the Maven Wrapper; builds and tests run in GitHub Actions.
- Keep HikariCP after pool lifecycle, timeout, recovery, and workload gates
  pass. Do not restore c3p0 or a Hibernate/Spring ORM runtime.
- SLF4J 2.x and Logback now own application and JUL logging. Keep the named
  audit and gameplay channels described in the
  [logging migration plan](LOGGING-MIGRATION-PLAN.md), and qualify output and
  access controls before stable release.
- Place new MySQL schema changes in numbered SQL files and keep MySQL-specific
  syntax in the repository layer. Liquibase remains a test-only probe and is
  not the 2.0 release journal. Platform 3.0 uses Flyway for PostgreSQL.
- Compile Java datapack scripts in CI and remove ECJ from the 2.0 runtime
  archives after the packaged bytecode has been qualified.
- The admin IRC bridge is not a supported feature. irclib is not a runtime
  dependency, and the release archive check rejects an irclib jar.
- Promote Jython 2.7.5b1 as the 2.0 runtime after the embedded bridge,
  supported scripts, and target-host behavior pass qualification. Platform 3.0
  advances to the final Jython 2.7 release. Jython 2.7.5b1 is the only
  candidate tested for promotion. GraalPy and Python 3 migration are excluded.
- Retain the custom network core and existing platform-thread model in 2.0.
  Netty, virtual threads, Generational ZGC, and the JDK AOT cache are Platform
  3.0 work after the move to Linux.
- Replace Javolution and Trove only in measured, reviewable areas. Preserve the
  current game behavior and benchmark hot world paths before changing them;
  the replacements are deferred to Platform 3.0.

## Experimental candidates

The pre-production program tests only candidates that remain in the approved
modernization scope. Jython 2.7.5b1 is the selected runtime target for 2.0;
GraalPy and Python 3 migration are excluded.

| Area | Candidate | CI / qualification status |
|---|---|---|
| Python 2 compatibility | Jython 2.7.5b1 | Runtime version is pinned for 2.0. Automated syntax, Java interop, and embedded bridge coverage is deferred to 2.1.0; Windows gameplay qualification remains before 2.0.0. |
| Runtime diagnostics | JFR and OpenTelemetry Java agent 2.26.1 | The agent is included separately in both Windows distributions; launch only when explicitly enabled with a configured OTLP endpoint. Measure overhead and verify redaction before operational use. |

See the [approved qualification tracks](INFRASTRUCTURE-MODERNIZATION-VISION-2.0.md#experimental-qualification-tracks)
for the promotion evidence required for Jython and runtime diagnostics.

## Governance

GitHub Actions generates a CycloneDX SBOM from the release source revision and
scans it for known vulnerabilities. The initial scan report is published for
triage; it is not yet a blanket release blocker. Every direct or transitive
runtime dependency change must be reviewed for supported Java 25 operation,
MySQL compatibility where relevant, licensing, maintenance status, and
presence in the intended release archives. Automated update proposals do not
replace this review. Reassess this inventory whenever a runtime dependency or
the target platform changes.
