# Infrastructure modernization program

The original qualification slice, [issue #52](https://github.com/l2go/l2jfree/issues/52), is complete in release `v1.5.1`. The broader program continues under the public [2.0.0 infrastructure vision](INFRASTRUCTURE-MODERNIZATION-VISION-2.0.md) and its [GitHub parent issue](https://github.com/l2go/l2jfree/issues/59).

The fixed deployment platform is Windows 10 x64, Microsoft Build of OpenJDK 25, and MySQL Server 8.4. The program modernizes the Java baseline, JDBC pools and schema workflow, datapack scripting, network and concurrency diagnostics, dependency governance, and operational telemetry in qualified stages.

Issue #52 delivered JDBC for login persistence, removed the c3p0 synthetic-table requirement, and qualified the associated runtime fixes. The GameServer was already using JDBC. The KnownList deadlock that caused a restart during combat was fixed in release `v1.5.1` through [PR #58](https://github.com/l2go/l2jfree/pull/58).

The 2.0.0 vision defines the target architecture, explains technology choices, assigns exit evidence to each stage, and makes target-host qualification a release gate. Do not treat completion of issue #52 or a green CI build as completion of the full modernization program.
