#!/bin/bash

# VisionAgent 日志查看脚本
# 使用方法：./view_logs.sh

echo "========================================="
echo "VisionAgent 实时日志"
echo "========================================="
echo ""

# 清空旧日志
adb logcat -c

# 实时查看日志（带颜色和时间戳）
adb logcat -v threadtime | grep --color=auto -E "com.tencent.edgeagent|VisionAgent|EdgeAgent"
