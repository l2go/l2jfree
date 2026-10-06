# 0011. Configuration model: defaults in the image, changes in one directory

- Status: Accepted
- Date: 2026-10-06

## Context

The 2.x line ships about thirty `.properties` files for the world and two for the login. An operator edits them inside the installation. The first image of Platform 3.0 copied the login files at start and rewrote four keys with a shell script, which does not scale to the world and leaves the operator no place of their own: an image update would replace every edit.

One process now reads both modules ([ADR-0003](0003-one-process-two-modules.md)), and the image is read-only ([ADR-0002](0002-linux-image-delivered-with-compose.md)). Operators need one place for their changes that survives an image update, and the defaults must stay readable as documentation.

## Decision

1. **Defaults** are the property files of the modules, shipped complete and commented in the image under `/opt/l2jfree/config`. They are read-only and are the reference of every key.
2. **Operator changes** live in one directory, `/var/lib/l2jfree/config`, a volume of the compose stack. A file there with the name of a default file overrides only the keys it contains. A key that is not in the operator file keeps its default. The operator file holds the few keys an operator changed, not a copy of the default.
3. **Deployment settings** come from environment variables with the prefix `L2JFREE_`: the database address and users, the address the server binds to, and the address it announces. They win over both files. A secret is never an environment value: a variable ending in `_FILE` names a file that holds it.
4. The loader lives in the commons module. It resolves a file name against the defaults directory, overlays the operator directory, and logs a warning for an operator key that no default file knows, because that is usually a typo. The two directories are set by the system properties `l2jfree.config.defaults` and `l2jfree.config.operator`. Without them the loader reads `./config` as before, so a developer checkout starts unchanged.
5. The entry point of the image starts the process and does nothing else with configuration.

## Alternatives considered

- One YAML file for everything. It reads well, but it needs a new schema for about seven hundred keys and a rewrite of the 4,700-line `Config` class. Nothing in the platform goals needs that.
- Environment variables only. They suit a handful of deployment settings, but not seven hundred game rates.
- Edit the files in a copy of the installation, as 2.x does. It is the failure this record exists to remove: an update replaces the edits.

## Consequences

- An image update changes the defaults and keeps the operator's overrides. An operator reads the effective value of a key from the default file and the override file, in that order.
- A default that changes meaning in a later release cannot be detected from the override. Release notes must name every changed default.
- Two directories need one more rule in the documentation: which wins. The rule is fixed by this record.
- Telnet, rates, and similar keys keep their names, so existing knowledge of the files still applies.

## Revisit when

The number of deployment settings outgrows environment variables, or operators ask for validation of values before start.
