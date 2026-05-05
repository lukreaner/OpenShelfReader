# Roadmap

This roadmap is intentionally conservative. The project should prove the Kavita-backed Android reading experience before expanding to additional backends, platforms or audiobook alignment.

## M0: Repository and architecture

- Create a minimal Kotlin Multiplatform scaffold.
- Add Android app shell.
- Add shared core module and first tests.
- Keep project documentation and ADRs up to date.

## M1: Source adapter foundation

- Define source adapter contracts.
- Implement Kavita authentication.
- Fetch libraries, series, books and covers.
- Keep Kavita DTOs out of UI and sync code.

## M2: Local storage and offline cache

- Add local schema for configured sources, normalized books, downloaded files and reading progress.
- Download and cache EPUB/PDF files.
- Store secrets through platform secure storage, not plain text.

## M3: Reader MVP

- Spike Readium EPUB integration.
- Add PDF reader integration.
- Persist local reading position.
- Add reader preferences for font size, line height, margins and theme.
- Fix dark mode/readability edge cases early.

## M4: Progress sync hardening

- Read remote progress from Kavita where supported.
- Write progress to Kavita conservatively.
- Add merge rules and tests.
- Add conflict UI for meaningful local/remote divergence.

## M5: Android local TTS

- Add local Android TTS for EPUB text.
- Support play, pause, resume and speed control.
- Keep cloud/AI TTS out of the MVP.

## M6: Android alpha

- Package an Android alpha build.
- Document setup, known limitations and supported formats.
- Collect real-world feedback before expanding scope.

## Later tracks

- iOS/iPadOS architecture spike.
- Calibre and Calibre-Web adapter design.
- TTS quality roadmap and optional cloud/provider architecture.
- Audiobookshelf linking and audiobook alignment research.
- Tolino/Kobo and jailbroken Kindle research tracks.
