# OpenShelf Reader

OpenShelf Reader is an early-stage open-source mobile reading client for self-hosted ebook libraries.

The goal is a Kindle-like reading experience without being locked into Kindle DRM or OPDS-only workflows: open the app, connect a library server, browse books directly, download what you need, read comfortably, and trust that progress is handled safely.

> Working codename. Rename freely before the first public announcement.

## Product thesis

Most self-hosted ebook workflows are either OPDS download workflows or device-specific sync workflows. OpenShelf Reader should feel closer to a direct media-library client: the library is the main interface, not a file picker.

## Current architectural stance

- Kavita is the first backend adapter.
- The app is local-first.
- No dedicated sync server in the MVP.
- Progress sync uses the backend adapter when the backend supports progress.
- EPUB/PDF rendering should use proven reader engines, not a custom renderer.
- DRM circumvention is out of scope.
- Android comes first, then iOS/iPadOS.
- Tolino/Kobo and Kindle Homebrew are research tracks, not MVP targets.

## MVP

The first MVP is successful when a user can:

1. Connect to a Kavita instance with server URL and API key.
2. Browse libraries, series, books and covers directly inside the app.
3. Download EPUB/PDF files for offline use.
4. Open EPUB and PDF files in a comfortable reader.
5. Customize font size, line height, margins and theme.
6. Use dark mode without contrast/theme bugs.
7. Persist reading position locally.
8. Sync reading progress to/from Kavita with conservative conflict handling.
9. Use local Android TTS from the current reading position.
10. Build and test the project through GitHub Actions.

## Non-goals for the MVP

- No dedicated sync server.
- No Calibre/Calibre-Web implementation yet, only architecture hooks.
- No OPDS-first UX.
- No Kindle/Tolino app target.
- No cloud/AI TTS in the first milestone.
- No Whispersync-like Audiobookshelf alignment yet.
- No DRM removal or DRM bypass.

## Suggested stack

- Shared core: Kotlin Multiplatform.
- Android UI: Jetpack Compose.
- Local storage: SQLDelight or Room/KMP-compatible alternative after a spike.
- Networking: Ktor Client or another KMP-compatible HTTP client.
- Reader engine: Readium Kotlin Toolkit first spike.
- iOS later: SwiftUI plus shared KMP core, with Readium Swift Toolkit as the likely reader layer.

## Project docs

- `docs/01-architecture.md`
- `docs/02-mvp-scope.md`
- `docs/03-sync-without-dedicated-server.md`
- `docs/04-kavita-adapter-plan.md`
- `docs/05-tts-plan.md`
- `docs/06-platform-strategy.md`
- `docs/08-github-setup.md`
- `docs/09-license-decision.md`
- `docs/roadmap.md`
- `docs/adr/`

## Development workflow

1. Pick a focused issue or create one from the roadmap.
2. Work on a branch with a narrow scope.
3. Add tests where possible.
4. Keep backend-specific DTOs out of UI and sync code.
5. Review sync, secret handling and reader regressions carefully before merging.

## Building locally

Requirements:

- JDK 17 or a newer JDK supported by the selected Gradle and Android Gradle Plugin versions.
- Android SDK platform 35 and build tools 35.0.0.

Useful commands:

```bash
./gradlew :shared:core:jvmTest
./gradlew :apps:android:assembleDebug
```

## License

OpenShelf Reader is licensed under Apache-2.0. See `LICENSE` and `docs/09-license-decision.md`.
