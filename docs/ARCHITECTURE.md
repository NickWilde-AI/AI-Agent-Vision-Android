# VisionAgent Android 架构设计

本文档描述当前真实架构、模块职责、主流程和演进方向。若代码和本文档不一致，以代码为准并及时更新文档。

## 架构目标

VisionAgent Android 的核心目标是构建安全可控的 Android Agent 主链路：

```text
观察屏幕 -> 规划任务 -> 检索本地策略 -> 模型决策 -> 安全检查 -> 执行动作 -> 再次观察
```

设计重点：

- 不让模型自由执行完整任务。
- 每轮只执行一个最小动作。
- App 内任务默认由模型自主决策；确定性策略只作为低风险兜底。
- 复杂 App 使用策略约束。
- 高风险动作必须被拦截或确认。
- 失败必须可记录、可回放、可沉淀为策略。
- 本地模型优先用于端侧能力验证，云端模型作为稳定兜底。

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
  L1CommandRouter
  PlannerAgent
  ReflectionAgent
  ActionGuard
  AppStrategyRegistry

Data Layer
  LocalModelEngineProvider
  GemmaLiteRtModelEngine
  LocalModelManager
  AgentResponseJsonParser
  LocalRagEngine
  AgentTraceStore
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

### 模型优先 Agent 流程

```text
用户输入
  -> MainViewModel.executeCommand()
  -> AgentOrchestrator.executeCommand()
  -> IntentRouter.parseIntent()
  -> AgentExecutor.executeTask()
  -> PlannerAgent.plan()
  -> LocalRagEngine.retrieve()
  -> captureScreen()
  -> ReflectionAgent.reflect()
  -> buildPrompt(plan + rag + reflection + uiTree)
  -> CloudFallbackManager.inference()
  -> ActionGuard.guard()
  -> AppStrategyRegistry.apply()
  -> ActionExecutor.execute()
  -> AgentTraceStore.recordStep()
  -> captureScreen()
  -> 下一轮
```

当前默认使用阿里千问云端模型自主决定每一轮动作。ADB 只能作为开发脚手架准备权限、安装 APK 和抓日志，不能代替 Agent 完成业务任务。

### L1 安全兜底流程

```text
用户输入
  -> MainViewModel.executeCommand()
  -> AgentOrchestrator.executeCommand()
  -> 模型优先执行
  -> 云端失败或不可观测状态
  -> L1CommandRouter.resolve()
  -> ActionExecutor.execute()
  -> AgentTraceStore.recordStep()
```

该流程用于调音量、亮度、返回、Home、最近任务、打开相机、打开 Wi-Fi 设置、打开常见 App 等低风险动作的兜底。它不替代模型自主决策，只负责避免低风险任务因为模型服务不可用而完全中断。

### 本地模型流程

```text
App 启动
  -> LocalModelManager 检测 Gemma 模型文件
  -> LocalModelEngineProvider 选择 GemmaLiteRtModelEngine
  -> UI 显示本地模型已就绪

首次本地推理
  -> GemmaLiteRtModelEngine.warmUp()
  -> LiteRT-LM 加载 gemma-4-E2B-it.litertlm
  -> Engine.createConversation()
  -> 模型输出单个 JSON 动作
  -> AgentResponseJsonParser.parse()
  -> AgentResponse
```

完整模型运行时加载延迟到首次本地推理，避免 App 启动时被 2.4GB 模型加载阻塞。

## 核心模块

### AgentOrchestrator

职责：

- 对 UI 层提供统一入口。
- 默认进入模型优先的 Agent 执行路径。
- 在云端任务失败时使用 L1 确定性低风险兜底。
- 初始化本地或云端执行路径。
- 管理状态机的顶层流转。
- 隐藏多轮 Agent 的内部复杂度。

当前路由顺序：

```text
云端已配置
  -> AgentExecutor 多轮千问链路
云端失败且 L1CommandRouter 命中
  -> 低风险确定性兜底动作
云端未配置且 L1CommandRouter 命中
  -> 低风险确定性兜底动作
云端未配置且 L1CommandRouter 未命中
  -> 本地模型单轮链路
```

