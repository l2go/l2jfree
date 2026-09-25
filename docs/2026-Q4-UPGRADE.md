# 2026 Q4 Upgrade

## Purpose

Move the existing L2JFree Gracia Final server deployment from Windows 7 SP1 x64 to Windows 10 x64 and use a current 64-bit Java and database stack. Windows 10 is the approved target for this upgrade. Windows 7 compatibility is not a requirement for the upgraded deployment.

This document records the selected target and migration gates. Repository changes for the build toolchain, JDBC driver, and Windows launchers are in place; the new stack has not passed runtime qualification, and this upgrade does not change game behavior.

## Proposed target profile

| Component | 2026 Q4 target | Qualification status |
|---|---|---|
| Operating system | Windows 10 Pro 22H2 x64, with the final generally available updates released before end of support | Required target. Windows 10 22H2 is the final feature release; after October 14, 2025, Windows 10 receives no regular security, quality, or feature updates. Windows 10 host qualification is pending. |
| Java runtime and build JDK | Microsoft Build of OpenJDK 25 LTS x64, latest security update available at deployment | Selected target. Microsoft publishes a Windows x64 build and documents Windows 10-or-later installation. CI is configured for JDK 25; qualify this legacy application on Windows 10 before production use. |
| Java bytecode | Java 8 (`--release 8`) for the first upgrade | Configured in the Maven build to preserve the existing bytecode contract while using a modern JDK. Revisit separately after application compatibility is established. |
| Maven | Apache Maven 3.9.16 through the Maven Wrapper, with a pinned SHA-256 distribution checksum | Implemented in the wrapper. Do not use Maven 4 release candidates for this upgrade. |
| Database | MySQL 8.4 LTS, latest patch available at deployment | Retained as the target: it is the maintained LTS line and is the lowest-risk dialect upgrade. Oracle's current certified-platform matrix does not list Windows 10 for current LTS lines, so native Windows 10 operation requires project qualification and is not represented as Oracle-certified. If Oracle-certified database hosting is mandatory, run MySQL 8.4 on a supported database host and connect to it from the Windows 10 application host. |
| JDBC driver | MySQL Connector/J 26.7.0 | POM dependency, driver class, and repository configuration updated. Connection, schema, and workload qualification remain pending. |
| Native runtime | Current Microsoft Visual C++ 2015–2022 Redistributable x64 required by the selected database package | Install the database package's required x64 runtime. The old MySQL 5.7.37 / VC++ 2013 pairing is not the target. |
| Architecture | 64-bit OS, JVM, and database | Do not carry forward any 32-bit runtime or memory ceiling from the Windows 7 deployment. |

Version availability changes during the quarter. Recheck moving lines at deployment, record exact package versions and SHA-256 values, and keep that record with the release artifacts. The Maven distribution checksum is pinned in the wrapper. MySQL 8.0 is not the fallback target: version 8.0.46 reached end of life in April 2026, so it is suitable only as a temporary intermediate for a documented in-place migration, not as the upgraded production server.

## Compatibility boundaries

- The repository is configured to compile with Java 8 release compatibility; CI is configured for Microsoft OpenJDK 11 and 25. The new workflow has not yet run, and neither result proves that the server has been run on Windows 10 or JDK 25.
- The server includes Jython 2.2.1, Spring 2.0.2, and Hibernate 3.2.2. Keep these behavior-sensitive libraries unchanged during the OS/JDK/database move. Test script loading and runtime behavior before considering a separate dependency modernization.
- MySQL 5.7.37 is the existing prepared-deployment baseline. MySQL does not support a direct in-place upgrade from 5.7 to 8.4; the documented major-version path goes through 8.0 first. Use a rehearsed, backed-up migration and retain the original database for rollback.
- The JDBC configuration now names `com.mysql.cj.jdbc.Driver` for Connector/J 26.7.0. Connection, schema, and workload checks remain required.
- Windows launchers now resolve Java from `JAVA_HOME` when set and otherwise use `PATH`; obsolete commented Java 6 and PermGen examples were removed. Heap sizing still requires measurement on the qualification host.
- No Go source, Go module, or Go build step is present in this repository. Go is not part of this upgrade unless a separate external tool is identified.

