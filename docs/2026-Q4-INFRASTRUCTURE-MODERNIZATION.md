# Infrastructure modernization

Modernize the inherited server in small, qualified changes. The first delivery
slice is [issue #52](https://github.com/l2go/l2jfree/issues/52): remove the
login server's legacy ORM and qualify both server database paths on Microsoft
JDK 25/MySQL 8.4. Each later slice gets its own issue and release decision
after the previous slice works on the target deployment image.

## Baseline

The build uses Maven Wrapper 3.9.16 and Microsoft OpenJDK 25, while production
classes target Java 8 bytecode. MySQL 8.4 and Connector/J 26.7 are the planned
deployment stack. Windows 10 and the exact release image still require runtime
qualification; a passing Ubuntu CI build does not establish that qualification.

Before #52, the login module used Spring 2.0.2 and Hibernate 3.2.2 to manage
`accounts` and `gameservers` through XML mappings and a c3p0 data source. On
JDK 25, Hibernate's CGLIB proxy initialization failed before the registration
utility reached its menu. The game server already uses JDBC with its own c3p0
pool; its database startup also needs to work without a synthetic test table.

## Delivery slices

| Order | Scope | Completion evidence |
|---|---|---|
| 1 | Replace the login module's Spring 2 / Hibernate 3 persistence integration and qualify both servers' JDBC startup (#52). Preserve DAO/service behavior and existing schemas. | Registration utility, login server, account manager, and game server start on target JDK 25 without `--add-opens`; persistence works on MySQL 8.4 without a required c3p0 test table. Release archives contain no Spring 2 or Hibernate 3 jars. |
| 2 | Further modernize login and game server connection pools and database configuration. | Connection loss, restart, idle connection recovery, and pool exhaustion are qualified on the target host. |
| 3 | Upgrade or replace Jython 2.2.1 and the inherited ECJ integration. | The deployed datapack scripts load and execute under JDK 25, including startup and scheduled scripts. |
| 4 | Review remaining legacy libraries and remove unused transitive dependencies. | Each replacement has a defined runtime path and the assembled archive is inspected for obsolete jars. |
| 5 | Revisit the Java 8 production bytecode target after runtime dependencies are qualified. | All modules compile and the packaged servers start under the chosen Java baseline; the minimum supported runtime is documented. |

The slices describe outcomes, not a bulk dependency version bump. Scope and
release numbering for slices 2–5 are set when their issues are filed. The
deployment checks in [2026-Q4-UPGRADE.md](2026-Q4-UPGRADE.md) remain the release
gate for each runtime change.

## First slice: issue #52

The [JDBC implementation plan](ISSUE-52-JDBC-IMPLEMENTATION-PLAN.md) is the
handoff for this slice.

The login module's Spring/Hibernate path has been replaced with JDBC behind the
existing DAO interfaces; transaction boundaries, missing-record behavior, and
the current schema are preserved. The game server already uses JDBC and is not
being migrated from an ORM. Its c3p0 pool now validates with `SELECT 1` instead
of relying on `connection_test_table`. Both servers must start and exercise
their database paths on the target host. Startup failures must retain their
underlying cause rather than being attributed to `spring.xml`.

`--add-opens=java.base/java.lang=ALL-UNNAMED` can help confirm the diagnosis or
support a short, explicitly temporary rollout. It is not the acceptance condition
for #52: it leaves the obsolete ORM and its other JDK 25 compatibility risks in
the release image.
