# ADR-0001: Mobile first, Android first

## Status

Accepted.

## Context

The project targets Android, iOS/iPadOS, Tolino and possibly jailbroken Kindle in the long term. Trying to build all targets at once would make the MVP too broad.

## Decision

Start with Android phone as the first production target. Build shared core in a way that iOS can reuse it later. Tablet/foldable polish follows once the core Android experience works.

## Consequences

- Android-specific reader and TTS spikes can happen first.
- iOS is not forgotten, but it is not a blocker for MVP.
- Tolino/Kindle are research tracks only.