## Windows 10 post-support operating constraint

This plan deliberately assumes no ESU subscription. Windows 10 continues to run, but Microsoft ended regular support on October 14, 2025, so the target host will not receive later Windows security fixes. Treat this as an accepted property of the chosen OS target, not as an update prerequisite. Reduce exposure by allowing only required game traffic through the firewall, keeping MySQL and administration interfaces off the public network, restricting remote administration to a trusted path, and maintaining tested offline backups.

## Migration sequence

### 1. Capture the Windows 7 baseline

Record the installed OS edition and service pack, Java bitness and exact build, MySQL version and configuration, VC++ runtimes, server ports, startup method, filesystem paths, and scheduled tasks. Export all databases and archive configuration, logs, and deployment packages. Verify that the backup can be restored to a disposable instance.

### 2. Prepare the Windows 10 host

Install Windows 10 Pro 22H2 x64 with the final generally available updates released before end of support. No ESU enrollment is part of this plan. Install the latest x64 runtime prerequisites for the chosen Java and database packages. Configure a dedicated non-administrator service account, firewall rules, time synchronization, log storage, and automatic startup/recovery.

### 3. Establish an unchanged-application checkpoint

Deploy the existing application and data on the Windows 10 host without changing game code or gameplay configuration. Use the exact Java and database versions captured from the running Windows 7 host for this checkpoint; the repository's JDK 11 CI baseline and MySQL 5.7.37 prepared package are not proof that those are the versions currently installed. This separates OS and path/permissions issues from later Java and database changes. Keep the Windows 7 host offline or isolated as the rollback copy; do not allow two live game servers to write to the same database.

### 4. Build and verify the new toolchain

The repository now pins Maven Wrapper 3.9.16, configures `--release 8`, and defines Microsoft OpenJDK 11/25 CI jobs. Run the workflow and verify all reactor modules build and package. Do not run the 1.4.0 package against MySQL 5.7: Connector/J 26.7 requires MySQL Server 8.4 or later. The server runtime qualification happens after the database migration below.

### 5. Migrate MySQL on a parallel instance

Keep MySQL 5.7.37 intact. Rehearse the documented 5.7 → 8.0 → 8.4 upgrade path on a full copy of the production database, or use a separately validated logical dump/restore procedure. Check the MySQL upgrade checker results, SQL modes, character sets/collations, indexes, stored objects, account authentication, and every repository SQL installer/update. Verify restore and rollback before proceeding; the live application remains on its current release and database throughout this rehearsal.

### 6. Qualify the combined target stack

On the Windows 10 qualification host, run the 1.4.0 package from a clean deployment layout against the migrated MySQL 8.4 instance. Check login and game server startup, Jython quest/script initialization, database reads and writes, scheduled events, clean shutdown, restart, and a sustained load window. Treat reflective-access, removed-API, SQL, authentication, or persistence failures as blockers. Keep the Windows 7 host and original database isolated as rollback copies.

### 7. Publish the release candidate

After the GitHub build matrix passes and the release contents are reviewed, push the `v1.4.0` tag to publish a GitHub prerelease candidate. The workflow checks that the tag matches the Maven project version, builds the three distribution ZIPs, computes SHA-256 checksums, and attaches them to the prerelease. This candidate is for deployment qualification; it is not a stable release and does not claim that Windows 10 runtime qualification has passed.

The first release carrying this upgrade is prepared as **1.4.0** (`v1.4.0`). Its release packages are portable JVM distributions and do not bundle Windows or MySQL installers.

### 8. Qualify and promote the exact deploy image

Build `l2jfree-deploy` from the three `v1.4.0` prerelease ZIPs in a clean staging tree. Add the qualified Java 25 / MySQL 8.4 runtime and site-specific data/configuration, and verify that the image contains no stale 1.3.0 JARs or MySQL 5.7 components. Run the deployment smoke and persistence checks against this exact image. After it passes, promote the existing GitHub prerelease to stable; do not rebuild or replace its ZIP assets.

