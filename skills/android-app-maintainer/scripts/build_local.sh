#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
SDK_ROOT="${ANDROID_SDK_ROOT:-/tmp/android-sdk}"
JDK_ROOT="${JAVA_HOME:-/tmp/android-build-tools/jdk}"
GRADLE_CACHE="${GRADLE_USER_HOME:-$PROJECT_ROOT/.gradle}"
GRADLE_BIN="${GRADLE_BIN:-}"
TASKS=("$@")

if [ "${#TASKS[@]}" -eq 0 ]; then
  TASKS=(lintDebug assembleDebug)
fi

if [ ! -x "$JDK_ROOT/bin/java" ]; then
  echo "JDK 17+ not found at: $JDK_ROOT" >&2
  echo "Set JAVA_HOME to a Linux JDK 17+ path before running this script." >&2
  exit 1
fi

JAVA_VERSION="$("$JDK_ROOT/bin/java" -version 2>&1 | sed -n '1p')"
JAVA_MAJOR="$(printf '%s\n' "$JAVA_VERSION" | sed -E 's/.*"([0-9]+).*/\1/')"
if [ "$JAVA_MAJOR" -lt 17 ]; then
  echo "Java 17+ is required; found: $JAVA_VERSION" >&2
  exit 1
fi

if [ ! -f "$SDK_ROOT/platforms/android-36/android.jar" ]; then
  echo "Android API 36 platform not found under: $SDK_ROOT" >&2
  exit 1
fi

if [ ! -x "$SDK_ROOT/build-tools/36.0.0/aapt2" ]; then
  echo "Android build-tools 36.0.0 for Linux not found under: $SDK_ROOT" >&2
  exit 1
fi

LOCAL_PROPERTIES="$PROJECT_ROOT/local.properties"
BACKUP_FILE=""
if [ -f "$LOCAL_PROPERTIES" ]; then
  BACKUP_FILE="$(mktemp)"
  cp "$LOCAL_PROPERTIES" "$BACKUP_FILE"
fi

restore_local_properties() {
  if [ -n "$BACKUP_FILE" ] && [ -f "$BACKUP_FILE" ]; then
    cp "$BACKUP_FILE" "$LOCAL_PROPERTIES"
    rm -f "$BACKUP_FILE"
  fi
}
trap restore_local_properties EXIT

printf 'sdk.dir=%s\n' "$SDK_ROOT" > "$LOCAL_PROPERTIES"
mkdir -p /tmp/android-build-home/.android

cd "$PROJECT_ROOT"
if [ -z "$GRADLE_BIN" ]; then
  for candidate in /mnt/c/Users/alfre/.gradle/wrapper/dists/gradle-8.13-bin/*/gradle-8.13/bin/gradle; do
    if [ -x "$candidate" ]; then
      GRADLE_BIN="$candidate"
      break
    fi
  done
fi
if [ -z "$GRADLE_BIN" ]; then
  GRADLE_BIN="$PROJECT_ROOT/gradlew"
fi

JAVA_HOME="$JDK_ROOT" \
ANDROID_HOME="$SDK_ROOT" \
ANDROID_SDK_ROOT="$SDK_ROOT" \
ANDROID_USER_HOME=/tmp/android-build-home/.android \
GRADLE_USER_HOME="$GRADLE_CACHE" \
"$GRADLE_BIN" --no-daemon \
  --gradle-user-home "$GRADLE_CACHE" \
  -Duser.home=/tmp/android-build-home \
  -Dandroid.aapt2FromMavenOverride="$SDK_ROOT/build-tools/36.0.0/aapt2" \
  "${TASKS[@]}"
