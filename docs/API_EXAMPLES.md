# API 与模块示例

本文档给出当前核心模块的最小使用示例。示例用于理解代码结构，不代表最终产品 API。

## 初始化云端模型

当前入口在 `MainViewModel.initializeCloudProvider()`：

```kotlin
cloudFallbackManager.initialize(
    apiKey = CloudConfig.getApiKey(),
    provider = CloudConfig.PROVIDER
)
```

默认配置：

```kotlin
val PROVIDER = CloudProvider.ALIYUN
```

阿里云客户端默认模型：

```kotlin
qwen-vl-max
```

## 调用云端推理

```kotlin
val response = cloudFallbackManager.inference(
    image = screenData.bitmap,
    prompt = prompt,
    uiTree = screenData.uiTreeText
)
```

返回值：

```kotlin
data class AgentResponse(
    val source: InferenceSource,
    val action: ActionType,
    val actionParams: ActionParams,
    val confidence: Float,
    val inferenceTimeMs: Long,
    val rawOutput: String? = null,
    val requiresConfirmation: Boolean = false
)
```

## Agent 单步动作格式

点击：

```json
{
  "action": "CLICK",
  "params": {
    "x": 540,
    "y": 1200,
    "description": "点击搜索框"
  },
  "confidence": 0.95,
  "reasoning": "根据 UI 树 center=(540,1200)"
}
```

输入：

```json
{
  "action": "INPUT_TEXT",
  "params": {
    "text": "你好",
    "targetX": 540,
    "targetY": 2100
  },
  "confidence": 0.9
}
```

停止：

```json
{
  "action": "NO_ACTION",
  "params": {
    "message": "草稿已填好，等待用户确认发送"
  },
  "confidence": 1.0
}
```

## 本地 RAG 检索

```kotlin
val ragContext = LocalRagEngine.getInstance().buildContext(
    query = "打开微信给 Nick 发消息",
    currentPackage = "com.tencent.mm"
)
```

示例命中：

```text
本地 RAG 策略命中：
1. [wechat] 微信策略：只填草稿，不自动发送
   微信任务应分解为打开微信、搜索联系人、进入聊天页、聚焦输入框、输入草稿。不要点击发送按钮；草稿填好后返回 NO_ACTION。
```

## PlannerAgent

```kotlin
val plan = PlannerAgent.getInstance().plan(
    goal = "打开微信给 Nick 发消息",
    currentPackage = "com.tencent.edgeagent"
)
```

输出：

```kotlin
AgentPlan(
    taskType = TaskType.WECHAT_DRAFT,
    targetPackage = "com.tencent.mm",
    safetyMode = SafetyMode.DRAFT_ONLY,
    localKnowledge = "...",
    constraints = listOf(...)
)
```

## ReflectionAgent

```kotlin
val reflection = ReflectionAgent.getInstance().reflect(
    history = conversationHistory,
    currentScreenData = screenData
)
```

用途：

- 连续失败时提示换路径。
- 连续 WAIT 时提示返回或恢复可观测状态。
- 当前截图和 UI 树都不可用时禁止猜测。

## ActionGuard

```kotlin
val guardResult = ActionGuard.getInstance().guard(
    plan = plan,
    response = response,
    currentPackage = screenData.currentPackage,
    uiTreeText = screenData.uiTreeText
)
```

微信发送按钮会被拦截：

```kotlin
ActionParams.Click(
    x = 900,
    y = 2100,
    description = "点击发送"
)
```

拦截后转换为：

```kotlin
AgentResponse(
    action = ActionType.NO_ACTION,
    actionParams = ActionParams.NoAction("已阻止高风险最终动作，等待用户手动确认")
)
```

## 捕获屏幕

```kotlin
val service = EdgeAgentAccessibilityService.getInstance()
val screenData = service?.captureScreenData()
```

`ScreenData` 包含：

```kotlin
val bitmap: Bitmap
val uiTreeText: String?
val screenWidth: Int
val screenHeight: Int
val currentPackage: String?
val hasRealScreenshot: Boolean
```

## 动作执行

```kotlin
val result = ActionExecutor.getInstance().execute(response)
```

返回：

```kotlin
sealed class ExecutionResult {
    data class Success(val message: String) : ExecutionResult()
    data class Failure(val message: String) : ExecutionResult()
}
```

## 多轮执行

```kotlin
val result = AgentExecutor.getInstance().executeTask(
    userGoal = "打开微信给 Nick 发消息",
    onProgress = { progress -> println(progress) },
    onDecision = { response -> println(response.action) }
)
```

实际流程：

```text
PlannerAgent
  -> LocalRagEngine
  -> captureScreen
  -> ReflectionAgent
  -> CloudFallbackManager
  -> ActionGuard
  -> ActionExecutor
  -> captureScreen
```

## 安全约束

任何新增动作类型都应检查：

- 是否可逆。
- 是否需要用户确认。
- 是否可能触发支付、发送、删除、提交。
- 是否能从 UI 树坐标验证。

高风险动作不得绕过 `ActionGuard`。
