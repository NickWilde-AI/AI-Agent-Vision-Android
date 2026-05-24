# VisionAgent Android 历史记录

本文档合并原 Phase 1-5 总结，用于保留项目早期演进脉络。当前真实架构以 [ARCHITECTURE.md](ARCHITECTURE.md) 为准，当前产品路线以 [PRODUCT.md](PRODUCT.md) 为准。

## Phase 1：架构基座

目标：建立 Android Agent 的基础工程结构。

完成内容：

| 内容 | 状态 |
| --- | --- |
| Kotlin Android 项目基座 | 完成 |
| `AgentStateMachine` | 完成 |
| `IntentRouter` | 完成 |
| `AgentResponse` / `ActionType` / `ActionParams` | 完成 |
| `ScreenData` | 完成 |
| Mock 本地推理引擎 | 完成 |
| 基础 UI | 完成 |

关键产物：

- `domain/model/AgentState.kt`
- `domain/model/AgentIntent.kt`
- `domain/model/AgentResponse.kt`
- `domain/agent/AgentStateMachine.kt`
- `domain/agent/IntentRouter.kt`
- `data/inference/ILocalModelEngine.kt`
- `data/inference/MockModelEngine.kt`

当前演进：

- Phase 1 的状态机和数据模型仍在使用。
- 当前项目已经引入 `AgentOrchestrator`、`AgentExecutor`、`PlannerAgent`、`ReflectionAgent`、`ActionGuard`、`LocalRagEngine`。

## Phase 2：无障碍感知与执行

目标：让 Agent 具备 Android 手机上的“眼”和“手”。

完成内容：

| 内容 | 状态 |
| --- | --- |
| `EdgeAgentAccessibilityService` | 完成 |
| `GestureExecutor` | 完成 |
| `ScreenCaptureManager` | 完成 |
| `UITreeExtractor` | 完成 |
| 无障碍服务配置 | 完成 |

当前增强：

- `ScreenCaptureService` 改为持续帧缓存。
- `UITreeExtractor` 增加结构化 `UiNode`。
- `ActionExecutor` 增加坐标越界校验。
- 文本输入增加 `ACTION_SET_TEXT` 和剪贴板降级。

已知限制：

- 不同厂商 ROM 对无障碍权限限制不同。
- WebView、自绘 UI、复杂 App 页面可能导致 UI 树信息不足。
- 屏幕录制权限需要用户授权。

## Phase 3：动作执行器

目标：把模型或策略输出的动作转换为真实无障碍操作。

完成内容：

| 动作 | 状态 |
| --- | --- |
| CLICK | 完成 |
| LONG_CLICK | 完成 |
| SWIPE | 完成 |
| INPUT_TEXT | 完成 |
| BACK | 完成 |
| HOME | 完成 |
| RECENTS | 完成 |
| OPEN_APP | 完成 |
| DEVICE_CONTROL | 基础完成 |
| WAIT | 完成 |
| NO_ACTION | 完成 |

当前增强：

- 坐标越界校验。
- 打开 App 优先使用系统启动 Intent，并保留桌面图标兜底。
- 文本输入优先 `ACTION_SET_TEXT`，失败后剪贴板粘贴。
- 亮度调节会检查 `WRITE_SETTINGS` 授权。
- 高风险动作交给 `ActionGuard` 拦截。

安全边界：

- 执行层只负责执行动作。
- 动作是否安全必须在 `ActionGuard` 和 App 专项策略中判断。

## Phase 4：云端模型接入

目标：通过统一接口接入云端模型，让 Agent 能基于截图和 UI 树输出结构化动作。

完成内容：

| 内容 | 状态 |
| --- | --- |
| `ICloudClient` | 完成 |
| `CloudFallbackManager` | 完成 |
| `AliyunClient` | 完成 |
| `DeepSeekClient` | 保留 |
| `CloudConfig` | 完成 |
| JSON 动作解析 | 完成 |
| API Key 本地注入 | 完成 |

当前默认 Provider：

```kotlin
CloudProvider.ALIYUN
```

当前默认模型：

```text
qwen-vl-max
```

设计约束：

- 模型必须返回单个 JSON 动作。
- 模型不应输出 Markdown。
- 模型不应输出多个动作。
- 模型不应输出屏幕范围外坐标。
- 高风险动作必须由 `ActionGuard` 拦截。

当前增强：

- 截图不可用时，阿里云客户端不再发送空白图。
- 输入坐标缺失时不再默认点击 `(0,0)`。
- Prompt 注入 RAG、规划和反思信息。
- 高风险动作由 `ActionGuard` 拦截。

## Phase 5：多轮 Agent 闭环

目标：让 Agent 不再一次性规划完整任务，而是采用逐轮反馈：

```text
观察屏幕 -> 决策一个动作 -> 执行动作 -> 等待页面变化 -> 再次观察
```

完成内容：

| 内容 | 状态 |
| --- | --- |
| `AgentExecutor` 多轮循环 | 完成 |
| 屏幕捕获验证 | 完成 |
| UI 树 prompt 注入 | 完成 |
| 打开 App 确定性首步 | 完成 |
| 重复动作检测 | 完成 |
| WAIT 循环检测 | 完成 |
| 每轮决策回调 | 完成 |

当前主循环：

```text
PlannerAgent
  -> LocalRagEngine
  -> captureScreen
  -> ReflectionAgent
  -> CloudFallbackManager / LocalModelEngine
  -> ActionGuard
  -> AppStrategyRegistry
  -> ActionExecutor
  -> AgentTraceStore
```

安全策略：

```text
微信任务默认只填草稿：
打开微信 -> 搜索联系人 -> 进入聊天 -> 输入草稿 -> NO_ACTION
```

禁止自动点击：

- 发送
- 支付
- 下单
- 删除
- 转账
- 提交

## Phase 6：日志、RAG、策略库和本地模型

目标：从“能执行”升级到“可观测、可复盘、可本地推理”。

完成内容：

| 内容 | 状态 |
| --- | --- |
| AgentTrace JSONL 日志 | 完成 |
| 最新 Trace 回放 | 完成 |
| RAG JSONL 持久化 | 完成 |
| App 专项策略注册 | 完成 |
| WeChat 草稿状态机 | 基础完成 |
| BrowserStrategy | 基础完成 |
| SystemSettingsStrategy | 基础完成 |
| Gemma 4 E2B 模型下载和部署 | 完成 |
| LiteRT-LM 0.12.0 接入 | 完成 |
| 本地模型健康检查 | 真机通过 |

真机本地模型验证结果：

```text
设备：Redmi K60
模型：Gemma 4 E2B
运行库：LiteRT-LM 0.12.0
结果来源：LOCAL_VLM
动作：NO_ACTION
置信度：0.95
推理耗时：18,983ms
```

当前后续：

1. 将本地模型健康检查写入 AgentTrace。
2. 增强模型状态 UI。
3. 继续扩展 App 专项策略库。
4. 推进 RAG 向量检索。
5. 建立真机任务评测集。
