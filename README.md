<div align="center">

<img src="assets/l2jfree-hero.png" alt="A dawn-lit mountain citadel traced with subtle connections between server nodes" width="100%">

# L2JFree

**Gracia Final · Protocol 83**

An archived Lineage II server, maintained for modern builds and Windows 10 deployment qualification.

[![Build](https://github.com/l2go/l2jfree/actions/workflows/build.yml/badge.svg)](https://github.com/l2go/l2jfree/actions/workflows/build.yml)
[![License: GPLv3](https://img.shields.io/badge/license-GPLv3-2563eb)](LICENSE)
![Java: 25](https://img.shields.io/badge/Java-25-2ea44f)

[Build](#quick-start) · [Upgrade](#windows-10-upgrade) · [Infrastructure](#infrastructure-modernization) · [Architecture](#architecture) · [Correctness](#correctness-program)

</div>

> **Target platform:** Windows 10 x64, Microsoft Build of OpenJDK 25, and MySQL Server 8.4.

## Quick start

Download the published [L2JFree release artifacts](https://github.com/l2go/l2jfree/releases). The release contains separate LoginServer, GameServer, and Datapack archives with a `SHA256SUMS.txt` manifest. The deployment target uses Microsoft OpenJDK 25 x64 and MySQL Server 8.4.

Build and package verification run in GitHub Actions with the checksum-pinned Maven Wrapper and Microsoft OpenJDK 25. Consult the [2.0.0 infrastructure vision](docs/INFRASTRUCTURE-MODERNIZATION-VISION-2.0.md) for the evolving Java baseline and delivery gates.

Operators can use the [operations runbook](docs/OPERATIONS-RUNBOOK.md) for release verification, database safety, startup/shutdown, and incident capture.

The [logging migration plan](docs/LOGGING-MIGRATION-PLAN.md) records the existing audit/gameplay log channels and the staged path to structured logging.

The [dependency inventory](docs/DEPENDENCY-INVENTORY.md) lists the current direct dependencies, selected platform components, and gated replacement candidates.

## Windows 10 deployment

| Component | Target |
|---|---|
| Host | Windows 10 Pro 22H2 x64 |
| Java | Microsoft Build of OpenJDK 25 x64 |
| Database | MySQL Server 8.4 installed as a Windows service; Connector/J 26.7.0 |

Both Windows server launchers reject Java runtimes whose major version is not 25. Set `JAVA_HOME` to the Microsoft JDK 25 installation or make that runtime the `java` command found through `PATH`.

The current stable release is [v1.5.1](https://github.com/l2go/l2jfree/releases/tag/v1.5.1). The private Windows deployment image is assembled from its three release archives and verified with their SHA-256 manifest. The [deployment qualification notes](docs/2026-Q4-UPGRADE.md) record the target and host checks.

## Infrastructure modernization

The first database qualification issue, [#52](https://github.com/l2go/l2jfree/issues/52), is complete in v1.5.1. The [modernization vision](docs/INFRASTRUCTURE-MODERNIZATION-VISION-2.0.md) defines the target architecture and staged 2.0.0 plan. Track implementation in [parent issue #59](https://github.com/l2go/l2jfree/issues/59).

Release archives include a checksum manifest and signed GitHub provenance. See the [release verification guide](docs/RELEASE-VERIFICATION.md) before deploying downloaded artifacts.

## Architecture

```mermaid
flowchart LR
    A[Commons] --> C[Login server]
    B[MMO core] --> C
    A --> D[Game server]
    B --> D
    E[Scripting engines] --> D
    D --> F[Datapack]
```

## Correctness program

The [1.4.0 Project](https://github.com/users/l2go/projects/1) shows the live backlog, priorities, milestones, and pull requests. The [audit decision record](docs/2026-Q4-CORRECTNESS-AUDIT.md) explains the filing bar and review gate. Each defect has one issue and one pull request; the maintainer merges after CI and manual review.

## Provenance and license

This fork descends from [savormix](https://github.com/savormix/l2jfree-genesis) through [lord_rex](https://github.com/l2jfree/l2jfree-ct2.3). The [original README](UPSTREAM_README.md) is preserved unchanged.

This noncommercial fan project is unaffiliated with NCSoft. It provides server source, not a client or hosted server. This fork added no client binaries or extracted client assets; inherited datapack text and HTML were not audited here. "Lineage 2" is a trademark of its owner. Code is distributed under [GPLv3](LICENSE).
