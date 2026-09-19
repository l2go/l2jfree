<div align="center">

# l2jfree

### Gracia Final. Protocol 83. Frozen since 2015 — building again since today.

[![build](https://github.com/l2go/l2jfree/actions/workflows/build.yml/badge.svg)](https://github.com/l2go/l2jfree/actions/workflows/build.yml)
[![license: GPLv3](https://img.shields.io/badge/license-GPLv3-blue.svg)](LICENSE)
[![JDK 11 · Windows 7 SP1 x64](https://img.shields.io/badge/target-JDK%2011%20%C2%B7%20Win7%20SP1%20x64-orange.svg)](#requirements)

</div>

> **Fan-made, non-commercial. Not affiliated with or endorsed by NCSoft.**
> "Lineage 2" is a trademark of its owner. No client binaries or extracted assets (art, audio,
> models) here or added by this fork. The upstream datapack's text content (NPC dialogue
> templates, HTML) is inherited from upstream under GPLv3, not authored or added here — this fork
> keeps the build tooling current, it doesn't audit or strip that inherited content. This project
> did not reverse-engineer anything itself; `l2jfree` upstream's own history predates this fork by
> years and isn't something this README can retroactively characterize.

## Lineage

```
savormix (2010) → lord_rex / l2jfree-ct2.3 (2015) → this fork (today)
```

Unmodified original: [`UPSTREAM_README.md`](UPSTREAM_README.md).

## Module graph

```mermaid
graph TD
    mod[l2jfree-module<br/><sub>parent · build config</sub>] -.-> commons[l2j-commons]
    mod -.-> mmocore[l2j-mmocore]
    mod -.-> script[l2jfree-scripting-engines]
    mod -.-> core[l2jfree-core]
    mod -.-> login[l2jfree-login]
    mod -.-> datapack[l2jfree-datapack]
    commons --> core --> datapack
    mmocore --> core
    script --> core
    commons --> login
    mmocore --> login
```

## Build

```sh
git clone git@github.com:l2go/l2jfree.git && cd l2jfree && ./mvnw install
```

On Windows: `mvnw.cmd install`. No separately installed Maven required — the wrapper fetches the
exact pinned version (3.6.3, checksum-verified) on first run. Only a JDK is your responsibility.

Verified on JDK 11 by CI on every push, via the same `./mvnw` anyone else would run — never taken
on faith, never built on one person's machine.

<details>
<summary><strong>What changed, and why</strong> — build tooling only, zero game logic touched</summary>

| Change | Reason |
|---|---|
| Root `pom.xml` | The reactor already existed, just rooted at `l2jfree-module/` instead of repo root. This is a 3-line delegator. |
| `maven-compiler-plugin` 3.3 → 3.8.1 | Only real JDK 11 risk in the plugin set. Bytecode level unchanged (still `1.8`). |
| `mysql-connector-java` 5.1.36 → 5.1.49 | Last 5.1.x release; fixes 3 known CVEs without jumping to the 8.x driver line. |
| `c3p0` 0.9.5.1 → 0.9.5.4 | Same 0.9.5.x branch; fixes CVE-2018-20433 (XXE, critical) and CVE-2019-5427 (billion-laughs XML expansion). |
| `maven-antrun-plugin` pinned to `1.8` | Was unpinned and had silently drifted to a release that broke the build's `<tasks>` syntax — a real bug CI caught on the first run, not a style choice. |
| CI added | Build health now lives on a reproducible JDK 11 runner. |
| Maven Wrapper (`mvnw`/`mvnw.cmd`) added | Pins the exact Maven version (checksum-verified) instead of requiring a separately installed, correctly-versioned Maven — one less manual step for anyone self-hosting this on the target OS. |

**Left alone, on purpose:**
- `spring` 2.0.2, `spring-mock` 2.0.2, `hibernate` 3.2.2.ga — old enough to be a plausible security
  risk, but bumping them is a behavioral gamble across a decade of changes, not a bounded fix.
- `jython` 2.2.1 — has a known, real vulnerability (CVE-2013-2027, local file-permission issue via
  umask handling). Not bumped: the next line (2.5+) is a materially different Python-language
  implementation, and this pack's 449 AI/quest scripts are written against 2.2's specific behavior —
  the same "behavioral gamble, not a bounded fix" reasoning as Spring/Hibernate above.

Both are flagged here deliberately, not silently carried or silently patched.

</details>

<details>
<summary><strong>Requirements</strong> — pinned to a specific target, not "whatever's newest"</summary>

| | Version | Why |
|---|---|---|
| OS (server-side) | Windows 7 Pro SP1 x64 | The floor everything below is chosen for. Client-side is unrestricted. |
| JDK | 11 (LTS) | Last LTS Oracle's certified configs list for Windows 7 SP1. |
| Maven | 3.6.3, via `./mvnw` — no manual install needed | JDK 11 compatibility, not an OS constraint. |
| MySQL | 5.7.37 specifically | Its own docs name Windows 7 SP1 explicitly; 5.7.37 also avoids a VC++ runtime dead end (see below). |
| Git | 2.46.2 | Last release before Windows 7/8 support was dropped upstream. |

**Deploying on the target OS — what you actually need to install:**

- **JRE 11**, not the full JDK, is enough to *run* a built release (see [Releases](../../releases)):
  [Temurin 11 JRE, Windows x64 MSI](https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.32.1%2B1/OpenJDK11U-jre_x64_windows_hotspot_11.0.32.1_1.msi)
  (`sha256: 2ee24ab2946b0454463bb38f5d9b2d7e4d2620af7f4fb46df6f313f32635ccdd`) — the same distribution CI
  builds with.
- **MySQL 5.7.37**, not the newer 5.7.44 — deliberately: [MySQL Archives — pick `mysql-5.7.37-winx64.zip`](https://downloads.mysql.com/archives/community/?product=mysql-installer-community&version=5.7.37)
  (Oracle blocks direct hotlinks to the file itself; use the archive page). 5.7.38+ needs the
  ever-updating "2015–2022 unified" VC++ runtime, whose *current* build supports only Windows
  10/11 with no archived older build available from Microsoft. 5.7.37 needs only the legacy,
  frozen VC++ 2013 package instead (see next item) — a version Microsoft stopped updating years
  ago, so its download link can't drift out from under this project the way the newer one did.
- **Visual C++ 2013 Redistributable**, version `12.0.40664.0` — install both, as is standard practice
  (x64 for 64-bit MySQL, x86 alongside it for any 32-bit components/tools):
  [x64 — `vcredist_x64.exe`](https://aka.ms/highdpimfc2013x64enu) ·
  [x86 — `vcredist_x86.exe`](https://aka.ms/highdpimfc2013x86enu)
  — both confirmed resolving, confirmed to support Windows 7 SP1. Do **not** substitute the newer
  "2015–2022" VC++ package here; it's a different runtime line and its current build has dropped
  Windows 7 support entirely.

</details>

## Not this

Not a modified/redistributed client — get that yourself, officially, unmodified. Not a live server
operated by anyone here — just buildable source, run it on your own infrastructure if you want it.

## License

GPLv3 — [`LICENSE`](LICENSE). Every source file already carries its own header; this just adds the
file the headers implied.

## Elsewhere

[L2JFree Genesis](https://github.com/savormix/l2jfree-genesis) · [l2jfree/l2jfree-ct2.3](https://github.com/l2jfree/l2jfree-ct2.3)
