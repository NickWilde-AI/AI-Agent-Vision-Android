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

VisionAgent 是一个面向 2026 年 AI 大厂面试的**顶级项目作品集**，展示了端侧 AI 工程落地能力和移动端架构设计水平。

### 核心特性

- 🚀 **端侧优先**：断网场景 100% 基础可用，本地推理优先
- 🔒 **隐私合规**：设备控制、文本输入等敏感操作绝不上云
- ☁️ **云端优先闭环**：当前阶段所有联网任务优先走云端多轮 Agent，先跑通商业化闭环
- 🎯 **真实操作**：基于 AccessibilityService 实现屏幕点击、滑动、输入、返回、Home
- 👁️ **坐标化 UI 树**：提取 `bounds` 与 `center`，让模型能精准定位屏幕元素
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
│  │   AgentStateMachine (7 状态状态机)                   │   │
│  │   IntentRouter (6 种意图类型 + 云端路由决策)         │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                       Data Layer                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  Perception  │  │  Inference   │  │  Execution   │      │
│  │   (感知)      │  │   (推理)     │  │   (执行)      │      │
│  │              │  │              │  │              │      │
│  │ Screen       │  │ MockVLM      │  │ Gesture      │      │
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

如果需要使用云端 AI 功能，编辑 `app/src/main/java/com/tencent/edgeagent/data/cloud/CloudConfig.kt`：

```kotlin
object CloudConfig {
    const val ENABLE_CLOUD = true  // 启用云端
    val PROVIDER = CloudProvider.DEEPSEEK
    const val DEEPSEEK_API_KEY = "sk-your-api-key-here"  // 替换为你的 API Key
}
```

获取 API Key：https://platform.deepseek.com/

3. **构建并安装**

```bash
./gradlew installDebug
```

或在 Android Studio 中点击 Run 按钮。

4. **开启无障碍权限**

设置 → 无障碍 → VisionAgent → 开启服务

5. **测试功能**

打开应用，点击测试按钮，观察日志输出和真实操作效果。

---

## 💡 核心功能

### 已实现功能 ✅

| 功能模块 | 状态 | 说明 |
|---------|------|------|
| 状态机系统 | ✅ 完成 | 7 个状态，完整的状态转换逻辑 |
| 意图路由 | ✅ 完成 | 6 种意图类型，智能云端决策 |
| Mock 推理引擎 | ✅ 完成 | 模拟真实 VLM 推理流程 |
| 无障碍服务 | ✅ 完成 | 屏幕捕获、坐标化 UI 树提取 |
| 手势执行 | ✅ 完成 | 点击、滑动、返回、Home |
| 动作执行器 | ✅ 完成 | AgentResponse → 真实无障碍操作 |
| 云端 API 集成 | ✅ 完成 | DeepSeek / 阿里云 API，统一客户端抽象 |
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
- **推理**：MockVLM (开发阶段) + DeepSeek API (云端)
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

说明类 Markdown 已收到 **`docs/`** 目录，避免根目录散乱。[**文档索引（一览表）**](docs/README.md)

| 文档 | 说明 |
|------|------|
| [上手与测试](docs/GETTING_STARTED.md) | 快速启动 + 详细测试步骤（原 QUICKSTART + TESTING_GUIDE） |
| [Phase 1 总结](PHASE1_SUMMARY.md) | 架构基座、状态机与意图路由（单独维护） |
| [架构设计](docs/ARCHITECTURE.md) | 分层、状态机与组件职责 |
| [Phase 演进历史](docs/PHASE_HISTORY.md) | Phase 2～5 总结合并归档 |
| [云端 API 示例](docs/API_EXAMPLES.md) | DeepSeek 等调用示例 |
| [AI 脚手架 Prompt](docs/AI_BOOTSTRAP_PROMPT.md) | 从零生成项目的 Prompt（非运行时 API 文档） |

---

## 🎯 面试亮点

### 1. 端侧 AI 工程落地能力

- **问题**：如何在移动端实现 AI Agent？
- **回答**：我采用「端侧优先，云端兜底」架构，本地 Mock 引擎模拟推理流程，低置信度自动调用 DeepSeek API。通过 AccessibilityService 捕获屏幕和 UI 树，实现真实的点击、滑动操作。

### 2. 架构设计能力

- **问题**：为什么选择状态机模式？
- **回答**：Agent 的工作流是典型的状态机模型。我定义了 7 个状态和明确的转换规则，每次状态转换都会校验合法性。比如只有在 REASONING_LOCAL 状态下，才能根据置信度决定是执行还是云端兜底。

### 3. 隐私合规意识

- **问题**：如何保证用户隐私？
- **回答**：我在 IntentRouter 中为每种意图设置了 `allowCloudFallback` 标志。设备控制和文本输入的意图，这个标志是 false，意味着数据绝不上云。这是在架构层面做的隐私保护。

### 4. 性能优化能力

- **问题**：频繁截图会导致内存问题，如何优化？
- **回答**：我实现了 Bitmap 对象池（ScreenCaptureManager），使用 ConcurrentLinkedQueue 管理最多 3 个 Bitmap。每次需要截图时先从池中获取，用完后回收到池中，避免频繁的内存分配和 GC。

---

## 🔧 开发进度

| Phase | 功能 | 状态 | 完成度 |
|-------|------|------|--------|
| Phase 1 | 架构与基座搭建 | ✅ 完成 | 100% |
| Phase 2 | 无障碍服务 | ✅ 完成 | 100% |
| Phase 3 | 真实操作执行 | ✅ 完成 | 80% |
| Phase 4 | 云端 API 集成 | ✅ 完成 | 100% |
| Phase 5 | 云端优先全无障碍 Agent | ✅ 完成 | 100% |
| Phase 6 | 本地 RAG | ⏳ 待开发 | 0% |
| Phase 7 | 本地 VLM | ⏳ 可选 | 0% |
| Phase 8 | 语音交互 | ⏳ 可选 | 0% |

**当前 MVP 状态**：已切换为云端优先多轮 Agent 主流程，支持坐标化 UI 树、全无障碍执行、截图验证和结构化日志。

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
