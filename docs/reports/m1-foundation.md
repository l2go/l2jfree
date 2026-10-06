# Milestone report: M1. Foundation

Closed on 2026-10-06. Planned outcome: decisions recorded, vision revised, 2.x retired, documents structured, and a pipeline that builds a Linux image and starts it with Docker Compose in CI.

## Result against the exit criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | Decision records cover platform and delivery, process model, data, network, data access, release policy, and retirement of 2.x | Met | [ADR-0001 to 0008](../adr/README.md), pull request #122 |
| 2 | `docs/index.md` lists every document with its status and the vision matches the records | Met | [index](../index.md), [vision](../PLATFORM-3.0-VISION.md) |
| 3 | A pipeline builds the image, runs `docker compose up -d --wait`, and the login port answers with its first packet. Pushes to `main` publish `edge` and `sha-*` images | Met | Pull request #126: first packet of 186 bytes, before and after a restart of the login service; image published, signed, and attested after the merge |
| 4 | The required checks in the `main` ruleset match the new pipeline | Met | `build`, `commit-identity`, `integration`, and `image` are required; `windows-package` was removed |
| 5 | The risk register is reviewed | Met | [risk register](../risks.md) |

## Delivery

| Measure | Value |
|---|---|
| Pull requests merged | 4 (#122, #124, #125, #126) |
| Time from opening to merge | 5 minutes, 11 minutes, 11 minutes, and 5 minutes |
| Lines changed | 1,540 added and 416 removed across 22, 3, 16, and 16 files |
| Workflow runs since the first pull request | 37, all successful |

The numbers come from the GitHub API. One person writes and merges, so they describe flow, not team throughput.

## Deviations

- **A small pull request that should not have existed.** Pull request #124 (links to the new board) was a correction of #122 and belonged in #125. The rule since: a follow-up to an open pull request goes into it as another commit.
- **A required check for a retired line.** The Windows packaging check was made required during the milestone and had to be removed again when the pipeline replaced it. Required checks now change in the same step as the pipeline.
- **Image visibility.** The release policy says images stay private until the release. The first publication produced a public package because the package inherits the visibility of the repository, and visibility cannot be changed through the API. This is recorded as an open item for the maintainer.
- **The skeleton runs the login server only.** The world on port 7777 is published without a listener. This was planned and is stated in the deploy guide and in the roadmap.

## Risks

No risk was closed. R-2 (rolling base) and R-5 (`io_uring`) are mitigated by the pins and by epoll as the default. See the [risk register](../risks.md).

## What went well

- Decisions are written before code, so M2 starts from records, not from memory.
- The pipeline proved the image, the stack, and the probe on the first run.
- Every change was reviewed by the checks before the merge.

## What to change in M2

- Batch work into large packages and keep follow-ups inside the open pull request.
- Run the data checks against a real PostgreSQL in the pipeline from the first commit.
- Decide the image visibility before the next publication.
