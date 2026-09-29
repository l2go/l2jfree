# 2026 Q4 deployment qualification: Windows 10

## Target and status

| Component | Target |
|---|---|
| Host | Windows 10 Pro 22H2 x64, final regular updates, no ESU |
| Java | Microsoft OpenJDK 25 LTS x64; Java 8 bytecode (`--release 8`) |
| Build | Maven Wrapper 3.9.16 with a pinned SHA-256 checksum |
| Database | MySQL 8.4 LTS, latest patch available at deployment |
| Driver | Connector/J 26.7.0 (`com.mysql.cj.jdbc.Driver`) |
| Release | GitHub `v1.4.0` release; qualify the three release ZIPs before deployment |

The code and build configuration are prepared. On 2026-09-26, [CI run 36194342679](https://github.com/l2go/l2jfree/actions/runs/36194342679) passed with Microsoft OpenJDK 25. CI compiles and packages on Ubuntu with that JDK. Maven skips tests by default. Windows 10 operation, the MySQL 8.4 service integration, and the deployment image remain to be qualified on the target host.

Windows 10 left regular support on October 14, 2025. Limit public ports to game traffic, keep MySQL and administration private, restrict remote access, and maintain restorable offline backups. Oracle's supported-platform table does not list Windows 10 for MySQL 8.4. Run the database on a supported host if certification is required; otherwise qualify it on Windows 10 as a project-specific deployment.

Connector/J 26.7 requires MySQL Server 8.4 or later. Keep Jython 2.2.1, Spring 2.0.2, and Hibernate 3.2.2 unchanged during deployment qualification; verify their runtime behavior before a separate dependency upgrade.

## Qualification and deployment

1. **Capture the target host.** Record Windows, Java, MySQL service, VC++ runtime, ports, paths, and startup settings.
   Back up databases, configuration, and packages; prove the database backup can be restored.
2. **Prepare Windows 10.** Install the required x64 runtimes and Microsoft OpenJDK 25. Install MySQL Server 8.4
   as a Windows service. Set up a dedicated application account, firewall rules, time synchronization, logs, and
   automatic restart.
3. **Validate the database copy.** Restore a logical backup into MySQL 8.4. Check SQL modes, character sets, indexes,
   stored objects, authentication, and repository SQL. Preserve the original database backup for rollback.
4. **Qualify the release image.** Assemble the server from the three ZIPs in the GitHub
   [v1.4.0 release](https://github.com/l2go/l2jfree/releases/tag/v1.4.0), verify them with `SHA256SUMS.txt`,
   and stage the image under `dist/l2jfree-1.4.0-win10-x64-mysql8.4-r1/`. Install it at `C:\l2jfree\`.
5. **Qualify runtime behavior.** Run the login and game servers with OpenJDK 25 and the MySQL 8.4 service.
   Check script loading, login, gameplay entry, database writes, scheduled events, sustained load, shutdown, and restart.
   Resolve Java and SQL errors before release.
6. **Cut over and record.** Take a final verified backup, deploy the qualified image, and monitor errors and resource use.
   Record exact OS, Java, Maven, MySQL, JDBC, VC++, and package checksum values with the release.

Never let old and new game servers write to the same database. The target is ready only when the exact release image starts after reboot, the checks above pass, and any enabled legacy test failures have an explicit release decision.

## References

- [Windows 10 support](https://support.microsoft.com/en-us/windows/deployment/updates-lifecycle/windows-10-support-has-ended-on-october-14-2025)
- [Microsoft OpenJDK downloads](https://learn.microsoft.com/en-us/java/openjdk/download)
- [MySQL supported platforms](https://www.mysql.com/support/supportedplatforms/database.html)
- [Connector/J compatibility](https://dev.mysql.com/doc/connector-j/en/connector-j-versions.html)
- [MySQL 8.4 upgrade notes](https://dev.mysql.com/doc/relnotes/mysql/8.4/en/news-8-4-0.html)
