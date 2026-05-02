#!/bin/bash

# 开发阶段一键开启 VisionAgent 无障碍服务
# 说明：正式产品不能静默开启无障碍服务；此脚本仅用于已连接 ADB 的开发/调试设备。

set -e

SERVICE="com.tencent.edgeagent/com.tencent.edgeagent.service.EdgeAgentAccessibilityService"

echo "========================================="
echo "开启 VisionAgent 无障碍服务"
echo "========================================="
echo "service: $SERVICE"
echo ""

CURRENT=$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')

if [ -z "$CURRENT" ] || [ "$CURRENT" = "null" ]; then
  NEW_VALUE="$SERVICE"
elif echo "$CURRENT" | grep -q "$SERVICE"; then
  NEW_VALUE="$CURRENT"
else
  NEW_VALUE="$CURRENT:$SERVICE"
fi

adb shell settings put secure enabled_accessibility_services "$NEW_VALUE"
adb shell settings put secure accessibility_enabled 1

echo "当前 enabled_accessibility_services:"
adb shell settings get secure enabled_accessibility_services

echo "当前 accessibility_enabled:"
adb shell settings get secure accessibility_enabled

echo ""
echo "完成。如果 App 里仍显示未连接，请关闭重开 App，或执行："
echo "adb shell am force-stop com.tencent.edgeagent && adb shell monkey -p com.tencent.edgeagent 1"
