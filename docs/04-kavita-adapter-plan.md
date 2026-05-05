# Kavita adapter plan

Kavita is the first source adapter. The app should use Kavita’s official REST API, not OPDS, for the primary experience.

## Authentication

Kavita supports auth keys for third-party clients. The adapter should accept:

- Server URL.
- API key.

The adapter should:

- Normalize server URL.
- Attach API key through the expected header.
- Test connectivity.
- Check key expiration if the endpoint is available.
- Store credentials through platform secure storage.

## Adapter responsibilities

The Kavita adapter translates Kavita API responses into app-owned domain models.

```text
Kavita DTO -> KavitaMapper -> Domain model
```

Kavita DTOs should stay inside the Kavita module.

## MVP endpoints to investigate

The first Kavita implementation should confirm exact routes and payloads from the OpenAPI definition before coding.

Likely areas:

- Authentication / auth key expiration.
- Libraries.
- Series.
- Volumes/chapters or book-level items.
- Cover image retrieval.
- File download.
- Reading progress get/set.
- Search.

## Error handling

Handle:

- Invalid URL.
- Network unavailable.
- TLS/certificate issues.
- Invalid API key.
- Expired API key.
- Unsupported Kavita version.
- Missing/unsupported file type.
- Download interrupted.
- Progress write failed.

## Caching

Cache metadata locally so the app can render something offline.

Metadata cache should not be treated as the final truth forever. Refresh from Kavita in background when network is available.

## Security

- Never log API keys.
- Redact `x-api-key` values in diagnostics.
- Do not include real user server URLs in test fixtures.
- Prefer HTTPS, but allow explicit insecure HTTP only behind a visible advanced setting for LAN/home-lab scenarios.

## Tests

Use fixture JSON copied from sanitized test data or generated minimal responses.

Tests should verify:

- URL normalization.
- Header injection redaction.
- Mapping from DTO to domain model.
- Progress conversion.
- Error mapping.
