#!/bin/bash

# Push Gemma 4 E2B LiteRT-LM model to the connected Android device.
# Usage:
#   ./push_gemma_model.sh
#   ANDROID_SERIAL=<serial> ./push_gemma_model.sh

set -euo pipefail

APP_ID="${APP_ID:-com.tencent.edgeagent}"
MODEL_NAME="${MODEL_NAME:-gemma-4-E2B-it.litertlm}"
LOCAL_MODEL_PATH="${LOCAL_MODEL_PATH:-local_models/gemma-4-e2b-it/$MODEL_NAME}"
DEVICE_TMP_PATH="${DEVICE_TMP_PATH:-/data/local/tmp/$MODEL_NAME}"
APP_MODEL_DIR="${APP_MODEL_DIR:-files/models/gemma-4-e2b-it}"
EXPECTED_SIZE="${EXPECTED_SIZE:-2588147712}"

if ! command -v adb >/dev/null 2>&1; then
  echo "未找到 adb。请确认 Android SDK platform-tools 已加入 PATH。"
  exit 1
fi

if [ ! -f "$LOCAL_MODEL_PATH" ]; then
  echo "未找到本地模型文件: $LOCAL_MODEL_PATH"
  exit 1
fi

LOCAL_SIZE=$(wc -c < "$LOCAL_MODEL_PATH" | tr -d ' ')
if [ "$LOCAL_SIZE" != "$EXPECTED_SIZE" ]; then
  echo "本地模型大小不匹配:"
  echo "  path: $LOCAL_MODEL_PATH"
  echo "  actual: $LOCAL_SIZE"
  echo "  expected: $EXPECTED_SIZE"
  exit 1
fi

DEVICE_ARGS=()
if [ -n "${ANDROID_SERIAL:-}" ]; then
  DEVICE_ARGS=(-s "$ANDROID_SERIAL")
else
  DEVICE_LINES=$(adb devices | sed '1d' | sed '/^[[:space:]]*$/d')
  DEVICE_COUNT=$(echo "$DEVICE_LINES" | grep -c "device$" || true)
  if [ "$DEVICE_COUNT" -ne 1 ]; then
    echo "请保证只有一台设备在线，或设置 ANDROID_SERIAL。"
    adb devices -l
    exit 1
  fi
  DEVICE_ID=$(echo "$DEVICE_LINES" | awk '/device$/ {print $1; exit}')
  DEVICE_ARGS=(-s "$DEVICE_ID")
fi

echo "目标设备:"
adb "${DEVICE_ARGS[@]}" shell 'getprop ro.product.model; getprop ro.product.device; getprop ro.build.version.release'

if ! adb "${DEVICE_ARGS[@]}" shell "run-as '$APP_ID' pwd" >/dev/null 2>&1; then
  echo "run-as $APP_ID 不可用。请先安装 debug 包：./deploy_device.sh"
  exit 1
fi

echo "推送模型到临时目录: $DEVICE_TMP_PATH"
adb "${DEVICE_ARGS[@]}" push "$LOCAL_MODEL_PATH" "$DEVICE_TMP_PATH"
adb "${DEVICE_ARGS[@]}" shell "chmod 644 '$DEVICE_TMP_PATH'"

echo "复制模型到 App 私有目录: $APP_MODEL_DIR"
adb "${DEVICE_ARGS[@]}" shell "run-as '$APP_ID' mkdir -p '$APP_MODEL_DIR'"
adb "${DEVICE_ARGS[@]}" shell "run-as '$APP_ID' cp '$DEVICE_TMP_PATH' '$APP_MODEL_DIR/$MODEL_NAME'"

REMOTE_SIZE=$(adb "${DEVICE_ARGS[@]}" shell "run-as '$APP_ID' wc -c '$APP_MODEL_DIR/$MODEL_NAME'" | awk '{print $1}' | tr -d '\r ')
if [ "$REMOTE_SIZE" != "$EXPECTED_SIZE" ]; then
  echo "设备端模型大小不匹配:"
  echo "  remote: $REMOTE_SIZE"
  echo "  expected: $EXPECTED_SIZE"
  exit 1
fi

echo "模型已部署:"
adb "${DEVICE_ARGS[@]}" shell "run-as '$APP_ID' ls -lh '$APP_MODEL_DIR/$MODEL_NAME'"
echo ""
echo "App 私有模型路径:"
echo "/data/data/$APP_ID/$APP_MODEL_DIR/$MODEL_NAME"
