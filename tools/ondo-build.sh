#!/usr/bin/env bash
# Resource-disciplined Gradle wrapper for the Ondo (Navic) port.
#
# This box has 13GB RAM shared with docker + jellyfin and swap is usually
# near-full; uncapped Gradle/Kotlin runs have been OOM-killed before. Every
# heavy build must go through here so that:
#   * non-essential docker is stopped for the duration (search providers remain
#     available, and stopped services are ALWAYS resumed, even
#     on failure/interrupt -- trap-based, same pattern as
#     /home/nicknas/tools/run-ondo-gradle-716.sh)
#   * Gradle never leaves a daemon behind (--no-daemon)
#   * no KotlinCompileDaemon is spawned at all
#     (kotlin.compiler.execution.strategy=in-process in gradle.properties)
#
# Usage: tools/ondo-build.sh :androidApp:assembleDebug [more gradle args...]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STATE="${ONDO_DOCKER_STATE:-/home/nicknas/tmp/ondo-navic-paused-containers.txt}"
LOG="${ONDO_BUILD_LOG:-/home/nicknas/logs/ondo-navic-gradle.log}"
SCRATCH="${TMPDIR:-/home/nicknas/tmp}"

mkdir -p "$SCRATCH" "$(dirname "$LOG")" "$(dirname "$STATE")"

log() { printf '[%s] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }

export TMPDIR="$SCRATCH" TMP="$SCRATCH" TEMP="$SCRATCH"
export ANDROID_HOME="${ANDROID_HOME:-/home/nicknas/Android/Sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/home/nicknas/Android/Sdk}"
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:--Djava.io.tmpdir=${SCRATCH}}"

paused=0
cleanup() {
  rc=$?
  # Never leave a Kotlin daemon running -- the whole point of the in-process
  # strategy. If one appeared anyway, that is a regression worth shouting about.
  if pgrep -af KotlinCompileDaemon >/dev/null 2>&1; then
    log "WARNING: a KotlinCompileDaemon exists despite in-process strategy:"
    pgrep -af KotlinCompileDaemon | sed 's/^/  /'
    pkill -f KotlinCompileDaemon || true
    log "killed stray KotlinCompileDaemon(s)"
  fi
  if [ "$paused" -eq 1 ]; then
    log "resuming docker services"
    /home/nicknas/tools/ondo-docker-resume.sh "$STATE" || log "docker resume had errors"
    paused=0
  fi
  exit "$rc"
}
trap cleanup EXIT INT TERM

if [ "${ONDO_SKIP_DOCKER_PAUSE:-0}" != "1" ]; then
  log "pausing non-jellyfin docker"
  /home/nicknas/tools/ondo-docker-pause.sh "$STATE"
  paused=1
fi

log "gradlew $* (--no-daemon)"
cd "$ROOT"
set +e
./gradlew "$@" --no-daemon 2>&1 | tee -a "$LOG"
rc="${PIPESTATUS[0]}"
set -e
log "gradle exited $rc"
exit "$rc"
