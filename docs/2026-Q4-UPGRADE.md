# 2026 Q4 deployment qualification: Windows 10

> **Retired.** This document describes the 2.x line. v2.5.0 is its final release ([ADR-0008](adr/0008-retire-the-2x-line.md)). See the [documentation index](index.md).

## Target and status

| Component | Target |
|---|---|
| Host | Windows 10 Pro 22H2 x64, final regular updates, no ESU |
| Java | Microsoft Build of OpenJDK 25 x64; Java 25 bytecode (`--release 25`) |
| Build | Maven Wrapper 3.9.16 with a pinned SHA-256 checksum |
| Database | MySQL 8.4 LTS, latest patch available at deployment |
| Driver | Connector/J 26.7.0 (`com.mysql.cj.jdbc.Driver`) |
| Stable release | GitHub `v2.5.0`. The maintainer ran this tree on the Windows 10 and MySQL 8.4 host. `v2.0.0` is the previous stable release and was not re-qualified as its own three-archive image. |

Release `v1.5.1` was qualified on the target Windows 10 and MySQL 8.4 host. The maintainer ran the `v2.5.0` tree on that host and accepted it for release. `v2.0.0` changed the Java bytecode target and connection pool and was not given a separate host qualification. GitHub Actions remains the build and automated verification environment.

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
4. **Qualify the release image.** Assemble the server from the two distribution ZIPs in the GitHub
   [v2.5.0 release](https://github.com/l2go/l2jfree/releases/tag/v2.5.0): LoginServer and GameServer.
   The datapack is already inside the GameServer archive. Verify both with `SHA256SUMS.txt`, stage the image
   under `dist/l2jfree-deploy`, and install it at `C:\l2jfree\`. The maintainer completed this run for v2.5.0.
5. **Qualify runtime behavior.** Run the login and game servers with OpenJDK 25 and the MySQL 8.4 service.
   Check script loading, login, gameplay entry, database writes, scheduled events, sustained load, shutdown, and restart.
   Resolve Java and SQL errors before cutover.
6. **Cut over and record.** Take a final verified backup, deploy the qualified image, and monitor errors and resource use.
   Record exact OS, Java, Maven, MySQL, JDBC, VC++, and package checksum values with the release.

Never let old and new game servers write to the same database. The target is ready only when the exact release image starts after reboot and the checks above pass. CI runs the unit suite and the MySQL integration job before a tag is published.

## References

- [Windows 10 support](https://support.microsoft.com/en-us/windows/deployment/updates-lifecycle/windows-10-support-has-ended-on-october-14-2025)
- [Microsoft OpenJDK downloads](https://learn.microsoft.com/en-us/java/openjdk/download)
- [MySQL supported platforms](https://www.mysql.com/support/supportedplatforms/database.html)
- [Connector/J compatibility](https://dev.mysql.com/doc/connector-j/en/connector-j-versions.html)
- [MySQL 8.4 upgrade notes](https://dev.mysql.com/doc/relnotes/mysql/8.4/en/news-8-4-0.html)
