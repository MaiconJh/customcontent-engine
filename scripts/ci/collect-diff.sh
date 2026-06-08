#!/usr/bin/env bash
set -euo pipefail

MAX_CHARS="${MAX_DIFF_CHARS:-50000}"
DIFF_FILE="${DIFF_FILE:-diff.patch}"
META_FILE="${DIFF_META_FILE:-diff-meta.json}"

git config --global --add safe.directory "$GITHUB_WORKSPACE" 2>/dev/null || true

BASE_SHA="${BASE_SHA:-}"
HEAD_SHA="${HEAD_SHA:-${GITHUB_SHA:-HEAD}}"

if [[ -z "$BASE_SHA" && "${GITHUB_EVENT_NAME:-}" == "pull_request" ]]; then
  BASE_SHA="${GITHUB_BASE_REF:-}"
  if [[ -n "$BASE_SHA" ]]; then
    git fetch --no-tags --depth=100 origin "$BASE_SHA" >/dev/null 2>&1 || true
    BASE_SHA="origin/$BASE_SHA"
  fi
fi

if [[ -z "$BASE_SHA" && -n "${GITHUB_EVENT_PATH:-}" && -f "$GITHUB_EVENT_PATH" ]]; then
  BASE_SHA="$(node -e "const e=require(process.env.GITHUB_EVENT_PATH); console.log(e.before || e.pull_request?.base?.sha || '')")"
fi

if [[ -z "$BASE_SHA" || "$BASE_SHA" =~ ^0+$ ]]; then
  BASE_SHA="$(git rev-parse HEAD~1 2>/dev/null || true)"
fi

if [[ -n "$BASE_SHA" ]]; then
  git fetch --no-tags --depth=100 origin "$BASE_SHA" >/dev/null 2>&1 || true
fi

RANGE="$BASE_SHA...$HEAD_SHA"
if [[ -z "$BASE_SHA" ]]; then
  RANGE="$HEAD_SHA"
fi

git diff --no-ext-diff --no-color --binary --stat "$RANGE" > diff-stat.txt 2>/dev/null || true
git diff --no-ext-diff --no-color --diff-filter=ACMRTUXB "$RANGE" -- \
  ':(exclude)**/*.lock' \
  ':(exclude)package-lock.json' \
  ':(exclude)yarn.lock' \
  ':(exclude)pnpm-lock.yaml' \
  ':(exclude)gradle/wrapper/gradle-wrapper.jar' \
  > "$DIFF_FILE.full" 2>/dev/null || true

node scripts/ci/sanitize-payload.js --input "$DIFF_FILE.full" --output "$DIFF_FILE" --max-chars "$MAX_CHARS" --text

node -e "const fs=require('fs'); const meta={event:process.env.GITHUB_EVENT_NAME||'', base:process.env.BASE_SHA||'$BASE_SHA', head:process.env.HEAD_SHA||'$HEAD_SHA', chars:fs.existsSync('$DIFF_FILE')?fs.statSync('$DIFF_FILE').size:0, truncated:fs.existsSync('$DIFF_FILE.full')&&fs.statSync('$DIFF_FILE.full').size>Number('$MAX_CHARS')}; fs.writeFileSync('$META_FILE', JSON.stringify(meta,null,2));"
