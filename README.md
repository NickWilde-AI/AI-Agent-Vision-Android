# VisionAgent Android - 端侧优先 AI 手机助手

<div align="center">

**端侧优先 · 隐私合规 · 端云协同**

一个基于 Android 原生开发的智能手机助手，采用「端侧优先，云端兜底」架构，实现屏幕理解与自动化操作。

[![Android](https://img.shields.io/badge/Android-7.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-blue.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg)](LICENSE)

</div>

---

## 📖 项目简介

VisionAgent 是一个真实可演进的 Android Agent 项目，目标是在用户授权范围内理解手机屏幕、规划下一步动作，并通过系统无障碍能力完成可验证的自动化操作。

### 核心特性

- 🚀 **端侧优先目标**：基础设备控制与确定性操作优先在本地完成
- 🔒 **隐私合规**：设备控制、文本输入等敏感操作绝不上云
- ☁️ **视觉模型闭环**：当前默认使用千问视觉模型完成屏幕理解与多轮决策
- 🎯 **真实操作**：基于 AccessibilityService 实现屏幕点击、滑动、输入、返回、Home
- 👁️ **坐标化 UI 树**：提取 `bounds` 与 `center`，让模型能精准定位屏幕元素
- 🧭 **Agent 编排层**：由 `AgentOrchestrator` 统一协调感知、推理、执行与状态转换
- 🧠 **状态机驱动**：7 个状态，清晰的状态转换逻辑
- 🔄 **多轮反馈循环**：截图/UI 树 → 云端决策 → 无障碍执行 → 截图验证

### 应用场景

- 语音控制手机操作（"打开微信"、"向上滑动"）
- 智能屏幕理解与自动化（识别界面元素并执行操作）
- 复杂任务规划与执行（多步骤操作自动化）
- 设备控制（音量、亮度调节等）

---

## 🏗️ 架构设计

### Clean Architecture 三层架构

```
┌─────────────────────────────────────────────────────────────┐
│                    Presentation Layer                        │
│              (UI/ViewModel - XML + StateFlow)                │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                      Domain Layer                            │
│  ┌──────────────────────────────────────────────────────┐   │
│  │   AgentOrchestrator (核心编排器)                    │   │
│  │   AgentStateMachine (7 状态状态机)                   │   │
│  │   IntentRouter (6 种意图类型 + 端云路由决策)         │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                       Data Layer                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  Perception  │  │  Inference   │  │  Execution   │      │
│  │   (感知)      │  │   (推理)     │  │   (执行)      │      │
│  │              │  │              │  │              │      │
│  │ Screen       │  │ Qwen-VL      │  │ Gesture      │      │
│  │ Capture      │  │ Engine       │  │ Executor     │      │
│  │              │  │              │  │              │      │
│  │ UI Tree      │  │ Cloud        │  │ Action       │      │
│  │ Extractor    │  │ Fallback     │  │ Executor     │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                   Service Layer                              │
│         VisionAgent AccessibilityService (无障碍服务)        │
└─────────────────────────────────────────────────────────────┘
```

### 状态机设计

```
IDLE → PERCEIVING → REASONING_LOCAL → EXECUTING → COMPLETED → IDLE
                         ↓ (置信度 < 0.75)
                    REASONING_CLOUD → EXECUTING → COMPLETED → IDLE
```

**7 个状态**：IDLE、PERCEIVING、REASONING_LOCAL、REASONING_CLOUD、EXECUTING、COMPLETED、ERROR

### 意图路由系统

**6 种意图类型**：
- `DEVICE_CONTROL` - 设备控制（100% 本地，不上云）
- `TEXT_INPUT` - 文本输入（隐私保护，不上云）
- `APP_OPERATION` - 应用操作（优先本地，低置信度云端兜底）
- `INFORMATION_QUERY` - 信息查询（优先本地 RAG）
- `COMPLEX_REASONING` - 复杂推理（直接云端）
- `UNKNOWN` - 未知意图

---

## 🚀 快速开始

### 环境要求

- Android Studio（最新版本）
- Android 设备或模拟器（API 24+，Android 7.0+）
- JDK 17
- Kotlin 2.0.21

### 安装步骤

1. **克隆项目**

```bash
git clone https://github.com/NickWilde-AI/AI-Agent-Vision-Android.git
cd AI-Agent-Vision-Android
```

2. **配置云端 API（可选）**

如果需要使用云端视觉模型，先在项目根目录的 `local.properties` 写入：

```properties
ALIYUN_API_KEY=your-qwen-api-key
```

默认 Provider 在 `CloudConfig.kt` 中配置为 `CloudProvider.ALIYUN`，当前会走阿里云百炼/千问视觉模型 `qwen-vl-max`。

3. **构建并安装**

```bash
./gradlew installDebug
```

或在 Android Studio 中点击 Run 按钮。

4. **开启无障碍权限**

设置 → 无障碍 → VisionAgent → 开启服务

5. **运行 Agent**

打开应用，输入指令或点击快捷动作，观察日志输出和真实操作效果。

---

## 💡 核心功能

### 已实现功能 ✅

| 功能模块 | 状态 | 说明 |
|---------|------|------|
| 状态机系统 | ✅ 完成 | 7 个状态，完整的状态转换逻辑 |
| 意图路由 | ✅ 完成 | 6 种意图类型，智能云端决策 |
| Agent 编排器 | ✅ 完成 | 收拢感知、推理、执行与状态机流转 |
| 本地 Mock 引擎 | ✅ 完成 | 断网和开发调试兜底 |
| 无障碍服务 | ✅ 完成 | 屏幕捕获、坐标化 UI 树提取 |
| 手势执行 | ✅ 完成 | 点击、滑动、返回、Home |
| 动作执行器 | ✅ 完成 | AgentResponse → 真实无障碍操作 |
| 云端 API 集成 | ✅ 完成 | 默认千问视觉模型，保留多 Provider 抽象 |
| 多轮 Agent 闭环 | ✅ 完成 | 云端决策 → 无障碍执行 → 截图验证 |
| 全无障碍打开应用 | ✅ 完成 | HOME → 桌面找图标 → 点击启动 |
| Bitmap 对象池 | ✅ 完成 | 内存优化，避免频繁 GC |

### 待实现功能 ⏳

| 功能模块 | 优先级 | 说明 |
|---------|--------|------|
| 应用内复杂任务 | 高 | 微信发消息、美团点外卖等 App 专项流程优化 |
| 高风险动作确认 | 高 | 发消息、下单、支付前弹窗确认 |
| 本地 RAG | 中 | FAISS/SQLite 向量检索 |
| 本地 VLM | 中 | Qwen 3.5 (0.8B/2B) 集成，替换云端推理 |
| 语音交互 | 低 | ASR + TTS |

---

## 📊 技术栈

### 核心技术

- **语言**：Kotlin 2.0.21 (100%)
- **架构**：Clean Architecture + 单例模式
- **异步**：Kotlin Coroutines + Flow
- **UI**：传统 XML 布局 + ViewBinding
- **推理**：Qwen-VL-Max（云端视觉）+ MockVLM（本地调试）
- **无障碍**：AccessibilityService + GestureDescription

### 依赖库

```kotlin
// 协程
kotlinx-coroutines-core: 1.8.0
kotlinx-coroutines-android: 1.8.0

// Lifecycle
androidx-lifecycle-runtime-ktx: 2.7.0
androidx-lifecycle-viewmodel-ktx: 2.7.0

// 日志
timber: 5.0.1
```

### 为什么不用 Hilt 和 Compose？

- **Hilt**：与 Kotlin 2.0 + Java 17 存在 JavaPoet 版本冲突，选择更稳定的单例模式
- **Compose**：降低项目复杂度，使用传统 XML 布局更易维护

---

## 📚 文档导航

说明类 Markdown 主要在 **`docs/`** 目录；索引即本节下表。

| 文档 | 说明 |
|------|------|
| [上手与测试](docs/GETTING_STARTED.md) | 快速启动 + 详细测试步骤（原 QUICKSTART + TESTING_GUIDE） |
| [架构设计](docs/ARCHITECTURE.md) | 分层、状态机与组件职责 |
| [云端 API 示例](docs/API_EXAMPLES.md) | DeepSeek 等调用示例 |
| [AI 脚手架 Prompt](docs/AI_BOOTSTRAP_PROMPT.md) | 从零生成项目的 Prompt（非运行时 API 文档） |

### 历史阶段总结（均在 `docs/`）

| Phase | 文档 | 说明 |
|-------|------|------|
| Phase 1 | [PHASE1_SUMMARY.md](docs/PHASE1_SUMMARY.md) | 架构基座、状态机与意图路由 |
| Phase 2 | [PHASE2_SUMMARY.md](docs/PHASE2_SUMMARY.md) | 无障碍视觉捕获与执行层 |
| Phase 3 | [PHASE3_SUMMARY.md](docs/PHASE3_SUMMARY.md) | 真实操作执行与集成 |
| Phase 4 | [PHASE4_SUMMARY.md](docs/PHASE4_SUMMARY.md) | 云端 API 集成 |
| Phase 5 | [PHASE5_SUMMARY.md](docs/PHASE5_SUMMARY.md) | 云端优先全无障碍 AI Agent |

---

## 🧭 产品化重构方向

当前项目已经从演示型结构切换为真实 Android Agent 的主线。后续开发优先围绕稳定性、可观测性和任务成功率推进。

### 近期重点

- **屏幕感知稳定性**：MediaProjection 改为持续帧监听和最新帧缓存，避免空白截图。
- **结构化 UI 树**：在文本摘要之外引入结构化 `UiNode`，支持规则匹配、坐标校验和失败重试。
- **App 专项策略**：先优化微信搜索联系人、进入聊天、美团搜索等高频路径。
- **高风险确认**：发消息、下单、支付、删除等动作必须进入用户确认流程。
- **失败样本记录**：保存每轮包名、UI 树、截图状态、模型输出、执行结果，用于回放和调参。

---

## 🔧 开发进度

| 模块 | 状态 | 下一步 |
|------|------|--------|
| Agent 编排 | 已接入 | 拆分任务计划、观察、执行结果模型 |
| 屏幕感知 | 可用 | 持续帧缓存 + 结构化 UI 树 |
| 云端视觉 | 已接入千问 | 强化 JSON 协议和重试策略 |
| 无障碍执行 | 可用 | 动作前校验 + 高风险确认 |
| App 策略 | 初始 | 微信/美团专项成功率优化 |

**当前 MVP 状态**：已支持坐标化 UI 树、千问视觉模型、多轮 Agent 闭环、无障碍执行和基础日志。

---

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

### 开发规范

- 使用 Kotlin 编写代码
- 遵循 Clean Architecture 原则
- 每个 PR 必须包含测试
- Commit 信息使用中文，描述精简清晰

### Commit 规范

```bash
git commit -m "新增本地模型推理引擎接口"
git commit -m "修复屏幕截图权限问题"
git commit -m "优化 Agent 状态机逻辑"
```

---

## 📄 许可证

本项目采用 Apache 2.0 许可证。详见 [LICENSE](LICENSE) 文件。

---

## 📧 联系方式

- **项目地址**：https://github.com/NickWilde-AI/AI-Agent-Vision-Android
- **问题反馈**：提交 Issue
- **技术交流**：欢迎 Star 和 Fork

---

<div align="center">

**如果这个项目对你有帮助，请给一个 ⭐️ Star！**

Made with ❤️ by EdgeAgent Team

</div>
