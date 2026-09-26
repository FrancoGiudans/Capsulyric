#!/usr/bin/env bash
# Resolve the channel from the branch, never from stale changelog metadata.
set -euo pipefail

case "${GITHUB_REF:-}" in
  refs/heads/main) CHANNEL=Stable ;;
  refs/heads/develop) CHANNEL=Preview ;;
  *) echo '::error::Releases must run on main or develop.' >&2; exit 1 ;;
esac

if [[ "${GITHUB_EVENT_NAME:-}" == workflow_dispatch ]]; then
  RAW="${RELEASE_VERSION:-}"
else
  # Match a release title, not quoted markers in revert messages or PR bodies.
  RAW=$(printf '%s\n' "${HEAD_COMMIT_MESSAGE:-}" | sed -nE 's/^\[release\][[:space:]]*(.*)$/\1/p' | head -n 1)
  if ! printf '%s\n' "${HEAD_COMMIT_MESSAGE:-}" | grep -qE '^\[release\]'; then
    echo '::error::No release title found in the final commit message.' >&2
    exit 1
  fi
  RAW=$(printf '%s' "$RAW" | sed -E 's/[[:space:]]+\(#[0-9]+\)[[:space:]]*$//')
fi
RAW=$(printf '%s' "$RAW" | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//')
VERSION_PREFIX="$RAW"
case "${RAW,,}" in
  stable|preview|experiment) REQUESTED_CHANNEL="${RAW,,}"; VERSION_PREFIX='' ;;
  *.stable|*.preview|*.experiment) REQUESTED_CHANNEL="${RAW##*.}"; REQUESTED_CHANNEL="${REQUESTED_CHANNEL,,}"; VERSION_PREFIX="${RAW%.*}" ;;
  *) REQUESTED_CHANNEL="${CHANNEL,,}" ;;
esac
if [[ "$REQUESTED_CHANNEL" != "${CHANNEL,,}" ]]; then
  echo "::error::${GITHUB_REF} only publishes ${CHANNEL}; use the matching branch." >&2
  exit 1
fi
if [[ -z "$VERSION_PREFIX" ]]; then
  VERSION_PREFIX="$(TZ=Asia/Shanghai date +'%y').$(TZ=Asia/Shanghai date +'%-m')"
elif [[ ! "$VERSION_PREFIX" =~ ^[0-9]+(\.[0-9]+)*$ ]]; then
  echo '::error::Version must contain dot-separated numbers with an optional matching channel.' >&2
  exit 1
fi
printf 'version_prefix=%s\nchannel=%s\n' "$VERSION_PREFIX" "$CHANNEL" >> "$GITHUB_OUTPUT"