### L1CommandRouter

职责：

- 将低风险自然语言命令直接映射为 `AgentResponse`。
- 在模型失败或不可观测状态下兜底调音量、打开相机、打开 Wi-Fi 设置这类低风险任务。
- 对包含发送、支付、下单、删除、提交等词的复杂命令主动不接管，让多轮安全链路处理。

当前覆盖：

- 音量调高/调低。
- 亮度调高/调低。
- Wi-Fi、蓝牙、飞行模式设置页跳转。
- 返回、Home、最近任务。
- 打开相机、微信、美团、支付宝、淘宝、抖音、QQ、电话、设置、浏览器。

### AgentExecutor

职责：

- 实现多轮观察和执行循环。
- 每轮构建包含 RAG、规划、反思和 UI 树的 prompt。
- 接收模型单步动作。
- 调用 ActionGuard 和 App 专项策略。
- 调用 ActionExecutor。
- 记录 AgentTrace。

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

### AppStrategyRegistry

职责：

- 为不同 App 和任务类型加载专项策略。
- 在通用模型输出之后执行二次校验。
- 将高风险或不符合当前状态机的动作改写为安全动作。

当前策略：

- `WechatStrategy`
- `BrowserStrategy`
- `SystemSettingsStrategy`

### LocalRagEngine

职责：

- 提供本地策略检索。
- 将策略和失败经验注入 prompt。
- 支持 JSONL 持久化。

当前内置策略：

- 高风险动作必须确认。
- 微信只填草稿。
- 微信联系人搜索路径。
- 系统设置策略。
- 浏览器搜索策略。

### AgentTraceStore

职责：

- 记录任务会话。
- 记录每轮屏幕状态、模型输出、执行结果和反思信息。
- 支持最新会话可读化回放。

当前配套命令：

```bash
./view_logs.sh --replay
```

### GemmaLiteRtModelEngine

职责：

- 使用 LiteRT-LM 加载 Gemma 4 E2B。
- 构造本地 Agent prompt。
- 运行本地推理。
- 将模型输出交给 `AgentResponseJsonParser`。
- 本地推理失败时安全返回 `NO_ACTION`。

当前真机结果：

```text
source=LOCAL_VLM
action=NO_ACTION
confidence=0.95
inferenceTimeMs=18983
```

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

### ActionExecutor

职责：

- 将 `AgentResponse` 转换为真实 Android 操作。
- 执行点击、长按、滑动、输入、返回、Home、最近任务、打开 App、设备控制、等待。
- 校验坐标边界。
- 返回 `ExecutionResult`。

执行依赖：

- 点击、滑动、输入、返回、Home、最近任务需要 `EdgeAgentAccessibilityService`。
- 打开 App、调音量、打开 Wi-Fi/蓝牙/飞行模式设置可以使用 `Application Context` 执行，避免 L1 任务因为无障碍服务未连接而整体失败。

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

本地模型失败默认安全降级：

```text
加载失败 / 推理失败 / JSON 解析失败 -> NO_ACTION
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
- `AgentTraceEvent`
- `LocalModelStatus`

## 当前限制

1. RAG 已有 JSONL 持久化，但还不是向量检索。
2. 本地模型健康检查尚未写入 AgentTrace。
3. 高风险确认 UI 还不是完整产品态。
4. App 专项策略库仍需扩展状态机和测试集。
5. LiteRT-LM 在当前设备上会出现 Dispatch/NPU 加速库缺失日志，但已能回退并完成推理。
6. 真机兼容性需要更多设备和 ROM 验证。

## 演进方向

短期：

- 本地模型健康检查写入 AgentTrace。
- 模型运行状态 UI。
- `WechatStrategy` 草稿状态机继续强化。
- 高风险确认 UI。
- Browser 和 Settings 专项策略完善。

中期：

- Room 持久化 RAG。
- 向量检索。
- App 策略库。
- 任务评测集。
- 本地 OCR。

长期：

- 更多本地模型对比。
- 用户偏好记忆。
- 跨 App 任务编排。
- 准系统级 Agent 体验。
