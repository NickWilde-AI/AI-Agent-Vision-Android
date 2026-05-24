# VisionAgent Android - 端侧优先 AI 手机助手

<div align="center">

**端侧模型 · 云端兜底 · 屏幕理解 · 多 Agent 协作 · 安全可控**

一个面向真实手机环境的 Android Agent。  
它能够在用户授权范围内理解屏幕、规划任务、执行操作，并在高风险动作前停下来等待确认。

[![Android](https://img.shields.io/badge/Android-7.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-blue.svg)](https://kotlinlang.org)
[![Local Model](https://img.shields.io/badge/Local-Gemma%204%20E2B-purple.svg)](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm)
[![Runtime](https://img.shields.io/badge/Runtime-LiteRT--LM%200.12.0-orange.svg)](https://github.com/google-ai-edge/LiteRT-LM)
[![Cloud](https://img.shields.io/badge/Cloud-Qwen--VL--Max-blueviolet.svg)](https://help.aliyun.com/zh/model-studio/)

</div>

---

## 项目简介

VisionAgent Android 是一个真实可演进的 Android Agent 项目。它不是简单的自动点击脚本，也不是让模型自由操作手机的演示工程，而是一套面向正式产品形态设计的手机智能体基座。

当前项目采用：

- **AccessibilityService** 执行点击、滑动、输入、返回、Home、打开 App 等动作。
- **MediaProjection** 提供屏幕截图。
- **结构化 UI 树** 提供可解释的页面状态。
- **Gemma 4 E2B + LiteRT-LM** 提供 Android 本地模型推理能力。
- **Qwen-VL-Max** 提供云端视觉模型兜底。
- **L1 安全兜底策略** 在模型失败时处理调音量、打开相机、Wi-Fi 设置等低风险任务。
- **Local RAG** 注入本地策略、安全约束和失败经验。
- **Planner / Reflection / ActionGuard** 组成多 Agent 决策链路。
- **AgentTrace** 记录失败日志和回放材料。

目标是让手机 Agent 具备三件事：

1. 看懂当前屏幕。
2. 每次只做一个可验证动作。
3. 在风险动作前停下来，让用户掌控最终决定。

---

## 当前能力

| 模块 | 状态 | 说明 |
| --- | --- | --- |
| 无障碍执行 | 已接入 | 点击、长按、滑动、返回、Home、最近任务、文本输入、打开 App |
| L1 兜底任务 | 已接入 | 调音量、亮度、Home、最近任务、相机、Wi-Fi 设置、常见 App 启动 |
| 屏幕感知 | 已接入 | 截图、UI 树、结构化 `UiNode` |
| 本地模型 | 已跑通 | Gemma 4 E2B + LiteRT-LM，真机健康检查成功 |
| 云端视觉 | 已接入 | 默认阿里云百炼 `qwen-vl-max` |
| Agent 编排 | 已接入 | `AgentOrchestrator` 统一调度 |
| 多轮闭环 | 已接入 | 每轮观察、决策、执行、验证 |
| 本地 RAG | 已接入 | JSONL 持久化和策略检索 |
| App 策略库 | 已接入 | 微信草稿、浏览器、系统设置 |
| AgentTrace | 已接入 | JSONL 日志和最新会话回放 |
| 安全保护 | 已接入 | 高风险最终动作拦截 |
| 真机验证 | 进行中 | Redmi K60 已完成本地模型烟测 |

---

## 安全边界

VisionAgent Android 默认不自动执行以下高风险动作：

- 发送消息
- 支付
- 下单
- 删除
- 转账
- 提交订单
- 不可逆确认

微信相关任务当前边界：

```text
打开微信 -> 搜索联系人 -> 进入聊天页 -> 输入草稿 -> 停止，等待用户手动发送
```

---

## 架构概览

```text
Presentation Layer
  MainActivity / MainViewModel
        ↓
Domain Layer
  AgentOrchestrator
  AgentExecutor
  PlannerAgent / ReflectionAgent / ActionGuard
  AgentStateMachine / IntentRouter
        ↓
Data Layer
  LocalModelEngine / LocalRagEngine / AgentTraceStore
  CloudFallbackManager / AliyunClient
  UITreeExtractor / ScreenCaptureManager
  ActionExecutor / GestureExecutor
        ↓
Service Layer
  EdgeAgentAccessibilityService
  ScreenCaptureService
```

核心执行链路：

```text
用户输入
  -> PlannerAgent 规划任务和安全模式
  -> LocalRagEngine 检索本地策略
  -> 捕获屏幕截图和 UI 树
  -> ReflectionAgent 注入避错提示
  -> Qwen-VL-Max 或本地模型返回单步动作
  -> ActionGuard 安全拦截
  -> ActionExecutor 执行动作
  -> 再次观察屏幕
```

低风险的 L1 确定性策略只作为模型失败或不可观测状态下的安全兜底，不替代模型自主决策。

---

## 快速开始

环境要求：

- Android Studio
- JDK 17
- Android SDK
- Android 7.0+ 真机或模拟器
- 推荐真机测试，无障碍和屏幕录制在模拟器上的行为不完全可靠

配置 `local.properties`：

```properties
sdk.dir=/path/to/Android/sdk
ALIYUN_API_KEY=your-api-key
```

构建、测试、安装：

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:installDebug
```

完整验证：

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

首次运行需要手动开启：

- 无障碍服务
- 屏幕录制授权
- 修改系统设置权限，亮度调节场景需要

开发测试机可以使用权限准备脚本减少重复操作：

```bash
./dev_bootstrap_permissions.sh
```

该脚本只用于开发环境准备，不通过 ADB 执行产品任务。真正的手机任务必须从 App 内由 Agent 执行。

---

## 本地模型

当前端侧模型链路：

```text
VisionAgent APK
  ├─ LiteRT-LM 0.12.0 运行库
  └─ 读取手机 App 私有目录中的 Gemma 4 E2B 模型文件
```

模型文件不打进 APK，部署路径为：

```text
/data/data/com.tencent.edgeagent/files/models/gemma-4-e2b-it/gemma-4-E2B-it.litertlm
```

推送脚本：

```bash
./push_gemma_model.sh
```

真机健康检查已经成功：

```text
source=LOCAL_VLM
action=NO_ACTION
confidence=0.95
inferenceTimeMs=18983
```

---

## 文档导航

当前保留 6 个长期维护入口：

| 文档 | 说明 |
| --- | --- |
| [README.md](README.md) | 项目门面、能力概览、快速开始 |
| [PROJECT_MEMORY.md](PROJECT_MEMORY.md) | 项目长期记忆、设备状态、当前任务线 |
| [docs/PRODUCT.md](docs/PRODUCT.md) | 产品定位、用户场景、版本规划、能力边界 |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 当前真实架构、模块职责、主流程 |
| [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) | 开发运行手册、API 示例、调试命令、AI 协作上下文 |
| [docs/HISTORY.md](docs/HISTORY.md) | Phase 1-5 历史记录和演进说明 |

---

## 当前优先级

1. 完成 L1 真机验收：调音量、Home、打开相机、打开 Wi-Fi 设置、打开常见 App。
2. 保持阿里千问作为云端主链路，打通 L2/L3 多轮任务。
3. 将本地模型健康检查和 L1 执行结果写入 AgentTrace。
4. 继续强化微信草稿状态机，确保不自动点击最终发送。
5. 扩展 App 专项策略库，优先覆盖浏览器、系统设置、美团、电话、微信。
6. 完成更多真机任务走查和失败样本沉淀。

---

## 安全声明

VisionAgent Android 的设计目标不是绕过第三方 App 的安全限制，也不是替用户自动完成不可逆操作。

项目默认遵守：

- 用户授权优先。
- 高风险动作确认优先。
- 可解释和可回放优先。
- 隐私敏感任务本地优先。

---

<div align="center">

**VisionAgent Android**  
让手机 Agent 从“能点”走向“可靠、安全、可交付”。

</div>
