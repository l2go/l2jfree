# Logging migration plan for 2.0.0

## Current logging architecture

The source tree originally contained 274 Java files that depended on Apache
Commons Logging. The initial staged migration has moved the `l2j-commons`
application callers to SLF4J 2.x. Source code no longer casts to Commons
Logging's `Jdk14Logger` adapter; the named `chat` channel now uses its JUL
logger directly.
At the same time, LoginServer and GameServer load separate JUL
`logging.properties` files and use custom handlers and formatters.

The current files route gameplay audit, chat, IRC, and item events into
separate GameServer logs. LoginServer has separate login, login-attempt, and
failed-login logs. These channels have independent formats, filters, retention,
and append behavior. They are operational data streams, not just console
formatting.

The JDBC modernization adds SLF4J's JUL provider to both server distributions
as a transition step. This sends HikariCP diagnostics through the existing JUL
configuration. The first source migration slice uses SLF4J in `l2j-commons`;
remaining application callers still use Commons Logging's existing JUL route.
No JUL-to-SLF4J bridge is installed, so the transition does not create a
logging feedback cycle. The final logging backend remains a separate, gated
migration.

## Target

Use SLF4J 2.x as the application logging API with a maintained Logback backend.
Produce consistent process and subsystem fields while preserving dedicated
audit, chat, item, IRC, login-attempt, and failed-login streams. Keep passwords,
session keys, database credentials, and other authentication secrets out of
every output channel.

## Safe transition

1. Capture representative output and rotation behavior for each current file
   channel on both server processes.
2. Replace concrete `Jdk14Logger` casts with named logger access that does not
   depend on a particular Commons Logging implementation. Completed: the
   common startup class uses SLF4J and retains the matching JUL logger name
   for redirected standard output and error; the `chat` channel obtains its
   JUL logger directly and keeps the same channel name.
3. Introduce SLF4J and Logback as an isolated CI-qualified logging slice. Use
   one-way bridges for remaining Commons Logging callers and JUL callers;
   exclude conflicting bindings and prevent bridge cycles.
4. Route the existing named channels to separate appenders with equivalent
   filters, formatters, and retention. Preserve distinct audit and
   authentication-failure access controls.
5. Migrate application call sites from Commons Logging in reviewable packages.
   Remove the Commons Logging bridge only after all production callers and
   third-party dependencies have a supported route.
6. Test routing, level changes, exception stack traces, rotation, restart
   append behavior, and concurrent writes in GitHub Actions. Requalify output
   and access controls on the Windows target.

## Adoption gates

- Every original operational channel has a documented target logger and output
  destination.
- No duplicate messages or feedback loops occur through JUL/JCL bridges.
- Passwords, session data, and database credentials are absent from
  representative success and failure logs.
- Log rotation and retention stay within the configured disk budget.
- Release archives contain the new backend and only the bridges required by
  remaining dependencies.
- Both server processes start, run, and shut down with logging diagnostics
  available if a backend configuration fails.

Do not remove the custom JUL handlers until equivalent output behavior has
passed these gates. Do not change log content and backend behavior in one
unreviewable bulk replacement.

## Migration progress

- Completed: application loggers in `l2j-commons` now use the SLF4J API. Fatal
  messages map to SLF4J error level; other levels and throwable arguments are
  preserved.
- Remaining: migrate LoginServer and GameServer callers in package-sized
  slices, then assess datapack scripts and third-party logging dependencies.
- Commons Logging remains available during this transition because production
  modules still contain callers. Do not remove it from runtime archives yet.
