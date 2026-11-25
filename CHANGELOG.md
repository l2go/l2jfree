# Changelog

Notable changes, in [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) format.

## [Unreleased]

## [3.3.1] - 2026-11-25

### Changed

- Movement, knownlist, effects, AI and client packet logic share one platform thread, so two tasks cannot write one creature at the same time. Scheduled database work (global tasks, the item flush, dropped items, the game clock, character load) runs on virtual threads and does not hold a player or item monitor. One session has one character load: disconnect or a newer selection drops it, and a dropped load is not entered and does not disconnect a character the replacement already entered. An item insert writes its augmentation and elemental attribute on the same connection; a change during the write cannot turn the next write into a duplicate insert or an update of a missing row, and a queued write cannot land after a newer one. A rift tick, an Olympiad step, a Festival step and a GM command apply as one step on the world thread, and that step does not wait for the world while holding a creature monitor. Logout's save is the one that is written; a later snapshot does not replace it and a skipped snapshot is not reported as saved. A handed-off world step reports the failure it threw, and a closed world or an interrupt finishes the wait. `BaseThreadPoolSize` and `ExtraThreadPerCore` are no longer read.
- Several actions still wait for the database on the world thread: a logout's own save, the commit of a multisell exchange, an admin command, and the creation of a character. A slow database holds the other world tasks for that time. A task started through the thread pool that runs longer than five seconds is logged, and an admin command that runs that long tells the GM. No load measurement of this has been made yet.

### Fixed

- A packet that writes no byte, as one does when its player left after it was queued, is not sent as an empty frame. The client no longer reads a frame with nothing in it after a logout.
- The autosave copies the character row, the subclasses, the cooldowns and the effects on the world thread, where they change, and writes that copy in the background. A summoned pet is still read by the write, and so is the inventory when `UpdateItemsOnCharStore` is on; it is off by default. A character that already left the world is not saved again on disconnect, so no lost save is logged for it. A background task that wakes up while the server stops is skipped quietly, and a global task whose previous run is still going logs that its run was skipped.

## [3.3.0] - 2026-11-14

### Added

- The server reaches readiness on an empty or partial world and logs each missing component. A missing datapack or catalog directory stops startup and names the configuration key and path.
- The image check fails when a datapack script fails to load.

### Changed

- Vitality is off by default, so kill rewards follow the configured rates without a vitality bonus; set `EnableVitality = True` in `vitality.properties` to bring it back.
- The image is published for linux/arm64 as well as linux/amd64; its base is Debian 13 slim instead of Arch Linux. The distribution adds the native network transports for linux/arm64. Security updates published for that Debian release are applied at build time; the base snapshot stays pinned.
- An ordinary monster's rewards reflect the contribution of each solo player or party. Experience and SP are proportional to actual HP damage. Coins and items use contribution weighted by level, with a participation threshold of 10% of the total weight; a solo player or party with at least 60% of the total weight receives the items. Players far above the monster's level have less loot weight. Within a party, experience, SP, and coins are shared equally; items follow the party's loot rules. Only players alive and within reward range when the monster dies receive rewards. A party that is the only side is paid at the moment of death.
- Loading an inventory skips an item whose template is missing, logs one warning, and keeps its database row.
- Flyway sets the session notice level in a callback instead of `initSql`.
- The Java engine moved to `engine/java`; module artifacts keep their names. The datapack remains a reactor module and is packed in the distribution.
- Added the command that verifies a release archive against its Sigstore bundle to the security policy.
- Added a one-sentence description to the front page; removed its support notice, which the security policy already states.
- Replaced the front page cards with six engineering decisions and how each is enforced, placed before the architecture; removed the stack line from the banner.
- Linked the project site at https://l2go.github.io/l2jfree/ from the front page (the banner, a badge, the navigation, and the line under the engineering cards), from the release bundle guide, and from the notes of every release.
- Named NCSOFT Corporation as the owner of the Lineage II and NCSOFT trademarks on the front page and in the release bundle, and stated that the project is independent, adds no game content, and does not modify or distribute the game client.

### Fixed

- A merchant can trade in a world without castles.
- A broken extensions file is reported as broken rather than missing.
- Killing a monster gave no experience or SP.
- A party share of experience was applied twice, and a party lost the coins left over from an equal split.
- Entering the world failed without a dimensional rift and logged errors for lairs without a zone and for castles without a zone. The hourly door task logged an error when its door was absent.
- A script rejected before loading, such as a Java script without bytecode, was missing from the loading summary.
- Releases are titled by their tag, for example `v3.1.1`, instead of `Platform 3.1.1`.

### Removed

- `UPSTREAM_README.md`; it remains in the history.

## [3.1.1] - 2026-10-12

Gameplay is unchanged from 3.1.0. This release updates documentation and delivery checks.

### Changed

- Pinned GitHub Actions by commit; Dependabot maintains the pins.
- Made the container package public, including `edge` and release images.
- Shortened the README, deploy guides, security policy, and contributing rules.
- Added CI checks for the configuration commands in the deploy guides.
- Added `l2jfree-<version>-compose.tar.gz.sigstore.json` beside the release archive and checksum.
- Limited image-retention write permissions to the cleanup job.
- Removed `maven-wrapper.jar`; the wrapper downloads Maven directly.

### Fixed

- The backup command of the release guide uses `-T` and dumps only `login` and `world`, as the restore expects.

### Removed

- Obsolete 2.x guides and duplicated plans, design records, and reports.

