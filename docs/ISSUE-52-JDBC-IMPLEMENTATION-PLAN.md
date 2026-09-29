# Issue #52: replace login ORM with JDBC

Implementation handoff for GPT-6 Luna. The agreed decision is to remove Spring
2.0.2 and Hibernate 3.2.2 from the login module and use JDBC for its two
database tables. Work only on [issue #52](https://github.com/l2go/l2jfree/issues/52)
in this slice. The broader sequence is in
[the infrastructure roadmap](2026-Q4-INFRASTRUCTURE-MODERNIZATION.md).

## Target and boundaries

- Make `register_gameserver.bat`, `loginserver_launcher.bat`, and
  `account_manager.bat` start on the installed Microsoft JDK 25 without
  `--add-opens` or another JDK. The target machine has no other JDK; do not
  ask the maintainer to confirm its version again.
- Preserve the existing `accounts` and `gameservers` tables, public service
  behavior, login protocol, password format, and released Java 8 bytecode
  target. Do not perform a schema migration in this issue.
- Keep c3p0 as the data source for this slice. Pool replacement, Jython, ECJ,
  and game server dependencies belong to later issues.
- Keep the XML server-name catalog (`servername.xml`) distinct from the
  `gameservers` database table. It is not an ORM mapping.
- Do not close #52 after a successful build alone. Target Windows 10, JDK 25,
  MySQL 8.4, and the assembled release image require runtime qualification.

## Current seams

| Concern | Current location | Replacement responsibility |
|---|---|---|
| Startup wiring | `L2Registry` and `src/main/resources/spring.xml` | Construct one pool, two JDBC DAOs, their services, and the XML catalog; initialize once and close the pool on shutdown. Keep clear startup causes. |
| Account persistence | `AccountsDAOHib`, `BaseRootDAOHib`, `Accounts.hbm.xml` | Parameterized JDBC over the existing `AccountsDAO` contract and all nine SQL columns. |
| Game server persistence | `GameserversDAOHib`, `BaseRootDAOHib`, `Gameservers.hbm.xml` | Parameterized JDBC over `GameserversDAO`, ordered by `server_id` where the old DAO orders by id. |
| Transactions | Spring `TransactionInterceptor` on `AccountsServices` and `GameserversServices` | Explicit transaction boundaries for operations containing multiple SQL statements; commit on success and roll back on failure. |
| Server names | `GameserversDAOXml` | Retain the catalog, but remove its Spring exception type and account for its dom4j dependency. |
| Packaging | `l2jfree-login/pom.xml`, `distribution.xml` | Remove the ORM stack and check the actual ZIP contents, including transitive jars. |

`L2Registry.getBean` is used by `LoginManager`, `GameServerManager`, and
`AccountManager`; the three startup paths load it through `loadRegistry`.
`L2Registry.getConnection` and pool metrics are public methods and must still
work. `getApplicationContext` and `setApplicationContext` have no callers in
this repository, so they can disappear with Spring. Search again before removal.

## Implementation sequence

### 1. Establish the behavior to preserve

Read the interfaces, Hibernate DAOs, mappings, service callers, table DDL,
registry, and three launch scripts. Record each read, insert, update, delete,
and collection operation before changing code. Pay particular attention to:

- missing account handling: `AccountsServices.getAccountById` returns `null`
  after a missing-record exception; `exists` returns `false`; deletion of a
  missing account reports an account modification error;
- `Accounts.lastactive` is a nullable unsigned `BIGINT` in SQL but a
  `BigDecimal` in Java; preserve null and exact integral values;
- SQL column names and defaults, including `lastIP`, `accessLevel`,
  `lastServerId`, and the three birthday columns;
- the current account creation/update paths, including auto-created accounts,
  password hashing, and updates to last IP, last active time, access level,
  and last server id;
- the difference between the database list of registered game servers and
  the XML catalog of available names;
- the old `createGameserver` semantics for an already occupied primary key;
  a silent overwrite would change registration behavior.

### 2. Introduce JDBC infrastructure

Create a small login-module component that builds `ComboPooledDataSource` from
the values already loaded into `Config`: `DATABASE_DRIVER`, `DATABASE_URL`,
`DATABASE_LOGIN`, and `DATABASE_PASSWORD`. Transfer the existing bounded pool
settings deliberately, document any changed defaults, and close the pool on
normal shutdown. Do not log the password. Fail startup with the underlying
cause if configuration or connection acquisition fails.

Use `PreparedStatement` parameters for values and try-with-resources for
results, statements, and borrowed connections. Keep SQL in the JDBC DAOs.
Choose a small transaction helper or explicit service-level connection
handling; one connection must cover every multi-statement logical operation.
Avoid a global shared `Connection`. Handle nested calls and rollback without
leaking connection state back into the pool.

### 3. Replace the two database DAOs

Implement all methods used by `AccountsDAO` and `GameserversDAO`; remove methods
only after confirming there are no callers and updating both interface and XML
implementation consistently. Prefer explicit inserts and updates over a blind
MySQL upsert: the account schema has defaults and nullable fields, and an
upsert must not overwrite data unexpectedly. Keep bulk methods atomic where
Spring previously supplied a transaction. Use one `DELETE FROM gameservers`
for `removeAll` if it preserves the intended result and rollback behavior.

Represent missing rows consistently at the DAO boundary and adapt services so
the externally observed behavior remains the same. Preserve a useful SQL cause
for unexpected failures. Do not turn database errors into “not found” or an
empty list. Review concurrent account creation and duplicate game server ids;
let the database primary key enforce uniqueness.

### 4. Remove Spring wiring and old transitive dependencies

Replace `L2Registry` bean lookup with an explicit, typed object graph while
preserving its current call sites or changing them together. Remove imports of
Spring exception classes from the services and `GameserversDAOXml`. Remove
`AccountsDAOHib`, `GameserversDAOHib`, and `BaseRootDAOHib` after the JDBC
replacements are wired. Remove `spring.xml`, both `.hbm.xml` mappings,
`ehcache.xml`, and the login POM's `spring`, `spring-mock`, `hibernate`, and
`jta` dependencies when no longer used.

`GameserversDAOXml` imports dom4j, currently supplied transitively. Either
declare a deliberate direct dependency or use a JDK XML parser. If switching
parsers, configure it against external entity resolution and preserve the
existing `servername.xml` format. Inspect the effective dependency tree and
the distribution ZIP; the packaged `libs` directory must not retain Spring 2,
Hibernate 3, CGLIB, or obsolete cache jars from this stack.

### 5. Qualify and deliver

First compile and inspect the packaged login ZIP. Then verify against a
disposable copy of the repository SQL schema and MySQL 8.4:

1. Start `register_gameserver.bat` on Microsoft JDK 25 without module-opening
   flags; list server names, register an id, restart the utility, and confirm
   the row and hex id load correctly.
2. Exercise an occupied id and `clean`; check that errors are visible and the
   database result matches the old intent.
3. Start the login server and account manager from the assembled image. Create,
   read, update, list, and delete an account; verify the password format,
   birthday fields, last active time, access level, and last server id.
4. Exercise login with an existing account and auto-create behavior where
   enabled. Restart and confirm persisted state, then check pool shutdown.
5. Confirm the ZIP contains no old ORM jars and record the exact JDK, MySQL,
   archive checksum, and observed results in the issue or release notes.

If target Windows qualification is unavailable, leave #52 open and report the
precise unverified checks. Do not claim the issue is fixed based on CI alone.

## Existing work and handoff rules

The workspace already has local, uncommitted changes: README and the
infrastructure roadmap, plus a one-line `L2Registry` diagnostic improvement.
Preserve them and inspect `git status` before editing. Untracked `.DS_Store`
files and `.mvn/wrapper/maven-wrapper.jar` also predate this handoff; do not
delete or add them casually. The Java files use CRLF, so account for that when
reviewing whitespace checks.

Keep the implementation scoped to #52 and explain any necessary deviations in
the PR. Report what was built and run, what was observed, and what remains for
target-host qualification. Do not advance the later infrastructure slices in
this PR. Run every `gh` command outside the sandbox (`require_escalated`);
GitHub CLI access fails from the sandbox in this workspace.
