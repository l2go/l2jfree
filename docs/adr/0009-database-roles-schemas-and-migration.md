# 0009. Database roles, schemas, and migration at start

- Status: Accepted
- Date: 2026-10-06

## Context

[ADR-0004](0004-postgresql-and-the-accepted-data-design.md) accepts the PostgreSQL data design: three schemas, forward-only migrations, and separate roles for the login and world modules. It leaves open how a module connects, who creates what, and how the server talks to a database whose login names are case-insensitive. The first module on PostgreSQL, the login module, forces these choices.

## Decision

1. The database is created once, on its first start, by the official image's init script, which runs as the superuser. The script creates the `citext` extension, one role per module, and the schema each role owns. The superuser password and each role password are generated on the first start and kept in a volume. No default password ships.
2. A module connects with its own role, which owns its schema. A module cannot read the schema of another module.
3. Each module applies the migrations of its own schema when it starts, with Flyway, as the role that owns the schema. The role needs no right to create extensions or schemas. A migration that was applied and later edited fails the validation, so a released migration is never changed.
4. Connections send text parameters untyped (`stringtype=unspecified`) and set the schema with `currentSchema`. Without the first setting, the driver types a login name as `varchar`, the server compares it as `text`, and a name that differs only in case no longer matches a `citext` column.
5. Migrations are applied by a shared runner in the commons module. Each module owns its migration files under `db/<schema>` on its classpath.

## Alternatives considered

- A migration role with more rights than the module role. It splits duties, but it adds a second credential to every module for no gain in this deployment.
- Flyway as a separate start-up step outside the server. It keeps rights apart, but an operator can then start a server against an old schema.
- Typed parameters and a cast in every statement. It is explicit, but one missed cast breaks case-insensitive matching silently.

## Consequences

- A role that owns its schema can alter it. The boundary between modules is the schema, not the role's rights inside it.
- A new module needs an entry in the init script and a role password in the secrets volume.
- The untyped-parameter setting affects every statement. The statement checks of the data port run against the real schema, so a statement that depends on a parameter type fails a test.

## Revisit when

A deployment needs a separate migration credential, or a module needs to read another module's schema.
