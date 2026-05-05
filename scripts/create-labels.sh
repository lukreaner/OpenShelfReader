#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   gh auth login
#   ./scripts/create-labels.sh owner/repo

REPO="${1:?Usage: ./scripts/create-labels.sh owner/repo}"

create_label() {
  local name="$1"
  local color="$2"
  local description="$3"
  gh label create "$name" --repo "$REPO" --color "$color" --description "$description" || true
}

create_label "area:architecture" "5319e7" "Architecture and technical decisions"
create_label "area:android" "0e8a16" "Android app"
create_label "area:ios" "1d76db" "iOS/iPadOS app"
create_label "area:kavita" "fbca04" "Kavita integration"
create_label "area:reader" "d93f0b" "EPUB/PDF reader"
create_label "area:sync" "b60205" "Progress sync and conflicts"
create_label "area:tts" "c5def5" "Text-to-speech"
create_label "area:storage" "0052cc" "Local storage and secure storage"
create_label "area:ci" "bfd4f2" "CI, builds and releases"
create_label "type:feature" "0e8a16" "Feature work"
create_label "type:bug" "d73a4a" "Bug or regression"
create_label "type:spike" "fbca04" "Research spike"
create_label "type:docs" "0075ca" "Documentation"
create_label "priority:p0" "b60205" "Critical MVP priority"
create_label "priority:p1" "d93f0b" "Important"
create_label "priority:p2" "c2e0c6" "Later"
create_label "blocked" "000000" "Blocked by another task"
