# ADR-0002: No dedicated sync server for now

## Status

Accepted.

## Context

A dedicated sync server could centralize progress, annotations and cross-backend identity, but it would add deployment, authentication, security and maintenance work before the mobile app is proven.

The user explicitly prefers avoiding a separate sync server if possible.

## Decision

Do not build a dedicated sync server for the MVP. Use local-first progress storage in the app and sync to Kavita through the Kavita adapter where supported.

## Consequences

- Sync architecture must remain adapter-based.
- The app must store enough local detail to avoid data loss.
- Kavita becomes the first remote progress carrier.
- Future server work requires a new ADR.
- Calibre/OPDS adapters may be metadata/download-only until they can support safe progress semantics.
