# l2jfree-platform

The launcher of the one process. `com.l2jfree.platform.Platform` starts the login module and the world module in one JVM
and wires them to each other through `l2jfree-contract` ([ADR-0003](../docs/adr/0003-one-process-two-modules.md)).
The module also builds the distribution and holds the architecture rules.

## Start order

1. `LoginModule.prepare()`: login configuration, database pool, schema migration, managers. No port is opened.
2. `GameServer.start(login)`: the world loads its data, is handed the `LoginPort`, and opens the world port (7777).
3. `login.start(world)`: the login module is handed the `WorldPort` and opens the login port (2106).

A client that reaches the login port therefore finds a world. A failure in any step ends the process.

## Distribution

`mvn package` writes `target/l2jfree-platform-<version>-dist.zip` with one top directory:

- `l2jfree-platform.jar`, the entry point, and `libs/`, every dependency.
- `agents/opentelemetry-javaagent.jar`.
- `config/`, the defaults of both modules ([ADR-0011](../docs/adr/0011-configuration-model.md)). The world's `logback.xml` and `telnet.properties` are the ones shipped.
- `data/` and `catalog/` of `l2jfree-datapack`, and its compiled scripts in `data/scripts/`.

## Architecture rules

`ArchitectureTest` (tag `architecture`) runs with the unit tests. It fails the build when the login module and the world
module depend on each other, when the contract depends on anything but the JDK, or when a package other than `platform`
depends on both modules.
