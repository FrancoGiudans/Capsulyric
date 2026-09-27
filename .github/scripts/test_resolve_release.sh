#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
output=$(mktemp)
trap 'rm -f "$output"' EXIT
count=0
check() {
  local branch=$1 event=$2 input=$3 message=$4 expected=$5
  : > "$output"
  if GITHUB_REF="$branch" GITHUB_EVENT_NAME="$event" RELEASE_VERSION="$input" HEAD_COMMIT_MESSAGE="$message" GITHUB_OUTPUT="$output" bash .github/scripts/resolve_release.sh 2>/dev/null; then
    [[ "$expected" != fail ]] || { echo "Unexpected success: $branch $input $message"; exit 1; }
    grep -qx "channel=$expected" "$output"
    grep -qE '^version_prefix=[0-9]+(\.[0-9]+)*$' "$output"
  else
    [[ "$expected" == fail ]] || { echo "Unexpected failure: $branch $input $message"; exit 1; }
  fi
  count=$((count + 1))
}
check refs/heads/main push '' '[release]26.9.1 (#123)' Stable
grep -qx 'version_prefix=26.9.1' "$output"
check refs/heads/develop push '' $'Merge pull request #123 from owner/release\n\n[release]26.9' Preview
check refs/heads/main push '' '[release]' Stable
check refs/heads/develop push '' '[release]' Preview
check refs/heads/main workflow_dispatch '' '' Stable
check refs/heads/develop workflow_dispatch Preview '' Preview
check refs/heads/main workflow_dispatch 26.9.Stable '' Stable
check refs/heads/develop workflow_dispatch 26.9.Preview '' Preview
check refs/heads/develop workflow_dispatch Stable '' fail
check refs/heads/main workflow_dispatch Preview '' fail
check refs/heads/develop workflow_dispatch Experiment '' fail
check refs/heads/release/26.9 workflow_dispatch '' '' fail
check refs/tags/main workflow_dispatch '' '' fail
check refs/heads/main push '' 'Revert "[release]26.9"' fail
check refs/heads/main push '' 'chore: sync develop and main' fail
check refs/heads/main workflow_dispatch '26.9; echo unsafe' '' fail
check refs/heads/main push '' '[release]26.9 $(echo unsafe)' fail
echo "$count release policy cases passed."
