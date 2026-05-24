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
  [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" >/dev/null 2>&1
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

  echo "[$(date '+%F %T')] keep awake foreground started"
  while true; do
    apply_awake_settings
    sleep "$WAKE_INTERVAL_SECONDS"
  done
}

start() {
  if is_running; then
    echo "keep_device_awake already running: pid=$(cat "$PID_FILE")"
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
    echo "running: pid=$(cat "$PID_FILE")"
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
