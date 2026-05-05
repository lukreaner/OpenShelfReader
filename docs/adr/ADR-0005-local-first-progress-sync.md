# ADR-0005: Local-first progress sync

## Status

Accepted.

## Context

Reading must work offline and progress loss would destroy trust. Remote progress cannot be blindly trusted, especially across engines, file versions and devices.

## Decision

Persist local progress immediately and treat it as operational truth while reading. Pull remote progress as a candidate, reconcile conservatively and write back through a debounced queue.

## Consequences

- Sync tests are mandatory.
- Conflict UI is required before alpha quality.
- Progress model must be richer than a single page number.
- Completion state must be protected against accidental downgrades.
