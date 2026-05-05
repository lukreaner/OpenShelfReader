# Product vision

OpenShelf Reader aims to become a modern self-hosted reading app for people who own or manage their own ebook files and want a Kindle-like experience without Amazon lock-in.

## Problem

Existing workflows tend to fall into one of these buckets:

- Kindle: polished reading, TTS and sync, but strong vendor lock-in and DRM concerns.
- OPDS clients: flexible discovery/download, but not a direct library experience.
- KOReader: powerful, especially on E-Ink, but not comfortable for everyone.
- Calibre-Web/Kobo sync: useful, but progress semantics can become device-specific.
- Readest-like apps: promising, but still often feel like download-first clients around OPDS or import workflows.

## Product promise

Open the app and directly interact with your own library.

The app should make a Kavita library feel first-class:

- Continue reading.
- New books.
- Series.
- Collections.
- Search.
- Offline downloads.
- Reading progress.
- Local TTS.

The reader should feel calm, predictable and trustworthy. The user should not need to think about OPDS feeds, progress stores or where a book was downloaded from.

## Guiding principles

1. Direct library UX beats catalog/download UX.
2. Reliable progress sync beats feature count.
3. Local-first behavior beats network dependency.
4. Backend adapters are replaceable.
5. Reader engines are dependencies, not things to reinvent.
6. E-Ink support is desirable, but mobile must work first.
7. TTS is a core reading mode, not a gimmick.
8. DRM circumvention is out of scope.
