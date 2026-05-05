# ADR-0006: Do not build a custom EPUB/PDF engine

## Status

Accepted.

## Context

EPUB and PDF rendering are complex. A custom engine would delay the project and likely create accessibility, layout and correctness issues.

## Decision

Use an established reader toolkit. The first spike should evaluate Readium Kotlin Toolkit for Android EPUB/PDF support and locator/progress behavior.

## Consequences

- Reader engine should be wrapped behind app-owned interfaces.
- The app must adapt to engine capabilities and limitations.
- Engine evaluation results must be documented before deeper integration.
