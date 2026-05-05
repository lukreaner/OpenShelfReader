# Sync without a dedicated sync server

The user preference is to avoid a separate sync server. This document defines how the app should still achieve reliable sync behavior.

## Principle

The app is local-first. Kavita is the first remote progress backend. The local database is not a replacement for Kavita, but it is the operational source of truth while the user is actively reading.

## Roles

### Local database

Stores precise app-owned state:

- Current reading position.
- Last known remote position.
- Sync timestamps.
- Local reading session ID.
- Device ID.
- Conflict markers.
- Pending writes.

### Kavita

Stores remote progress where Kavita supports it. Kavita is used for cross-device propagation.

### Source adapter

Each adapter declares whether it can read and/or write progress. Kavita should support both for the MVP. Future Calibre/OPDS adapters may support neither or only partial progress.

## Position model

Do not use “page number” alone for EPUB.

Use a layered locator:

```kotlin
data class ReadingPosition(
    val bookIdentityId: String,
    val format: PublicationFormat,
    val locator: ReadingLocator,
    val progression: Double?,
    val chapterHref: String?,
    val chapterIndex: Int?,
    val chapterProgression: Double?,
    val pdfPageIndex: Int?,
    val pdfPageOffset: Double?,
    val source: ProgressSource,
    val deviceId: String,
    val sessionId: String,
    val updatedAtEpochMillis: Long,
    val finished: Boolean
)
```

For EPUB, prefer reader-engine-native locators when available. Readium’s locator concepts should be investigated in the reader spike.

For PDF, use page index plus offset.

## Book identity

Progress should attach to a stable app-owned book identity, not only a remote backend ID.

Suggested fields:

```text
book_identity
  id
  source_id
  source_type
  remote_book_id
  remote_file_id
  file_hash
  file_size
  epub_identifier
  title_normalized
  author_normalized
  created_at
  updated_at
```

Remote IDs are useful but not sufficient if a backend changes or a user later imports the same book from another source.

## Sync events

Write progress through a queue:

```text
sync_event
  id
  type: progress_write | progress_pull | conflict_detected
  book_identity_id
  local_position_id
  remote_snapshot_json
  status: pending | running | completed | failed | skipped
  retry_count
  created_at
  updated_at
```

This gives us retryability and debugging.

## Pull algorithm

When opening a book or refreshing continue-reading:

1. Load local position.
2. Pull remote position if available.
3. If no local position exists, use remote position.
4. If local is newer and not meaningfully behind, keep local.
5. If remote is newer and ahead, offer to jump or auto-accept according to safe thresholds.
6. If positions diverge significantly, create a conflict record and ask the user.
7. Never downgrade `finished=true` automatically.

## Write algorithm

While reading:

1. Persist local progress immediately.
2. Enqueue remote write.
3. Debounce writes.
4. Write on app background, chapter change and session end.
5. Retry failed writes.
6. Do not block reading on remote write success.

## Conflict policy

Use thresholds, not timestamp-only logic.

Suggested initial thresholds:

- EPUB progression difference under 1 percent: safe to merge by recency.
- EPUB progression difference over 5 percent: conflict if newer position moves backwards.
- PDF page difference under 1 page: safe to merge by recency.
- PDF page difference over 3 pages: conflict if newer position moves backwards.
- `finished=true`: never unset without explicit user action.

These numbers are intentionally conservative and should be tuned through real use.

## UI for conflicts

Conflict message should be simple:

```text
This book has two different reading positions.

This device: Chapter 12, about 64%
Kavita: Chapter 10, about 51%

Continue from this device
Use Kavita position
Keep both for now
```

## Why this is enough for MVP

A separate server would be cleaner for future multi-backend identity, but unnecessary for the first goal. Kavita can be the remote progress carrier, while the app’s local database provides precision, offline safety and conflict recovery.
