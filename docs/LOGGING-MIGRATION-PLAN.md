# Logging migration plan for 2.0.0

## Current logging architecture

The source tree originally contained 274 Java files that depended on Apache
Commons Logging. All first-party Java callers have since moved to SLF4J 2.x or
a specialized logging adapter. Source code no longer casts to Commons
Logging's `Jdk14Logger` adapter; the named `chat` channel uses its JUL logger
directly where JUL record parameters are required.
At the same time, LoginServer and GameServer load separate JUL
`logging.properties` files and use custom handlers and formatters.

The current files route gameplay audit, chat, IRC, and item events into
separate GameServer logs. LoginServer has separate login, login-attempt, and
failed-login logs. These channels have independent formats, filters, retention,
and append behavior. They are operational data streams, not just console
formatting.

The JDBC modernization adds SLF4J's JUL provider to both server distributions
as a transition step. This sends HikariCP diagnostics through the existing JUL
configuration. The JCL-to-SLF4J bridge remains for third-party libraries and
dynamically loaded scripts while those are audited. No JUL-to-SLF4J bridge is
installed, so the transition does not create a logging feedback cycle. The
final logging backend remains a separate, gated migration.

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
3. Introduce SLF4J and Logback as an isolated CI-qualified logging slice. Keep
   only one-way adapters for third-party Commons Logging and JUL callers;
   exclude conflicting bindings and prevent bridge cycles.
4. Route the existing named channels to separate appenders with equivalent
   filters, formatters, and retention. Preserve distinct audit and
   authentication-failure access controls.
5. Migrate application call sites from Commons Logging. Completed for
   first-party Java sources. Remove the Commons Logging bridge only after all
   production dependencies and dynamically loaded scripts have a supported
   route.
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

- Completed: all first-party Java application callers across `l2j-commons`,
  LoginServer, GameServer, and MMOCore use SLF4J or a specialized logging
  adapter. Fatal-level calls map to SLF4J error level; throwable arguments and
  the existing audit, chat, and IRC logger names are preserved. LoginServer
  no longer logs the local or supplied player session key on failed
  authentication. The core and MMOCore modules no longer need the Commons
  Logging API as a compile dependency.
- In progress: SLF4J 2.0.20, Logback 1.5, and the JCL-to-SLF4J compatibility
  bridge are configured. The bridge remains on the runtime classpath pending
  an audit of third-party libraries and dynamically loaded scripts. Logback
  declares dedicated appenders for audit, chat, IRC, item,
  login, login-attempt, and failed-login logger names at the existing
  `log/.../*.log` paths. Each appender appends to the active file and has a
  30-day / 128 MiB rolling cap. Ordinary SLF4J loggers go to stdout. Existing
  JUL `logging.properties` and handlers remain enabled for JUL callers.
- Blocking parity work before migration can be considered complete: the
  Logback appenders currently use a common message formatter and do not
  reproduce the existing channel-specific JUL filters and formatters. In
  particular, item-event filtering (excluded item types and `Consume`
  processes), channel-specific field ordering, output equivalence, and the
  access controls for audit and authentication-failure files have not been
  qualified. Implement equivalent filters/formatters or record an approved
  behavior change, then verify routing, rotation, restart append behavior,
  concurrency, and Windows permissions in GitHub Actions and on the target
  host. Keep JUL handlers until that parity gate passes.
- The SLF4J/JCL runtime bridge replaces the Commons Logging implementation in
  the runtime classpath. The remaining bridge requirement is limited to
  third-party libraries and dynamically loaded scripts, pending inventory.
