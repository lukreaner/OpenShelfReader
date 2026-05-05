# ADR-0003: Kotlin Multiplatform shared core

## Status

Accepted for first implementation spike.

## Context

The app should eventually support Android and iOS. The most valuable shared parts are not UI, but domain models, backend adapters, sync rules, local identity logic and tests.

## Decision

Use Kotlin Multiplatform for shared business logic. Keep platform-specific UI, reader integration, TTS and secure storage behind interfaces.

## Consequences

- Android can ship first without throwing away core logic.
- iOS can later reuse source adapters and sync logic.
- Reader engine integration remains platform-specific.
- Implementation tasks should avoid pushing Android-only domain logic into app UI modules.
