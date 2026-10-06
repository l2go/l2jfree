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

### Changed

- The vision describes a Linux image delivered with Docker Compose on Docker Desktop, Colima, or Docker Engine, with ports 2106 and 7777.
- The documentation archive carries the whole `docs/` tree.

### Removed

- The dead plain-HTTP distribution repository in the root build file.

## [2.5.0] - 2026-10-05

Final release of the 2.x line. Java 25 bytecode, HikariCP, Jython 2.7.5b1, Logback loaded from `config/logback.xml`, the datapack inside the GameServer archive, unit tests in CI, and the dependency upgrades named in the v2.0.0 vulnerability report. See the [release](https://github.com/l2go/l2jfree/releases/tag/v2.5.0).

[Unreleased]: https://github.com/l2go/l2jfree/compare/v2.5.0...HEAD
[2.5.0]: https://github.com/l2go/l2jfree/releases/tag/v2.5.0
