# MVP scope

## MVP name

Kavita Mobile Reader Alpha.

## Platform

Android first.

Reasoning:

- Easier sideloading.
- Faster device testing.
- Android TTS available.
- Tablets and foldables can be added after phone layouts.
- iOS can reuse shared core once domain/sync/source contracts are stable.

## Must-have features

### Connection

- Add Kavita server URL.
- Add Kavita API key.
- Test connection.
- Store credentials securely.
- Show auth/key expiration errors clearly.

### Library

- List Kavita libraries.
- List series/books.
- Show covers.
- Search if API support is straightforward.
- Show continue reading.
- Show recently added if API support is straightforward.

### Download/offline

- Download EPUB/PDF.
- Show download status.
- Open cached file offline.
- Avoid redownloading unchanged files when possible.

### Reader

- Open EPUB.
- Open PDF.
- Persist local progress.
- Support font size, line height, margins, theme.
- Support light/dark/sepia.
- Avoid dark-mode contrast bugs.

### Sync

- Pull remote progress from Kavita.
- Write progress to Kavita.
- Debounce writes.
- Handle offline mode.
- Avoid destructive overwrites.
- Show conflict if local and remote positions diverge meaningfully.

### TTS

- Android local TTS for EPUB text.
- Start from current reading position or current chapter.
- Pause/resume.
- Speed control.
- TTS progress updates reading position conservatively.

### CI

- Build shared core.
- Run unit tests.
- Build Android debug artifact if practical.

## Explicitly out of MVP

- iOS production app.
- iPad polish.
- Foldable polish.
- Calibre/Calibre-Web implementation.
- Audiobookshelf integration.
- AI TTS.
- Tolino/Kobo.
- Jailbroken Kindle.
- Annotations/highlights beyond what reader spike needs.
- Own sync server.
- DRM removal/bypass.

## MVP acceptance test

With two Android devices or one device plus a clean install simulation:

1. Add the same Kavita server.
2. Download an EPUB.
3. Read to a specific chapter/position.
4. Close the app.
5. Open on the second device.
6. Accept remote progress if prompted.
7. Continue near the expected location.
8. Use TTS for part of the chapter.
9. Stop TTS and verify reading progress is sensible.
10. Turn network off and verify local reading still works.
