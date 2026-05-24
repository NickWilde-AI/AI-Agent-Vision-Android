#!/bin/bash

# Development-only device awake helper for VisionAgent.
#
# It keeps the test phone usable during long Agent runs. It must not be treated
# as a product feature and must not execute user tasks through ADB.

set -euo pipefail

ADB_BIN="${ADB_BIN:-adb}"
WAKE_INTERVAL_SECONDS="${WAKE_INTERVAL_SECONDS:-45}"
SCREEN_OFF_TIMEOUT_MS="${SCREEN_OFF_TIMEOUT_MS:-2147483647}"

ADB_ARGS=()
SERIAL_LABEL="default"
if [ -n "${ANDROID_SERIAL:-}" ]; then
  ADB_ARGS=(-s "$ANDROID_SERIAL")
  SERIAL_LABEL="$(echo "$ANDROID_SERIAL" | tr -c 'A-Za-z0-9_.-' '_')"
fi

SCRIPT_PATH="$(cd "$(dirname "$0")" && pwd)/$(basename "$0")"
LAUNCH_LABEL="com.visionagent.keep-device-awake.${SERIAL_LABEL}"
LAUNCH_DOMAIN="gui/$(id -u)"
PLIST_FILE="${TMPDIR:-/tmp}/${LAUNCH_LABEL}.plist"
PID_FILE="${TMPDIR:-/tmp}/visionagent_keep_device_awake_${SERIAL_LABEL}.pid"
LOG_FILE="${TMPDIR:-/tmp}/visionagent_keep_device_awake_${SERIAL_LABEL}.log"

