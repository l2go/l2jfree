# Infrastructure modernization program

The original qualification slice, [issue #52](https://github.com/l2go/l2jfree/issues/52), is complete in release `v1.5.1`. Stable [v2.5.0](https://github.com/l2go/l2jfree/releases/tag/v2.5.0) is the current release. [v2.0.0](https://github.com/l2go/l2jfree/releases/tag/v2.0.0) remains published as the previous stable release. The public [2.0.0 infrastructure vision](INFRASTRUCTURE-MODERNIZATION-VISION-2.0.md) records that line and the 2.5.0 execution status. Parent issue [#59](https://github.com/l2go/l2jfree/issues/59) tracked the work that produced v2.0.0. The maintainer accepted the v2.5.0 tree on Windows 10 and MySQL 8.4. Vulnerability triage remains open in [#92](https://github.com/l2go/l2jfree/issues/92).

The fixed deployment platform is Windows 10 x64, Microsoft Build of OpenJDK 25, and MySQL Server 8.4. The program modernizes the Java baseline, JDBC pools and schema workflow, datapack scripting, network and concurrency diagnostics, dependency governance, and operational telemetry in qualified stages.

Issue #52 delivered JDBC for login persistence, removed the c3p0 synthetic-table requirement, and qualified the associated runtime fixes. The GameServer was already using JDBC. The KnownList deadlock that caused a restart during combat was fixed in release `v1.5.1` through [PR #58](https://github.com/l2go/l2jfree/pull/58).

The 2.0.0 vision defines the target architecture, explains technology choices, assigns exit evidence to each stage, and makes target-host qualification a release gate. Do not treat completion of issue #52 or a green CI build as completion of the full modernization program.
