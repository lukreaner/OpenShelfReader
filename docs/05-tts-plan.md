# TTS plan

TTS is a core feature because it replaces one of the most useful Kindle/Alexa behaviors.

## MVP: Android local TTS

Implement local Android TTS first.

Must support:

- Choose installed voice where practical.
- Set speech rate.
- Pause/resume/stop.
- Start from current chapter or current reading position.
- Continue into next chapter if user enables queueing.
- Update reading progress conservatively while speaking.
- Handle app backgrounding.

## Text extraction

Do not scrape rendered UI text if the reader engine exposes proper content extraction or locator-based APIs. The reader spike must determine the best way to get chapter text and map speech progress back to reading locators.

## TTS text pipeline

```text
Reader position
  -> chapter/text segment resolver
  -> cleanup
  -> sentence/paragraph segmentation
  -> TTS queue
  -> progress updates
```

Cleanup should handle:

- Footnote markers.
- Repeated headers/metadata if present.
- Broken whitespace.
- HTML entities.
- Language selection.

## Later: iOS local TTS

Use platform-native iOS speech APIs after iOS shell exists.

## Later: AI TTS

AI TTS must be provider-based and optional.

Future provider interface:

```kotlin
interface TtsProvider {
    val id: TtsProviderId
    val capabilities: TtsCapabilities
    suspend fun synthesize(request: TtsRequest): TtsResult
}
```

Required before cloud/AI TTS:

- Privacy warning.
- Per-provider API key storage.
- Cost controls.
- Cache controls.
- Delete cached generated audio.
- “Never send book text to cloud” setting.

## Later: Audiobookshelf bridge

Whispersync-like behavior should not be implemented until the reader and local TTS are stable.

Prepare data model only:

```text
audio_link
  ebook_identity_id
  audio_provider
  remote_audiobook_id
  confidence
  created_by: manual | automatic

audio_alignment
  ebook_locator
  audio_time_ms
  confidence
  source: manual | generated | imported
```