adb_cmd() {
  if [ ${#ADB_ARGS[@]} -gt 0 ]; then
    "$ADB_BIN" "${ADB_ARGS[@]}" "$@"
  else
    "$ADB_BIN" "$@"
  fi
}

usage() {
  cat <<EOF
Usage: ./keep_device_awake.sh <start|stop|status|once|foreground>

Environment:
  ANDROID_SERIAL              Optional adb device serial, for example 192.168.10.166:39791
  ADB_BIN                     Optional adb binary path
  WAKE_INTERVAL_SECONDS       Heartbeat interval, default 45
  SCREEN_OFF_TIMEOUT_MS       Screen timeout value, default 2147483647

This script is development-only:
  - raises Android screen_off_timeout
  - enables adb stay-awake mode
  - periodically sends KEYCODE_WAKEUP so long test runs are not interrupted

Logs:
  $LOG_FILE
EOF
}

is_running() {
  if use_launchctl; then
    launchctl print "$LAUNCH_DOMAIN/$LAUNCH_LABEL" >/dev/null 2>&1
    return $?
  fi
  [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" >/dev/null 2>&1
}

use_launchctl() {
  [ "$(uname -s)" = "Darwin" ] && command -v launchctl >/dev/null 2>&1
}

write_launchd_plist() {
  cat > "$PLIST_FILE" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key>
  <string>$LAUNCH_LABEL</string>
  <key>ProgramArguments</key>
  <array>
    <string>/bin/bash</string>
    <string>$SCRIPT_PATH</string>
    <string>foreground</string>
  </array>
  <key>EnvironmentVariables</key>
  <dict>
    <key>ADB_BIN</key>
    <string>$ADB_BIN</string>
    <key>ANDROID_SERIAL</key>
    <string>${ANDROID_SERIAL:-}</string>
    <key>WAKE_INTERVAL_SECONDS</key>
    <string>$WAKE_INTERVAL_SECONDS</string>
    <key>SCREEN_OFF_TIMEOUT_MS</key>
    <string>$SCREEN_OFF_TIMEOUT_MS</string>
  </dict>
  <key>RunAtLoad</key>
  <true/>
  <key>KeepAlive</key>
  <true/>
  <key>StandardOutPath</key>
  <string>$LOG_FILE</string>
  <key>StandardErrorPath</key>
  <string>$LOG_FILE</string>
</dict>
</plist>
EOF
}

apply_awake_settings() {
  adb_cmd wait-for-device >/dev/null 2>&1 || return 0
  adb_cmd shell settings put system screen_off_timeout "$SCREEN_OFF_TIMEOUT_MS" >/dev/null 2>&1 || true
  adb_cmd shell settings put global stay_on_while_plugged_in 7 >/dev/null 2>&1 || true
  adb_cmd shell svc power stayon true >/dev/null 2>&1 || true
  adb_cmd shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  adb_cmd shell wm dismiss-keyguard >/dev/null 2>&1 || true
}

run_foreground() {
  echo $$ > "$PID_FILE"
  trap 'rm -f "$PID_FILE"; exit 0' INT TERM EXIT

  set +e
  local tick=0
  echo "[$(date '+%F %T')] keep awake foreground started"
  while true; do
    apply_awake_settings
    tick=$((tick + 1))
    if [ $((tick % 10)) -eq 0 ]; then
      echo "[$(date '+%F %T')] keep awake heartbeat tick=$tick"
    fi
    sleep "$WAKE_INTERVAL_SECONDS"
  done
}

start() {
  if is_running; then
    echo "keep_device_awake already running"
    echo "log: $LOG_FILE"
    return 0
  fi

  if use_launchctl; then
    write_launchd_plist
    launchctl bootout "$LAUNCH_DOMAIN" "$PLIST_FILE" >/dev/null 2>&1 || true
    launchctl bootstrap "$LAUNCH_DOMAIN" "$PLIST_FILE"
    launchctl kickstart -k "$LAUNCH_DOMAIN/$LAUNCH_LABEL" >/dev/null 2>&1 || true
    echo "keep_device_awake started by launchctl: $LAUNCH_LABEL"
    echo "log: $LOG_FILE"
    return 0
  fi

  nohup env \
    ADB_BIN="$ADB_BIN" \
    ANDROID_SERIAL="${ANDROID_SERIAL:-}" \
    WAKE_INTERVAL_SECONDS="$WAKE_INTERVAL_SECONDS" \
    SCREEN_OFF_TIMEOUT_MS="$SCREEN_OFF_TIMEOUT_MS" \
    "$0" foreground > "$LOG_FILE" 2>&1 &
  echo $! > "$PID_FILE"
  sleep 1

  if is_running; then
    echo "keep_device_awake started: pid=$(cat "$PID_FILE")"
    echo "log: $LOG_FILE"
  else
    echo "failed to start keep_device_awake; see $LOG_FILE" >&2
    return 1
  fi
}

stop() {
  if use_launchctl; then
    launchctl bootout "$LAUNCH_DOMAIN" "$PLIST_FILE" >/dev/null 2>&1 || \
      launchctl bootout "$LAUNCH_DOMAIN/$LAUNCH_LABEL" >/dev/null 2>&1 || true
    rm -f "$PID_FILE"
    echo "keep_device_awake stopped: $LAUNCH_LABEL"
    return 0
  fi

  if ! is_running; then
    rm -f "$PID_FILE"
    echo "keep_device_awake is not running"
    return 0
  fi

  local pid
  pid="$(cat "$PID_FILE")"
  kill "$pid" >/dev/null 2>&1 || true
  rm -f "$PID_FILE"
  echo "keep_device_awake stopped: pid=$pid"
}

status() {
  if is_running; then
    if use_launchctl; then
      echo "running: $LAUNCH_LABEL"
      launchctl print "$LAUNCH_DOMAIN/$LAUNCH_LABEL" 2>/dev/null | grep -E "pid =|state =" || true
    else
      echo "running: pid=$(cat "$PID_FILE")"
    fi
    echo "log: $LOG_FILE"
  else
    echo "not running"
  fi
}

case "${1:-}" in
  start)
    start
    ;;
  stop)
    stop
    ;;
  status)
    status
    ;;
  once)
    apply_awake_settings
    echo "awake settings applied once"
    ;;
  foreground)
    run_foreground
    ;;
  -h|--help|"")
    usage
    ;;
  *)
    echo "Unknown command: $1" >&2
    usage
    exit 2
    ;;
esac
