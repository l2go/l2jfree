# Deploy

The delivery of Platform 3.0: one Linux image started with Docker Compose. The decision and its limits are in [ADR-0002](../docs/adr/0002-linux-image-delivered-with-compose.md). The [architecture views](../docs/architecture.md) show how the parts fit.

> **Work in progress.** Today the image carries the LoginServer, which runs on PostgreSQL 18. The world on port 7777 and the single process arrive in milestone M2 ([roadmap](../docs/roadmap.md)).

## Run it

```sh
docker compose -f deploy/compose.yaml up -d --wait
```

The stack starts three services: a one-shot `secrets` service that creates the database passwords, the `db` service (PostgreSQL 18, with a role and a schema per module, see [ADR-0009](../docs/adr/0009-database-roles-schemas-and-migration.md)), and the `login` service, which migrates its schema when it starts. `--wait` returns when they are healthy.

| Port | Service | State |
|---|---|---|
| 2106 | Login | Listening |
| 7777 | World | Published, no listener until M2 |

The image is private until the release. Log in to the registry first, or point `L2JFREE_IMAGE` at an image you built:

```sh
docker login ghcr.io
```

| Variable | Default | Meaning |
|---|---|---|
| `L2JFREE_IMAGE` | `ghcr.io/l2go/l2jfree:edge` | Image to run |
| `L2JFREE_BIND` | `0.0.0.0` | Host address that publishes the ports |

Stop with `docker compose -f deploy/compose.yaml down`. Add `-v` to delete the data volumes.

## Environments

| Environment | Status |
|---|---|
| Linux, Docker Engine with the Compose plugin | Verified in CI on every merge to `main` (amd64) |
| Windows, Docker Desktop with the WSL 2 backend | Verified by hand before the release |
| macOS on Intel, Colima | Verified by hand before the release |
| macOS on Apple Silicon, Colima | Runs through emulation; verified by hand before the release |

Colima does not include Compose. Install the Docker CLI and the Compose plugin next to it. On Apple Silicon start Colima with Rosetta for speed: `colima start --vm-type vz --vz-rosetta`.

## What the image contains

| Part | Choice |
|---|---|
| Base | Arch Linux, `linux/amd64`, pinned by dated tag and digest |
| Java | Eclipse Temurin 25 JRE archive, pinned by SHA-256 |
| User | `10001`, not root |
| File system | Read-only root; the work directory is a tmpfs; state lives in named volumes |
| Capabilities | All dropped, `no-new-privileges` set |

`deploy/verify-image.sh` checks these properties in CI. `deploy/probe/login_init.py` checks that the login port sends its first packet.

## Pipeline

Every pull request builds the image from the verified LoginServer distribution, runs the content check, starts the stack with `docker compose up -d --wait`, probes the login port, restarts the login service, and probes again. A merge to `main` also publishes `edge` and `sha-<commit>` images with a cosign signature, an SBOM, and build provenance. Versions tagged only `sha-*` are deleted after 30 days.

## Known limits

- Server features that depend on the client address, such as bans and connection limits, see the Docker gateway address on Docker Desktop and Colima.
- Players on other machines need a reachable host address; this is configured when the world arrives.
- `io_uring` is blocked by Docker's default security profile, so the network core uses epoll by default.
