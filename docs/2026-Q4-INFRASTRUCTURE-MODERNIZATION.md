# Infrastructure modernization

Modernize the inherited server in small, qualified changes. The first delivery
slice is [issue #52](https://github.com/l2go/l2jfree/issues/52): login server
startup on Microsoft JDK 25. Each later slice gets its own issue and release
decision after the previous slice works on the target deployment image.

## Baseline

The build uses Maven Wrapper 3.9.16 and Microsoft OpenJDK 25, while production
classes target Java 8 bytecode. MySQL 8.4 and Connector/J 26.7 are the planned
deployment stack. Windows 10 and the exact release image still require runtime
qualification; a passing Ubuntu CI build does not establish that qualification.

The login module alone uses Spring 2.0.2 and Hibernate 3.2.2. They manage two
tables, `accounts` and `gameservers`, through XML mappings and a c3p0 data
source. Both the registration utility and login server load the entire Spring
context at startup. On JDK 25, Hibernate's CGLIB proxy initialization fails
before the registration utility reaches its menu.

## Delivery slices

| Order | Scope | Completion evidence |
|---|---|---|
| 1 | Replace the login module's Spring 2 / Hibernate 3 persistence integration (#52). Preserve the DAO and service behavior and existing schema. | Registration utility, login server, and account manager start on the target JDK 25 without `--add-opens`; account and game server reads and writes work on MySQL 8.4. The release archive contains no Spring 2 or Hibernate 3 jars. |
| 2 | Modernize the login and game server database connection pools and configuration. | Connection loss, restart, idle connection recovery, and pool exhaustion are qualified on the target host. |
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

The long-term fix removes the legacy Spring/Hibernate initialization path from
the login module. Since this module maps only two tables, JDBC implementations
behind the existing DAO interfaces are the preferred design. The implementation
must preserve transaction boundaries, missing-record behavior, and the current
database schema. It must also surface the underlying startup exception rather
than attributing every initialization error to `spring.xml`.

`--add-opens=java.base/java.lang=ALL-UNNAMED` can help confirm the diagnosis or
support a short, explicitly temporary rollout. It is not the acceptance condition
for #52: it leaves the obsolete ORM and its other JDK 25 compatibility risks in
the release image.
