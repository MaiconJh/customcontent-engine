#!/usr/bin/env bash
set -euo pipefail

if ! command -v gh >/dev/null 2>&1; then
  echo "GitHub CLI (gh) is not installed. Install it and run: gh auth login" >&2
  exit 1
fi

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "This command must be run inside a git repository." >&2
  exit 1
fi

branch="$(git rev-parse --abbrev-ref HEAD)"

if [[ "$branch" == "HEAD" ]]; then
  echo "Cannot determine a branch name because the repository is in detached HEAD state." >&2
  exit 1
fi

echo "Triggering build-test.yml for branch: $branch"
gh workflow run build-test.yml --ref "$branch"

echo
echo "Workflow dispatched. Watch it with:"
echo "  gh run watch"
