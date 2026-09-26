# 2026 Q4 correctness audit

Decision record for finding defects in the Java server, filing them, and proving the fixes. Windows 10, OpenJDK 25, and MySQL 8.4 qualification stays in [2026-Q4-UPGRADE.md](2026-Q4-UPGRADE.md). This audit does not change gameplay scope and does not replace that qualification.

Repository text, issues, and commit messages stay in English. Commit messages do not carry a `Co-authored-by` trailer.

## Baseline

| Fact | State on 2026-09-26 |
|---|---|
| Production bytecode | Java 8 (`maven.compiler.release` 8). CI builds with Microsoft OpenJDK 11 and 25 |
| Modules | `l2j-commons`, `l2j-mmocore`, `l2jfree-login`, `l2jfree-core`, `l2jfree-scripting-engines`, `l2jfree-datapack` |
| Core size | About 1,658 Java files. About 613 of them are network packets |
| Tests | JUnit 4.13.2 on the classpath. Existing tests extend `junit.framework.TestCase`. Commons tests are pure. Core tests cover a handful of formulas and parsers and mutate `Config` through `ConfigHelper`. Login tests boot a Spring XML mock context |
| Surefire | `skipTests` defaults to `true`. CI runs `./mvnw install` and does not execute tests |
| Static analysis | No SpotBugs, PMD, Error Prone, or Checkstyle |
| Tracker | GitHub Issues are disabled on `l2go/l2jfree`. Labels are the GitHub defaults. There are no milestones |
| Hot path | `ItemContainer` calls `ItemTable`, `L2World`, and `L2DatabaseFactory`. `GameServer.main` loads config and the database immediately |

Inherited Jython 2.2.1, Spring 2.0.2, and Hibernate 3.2.2 stay as they are for this audit. A dependency upgrade is separate work.

## Where defects are looked for

The audit reads invariants, not the tree file by file. Packet classes are checked as one pattern: the shared reader, bounds on `readD` / `readS` / `readCompQ`, then the same pattern across the packet set.

A defect is a broken rule with a consequence on a running server: an item created or destroyed twice, a session desync, a crash, or a broken login. Style, naming, and "this could be rewritten" stay out of the tracker.

The rules, and the code that holds them:

| Invariant | Where it lives |
|---|---|
| An item is not created from nothing and is not removed twice | Inventory, trade, warehouse, multisell, enchant |
| One character has one session | Login, `L2Client`, disconnect |
| A client packet cannot break the decode | Packet base classes, then the packet set |
| A trade or warehouse write is atomic | `L2DatabaseFactory`, the SQL queue, character save |
| A world object id is unique | `idfactory`, knownlist, `ThreadPoolManager` |

Datapack HTML, Python, and SQL content is a later pass. It is a different defect class.

Search order inside the hunt:

1. SpotBugs on the already built jars, plus a focused search for string-built SQL, empty `catch`, a `HashMap` shared across threads, `==` on `Integer`, and `Thread.sleep` on a game thread. Hits stay in working notes until they name a broken invariant.
2. Read the code behind each invariant above. One root cause is one finding, even when many call sites share it.

## How a finding is filed

The tracker is GitHub Issues on `l2go/l2jfree`. Issues are turned on for this audit. The repository stays an archive in the README sense: there is no public form for outside bug reports.

Each filed issue has one root cause and all of the following:

- Title states the failure, not the file name.
- The broken invariant.
- The impact on a running server.
- The evidence: symbol, path, and why that path breaks the invariant.
- How to confirm it. A race that needs a live server is confirmed by the thread interleaving, and the issue says so.
- One acceptance sentence: what is true after the fix.

The fix does not rewrite the subsystem. Severity is relative to 1.4.0 qualification:

| Severity | When |
|---|---|
| `blocker` | A normal path creates or loses an item, breaks a session, or crashes the server |
| `major` | The same class of failure on a narrow path, or a race with a specific interleaving |
| `minor` | The result is wrong and the damage is limited |

Labels are a small set: `bug`, `severity:blocker`, `severity:major`, `severity:minor`, `area:economy`, `area:session`, `area:packets`, `area:persistence`, `area:concurrency`. One milestone holds the audit. One tracking issue states the scope, the method, the filing bar, and what is left unfiled. Child issues link to it.

The first batch is the economy, session, and persistence findings that clear the bar, on the order of 8–15 issues. A SpotBugs warning is not an issue. A security-shaped game bug (packet trust, admin command) is a normal public issue that states impact and the fix, without a step-by-step exploit. A leaked credential would use a private advisory; item and session bugs do not.

A fix PR uses `Fixes #n` and keeps the main branch green.

## Tests