## [3.1.0] - 2026-10-12

### Fixed

- Login refusals no longer close a newer connection. Password changes check the verified hash; remote administration receives its generated password; security cards require the configured id.
- Disconnect saves rejoin the packet queue. Pets are marked saved only after a successful insert.
- Fixed item-weight overflow and NPC sales above the adena limit.
- Fixed stock reservation and refunds for failed NPC purchases. Buy stores validate the selected item and the buyer's capacity, and refund failed transfers.
- Failed trades roll back transferred items and no longer report success. Manufacture accepts only recipes listed by the shop.
- Manor orders validate the castle, sale limits, price range, and reward type.
- Failed character creation releases the name. Subclass replacement is transactional; failed deletion preserves the subclass; effects remain on the old class until restoration.
- Party-room joins update the waiting list only after taking a seat; broadcasts use a stable member list. Clan rosters are visible only to allies and GMs.

## [3.0.0] - 2026-10-12

A 2.x database cannot be migrated; start with a fresh database.

### Added

- A `linux/amd64` Docker image on pinned Arch Linux and Temurin 25, delivered with Docker Compose. Login and world run in one JVM on ports 2106 and 7777.
- PostgreSQL 18, separate database roles, startup migrations, and atomic saves. Game content is loaded into `catalog`; players, items, and clans are stored in `world`.
- A typed login/world contract, architecture checks, and a startup lock against a second server using the database.
- Integration tests, SQL preparation and JDBC type checks, and a generated data dictionary checked in CI.
- Signed `edge` and `sha-*` images with SBOMs and provenance; retention of `sha-*` images for 30 days.
- Release verification, image scanning, a compose bundle pinned by digest, and publication after maintainer approval.
- CI checks for concurrent clients, role isolation, datapack script loading, restart persistence, configuration overrides, backup/restore, image updates, and graceful shutdown.
- Configuration overrides, connection limits, a 60-second container stop timeout, and a 30-second client shutdown grace period.
- Query statistics and slow-query plans, incident capture, startup measurements, and a rolling 12-hour flight recording capped at 256 MB.
- Security and contribution policies, issue forms, code owners, scanning, Dependabot updates, and documentation link checks.
- Design records, architecture views, plans, and reports, later removed in 3.1.1.
- Secure random session keys and generated administration passwords. Passwords use salted PBKDF2-HMAC-SHA256 with 600,000 iterations; legacy account hashes are upgraded at login.

### Changed

- Replaced MySQL with PostgreSQL and separate module roles; database passwords are generated on first start.
- Combined login and world into one service. Deployment uses Docker Desktop, Colima, or Docker Engine.
- Enabled epoll by default with an executable directory for native libraries; unavailable transports stop startup. G1 without an AOT cache is the default.
- An online world reports its age limit in the server list; before, it reported none.

### Fixed

- Restored datapack script initialization with Jython 2.7.4 and checked script loading in CI.
- Fixed shutdown loops and concurrent collection changes affecting raids, Olympiad, parties, petitions, sieges, items, warehouses, scripts, and quest timers.
- Fixed lost connection-count updates, empty NPC skill handling, and SQL parameter/type mismatches.
- Restored server logs to container output. Packet queues no longer leave packets unread; periodic tasks continue after a logged error.
- Fixed Tower of Naia door lists, Benom spawns and siege timers, quest timer collisions, blocking quest handlers, and stale quest spawns.
- Fixed GM checks and connection leaks in telnet administration, and custom drops missing after an NPC reload.
- Fixed pet creation, rolled-back saves, stale character names, duplicate Olympiad registration, and repeated player deletion. Shutdown flushes the remaining SQL queue.
- Login updates preserve concurrent bans and passwords; stale disconnects no longer remove newer sessions. Failed character creation replies and releases its id.
- Subclass saves no longer change the live class index. Failed item saves preserve rows and save flags for retry.
- Password comparisons check the whole value in constant time; an empty stored hash no longer accepts every password.

### Removed

- MySQL, its installers, SQL files, update scripts, backups, table optimizer, and Liquibase compatibility test.
- Separate module distributions, launchers, the login/world socket protocol, server registration, hex ids, and login telnet status.
- Windows packaging, the 2.x release job, the dead HTTP dependency repository, and optional 2.x content mods.
- Factions, community-board item auctions, deleted-mail archives, the Rebuild id factory, the Compaction option, and the Olympiad start-values file.

## [2.5.0] - 2026-10-05

Final 2.x release: Java 25 bytecode, HikariCP, Jython 2.7.5b1, Logback configuration,
the bundled datapack, CI unit tests, and dependency upgrades. See the
[release](https://github.com/l2go/l2jfree/releases/tag/v2.5.0).

[Unreleased]: https://github.com/l2go/l2jfree/compare/v3.3.1...HEAD
[3.3.1]: https://github.com/l2go/l2jfree/compare/v3.3.0...v3.3.1
[3.3.0]: https://github.com/l2go/l2jfree/compare/v3.1.1...v3.3.0
[3.1.1]: https://github.com/l2go/l2jfree/compare/v3.1.0...v3.1.1
[3.1.0]: https://github.com/l2go/l2jfree/compare/v3.0.0...v3.1.0
[3.0.0]: https://github.com/l2go/l2jfree/compare/v2.5.0...v3.0.0
[2.5.0]: https://github.com/l2go/l2jfree/releases/tag/v2.5.0
