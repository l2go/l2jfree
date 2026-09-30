# 2026 Q4 deployment qualification: Windows 10

## Target and status

| Component | Target |
|---|---|
| Host | Windows 10 Pro 22H2 x64, final regular updates, no ESU |
| Java | Microsoft Build of OpenJDK 25 x64; Java 25 bytecode (`--release 25`) |
| Build | Maven Wrapper 3.9.16 with a pinned SHA-256 checksum |
| Database | MySQL 8.4 LTS, latest patch available at deployment |
| Driver | Connector/J 26.7.0 (`com.mysql.cj.jdbc.Driver`) |
| Stable release | GitHub `v1.5.1`; the 2.0.0 infrastructure modernization is in progress |

Release `v1.5.1` was qualified on the target Windows 10 and MySQL 8.4 host. The 2.0.0 modernization changes the Java bytecode target and connection pool; qualify the resulting release image again before deployment. GitHub Actions remains the only build and automated verification environment.

Windows 10 left regular support on October 14, 2025. Limit public ports to game traffic, keep MySQL and administration private, restrict remote access, and maintain restorable offline backups. Oracle's supported-platform table does not list Windows 10 for MySQL 8.4. Run the database on a supported host if certification is required; otherwise qualify it on Windows 10 as a project-specific deployment.

Connector/J 26.7 is the currently pinned driver. The modernization vision records the staged script engine and dependency replacements; do not treat the old `v1.5.1` deployment image as a 2.0.0 qualification.

## Qualification and deployment

1. **Capture the target host.** Record Windows, Java, MySQL service, VC++ runtime, ports, paths, and startup settings.
   Back up databases, configuration, and packages; prove the database backup can be restored.
2. **Prepare Windows 10.** Install the required x64 runtimes and Microsoft OpenJDK 25. Install MySQL Server 8.4
   as a Windows service. Set up a dedicated application account, firewall rules, time synchronization, logs, and
   automatic restart.
3. **Validate the database copy.** Restore a logical backup into MySQL 8.4. Check SQL modes, character sets, indexes,
   stored objects, authentication, and repository SQL. Preserve the original database backup for rollback.
4. **Qualify the release image.** Assemble the server from the three ZIPs in the GitHub
   [v1.5.1 release](https://github.com/l2go/l2jfree/releases/tag/v1.5.1), verify them with `SHA256SUMS.txt`,
   and stage the image under `dist/l2jfree-deploy`. Install it at `C:\l2jfree\`.
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
