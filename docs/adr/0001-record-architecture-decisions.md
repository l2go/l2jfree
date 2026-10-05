# 0001. Record architecture decisions

- Status: Accepted
- Date: 2026-10-06

## Context

The project is moving from the 2.x line to Platform 3.0. Many decisions are made at once, some by comparison and some by the maintainer alone. Without a record, the reason for a decision is lost, and later changes cannot tell whether a constraint was chosen or accidental.

## Decision

Keep one short record per architecture decision in `docs/adr/`, in English, numbered in order. Link each record from `docs/adr/README.md`. A decision that was not compared against alternatives is recorded as such, with its accepted risks and the conditions that would reopen it.

## Alternatives considered

- Keep decisions inside the vision document. It mixes rationale with plans and becomes long and stale.
- Use issues only. Issues close and lose context, and they are not versioned with the code.

## Consequences

Decisions become reviewable in pull requests. Each record costs a page of writing. Superseding a decision needs a new record.

## Revisit when

The record format stops fitting the decisions being made.
