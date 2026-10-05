# Logging migration plan for 2.0.0

## Current logging architecture

All first-party Java logging calls now use SLF4J 2.x or the narrow JUL adapter
needed for standard-output redirection. The dedicated channel names remain
stable across LoginServer and GameServer.

The operational channels route gameplay audit, chat, and item events into
separate GameServer logs. LoginServer has separate login, login-attempt, and
failed-login logs. These channels have independent formats, filters, retention,
and append behavior. They are operational data streams, not just console
formatting.

Logback is the single backend for both processes. JUL records are forwarded
one way through `jul-to-slf4j`; JUL root handlers and per-process
`logging.properties` are removed. A repository-wide source and datapack search
found no Commons Logging API use, so `jcl-over-slf4j` is removed as well.

## Target

Use SLF4J 2.x as the application logging API with a maintained Logback backend.
Produce consistent process and subsystem fields while preserving dedicated
audit, chat, item, login-attempt, and failed-login streams. Keep passwords,
session keys, database credentials, and other authentication secrets out of
every output channel.

## Safe transition

1. Capture representative output and rotation behavior for each current file
   channel on both server processes.
2. Replace implementation-specific logging casts and migrate application
   call sites. Completed for first-party Java sources.
3. Install a one-way JUL-to-SLF4J bridge before application JUL loggers are
   initialized; remove JUL root handlers and duplicate output paths.
4. Route named operational channels through dedicated Logback appenders.
   Preserve item exclusions and in-game/status listener delivery.
5. Verify routing, levels, exception stack traces, rotation, restart append
   behavior, concurrent writes, and Windows file permissions in GitHub
   Actions and on the target host.

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

The backend and named-channel migration are implemented together so there is
only one owner for each log file. Output formatting and target-host file
permissions still require acceptance before stable release.

## Migration progress

- Completed: all first-party Java application callers across `l2j-commons`,
  LoginServer, GameServer, and MMOCore use SLF4J or a specialized logging
  adapter. Fatal-level calls map to SLF4J error level; throwable arguments and
  the existing audit and chat logger names are preserved. LoginServer
  no longer logs the local or supplied player session key on failed
  authentication. The core and MMOCore modules no longer need the Commons
  Logging API as a compile dependency.
- Implemented in this block: Logback is the sole backend; JUL uses the
  one-way `jul-to-slf4j` adapter; per-process JUL configurations and the
  Commons Logging bridge are removed. Dedicated appenders remain for audit,
  chat, item, login, login-attempt, and failed-login output. Item
  consumption and the legacy excluded item categories are filtered; root
  events still reach admin/status listeners. Existing active log paths are
  retained, append mode is enabled, and rolling limits are explicit.
- Remaining before calling this migration complete: remote CI packaging and
  dependency-closure verification; inspect representative output and
  exception rendering; verify permissions and rotation on Windows; confirm
  no secrets enter authentication logs. No local build, test, or server run
  is part of this workflow.
