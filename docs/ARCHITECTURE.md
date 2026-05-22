# VisionAgent Android 架构设计

本文档描述当前真实架构、模块职责和演进方向。若代码和本文档不一致，以代码为准并及时更新文档。

## 架构目标

VisionAgent Android 的核心目标是构建安全可控的 Android Agent 主链路：

```text
观察屏幕 -> 规划任务 -> 检索本地策略 -> 单步决策 -> 安全检查 -> 执行动作 -> 再次观察
```

设计重点：

- 不让模型自由执行完整任务。
- 每轮只执行一个最小动作。
- 复杂 App 使用策略约束。
- 高风险动作必须被拦截或确认。
- 所有失败都应该可记录、可回放、可沉淀为策略。

## 分层结构

```text
Presentation Layer
  MainActivity
  MainViewModel

Domain Layer
  AgentOrchestrator
  AgentExecutor
  AgentStateMachine
  IntentRouter
  PlannerAgent
  ReflectionAgent
  ActionGuard

Data Layer
  LocalRagEngine
  CloudFallbackManager
  AliyunClient / DeepSeekClient
  UITreeExtractor
  ScreenCaptureManager
  ActionExecutor
  GestureExecutor

Service Layer
  EdgeAgentAccessibilityService
  ScreenCaptureService
```

## 主流程

```text
用户输入
  -> MainViewModel.executeCommand()
  -> AgentOrchestrator.executeCommand()
  -> AgentStateMachine: IDLE -> PERCEIVING
  -> AgentExecutor.executeTask()
  -> PlannerAgent.plan()
  -> LocalRagEngine.retrieve()
  -> captureScreen()
  -> ReflectionAgent.reflect()
  -> buildPrompt(plan + rag + reflection + uiTree)
  -> CloudFallbackManager.inference()
  -> ActionGuard.guard()
  -> ActionExecutor.execute()
  -> captureScreen()
  -> 下一轮
```

## 核心模块

### AgentOrchestrator

职责：

- 对 UI 层提供统一入口。
- 初始化本地或云端执行路径。
- 管理状态机的顶层流转。
- 隐藏多轮 Agent 的内部复杂度。

### AgentExecutor

职责：

- 实现多轮观察和执行循环。
- 每轮构建包含 RAG、规划、反思和 UI 树的 prompt。
- 接收模型单步动作。
- 调用 ActionGuard。
- 调用 ActionExecutor。
- 保存本轮对话历史。

### PlannerAgent

职责：

- 识别任务类型。
- 识别目标包名。
- 判断安全模式。
- 检索本地 RAG 策略。
- 生成 `AgentPlan`。

当前任务类型：

- `OPEN_APP`
- `DEVICE_CONTROL`
- `WECHAT_DRAFT`
- `BROWSER_SEARCH`
- `APP_NAVIGATION`
- `GENERAL`

当前安全模式：

- `AUTO`
- `REQUIRE_CONFIRMATION`
- `DRAFT_ONLY`

### LocalRagEngine

职责：

- 提供本地策略检索。
- 当前为无依赖关键词检索。
- 后续可替换为 Room + Embedding + 向量检索。

当前内置策略：

- 高风险动作必须确认。
- 微信只填草稿。
- 微信联系人搜索路径。
- 系统设置策略。
- 浏览器搜索策略。

### ReflectionAgent

职责：

- 检测连续失败。
- 检测重复动作。
- 检测连续等待。
- 检测截图和 UI 树同时不可用。
- 给模型注入下一步避错提示。
- 必要时中止任务。

### ActionGuard

职责：

- 执行动作前进行安全拦截。
- 拦截发送、支付、下单、删除、转账、提交等高风险动作。
- 微信草稿模式下禁止点击发送。
- 将被拦截动作转为 `NO_ACTION`。

### UITreeExtractor

职责：

- 从 `AccessibilityNodeInfo` 提取 UI 树。
- 生成模型可读的文本摘要。
- 生成结构化 `UiNode`。
- 输出可点击元素列表和坐标。

### ScreenCaptureService

职责：

- 管理 MediaProjection。
- 使用 ImageReader 持续监听屏幕帧。
- 缓存最新截图。
- 为 Agent 提供稳定截图来源。

## 状态机

当前状态：

- `IDLE`
- `PERCEIVING`
- `REASONING_LOCAL`
- `REASONING_CLOUD`
- `EXECUTING`
- `ERROR`
- `COMPLETED`

状态机用于 UI 状态和主链路约束。多 Agent 内部状态目前由 `AgentExecutor` 管理，后续可进一步拆为任务级状态机。

## 安全策略

默认禁止自动执行：

- 发送消息
- 支付
- 下单
- 删除
- 转账
- 提交订单
- 不可逆确认

微信任务默认 `DRAFT_ONLY`：

```text
允许：打开微信、搜索联系人、进入聊天页、输入草稿
禁止：点击发送
```

## 数据模型

关键模型：

- `AgentResponse`
- `ActionType`
- `ActionParams`
- `ScreenData`
- `AgentPlan`
- `AgentReflection`
- `RagDocument`
- `RagHit`
- `UiNode`
- `UiTreeSnapshot`

## 当前限制

1. RAG 仍是关键词检索，不是向量检索。
2. 没有持久化失败轨迹。
3. 没有产品级确认 UI。
4. App 专项策略还未状态机化。
5. 本地 VLM 未接入。
6. 真机兼容性需要更多设备验证。

## 演进方向

短期：

- `AgentTrace` 失败日志。
- `WechatStrategy` 草稿状态机。
- 高风险确认 UI。
- Browser 和 Settings 专项策略。

中期：

- Room 持久化 RAG。
- 失败样本检索。
- App 策略库。
- 任务评测集。

长期：

- 本地视觉模型。
- 本地 OCR。
- 用户偏好记忆。
- 跨 App 任务编排。
