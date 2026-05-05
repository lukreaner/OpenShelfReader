# GitHub setup

## Manual repository setup

1. Create a new GitHub repository.
2. Choose public or private.
3. Do not initialize with README if you are pushing these files as the first commit.
4. Push local files.
5. Enable issues.
6. Add branch protection for `main` once CI exists.
7. Require PR review before merge when contributors appear.

## Suggested labels

- `area:architecture`
- `area:android`
- `area:ios`
- `area:kavita`
- `area:reader`
- `area:sync`
- `area:tts`
- `area:storage`
- `area:ci`
- `type:feature`
- `type:bug`
- `type:spike`
- `type:docs`
- `priority:p0`
- `priority:p1`
- `priority:p2`
- `blocked`

Use `scripts/create-labels.sh` after installing and authenticating GitHub CLI.

## Issue forms

This repository includes issue forms in `.github/ISSUE_TEMPLATE/`. GitHub will render these as structured issue creation forms.

## Project board

Create a GitHub project with columns or statuses:

```text
Backlog
Ready
In progress
Review
Done
```

## Milestones

Suggested milestones:

1. `M0 Docs and architecture`
2. `M1 Android Kavita MVP skeleton`
3. `M2 Reader and offline`
4. `M3 Progress sync hardening`
5. `M4 TTS MVP`
6. `M5 Android alpha`
7. `M6 iOS planning`
