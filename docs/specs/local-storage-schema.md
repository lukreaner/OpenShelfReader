# Local storage schema sketch

This is a conceptual schema. Adapt for SQLDelight/Room once the storage decision is made.

```sql
CREATE TABLE source_account (
  id TEXT PRIMARY KEY,
  type TEXT NOT NULL,
  display_name TEXT NOT NULL,
  base_url TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE TABLE library_cache (
  id TEXT PRIMARY KEY,
  source_id TEXT NOT NULL,
  remote_id TEXT NOT NULL,
  name TEXT NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE TABLE book_identity (
  id TEXT PRIMARY KEY,
  source_id TEXT NOT NULL,
  remote_book_id TEXT,
  remote_file_id TEXT,
  file_hash TEXT,
  file_size INTEGER,
  epub_identifier TEXT,
  title_normalized TEXT,
  author_normalized TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE TABLE publication_file (
  id TEXT PRIMARY KEY,
  book_identity_id TEXT NOT NULL,
  source_id TEXT NOT NULL,
  remote_file_id TEXT,
  local_path TEXT NOT NULL,
  format TEXT NOT NULL,
  file_size INTEGER,
  file_hash TEXT,
  downloaded_at INTEGER NOT NULL,
  last_opened_at INTEGER
);

CREATE TABLE reading_position (
  id TEXT PRIMARY KEY,
  book_identity_id TEXT NOT NULL,
  format TEXT NOT NULL,
  locator_json TEXT NOT NULL,
  progression REAL,
  chapter_href TEXT,
  chapter_index INTEGER,
  chapter_progression REAL,
  pdf_page_index INTEGER,
  pdf_page_offset REAL,
  source TEXT NOT NULL,
  device_id TEXT NOT NULL,
  session_id TEXT NOT NULL,
  updated_at INTEGER NOT NULL,
  finished INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE remote_progress_snapshot (
  id TEXT PRIMARY KEY,
  book_identity_id TEXT NOT NULL,
  source_id TEXT NOT NULL,
  remote_progress_json TEXT NOT NULL,
  normalized_position_id TEXT,
  fetched_at INTEGER NOT NULL
);

CREATE TABLE sync_event (
  id TEXT PRIMARY KEY,
  type TEXT NOT NULL,
  book_identity_id TEXT NOT NULL,
  local_position_id TEXT,
  remote_snapshot_id TEXT,
  status TEXT NOT NULL,
  retry_count INTEGER NOT NULL DEFAULT 0,
  last_error TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE TABLE sync_conflict (
  id TEXT PRIMARY KEY,
  book_identity_id TEXT NOT NULL,
  local_position_id TEXT NOT NULL,
  remote_snapshot_id TEXT NOT NULL,
  resolution TEXT,
  created_at INTEGER NOT NULL,
  resolved_at INTEGER
);
```

## Secure storage

API keys should not go into this database unless encrypted by a platform keystore abstraction. Prefer Android Keystore/iOS Keychain through platform-specific implementations.
