# VisionAgent Android - 端侧优先 AI 手机助手

<div align="center">

**端侧优先 · 隐私合规 · 视觉理解 · 多 Agent 协作**

一个面向真实手机环境的 Android Agent。  
它能够在用户授权范围内理解屏幕、规划任务、执行操作，并在高风险动作前停下来等待确认。

[![Android](https://img.shields.io/badge/Android-7.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-blue.svg)](https://kotlinlang.org)
[![Model](https://img.shields.io/badge/Vision-Qwen--VL--Max-blueviolet.svg)](https://help.aliyun.com/zh/model-studio/)
[![Status](https://img.shields.io/badge/Status-Active%20Development-orange.svg)](#当前状态)

</div>

---

## 项目简介

VisionAgent Android 是一个真实可演进的 Android Agent 项目。它不是简单的自动点击脚本，也不是让模型自由操作手机的 Demo，而是一套面向正式产品形态设计的手机智能体基座。

项目当前采用：

- **AccessibilityService** 作为操作执行层
- **MediaProjection** 作为屏幕视觉输入
- **结构化 UI 树** 作为可解释的页面状态
- **Qwen-VL-Max** 作为默认云端视觉模型
- **Local RAG** 注入本地策略和安全记忆
- **Planner / Reflection / ActionGuard** 组成多 Agent 决策链路

目标是让手机 Agent 具备三件事：

1. 看懂当前屏幕。
2. 每次只做一个可验证动作。
3. 在风险动作前停下来，让用户掌控最终决定。

---

## 核心特性

- **真实手机操作**  
  基于无障碍服务执行点击、滑动、输入、返回、Home、最近任务、打开 App。

- **视觉 + UI 树双通道感知**  
  同时使用屏幕截图和 Accessibility UI 树，提取 `bounds`、`center`、可点击元素和结构化 `UiNode`。

- **多轮反馈闭环**  
  `观察屏幕 -> 决策单步动作 -> 执行 -> 等待页面变化 -> 再次观察`。

- **本地 RAG 策略库**  
  内置微信草稿、高风险确认、系统设置、浏览器搜索等策略，减少模型自由发挥。

- **多 Agent 协作**  
  `PlannerAgent` 负责规划，`ReflectionAgent` 负责反思，`ActionGuard` 负责安全拦截。

- **高风险动作保护**  
  默认阻止自动发送、支付、下单、删除、转账、提交等不可逆动作。

- **千问视觉模型接入**  
  默认使用阿里云百炼 `qwen-vl-max` 处理屏幕理解和动作决策。

---

## 适用场景

当前适合优先验证：

- 打开 App
- 控制系统返回、Home、最近任务
- 页面滑动和点击
- 系统设置跳转
- 浏览器搜索
- 设备控制
- 微信联系人搜索和草稿输入

当前不建议自动执行：

- 自动发送微信消息
- 自动下单
- 自动支付
- 自动删除内容
- 自动提交敏感表单

微信相关任务当前产品边界：

```text
打开微信 -> 搜索联系人 -> 进入聊天页 -> 输入草稿 -> 停止，等待用户手动发送
```

---

## 架构概览

```text
┌─────────────────────────────────────────────────────────────┐
│                    Presentation Layer                        │
│              MainActivity / MainViewModel                   │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                      Domain Layer                            │
│  AgentOrchestrator                                          │
│  AgentExecutor                                              │
│  PlannerAgent / ReflectionAgent / ActionGuard               │
│  AgentStateMachine / IntentRouter                           │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                       Data Layer                             │
│  LocalRagEngine                                             │
│  CloudFallbackManager / AliyunClient                        │
│  UITreeExtractor / ScreenCaptureManager                     │
│  ActionExecutor / GestureExecutor                           │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                      Service Layer                           │
│  EdgeAgentAccessibilityService                              │
│  ScreenCaptureService                                       │
└─────────────────────────────────────────────────────────────┘
```

核心执行链路：

```text
用户输入
  -> PlannerAgent 规划任务和安全模式
  -> LocalRagEngine 检索本地策略
  -> 捕获屏幕截图和 UI 树
  -> ReflectionAgent 注入避错提示
  -> Qwen-VL-Max 返回单步动作
  -> ActionGuard 安全拦截
  -> ActionExecutor 执行动作
  -> 再次观察屏幕
```

---

## 当前状态

| 模块 | 状态 | 说明 |
| --- | --- | --- |
| 无障碍执行 | 已接入 | 点击、长按、滑动、返回、Home、最近任务、文本输入、打开 App |
| 屏幕感知 | 已接入 | 截图、UI 树、结构化 `UiNode` |
| 云端视觉 | 已接入 | 默认阿里云百炼 `qwen-vl-max` |
| Agent 编排 | 已接入 | `AgentOrchestrator` 统一调度 |
| 多轮闭环 | 已接入 | 每轮观察、决策、执行、验证 |
| 本地 RAG | 基础版已接入 | 内置策略检索 |
| 多 Agent | 基础版已接入 | Planner / Reflection / ActionGuard |
| 安全保护 | 基础版已接入 | 高风险最终动作拦截 |
| 真机产品化验证 | 进行中 | 需要更多设备和 ROM 验证 |

---

## 快速开始

### 环境要求

- Android Studio
- JDK 17
- Android SDK
- Android 7.0+ 真机或模拟器
- Kotlin 2.0.21

### 配置 API Key

在项目根目录 `local.properties` 中写入：

```properties
sdk.dir=/path/to/Android/sdk
ALIYUN_API_KEY=your-api-key
```

`local.properties` 已被 `.gitignore` 忽略，不应提交。

### 构建

```bash
./gradlew :app:assembleDebug
```

### 测试

```bash
./gradlew :app:testDebugUnitTest
```

### 安装

```bash
./gradlew :app:installDebug
```

### 权限

首次运行需要手动开启：

- 无障碍服务
- 屏幕录制授权
- 修改系统设置权限，亮度调节场景需要

---

## 技术栈

- **语言**：Kotlin
- **架构**：Clean Architecture + Domain Agent Layer
- **异步**：Coroutines + Flow
- **UI**：XML + ViewModel
- **视觉模型**：Qwen-VL-Max
- **本地策略**：Local RAG
- **系统能力**：AccessibilityService + MediaProjection
- **日志**：Timber

---

## 文档导航

| 文档 | 说明 |
| --- | --- |
| [产品文档](docs/ROADMAP.md) | 产品愿景、用户场景、版本规划和上线目标 |
| [架构设计](docs/ARCHITECTURE.md) | 当前真实架构、模块职责和演进方向 |
| [上手与测试](docs/GETTING_STARTED.md) | 构建、权限、真机验证和常见问题 |
| [开发指南](AI_DEVELOPMENT_GUIDE.md) | 代码规范、调试方法和协作规则 |
| [API 示例](docs/API_EXAMPLES.md) | RAG、多 Agent、云端模型和安全拦截示例 |
| [AI 协作 Prompt](docs/AI_BOOTSTRAP_PROMPT.md) | 给 AI 开发助手使用的上下文 Prompt |

历史记录：

| 文档 | 说明 |
| --- | --- |
| [Phase 1](docs/PHASE1_SUMMARY.md) | 架构基座 |
| [Phase 2](docs/PHASE2_SUMMARY.md) | 无障碍感知与执行 |
| [Phase 3](docs/PHASE3_SUMMARY.md) | 动作执行器 |
| [Phase 4](docs/PHASE4_SUMMARY.md) | 云端模型接入 |
| [Phase 5](docs/PHASE5_SUMMARY.md) | 多轮 Agent 闭环 |

---

## 产品路线

近期重点：

1. 失败日志和回放系统。
2. 高风险动作确认 UI。
3. 微信草稿状态机。
4. RAG 持久化和失败样本检索。
5. 浏览器、系统设置等稳定 App 策略。
6. 真机兼容性评测。

完整产品规划见 [docs/ROADMAP.md](docs/ROADMAP.md)。

---

## 安全声明

VisionAgent Android 的设计目标不是绕过第三方 App 的安全限制，也不是替用户自动完成不可逆操作。

项目默认遵守：

- 用户授权优先。
- 高风险动作确认优先。
- 可解释和可回放优先。
- 隐私敏感任务本地优先。

---

## 验证状态

最近一次本地验证：

```text
./gradlew :app:assembleDebug :app:testDebugUnitTest
BUILD SUCCESSFUL
```

真机端到端效果取决于设备、ROM、权限状态和目标 App 页面结构。

---

<div align="center">

**VisionAgent Android**  
让手机 Agent 从“能点”走向“可靠、安全、可交付”。

</div>
