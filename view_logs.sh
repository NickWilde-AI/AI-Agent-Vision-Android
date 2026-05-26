#!/bin/bash

# VisionAgent 日志查看脚本
# 使用方法：
#   ./view_logs.sh                         # 查看项目默认关键日志
#   ./view_logs.sh --trace                 # 查看最近一次 AgentTrace 失败/执行轨迹
#   ./view_logs.sh --replay                # 以可读格式回放最近一次 AgentTrace
#   ./view_logs.sh DeepSeek                # 只看包含 DeepSeek 的日志
#   ./view_logs.sh OPEN_APP                # 只看打开应用链路
#   ./view_logs.sh "AgentTask|OPEN_APP"    # 同时看多个关键字（正则）
#   ./view_logs.sh --no-clear DeepSeek     # 不清空旧日志，直接开始过滤

set -e

APP_ID="${APP_ID:-com.tencent.edgeagent}"
DEFAULT_PATTERN="com.tencent.edgeagent|VisionAgent|EdgeAgent|AgentTask|AgentFlow|AgentTrace|AppStrategy|RAG"
NO_CLEAR=false
TRACE_MODE=false
REPLAY_MODE=false

if [ "$1" = "--no-clear" ]; then
  NO_CLEAR=true
  shift
fi

if [ "$1" = "--trace" ]; then
  TRACE_MODE=true
  shift
fi

if [ "$1" = "--replay" ]; then
  TRACE_MODE=true
  REPLAY_MODE=true
  shift
fi

if [ "$TRACE_MODE" = true ]; then
  echo "========================================="
  if [ "$REPLAY_MODE" = true ]; then
    echo "VisionAgent 最近一次 AgentTrace 回放"
  else
    echo "VisionAgent 最近一次 AgentTrace"
  fi
  echo "========================================="
  TRACE_FILE=$(adb shell run-as "$APP_ID" cat files/agent_traces/latest.txt 2>/dev/null | tr -d '\r' || true)
  if [ -z "$TRACE_FILE" ]; then
    echo "未找到 AgentTrace。请先运行一次 Agent 任务。"
    exit 1
  fi
  echo "Trace 文件: $TRACE_FILE"
  echo ""
  if [ "$REPLAY_MODE" = true ]; then
    adb shell run-as "$APP_ID" cat "$TRACE_FILE" | python3 -c '
import json
import sys
from datetime import datetime

def fmt_time(value):
    try:
        return datetime.fromtimestamp(int(value) / 1000).strftime("%Y-%m-%d %H:%M:%S")
    except Exception:
        return "unknown"

for raw in sys.stdin:
    raw = raw.strip()
    if not raw:
        continue
    try:
        event = json.loads(raw)
    except Exception:
        print(raw)
        continue

    event_type = event.get("type")
    if event_type == "session_start":
        print("开始: {} ({})".format(event.get("goal", ""), fmt_time(event.get("timestamp", 0))))
    elif event_type == "plan":
        plan = event.get("plan") or {}
        print("规划: {} / {} / target={}".format(
            plan.get("taskType", ""),
            plan.get("safetyMode", ""),
            plan.get("targetPackage", "")
        ))
    elif event_type == "edge_cloud_decision":
        decision = event.get("decision") or {}
        print("端云路由: mode={} fallback={} intent={} privacy={} reason={}".format(
            decision.get("primaryMode", ""),
            decision.get("fallbackMode", ""),
            decision.get("intentType", ""),
            decision.get("privacyClass", ""),
            decision.get("reason", "")
        ))
    elif event_type == "model_diagnostic":
        model = event.get("model") or {}
        response = event.get("response") or {}
        print("模型诊断: success={} model={} version={} action={} elapsedMs={} error={}".format(
            event.get("success"),
            model.get("name", ""),
            model.get("version", ""),
            response.get("action", "none"),
            event.get("elapsedMs", 0),
            event.get("error", "")
        ))
    elif event_type == "step":
        screen = event.get("screen") or {}
        response = event.get("response") or {}
        execution = event.get("execution") or {}
        params = response.get("params") or {}
        print("第 {} 轮: pkg={} capture={} action={} params={} result={} {}".format(
            event.get("round", "?"),
            screen.get("packageName", ""),
            screen.get("captureMode", "UNKNOWN"),
            response.get("action", "none"),
            params,
            execution.get("status", "none"),
            execution.get("message", "")
        ))
        note = event.get("note")
        if note:
            print(f"  note: {note}")
    elif event_type == "session_finish":
        print("结束: success={} reason={}".format(event.get("success"), event.get("reason", "")))
'
  else
    adb shell run-as "$APP_ID" cat "$TRACE_FILE"
  fi
  exit 0
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
echo "用法示例: ./view_logs.sh DeepSeek | ./view_logs.sh \"AgentTask|OPEN_APP\" | ./view_logs.sh --replay"
echo "按 Ctrl+C 退出"
echo ""

if [ "$NO_CLEAR" = false ]; then
  adb logcat -c
fi

adb logcat -v threadtime | grep --line-buffered --color=auto -E "$PATTERN"
