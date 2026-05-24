#!/bin/bash

# VisionAgent 真机部署脚本
# 使用方法：
#   ./deploy_device.sh              # 构建、安装并启动 App
#   ./deploy_device.sh --no-build   # 跳过构建，直接安装现有 APK
#   ./deploy_device.sh --logs       # 部署后自动查看关键日志

set -euo pipefail

APP_ID="${APP_ID:-com.tencent.edgeagent}"
MAIN_ACTIVITY="${MAIN_ACTIVITY:-com.tencent.edgeagent/.ui.MainActivity}"
APK_PATH="${APK_PATH:-app/build/outputs/apk/debug/app-debug.apk}"

NO_BUILD=false
TAIL_LOGS=false

for arg in "$@"; do
  case "$arg" in
    --no-build)
      NO_BUILD=true
      ;;
    --logs)
      TAIL_LOGS=true
      ;;
    *)
      echo "未知参数: $arg"
      exit 1
      ;;
  esac
done

if ! command -v adb >/dev/null 2>&1; then
  echo "未找到 adb。请确认 Android SDK platform-tools 已加入 PATH。"
  exit 1
fi

DEVICE_LINES=$(adb devices | sed '1d' | sed '/^[[:space:]]*$/d')
DEVICE_COUNT=$(echo "$DEVICE_LINES" | grep -c "device$" || true)
UNAUTHORIZED_COUNT=$(echo "$DEVICE_LINES" | grep -c "unauthorized" || true)
OFFLINE_COUNT=$(echo "$DEVICE_LINES" | grep -c "offline" || true)

if [ "$UNAUTHORIZED_COUNT" -gt 0 ]; then
  echo "检测到未授权设备。请在手机上允许 USB 调试后重试。"
  adb devices -l
  exit 1
fi

if [ "$OFFLINE_COUNT" -gt 0 ]; then
  echo "检测到 offline 设备。请重新插拔 USB 或重启 adb server 后重试。"
  adb devices -l
  exit 1
fi

if [ "$DEVICE_COUNT" -eq 0 ]; then
  echo "未检测到真机。请连接手机并开启 USB 调试。"
  adb devices -l
  exit 1
fi

if [ "$DEVICE_COUNT" -gt 1 ]; then
  echo "检测到多台设备，请设置 ANDROID_SERIAL 指定目标设备。"
  adb devices -l
  exit 1
fi

DEVICE_ID=$(echo "$DEVICE_LINES" | awk '/device$/ {print $1; exit}')
echo "目标设备: $DEVICE_ID"

if [ "$NO_BUILD" = false ]; then
  echo "构建 Debug APK..."
  ./gradlew :app:assembleDebug
fi

if [ ! -f "$APK_PATH" ]; then
  echo "未找到 APK: $APK_PATH"
  exit 1
fi

echo "安装 APK..."
adb -s "$DEVICE_ID" install -r "$APK_PATH"

echo "启动 App..."
adb -s "$DEVICE_ID" shell am start -n "$MAIN_ACTIVITY"

echo ""
echo "部署完成。接下来需要在手机上手动确认："
echo "1. 在 App 内点击开启无障碍，启用 VisionAgent 服务"
echo "2. 点击授权屏幕录制，允许屏幕捕获"
echo ""
echo "常用调试命令："
echo "  ./view_logs.sh              # 实时日志"
echo "  ./view_logs.sh --replay     # 最近一次 AgentTrace 回放"

if [ "$TAIL_LOGS" = true ]; then
  echo ""
  ./view_logs.sh
fi
