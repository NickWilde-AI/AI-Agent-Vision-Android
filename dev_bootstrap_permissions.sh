#!/bin/bash

# Development-only permission bootstrap for VisionAgent.
#
# This script prepares the test device so Agent tasks can be executed by the app
# itself. It must not be used as a product capability and must not execute user
# tasks through ADB.

set -euo pipefail

ADB_BIN="${ADB_BIN:-adb}"
APP_ID="com.tencent.edgeagent"
MAIN_ACTIVITY="$APP_ID/.ui.MainActivity"
ACCESSIBILITY_SERVICE="$APP_ID/$APP_ID.service.EdgeAgentAccessibilityService"
ENABLE_SCREEN_CAPTURE=1

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
Usage: ./dev_bootstrap_permissions.sh [--no-screen-capture]

Environment:
  ANDROID_SERIAL   Optional adb device serial, for example 192.168.10.166:39791
  ADB_BIN          Optional adb binary path

This script only prepares permissions for local development:
  - enable VisionAgent accessibility service when adb shell is allowed
  - allow useful debug appops when supported by the device
  - optionally click the MediaProjection consent dialog
  - start VisionAgent
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --no-screen-capture)
      ENABLE_SCREEN_CAPTURE=0
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

put_appop() {
  local op="$1"
  local mode="$2"
  adb_cmd shell appops set "$APP_ID" "$op" "$mode" >/dev/null 2>&1 || true
}

enable_accessibility() {
  local current
  local new_value
  current="$(adb_cmd shell settings get secure enabled_accessibility_services | tr -d '\r')"

  if [ -z "$current" ] || [ "$current" = "null" ]; then
    new_value="$ACCESSIBILITY_SERVICE"
  elif echo "$current" | grep -q "$ACCESSIBILITY_SERVICE"; then
    new_value="$current"
  else
    new_value="$current:$ACCESSIBILITY_SERVICE"
  fi

  adb_cmd shell settings put secure enabled_accessibility_services "$new_value" || true
  adb_cmd shell settings put secure accessibility_enabled 1 || true
}

tap_matching_node() {
  local pattern="$1"
  local dump_file
  local point
  dump_file="$(mktemp)"

  adb_cmd shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb_cmd exec-out cat /sdcard/window.xml > "$dump_file" 2>/dev/null || true

  point="$(python3 - "$dump_file" "$pattern" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

path, pattern = sys.argv[1], sys.argv[2]
try:
    root = ET.parse(path).getroot()
except Exception:
    sys.exit(1)

regex = re.compile(pattern)
for node in root.iter("node"):
    if node.attrib.get("clickable") != "true" and node.attrib.get("focusable") != "true":
        continue

    fields = [
        node.attrib.get("text", ""),
        node.attrib.get("content-desc", ""),
        node.attrib.get("resource-id", ""),
    ]
    if not any(regex.search(value) for value in fields):
        continue

    bounds = node.attrib.get("bounds", "")
    match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if not match:
        continue

    x1, y1, x2, y2 = map(int, match.groups())
    print(f"{(x1 + x2) // 2} {(y1 + y2) // 2}")
    sys.exit(0)

sys.exit(1)
PY
)"
  rm -f "$dump_file"

  if [ -z "$point" ]; then
    return 1
  fi

  adb_cmd shell input tap $point
}

node_exists() {
  local pattern="$1"
  local dump_file
  dump_file="$(mktemp)"

  adb_cmd shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb_cmd exec-out cat /sdcard/window.xml > "$dump_file" 2>/dev/null || true

  python3 - "$dump_file" "$pattern" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

path, pattern = sys.argv[1], sys.argv[2]
try:
    root = ET.parse(path).getroot()
except Exception:
    sys.exit(1)

regex = re.compile(pattern)
for node in root.iter("node"):
    fields = [
        node.attrib.get("text", ""),
        node.attrib.get("content-desc", ""),
        node.attrib.get("resource-id", ""),
    ]
    if any(regex.search(value) for value in fields):
        sys.exit(0)

sys.exit(1)
PY
  local result=$?
  rm -f "$dump_file"
  return "$result"
}

bootstrap_screen_capture() {
  adb_cmd shell am start -n "$MAIN_ACTIVITY" >/dev/null
  sleep 1

  if node_exists "tv_screen_capture_status|已授权"; then
    return 0
  fi

  tap_matching_node "btn_request_screen_capture|去授权" || return 1
  sleep 1

  if node_exists "tv_screen_capture_status|已授权"; then
    return 0
  fi

  tap_matching_node "立即开始|开始|Start now|Start|允许|Allow" || return 1
  sleep 1

  node_exists "tv_screen_capture_status|已授权"
}

echo "== VisionAgent dev permission bootstrap =="
adb_cmd wait-for-device

echo "Device:"
adb_cmd devices -l | sed -n '1,3p'

echo "Enable accessibility service..."
enable_accessibility

echo "Apply debug appops when supported..."
put_appop WRITE_SETTINGS allow
put_appop SYSTEM_ALERT_WINDOW allow
put_appop PROJECT_MEDIA allow
put_appop POST_NOTIFICATION allow
put_appop RUN_ANY_IN_BACKGROUND allow

echo "Start VisionAgent..."
adb_cmd shell am start -n "$MAIN_ACTIVITY" >/dev/null
sleep 1

if [ "$ENABLE_SCREEN_CAPTURE" = "1" ]; then
  echo "Try MediaProjection consent automation..."
  if bootstrap_screen_capture; then
    echo "MediaProjection consent flow completed."
  else
    echo "MediaProjection still needs one manual tap on this ROM."
  fi
fi

echo ""
echo "Current accessibility setting:"
adb_cmd shell settings get secure enabled_accessibility_services
adb_cmd shell settings get secure accessibility_enabled

echo ""
echo "Current appops:"
adb_cmd shell appops get "$APP_ID" | grep -E "WRITE_SETTINGS|SYSTEM_ALERT_WINDOW|PROJECT_MEDIA|BIND_ACCESSIBILITY_SERVICE|ACCESS_ACCESSIBILITY|START_FOREGROUND|POST_NOTIFICATION" || true

echo ""
echo "Done. Product tasks should now be executed from the VisionAgent app, not by ADB."