### 9. Cut over and observe

Schedule a maintenance window, stop both server processes cleanly, take a final verified backup, and deploy the staged image built from `v1.4.0`. Monitor login success, exceptions, database errors, memory, CPU, network disconnects, and scheduled tasks. Keep the old host and original database unchanged until the agreed observation period passes.

### 10. Record the new supported profile

After the observation period, record the exact Windows, Java, Maven, database, JDBC, and VC++ versions used by the qualified release and deploy image. State clearly that the profile is Windows 10 x64 with no ESU, and record the post-support OS constraint and the MySQL Windows 10 qualification limitation.

## `l2jfree-deploy` consumer handoff

The current `repos/l2jfree-deploy` tree is the 1.3.0 deployment baseline: it contains JRE 11, MySQL 5.7.37, Connector/J 5.1.49, and 1.3.0 module JARs. It cannot be used unchanged with the 1.4.0 release because Connector/J 26.7 supports MySQL Server 8.4 and later. Rebuild the deploy image from the three `v1.4.0` release ZIPs in a clean staging tree and then apply only the intended site configuration and data. Do not overlay the release onto the old tree: obsolete JARs and Windows 7-era launch settings would remain. Qualify the database host separately because Oracle's supported-platform matrix does not list Windows 10 for MySQL 8.4.

## Verification snapshot (2026-09-26)

- GitHub Actions run [36194342679](https://github.com/l2go/l2jfree/actions/runs/36194342679) passed both build matrix jobs using Microsoft Build of OpenJDK 11 and 25. The workflow runs `./mvnw -B -ntp install`; tests remain skipped by default, so this run verifies compilation and packaging, not the test suites.
- Windows 10 runtime qualification, MySQL 8.4 integration, and the site-specific `l2jfree-deploy` image have not been qualified. Build that image from the exact prerelease ZIPs and record its smoke and persistence results before promoting the prerelease to stable.

## Exit criteria

- A clean Windows 10 x64 host can install and start the server after reboot without a developer workstation or manual IDE steps.
- `l2jfree-deploy` is assembled from the exact 1.4.0 release ZIPs in a clean staging tree and contains no 1.3.0 application JARs, Java 11 runtime, MySQL 5.7 server, or Connector/J 5.1 driver.
- CI produces release candidate archives with checksums using the pinned JDK and Maven versions; the exact candidate passes deploy-image qualification before promotion to a stable release.
- Login, gameplay entry, representative quests/scripts, persistence, backup, clean shutdown, restart, and rollback all pass on the qualification deployment.
- The explicitly enabled legacy test suites pass, or every remaining failure has a documented release disposition before the prerelease is promoted to stable.
- The exact Windows, Java, Maven, database, JDBC, and VC++ versions are recorded with the qualified release artifacts.
- The old Windows 7 deployment remains available only as an isolated rollback reference and is no longer the supported target.

## References

- [Windows 10 support and ESU](https://support.microsoft.com/en-us/windows/deployment/updates-lifecycle/windows-10-support-has-ended-on-october-14-2025)
- [Microsoft Build of OpenJDK downloads](https://learn.microsoft.com/en-us/java/openjdk/download)
- [Microsoft Build of OpenJDK support roadmap](https://learn.microsoft.com/en-us/java/openjdk/support)
- [Apache Maven release history](https://maven.apache.org/docs/history.html)
- [MySQL 8.4 supported platforms](https://www.mysql.com/support/supportedplatforms/database.html)
- [MySQL 8.0 end-of-life notice](https://dev.mysql.com/doc/relnotes/mysql/8.0/en/)
- [MySQL 5.7 to 8.4 upgrade note](https://dev.mysql.com/doc/relnotes/mysql/8.4/en/news-8-4-0.html)
- [MySQL Connector/J compatibility](https://dev.mysql.com/doc/connector-j/en/connector-j-versions.html)
- [MySQL Connector/J 26.7 release notes](https://dev.mysql.com/doc/relnotes/connector-j/en/news-26-7-0.html)
