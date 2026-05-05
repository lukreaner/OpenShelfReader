# Contributing

OpenShelf Reader is early-stage. Contributions should be small, issue-focused and easy to review.

## Before contributing

Read:

- `README.md`
- `docs/01-architecture.md`
- `docs/03-sync-without-dedicated-server.md`
- Relevant ADRs in `docs/adr/`

## Pull requests

- Link an issue where possible.
- Keep scope small.
- Add tests where possible.
- Do not commit secrets, real API keys or private server URLs.
- Do not implement DRM circumvention.
- Do not add a dedicated sync server unless an accepted ADR changes the project direction.
- Explain what changed and how it was tested.

## Review focus

Review especially for:

- Sync or data-loss risks.
- Secret handling.
- Backend DTOs leaking into UI or sync logic.
- Over-broad changes.
- Dark mode/readability regressions.
- TTS behavior in foreground/background.
