# Dependency inventory and modernization status

This inventory records direct Maven dependencies and the infrastructure
decisions for the 2.0.0 program. The CycloneDX SBOM attached to each CI release
is the authoritative complete inventory, including transitive dependencies.
Version values marked as properties are maintained in
`l2jfree-module/pom.xml`.

## Current direct dependencies

| Module | Dependency | Current version | Scope | Purpose and status |
|---|---|---:|---|---|
| `l2j-commons` | Commons Logging | 1.2 | Runtime | Retained while other production modules still call the legacy facade. |
| `l2j-commons` | SLF4J API | 2.0.20 | Runtime | Application logging API for the first migrated module; uses the existing JUL provider during transition. |
| `l2j-commons` | Apache Commons Lang | 3.20.0 | Runtime | General utility library. |
| `l2j-commons` | Apache Commons IO | 2.22.0 | Runtime | File and stream utilities. |
| `l2j-commons` | MySQL Connector/J | 26.7.0 | Runtime | JDBC driver for the fixed MySQL 8.4 platform. |
| `l2j-commons` | Javolution | 5.4.1 | Runtime | Legacy collections and utility structures; replace selectively after profiling. |
| LoginServer, GameServer | HikariCP | 7.1.0 | Runtime | JDBC connection pool replacing c3p0. |
| LoginServer, GameServer | SLF4J JUL provider | 2.0.20 | Runtime | Routes HikariCP SLF4J diagnostics into the existing JUL configuration during migration. |
| GameServer | ECJ | 3.44.0 | Runtime | Compiles datapack Java scripts at runtime. |
| GameServer | Jython | 2.2.1 | Runtime | Executes legacy Python 2 datapack scripts; replacement is gated on script qualification. |
| GameServer | irclib | 1.10 | Runtime | Optional IRC integration. |
| GameServer | Trove4j | 2.1.0 | Runtime | Legacy primitive collections; replace selectively after profiling. |
| LoginServer, GameServer | Testcontainers JUnit Jupiter | 2.0.5 | Test | MySQL integration infrastructure in GitHub Actions. |
| LoginServer | Liquibase Core | 4.33.0 | Test | MySQL 8.4 migration compatibility probe only; not included in runtime distributions and not approved for production migrations. |
| All test modules | JUnit Jupiter | 6.1.3 | Test | Unit and integration test framework. |
| All test modules | AssertJ | 3.27.7 | Test | Fluent test assertions. |
| All test modules | Mockito JUnit Jupiter | 5.24.0 | Test | Test doubles for unit tests. |

Internal modules are `l2j-commons`, `l2j-mmocore`, `l2jfree-scripting-engines`,
`l2jfree-login`, `l2jfree-core`, and `l2jfree-datapack`. LoginServer and
GameServer remain separate runtime processes. The datapack is a distinct
artifact and depends on GameServer code for its Java script sources.

## Target decisions

- Keep Microsoft Build of OpenJDK 25, MySQL Server 8.4, and MySQL Connector/J
  as fixed platform choices.
- Keep Maven and the Maven Wrapper; builds and tests run in GitHub Actions.
- Keep HikariCP after pool lifecycle, timeout, recovery, and workload gates
  pass. Do not restore c3p0 or a Hibernate/Spring ORM runtime.
- Move application logging to SLF4J 2.x and Logback only after preserving the
  named JUL audit and gameplay channels described in the
  [logging migration plan](LOGGING-MIGRATION-PLAN.md).
- Adopt a production migration framework only after MySQL 8.4 support and both
  database baselines are proven. Liquibase remains test-only until that
  decision and a complete transitive license review.
- Retain Jython until supported Python scripts have replacements and behavior
  checks. Retain the custom network core until a Netty prototype passes
  protocol compatibility and comparative performance gates.
- Replace Javolution and Trove only in measured, reviewable areas. Preserve the
  current game behavior and benchmark hot world paths before changing them.

## Governance

GitHub Actions generates a CycloneDX SBOM from the release source revision and
scans it for known vulnerabilities. The initial scan report is published for
triage; it is not yet a blanket release blocker. Every direct or transitive
runtime dependency change must be reviewed for supported Java 25 operation,
MySQL compatibility where relevant, licensing, maintenance status, and
presence in the intended release archives. Automated update proposals do not
replace this review. Reassess this inventory whenever a runtime dependency or
the target platform changes.
