# VisionAgent Android

VisionAgent Android 是一个面向真实手机环境的 Android Agent 项目。它通过无障碍服务、屏幕截图、结构化 UI 树、视觉模型、本地 RAG 和多 Agent 协作，在用户授权范围内完成可验证的手机操作。

当前项目重点不是“让模型自由点击屏幕”，而是构建一套可控、可观测、可逐步产品化的 Android Agent 基座。

## 当前状态

已具备的核心能力：

| 模块 | 状态 | 说明 |
| --- | --- | --- |
| 无障碍执行 | 已接入 | 点击、长按、滑动、返回、Home、最近任务、文本输入、打开 App |
| 屏幕感知 | 已接入 | MediaProjection 截图、Accessibility UI 树、结构化 `UiNode` |
| 云端视觉模型 | 已接入 | 默认走阿里云百炼 `qwen-vl-max` |
| Agent 编排 | 已接入 | `AgentOrchestrator` 统一协调任务执行 |
| 多轮反馈循环 | 已接入 | 观察屏幕、模型决策、执行动作、再次观察 |
| 本地 RAG | 基础版已接入 | 内置微信草稿、安全确认、系统设置、浏览器等策略 |
| 多 Agent | 基础版已接入 | `PlannerAgent`、`ReflectionAgent`、`ActionGuard` |
| 安全拦截 | 基础版已接入 | 阻止发送、支付、下单、删除、提交等高风险最终动作 |

当前尚未完成：

| 模块 | 缺口 |
| --- | --- |
| 真机端到端验证 | 需要真实设备验证截图、无障碍动作、千问接口、厂商 ROM 兼容性 |
| App 专项策略 | 微信、美团、浏览器、设置等还需要状态机化策略 |
| RAG 持久化 | 当前是内置关键词检索，尚未接 Room、Embedding、向量检索 |
| 失败回放 | 尚未保存每轮截图、UI 树、模型输出、执行结果 |
| 用户确认 UI | 当前高风险动作会被拦截，但还没有产品级确认弹窗 |
| 本地 VLM | 尚未接入端侧视觉模型 |

## 设计原则

1. 每轮只执行一个最小动作，执行后重新观察屏幕。
2. App 内复杂任务必须由策略约束，不允许模型任意自由点击。
3. 高风险动作默认不自动执行，包括发送、支付、下单、删除、转账、提交。
4. 视觉截图和 UI 树同时使用；缺任何一侧时必须降低动作激进程度。
5. RAG 用来注入本地策略和失败经验，减少模型幻觉。
6. 所有真实动作必须可追踪、可解释、可回放。

## 架构概览

```text
UI / MainActivity
  -> MainViewModel
  -> AgentOrchestrator
  -> PlannerAgent
  -> LocalRagEngine
  -> AgentExecutor
     -> ReflectionAgent
     -> CloudFallbackManager / Qwen-VL-Max
     -> ActionGuard
     -> ActionExecutor
  -> EdgeAgentAccessibilityService
  -> ScreenCaptureService
```

关键链路：

```text
用户输入
  -> 规划任务和安全模式
  -> 检索本地策略
  -> 捕获截图和 UI 树
  -> 模型返回单步动作
  -> 安全拦截
  -> 无障碍执行
  -> 等待页面变化
  -> 再次捕获屏幕
```

## 快速开始

### 1. 配置 API Key

在项目根目录创建或更新 `local.properties`：

```properties
sdk.dir=/path/to/Android/sdk
ALIYUN_API_KEY=your-api-key
```

当前默认配置在 `CloudConfig.kt`：

```kotlin
val PROVIDER = CloudProvider.ALIYUN
```

阿里云客户端默认模型：

```kotlin
qwen-vl-max
```

### 2. 构建

```bash
./gradlew :app:assembleDebug
```

### 3. 单元测试

```bash
./gradlew :app:testDebugUnitTest
```

### 4. 真机运行前置条件

需要在 Android 设置中开启：

- 无障碍服务
- 屏幕录制授权
- 修改系统设置权限，亮度调节场景需要

建议先验证低风险任务：

- 打开设置
- 返回
- Home
- 向上滑动
- 打开浏览器搜索
- 微信只填草稿，不自动发送

## 文档导航

| 文档 | 用途 |
| --- | --- |
| [开发路线图](docs/ROADMAP.md) | 从 0 到 1 的完整开发任务、验收标准和理想形态 |
| [架构设计](docs/ARCHITECTURE.md) | 当前真实架构、模块职责、数据流和演进方向 |
| [上手与测试](docs/GETTING_STARTED.md) | 环境、构建、权限、真机验证流程 |
| [开发指南](AI_DEVELOPMENT_GUIDE.md) | 代码规范、调试方法、贡献方式 |
| [API 示例](docs/API_EXAMPLES.md) | 云端模型、本地 RAG、多 Agent、安全拦截示例 |
| [AI 协作 Prompt](docs/AI_BOOTSTRAP_PROMPT.md) | 给 AI 开发助手使用的项目上下文 Prompt |

历史阶段记录：

| 文档 | 内容 |
| --- | --- |
| [Phase 1](docs/PHASE1_SUMMARY.md) | 架构基座和状态机 |
| [Phase 2](docs/PHASE2_SUMMARY.md) | 无障碍服务和屏幕感知 |
| [Phase 3](docs/PHASE3_SUMMARY.md) | 动作执行器 |
| [Phase 4](docs/PHASE4_SUMMARY.md) | 云端模型接入 |
| [Phase 5](docs/PHASE5_SUMMARY.md) | 多轮 Agent 闭环 |

## 推荐开发顺序

当前下一步优先级：

1. 真机端到端验证和失败日志。
2. 微信草稿策略状态机。
3. ActionGuard 确认弹窗。
4. RAG 持久化和失败样本检索。
5. Browser / Settings 专项策略。
6. 本地 VLM 探索。

完整路线见 [docs/ROADMAP.md](docs/ROADMAP.md)。

## 安全边界

默认禁止自动执行：

- 发送消息
- 支付
- 下单
- 删除
- 转账
- 提交订单
- 任何不可逆确认

微信相关任务当前只允许做到：

```text
打开微信 -> 找联系人 -> 进入聊天 -> 填入草稿 -> 停止等待用户确认
```

## 验证状态

最近一次本地验证：

```text
./gradlew :app:assembleDebug :app:testDebugUnitTest
BUILD SUCCESSFUL
```

真机验证依赖实际设备连接和系统授权，不应仅用构建成功代表功能完成。
