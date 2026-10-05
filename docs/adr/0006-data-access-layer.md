# 0006. Data access: targeted repository seam

- Status: Accepted
- Date: 2026-10-06

## Context

GameServer code calls JDBC directly from many game-logic classes. Moving to PostgreSQL means the SQL dialect has to change at those call sites. Placing every call behind repositories is the cleanest result and the most expensive.

## Decision

1. Put a repository seam in front of the data where PostgreSQL gives a new property: saving a player in one transaction, the world lock that stops a second server on the same database, and queues claimed with row locks.
2. Add the seam for two or three domains where it pays off most: inventory, player, and clan.
3. Move the remaining call sites with their dialect changed and no seam. A dependency rule limits direct database access to those places, and the number of violations must not grow.

## Alternatives considered

- Move every call as it stands. Fastest, but the dialect stays spread through the game logic with no boundary.
- Seam everywhere. The cleanest, but it dominates the schedule and is not required by the platform.

## Consequences

- The boundary is real where it matters and the rest is measured and frozen.
- The rule that limits direct access gives a visible metric that falls over time. Reducing it further is future work, not a release condition.

## Revisit when

A domain outside the seam causes a defect or a change that the seam would have contained.
