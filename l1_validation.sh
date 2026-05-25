#!/bin/bash

# Development-only L1 validation helper.
#
# Product tasks must still be typed and started inside the VisionAgent app.
# This script prepares the device, runs local automated checks, and prints the
# manual matrix path for structured true-device validation.

set -euo pipefail

ADB_BIN="${ADB_BIN:-adb}"
APP_ID="com.tencent.edgeagent"
MAIN_ACTIVITY="$APP_ID/.ui.MainActivity"

ADB_ARGS=()
if [ -n "${ANDROID_SERIAL:-}" ]; then
  ADB_ARGS=(-s "$ANDROID_SERIAL")
fi

adb_cmd() {
  if [ ${#ADB_ARGS[@]} -gt 0 ]; then
    "$ADB_BIN" "${ADB_ARGS[@]}" "$@"
  else
    "$ADB_BIN" "$@"
  fi
}

usage() {
  cat <<EOF
Usage: ./l1_validation.sh [--no-install]

Runs automated L1 checks and prepares the test phone for manual matrix testing.

Environment:
  ANDROID_SERIAL   Optional adb device serial
  ADB_BIN          Optional adb binary path

Manual matrix:
  docs/L1_TEST_MATRIX.md
EOF
}

INSTALL_APK=1
while [ $# -gt 0 ]; do
  case "$1" in
    --no-install)
      INSTALL_APK=0
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
      exit 2
      ;;
  esac
done

echo "== VisionAgent L1 validation =="
./keep_device_awake.sh start

echo "Run unit tests for L1 routing and strategies..."
./gradlew :app:testDebugUnitTest

echo "Build debug APK..."
./gradlew :app:assembleDebug

if [ "$INSTALL_APK" -eq 1 ]; then
  echo "Install debug APK..."
  adb_cmd wait-for-device
  adb_cmd install -r app/build/outputs/apk/debug/app-debug.apk
fi

echo "Prepare dev permissions..."
./dev_bootstrap_permissions.sh --no-screen-capture || true

echo "Start VisionAgent..."
adb_cmd shell am start -n "$MAIN_ACTIVITY" >/dev/null

echo
echo "Automated checks complete."
echo "Continue manual true-device validation from VisionAgent UI:"
echo "  docs/L1_TEST_MATRIX.md"
echo
echo "After each manual run, replay the latest trace:"
echo "  ./view_logs.sh --replay"

