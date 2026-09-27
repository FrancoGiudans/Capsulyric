#!/usr/bin/env bash
set -euo pipefail

TARGET_BRANCH=${1:-${GITHUB_REF#refs/heads/}}
case "$TARGET_BRANCH" in
  main) EXPECTED_PREVIEW=false ;;
  develop) EXPECTED_PREVIEW=true ;;
  *) echo "::error::Release metadata can only be validated for main or develop (got '$TARGET_BRANCH')." >&2; exit 1 ;;
esac

CHANGELOG_FILE=${CHANGELOG_FILE:-CHANGELOG.md}
PREVIEW_MARKER=$(awk '
  /^## Release Metadata[[:space:]]*$/ { in_metadata=1; next }
  /^## / { in_metadata=0 }
  in_metadata && /^- \*\*Preview\*\*: `(true|false)`[[:space:]]*$/ {
    if (found != "") { duplicate=1 }
    value=$0
    sub(/^[^`]*`/, "", value)
    sub(/`.*/, "", value)
    found=value
  }
  END {
    if (duplicate) exit 2
    if (found != "") print found
  }
' "$CHANGELOG_FILE") || {
  echo '::error::CHANGELOG.md must contain exactly one Preview marker under Release Metadata.' >&2
  exit 1
}
if [[ -z "$PREVIEW_MARKER" ]]; then
  echo '::error::CHANGELOG.md is missing `- **Preview**: `true` or `false`` under `## Release Metadata`.' >&2
  exit 1
fi
if [[ "$PREVIEW_MARKER" != "$EXPECTED_PREVIEW" ]]; then
  echo "::error::Target branch '$TARGET_BRANCH' requires CHANGELOG Preview: ${EXPECTED_PREVIEW}, found ${PREVIEW_MARKER}." >&2
  exit 1
fi
