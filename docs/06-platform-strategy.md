# Platform strategy

## Phase 1: Android phone

Build the first real app on Android.

Priorities:

- Kavita connection.
- Library browsing.
- EPUB/PDF opening.
- Local progress.
- Kavita progress sync.
- Local TTS.

## Phase 2: Android tablet and foldables

Improve layout once phone UX works.

Priorities:

- Two-pane library view.
- Reader settings optimized for larger displays.
- Optional two-column reading if reader engine supports it well.
- Foldable posture/hinge awareness as a spike.

## Phase 3: iOS/iPadOS

Add iOS after shared core stabilizes.

Approach:

- Reuse KMP core for domain, sync, source adapters and storage logic where possible.
- Use platform-native UI if that gives better polish.
- Use Readium Swift Toolkit or equivalent for iOS reader layer.

## Phase 4: Tolino/Kobo research

Do not put this in MVP.

Research questions:

- Can modern Tolino/Kobo devices run custom clients meaningfully?
- Is there a supported or stable way to read/write progress?
- Is a companion bridge safer than a full app?
- Would the UX be better than KOReader for this user?

## Phase 5: jailbroken Kindle research

Do not put this in MVP.

Research questions:

- Which devices/firmware versions are viable?
- Is KOReader-compatible sync bridge more realistic than a full native app?
- Can any solution be maintained without constant firmware breakage?

## Policy

Mobile app quality comes first. E-Ink support is valuable, but not at the cost of stalling the whole project.
