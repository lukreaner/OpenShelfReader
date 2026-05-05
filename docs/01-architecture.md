# Architecture

## Overview

OpenShelf Reader should be structured as a modular mobile app with a shared core and platform-specific shells.

```text
apps/
  android/          Android app shell and UI
  ios/              iOS app shell, added after Android MVP

shared/
  core/             Domain models and use cases
  source-api/       SourceAdapter contracts
  kavita-api/       Kavita adapter/auth implementation
  sync/             Local-first progress sync and conflict rules
  storage/          Local database abstractions
  reader-api/       Reader-engine abstraction layer
  tts-api/          TTS provider abstraction
```

The initial implementation should create a minimal Kotlin Multiplatform scaffold before adding backend, reader or TTS functionality.

## Key rule

The app must not be internally modelled as a Kavita-only app. Kavita is the first source adapter, not the domain model.

Bad:

```text
UI -> Kavita DTOs -> Kavita progress
```

Good:

```text
UI -> Domain models -> Use cases -> SourceAdapter -> Kavita DTOs
                     -> Local database
                     -> Sync engine
```

## Major modules

### Domain core

Contains app-owned models:

- `Library`
- `Series`
- `Book`
- `PublicationFile`
- `Author`
- `ReadingPosition`
- `ReadingSession`
- `DeviceState`
- `SyncEvent`
- `ReaderPreferences`

### Source adapter API

A neutral interface for backend integrations.

```kotlin
interface SourceAdapter {
    val sourceId: SourceId
    val capabilities: SourceCapabilities

    suspend fun authenticate(credentials: SourceCredentials): AuthResult
    suspend fun listLibraries(): List<RemoteLibrary>
    suspend fun listSeries(libraryId: RemoteLibraryId): List<RemoteSeries>
    suspend fun listBooks(seriesId: RemoteSeriesId): List<RemoteBook>
    suspend fun getBook(bookId: RemoteBookId): RemoteBookDetails
    suspend fun downloadPublicationFile(fileId: RemoteFileId): DownloadHandle

    suspend fun getRemoteProgress(bookId: RemoteBookId): RemoteProgress?
    suspend fun setRemoteProgress(bookId: RemoteBookId, progress: RemoteProgress): ProgressWriteResult
}
```

Capabilities matter because not every source can do everything.

```kotlin
data class SourceCapabilities(
    val supportsLibraryBrowsing: Boolean,
    val supportsSearch: Boolean,
    val supportsDownloads: Boolean,
    val supportsRemoteProgressRead: Boolean,
    val supportsRemoteProgressWrite: Boolean,
    val supportsCollections: Boolean,
    val supportsSeriesMetadata: Boolean
)
```

### Kavita adapter

Translates Kavita API data into domain models. Kavita DTOs must not leak into UI, reader or sync modules.

### Local storage

Stores:

- configured sources
- normalized libraries/books
- downloaded files
- book identity mappings
- reading positions
- sync queue
- sync conflict records
- reader preferences
- TTS preferences

### Sync engine

Responsible for comparing local and remote progress, deciding safe writes, surfacing conflicts and retrying failed remote writes.

### Reader layer

The reader UI should use an established reader toolkit, probably Readium first. The app should wrap that toolkit through a small app-owned abstraction so future engine changes do not poison the entire app.

### TTS layer

TTS is provider-based:

```text
TtsProvider
  AndroidLocalTtsProvider
  AppleLocalTtsProvider
  CloudTtsProvider later
  LocalTtsModelProvider later
```

MVP only needs Android local TTS.

## Data flow: opening a book

```text
User taps book
  -> Domain use case resolves local file
  -> if missing, SourceAdapter downloads file
  -> local DB records file and content identity
  -> Sync engine chooses initial reading position
  -> Reader engine opens file with locator
  -> Reader emits progress events
  -> local DB persists progress immediately
  -> Sync engine debounces remote progress write
```

## Data flow: app startup

```text
App starts
  -> load sources and local cache
  -> render last known local state quickly
  -> refresh Kavita libraries in background
  -> pull remote progress for visible/continue-reading items
  -> reconcile without destructive overwrites
```

## Why no dedicated sync server now

A dedicated sync server would add deployment, user management, security and operational complexity before the core app experience is proven. The MVP should use Kavita as the remote progress backend where possible, while keeping the internal architecture ready for a future optional server if a later ADR approves it.