Tests prove a defect that is already understood. They are not the search tool, and there is no coverage target. JaCoCo may be reported. It is not a gate.

Old tests and their infrastructure are removed when the new stack lands: every `src/test` tree, `ConfigHelper`, the login Spring XML fixtures, the JUnit 4 dependency, and `skipTests`. The JUnit Vintage engine is not added. Old `TestCase` classes are not ported.

### Stack

Tests execute on JDK 25. JDK 11 stays a compile-and-package check, because the test stack requires Java 17. Production bytecode stays `--release 8`. Test sources use `testRelease` 21, so tests may use records and pattern matching without changing server bytecode.

| Role | Artifact | Version |
|---|---|---|
| Runner and API | `org.junit:junit-bom`, `org.junit.jupiter:junit-jupiter` | 6.1.3 |
| Assertions | `org.assertj:assertj-core` | 3.27.7 |
| Mocks | `org.mockito:mockito-junit-jupiter` | 5.24.0 |
| SQL | `org.testcontainers:testcontainers-bom`, `testcontainers-mysql`, `testcontainers-junit-jupiter` | 2.0.5 |
| Package boundaries | `com.tngtech.archunit:archunit-junit6` | 1.5.0 |

AssertJ 4 is still a milestone, so the stack stays on the latest 3.x. Versions are pinned from the BOM in the parent POM. Surefire already runs the JUnit Platform; on JDK 25 it needs `-javaagent` pointed at `mockito-core`, because the JDK no longer allows Mockito to attach itself.

Left out on purpose:

| Omitted | Reason |
|---|---|
| jqwik | 1.10 carries an anti-AI usage clause, and the maintainer has not committed to a JUnit 6 line |
| JUnit Vintage, JUnit 4, Hamcrest, PowerMock | Retired with the old tests |
| Spring Test | Login still uses Spring 2.0.2. New tests do not boot that context |
| H2 or an embedded MySQL stand-in | Connector/J 26.7 targets MySQL 8.4. A different engine would hide the defect |
| Instancio | Useful only after a small value type exists. God objects such as `L2Player` are a bad generator target |

### How a test is written

One invariant is one test class, grouped with `@Nested`. The display name is the property, for example that merging two stacks does not change the total count. Assertions go through AssertJ only. Edges use `@ParameterizedTest`.

A port this code owns (item store, id allocation) gets a hand-written fake under `src/test`. Mockito is limited to a collaborator that should not be reimplemented: JDBC, a clock, or an executor.

Testcontainers runs MySQL 8.4 and is marked `@Tag("integration")`. The default `./mvnw install` excludes that tag. A separate JDK 25 CI job with Docker runs it, and only for a defect that is in the SQL itself. Schema comes from the repository SQL, not from a parallel test schema.

ArchUnit starts when the first seam is extracted. The first rule is that game logic does not call `L2DatabaseFactory` directly. Before that seam exists, the rule would only fail on the legacy tree.

Code that can already run in-process (formulas, parsers, a packet fed from a buffer, session logic that does not need the database) gets its regression in the fix PR. `ItemContainer`, the world, and SQL do not. A test that mocks the surrounding singletons would freeze the current shape and be deleted during the redesign. For those defects the test is added in the same PR that cuts the seam and fixes the bug. The main branch does not carry a red test.

## Phases

### 1. Find

No production edits and no new test stack. The inherited tests stay in place and stay skipped.

SpotBugs and the focused search feed working notes. The invariant read files an issue only when the bar above is met. The tracking issue is opened in this phase, after Issues, the labels, the issue form, and the milestone exist.

### 2. Pin what already runs

A short tooling change comes first: delete the old tests and XML fixtures, add JUnit 6, AssertJ, and Mockito, set `testRelease` 21, configure the Surefire agent, and run unit tests on the JDK 25 CI job. JDK 11 keeps compiling only.

Then each open issue whose path runs in-process is closed by one PR that contains the regression and the fix. Issues that need the world or the database stay open.

### 3. Modernize along the open issues

One invariant at a time. The PR extracts the seam, adds the test, and fixes the defect. ArchUnit covers the new boundary. Testcontainers is added only when the defect is in SQL. Packages that were not touched are not backfilled with tests.

The tracking issue closes when every child is fixed or explicitly deferred with a reason.

## Out of this audit

- Windows 10 runtime qualification, the MySQL 8.4 migration rehearsal, and the deployment image. Those steps stay in the upgrade plan.
- A project-wide coverage percentage.
- A JUnit 5-to-6 side migration of the old tests. They are deleted.
- Rewriting the server so that it is testable, ahead of a filed defect.
- Datapack content.
- Upgrading Jython, Spring, or Hibernate.
