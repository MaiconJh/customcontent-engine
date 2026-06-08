#!/usr/bin/env bash
set -uo pipefail

LOG_FILE="${BUILD_LOG_FILE:-build.log}"
: > "$LOG_FILE"

run_and_log() {
  local label="$1"
  shift
  {
    echo "==> ${label}"
    "$@"
  } 2>&1 | tee -a "$LOG_FILE"
  return "${PIPESTATUS[0]}"
}

chmod +x ./gradlew 2>/dev/null || true

if [[ -x "./gradlew" ]]; then
  if [[ -n "${CI_BUILD_COMMAND:-}" ]]; then
    # shellcheck disable=SC2086
    run_and_log "Gradle custom command" ${CI_BUILD_COMMAND}
  else
    run_and_log "Gradle build" ./gradlew clean build --no-daemon
  fi
  exit $?
fi

if [[ -f "build.gradle" || -f "build.gradle.kts" ]]; then
  if command -v gradle >/dev/null 2>&1; then
    run_and_log "System Gradle fallback" gradle clean build --no-daemon
    exit $?
  fi
  echo "Gradle project detected, but ./gradlew is unavailable and system gradle is not installed." | tee -a "$LOG_FILE"
  exit 127
fi

if [[ -f "pom.xml" ]]; then
  run_and_log "Maven verify" mvn -B clean verify
  exit $?
fi

echo "No supported Java build tool detected. Expected Gradle or Maven." | tee -a "$LOG_FILE"
exit 127
