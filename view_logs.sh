#!/bin/bash

# VisionAgent 日志查看脚本
# 使用方法：
#   ./view_logs.sh                         # 查看项目默认关键日志
#   ./view_logs.sh DeepSeek                # 只看包含 DeepSeek 的日志
#   ./view_logs.sh OPEN_APP                # 只看打开应用链路
#   ./view_logs.sh "AgentTask|OPEN_APP"    # 同时看多个关键字（正则）
#   ./view_logs.sh --no-clear DeepSeek     # 不清空旧日志，直接开始过滤

set -e

DEFAULT_PATTERN="com.tencent.edgeagent|VisionAgent|EdgeAgent|AgentTask|AgentFlow"
NO_CLEAR=false

if [ "$1" = "--no-clear" ]; then
  NO_CLEAR=true
  shift
fi

if [ $# -gt 0 ]; then
  PATTERN="$*"
else
  PATTERN="$DEFAULT_PATTERN"
fi

echo "========================================="
echo "VisionAgent 实时日志"
echo "========================================="
echo "过滤关键字: $PATTERN"
echo "用法示例: ./view_logs.sh DeepSeek | ./view_logs.sh \"AgentTask|OPEN_APP\""
echo "按 Ctrl+C 退出"
echo ""

if [ "$NO_CLEAR" = false ]; then
  adb logcat -c
fi

adb logcat -v threadtime | grep --line-buffered --color=auto -E "$PATTERN"
