# Changelog

All notable changes are recorded here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Platform 3.0 ships as one release ([ADR-0007](docs/adr/0007-release-policy.md)), so this file collects the changes toward `v3.0.0` under Unreleased.

## [Unreleased]

### Added

- Decision records for Platform 3.0: delivery, process model, data, network core, data access, release policy, and retirement of 2.x.
- Roadmap, risk register, and documentation index.
- Platform 3.0 project board and issue forms for work packages, decisions, and features.
- Security policy, contributing guide, code of conduct, support guide, and code owners.
- Code scanning, dependency review, OpenSSF Scorecard, and a documentation link check.
- A CI check of the datapack Python scripts with Jython.
- The Platform 3.0 walking skeleton: a `linux/amd64` image on a pinned Arch Linux base with a pinned Temurin 25 JRE, a Docker Compose stack that publishes ports 2106 and 7777, a content check, and a first-packet probe of the login port.
- Publication of `edge` and `sha-*` images with a cosign signature, an SBOM, and build provenance, and a daily job that deletes `sha-*` versions older than 30 days.
- Architecture views as diagrams in `docs/architecture.md`.
- PostgreSQL 18 for the login module: the `login` schema and its Flyway migration, a shared migration runner, a pool that sends text untyped, and integration tests against a real database.
- A decision record for database roles, schemas, and migration at start, and the report of milestone M1.
- Monthly Dependabot updates for the image base and the compose images.

### Changed

- The vision describes a Linux image delivered with Docker Compose on Docker Desktop, Colima, or Docker Engine, with ports 2106 and 7777.
- The documentation archive carries the whole `docs/` tree.
- The Docker Compose stack runs PostgreSQL 18 instead of MySQL, with passwords generated on the first start.

### Removed

- The dead plain-HTTP distribution repository in the root build file.
- The Windows packaging job and the 2.x release job of the pipeline.
- MySQL from the login module, its SQL files and installer scripts, and the Liquibase compatibility test.

## [2.5.0] - 2026-10-05

Final release of the 2.x line. Java 25 bytecode, HikariCP, Jython 2.7.5b1, Logback loaded from `config/logback.xml`, the datapack inside the GameServer archive, unit tests in CI, and the dependency upgrades named in the v2.0.0 vulnerability report. See the [release](https://github.com/l2go/l2jfree/releases/tag/v2.5.0).

[Unreleased]: https://github.com/l2go/l2jfree/compare/v2.5.0...HEAD
[2.5.0]: https://github.com/l2go/l2jfree/releases/tag/v2.5.0
