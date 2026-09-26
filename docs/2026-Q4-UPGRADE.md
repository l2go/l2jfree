# 2026 Q4 upgrade: Windows 7 to Windows 10

## Target and status

| Component | Target |
|---|---|
| Host | Windows 10 Pro 22H2 x64, final regular updates, no ESU |
| Java | Microsoft OpenJDK 25 LTS x64; Java 8 bytecode (`--release 8`) |
| Build | Maven Wrapper 3.9.16 with a pinned SHA-256 checksum |
| Database | MySQL 8.4 LTS, latest patch available at deployment |
| Driver | Connector/J 26.7.0 (`com.mysql.cj.jdbc.Driver`) |
| Release | `v1.4.0` prerelease candidate; promote the same ZIPs after qualification |

The code and build configuration are prepared. On 2026-09-26, [CI run 36194342679](https://github.com/l2go/l2jfree/actions/runs/36194342679) passed on Microsoft OpenJDK 11 and 25. JDK 11 was the Windows 7 transition baseline and is no longer in the workflow. CI now compiles and packages on Ubuntu with Microsoft OpenJDK 25 only. Maven skips tests by default. Windows 10 operation, MySQL 8.4 integration, and the deployment image remain unqualified.

Windows 10 left regular support on October 14, 2025. Limit public ports to game traffic, keep MySQL and administration private, restrict remote access, and maintain restorable offline backups. Oracle's supported-platform table does not list Windows 10 for MySQL 8.4. Run the database on a supported host if certification is required; otherwise qualify it on Windows 10 as a project-specific deployment.

Connector/J 26.7 requires MySQL Server 8.4 or later. Do not run the 1.4.0 package against MySQL 5.7. Keep Jython 2.2.1, Spring 2.0.2, and Hibernate 3.2.2 unchanged during this migration; verify their runtime behavior before a separate dependency upgrade.

## Migration

1. **Capture the current host.** Record exact Windows, Java, MySQL, VC++ runtime, ports, paths, and startup settings.
   Back up databases, configuration, and packages; prove the database backup can be restored.
2. **Prepare Windows 10.** Install the required x64 runtimes. Set up a dedicated service account, firewall,
   time synchronization, logs, and automatic restart. Start the existing application with its current Java
   and database versions to isolate OS and path issues.
3. **Migrate a database copy.** Rehearse MySQL 5.7 → 8.0 → 8.4, or validate a logical dump and restore.
   Check the upgrade checker, SQL modes, character sets, indexes, stored objects, authentication, and repository SQL.
   Preserve the original database for rollback. MySQL 8.0 is an intermediate step, not the production target.
4. **Qualify the new stack.** Run the 1.4.0 login and game servers on Windows 10 with OpenJDK 25 and MySQL 8.4.
   Check script loading, login, gameplay entry, database writes, scheduled events, sustained load, shutdown, and restart.
   Resolve Java and SQL errors before release.
5. **Qualify the release image.** Assemble it from the three `v1.4.0` prerelease ZIPs and the selected runtimes.
   Verify checksums and exclude old JARs and MySQL 5.7 components. Repeat startup, persistence, backup, and rollback
   checks on this exact image; then promote the same release assets to stable.
6. **Cut over and record.** Stop the old servers, take a final verified backup, deploy the qualified image, and monitor
   errors and resource use. Keep the Windows 7 host and original database isolated until the observation period ends.
   Record exact OS, Java, Maven, MySQL, JDBC, VC++, and package checksum values with the release.

Never let old and new game servers write to the same database. The target is ready only when the exact release image starts after reboot, the checks above pass, and any enabled legacy test failures have an explicit release decision.

## References

- [Windows 10 support](https://support.microsoft.com/en-us/windows/deployment/updates-lifecycle/windows-10-support-has-ended-on-october-14-2025)
- [Microsoft OpenJDK downloads](https://learn.microsoft.com/en-us/java/openjdk/download)
- [MySQL supported platforms](https://www.mysql.com/support/supportedplatforms/database.html)
- [Connector/J compatibility](https://dev.mysql.com/doc/connector-j/en/connector-j-versions.html)
- [MySQL 8.4 upgrade notes](https://dev.mysql.com/doc/relnotes/mysql/8.4/en/news-8-4-0.html)
