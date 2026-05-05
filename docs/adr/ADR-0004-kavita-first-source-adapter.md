# ADR-0004: Kavita first source adapter

## Status

Accepted.

## Context

The user already runs Kavita and dislikes OPDS workflows. Kavita has a documented API and is the best first backend for a direct library experience.

## Decision

Implement Kavita as the first source adapter. Do not implement OPDS-first browsing for MVP.

## Consequences

- Kavita integration quality matters more than broad backend support in the beginning.
- The domain model must remain backend-neutral.
- Calibre/Calibre-Web are later adapters.
