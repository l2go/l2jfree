# Contributing

Thank you for looking. This project is run as an engineering record as much as a codebase, so the rules below are short and strict.

## Before you start

- Read the [vision](docs/PLATFORM-3.0-VISION.md), the [decision records](docs/adr/README.md), and the [roadmap](docs/roadmap.md). A change that contradicts an accepted decision needs a new decision record first.
- Look at the [Platform 3.0 board](https://github.com/users/l2go/projects/2). One milestone is open at a time.
- Open an issue with the form that fits: 🐛 defect, 🛠️ work package, 🧭 decision proposal, or ✨ feature. A security problem goes to a [private advisory](SECURITY.md).

## Rules

| Rule | Detail |
|---|---|
| Language | English in code comments, commits, issues, pull requests, and documents |
| Builds | Nothing is built or run on a workstation. GitHub Actions is the build and test environment |
| One change, one pull request | A defect is one root cause. A work package is one meaningful slice |
| Review | The maintainer merges by hand after the required checks pass and review threads are resolved. `main` takes squash merges only |
| Identity | Every commit must resolve to the `l2go` account and must not carry a `Co-authored-by` trailer. The `commit-identity` check enforces it |

## Commits

- Subject in the imperative, capitalized, no final period, up to about 70 characters.
- A body that explains why, wrapped near 72 columns. A defect fix starts the body with `Fixes #n`.
- Squash merge adds ` (#N)` to the subject.

## Pull requests

Fill in the template: the invariant, what changes for a running server, how it was verified, the decision and risk, and what is not included. Link the issue and set the milestone.

## Labels

| Group | Labels |
|---|---|
| Type | `bug`, `type:feature`, `type:task`, `type:docs`, `type:decision`, `type:epic`, `type:chore` |
| Severity | `severity:blocker`, `severity:major`, `severity:minor` |
| Area | `area:economy`, `area:packets`, `area:session`, `area:persistence`, `area:concurrency`, `area:commons`, `area:dependencies`, `area:observability`, `area:testing`, `area:release`, `area:docs`, `area:delivery`, `area:network`, `area:runtime`, `area:security` |
| Epic | `epic:direction`, `epic:pipeline`, `epic:platform`, `epic:operations` |
| Flow | `needs-decision` |

Severity measures impact on a running server. Priority and size live on the project board, not in labels.

## Conduct

This project follows the [Code of Conduct](CODE_OF_CONDUCT.md).
