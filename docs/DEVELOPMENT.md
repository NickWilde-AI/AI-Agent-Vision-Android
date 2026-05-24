# VisionAgent Android 开发运行手册

本文档面向项目开发者和 AI 编程助手，合并原“开发指南、上手与测试、API 示例、AI 协作 Prompt”的长期有效内容。

## 工程边界

VisionAgent Android 是一个真实 Android Agent 工程。它使用无障碍服务、屏幕截图、结构化 UI 树、本地模型、云端视觉模型、RAG 和多 Agent 协作，在用户授权范围内完成可验证手机操作。

项目目标：

- 构建安全可控的手机 Agent 主链路。
- 用 RAG 和 App 策略约束模型行为。
- 将复杂任务拆成单步执行和逐轮验证。
- 在高风险动作前停止并请求用户确认。
- 支持端侧模型，并保留云端视觉模型兜底。

项目不追求：

- 让模型无约束自由点击。
- 绕过第三方 App 风控。
- 自动发送、支付、下单等不可逆动作。

## 环境要求

- Android Studio
- JDK 17
- Android SDK
- Android 7.0+ 设备或模拟器
- 推荐真机测试，因为无障碍和屏幕录制在模拟器上的行为不完全可靠

## 配置 API Key

编辑项目根目录 `local.properties`：

```properties
sdk.dir=/Users/your-name/Library/Android/sdk
ALIYUN_API_KEY=your-api-key
```

注意：

- 不要把 API Key 写入源码。
- 不要提交 `local.properties`。
- 当前默认 Provider 是 `CloudProvider.ALIYUN`。
- 当前默认云端视觉模型是 `qwen-vl-max`。

## 常用命令

构建：

```bash
./gradlew :app:assembleDebug
```

单元测试：

```bash
./gradlew :app:testDebugUnitTest
```

Lint：

```bash
./gradlew :app:lintDebug
```

完整本地验证：

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

查看设备：

```bash
adb devices -l
```

安装：

```bash
./gradlew :app:installDebug
```

或安装已构建 APK：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

启动 App：

```bash
adb shell am start -n com.tencent.edgeagent/.ui.MainActivity
```

过滤核心日志：

```bash
adb logcat | grep -E "AgentTask|PlannerAgent|ReflectionAgent|ActionGuard|ActionExecutor|ScreenCapture|LocalGemma|LocalModel"
```

开发设备权限准备：

```bash
./dev_bootstrap_permissions.sh
```

说明：

- 这个脚本只用于开发测试设备，不是产品能力。
- 脚本只负责开启无障碍、设置调试 appops、尝试完成屏幕录制授权弹窗，不通过 ADB 执行业务任务。
- 业务任务必须从 App 内由 `AgentOrchestrator`、千问云端模型、本地策略或本地模型触发。
- `MediaProjection` 是 Android 的敏感授权，普通 App 不能真正静默授权；脚本只能在测试机上自动点击系统授权弹窗。如果 ROM 拦截，仍需要一次人工确认。
- 如果测试机仍停在锁屏或通知遮罩，脚本会提示先解锁；安全锁屏不能由普通开发脚本绕过。

开发设备保持亮屏：

```bash
./keep_device_awake.sh start
./keep_device_awake.sh status
./keep_device_awake.sh stop
```

说明：

- 这个脚本只用于真机长时间测试，避免任务执行中途熄屏。
- 脚本会设置较长的 `screen_off_timeout`，开启 `svc power stayon`，并定期发送 `KEYCODE_WAKEUP`。
- macOS 上 `start` 会优先使用 `launchctl` 托管，避免普通后台进程被终端会话回收。
- 脚本不读取页面、不点击业务控件、不替 Agent 执行任务。
- 如果有多台设备，先设置 `ANDROID_SERIAL=设备序列号`。

回放最新 AgentTrace：

```bash
./view_logs.sh --replay
```

## 无线 ADB

如果手机无线调试页面已经显示“已配对的设备”，通常可以直接连接主页面上的连接端口：

```bash
adb connect 手机IP:连接端口
adb devices -l
```

如果还没有配对，使用配对码方式比二维码更稳定：

```bash
adb pair 手机IP:配对端口
adb connect 手机IP:连接端口
```

注意：配对端口和连接端口通常不是同一个。

## 本地模型部署

模型文件不进入 Git，也不打入 APK。当前本地路径：

```text
local_models/gemma-4-e2b-it/gemma-4-E2B-it.litertlm
```

手机 App 私有路径：

```text
/data/data/com.tencent.edgeagent/files/models/gemma-4-e2b-it/gemma-4-E2B-it.litertlm
```

推送模型：

```bash
./push_gemma_model.sh
```

推送逻辑：

1. 校验本地模型文件存在。
2. 通过 ADB 推送到 `/data/local/tmp`。
3. 使用 `run-as com.tencent.edgeagent` 复制到 App 私有目录。
4. 校验手机端文件大小。

## 必要权限

首次运行后需要手动开启：

1. 无障碍服务
   - 设置 -> 无障碍 -> VisionAgent -> 开启

2. 屏幕录制
   - App 内点击屏幕录制授权

3. 修改系统设置
   - 亮度调节场景需要
   - App 会跳转到授权页

## 推荐验证顺序

先验证低风险能力：

1. App 能启动。
2. 本地模型检查成功。
3. 无障碍服务能开启。
4. 屏幕录制能授权。
5. 点击中心。
6. 向上滑动。
7. 返回。
8. Home。
9. 打开设置。
10. 浏览器搜索。

再验证受控 App 能力：

1. 打开微信。
2. 搜索联系人。
3. 进入聊天页。
4. 输入草稿。
5. 确认不会自动点击发送。

不要用自动发送消息作为第一条验收任务。

## 当前关键模块

| 模块 | 路径 | 职责 |
| --- | --- | --- |
| UI | `app/src/main/java/com/tencent/edgeagent/ui` | 命令输入、权限状态、执行进度展示 |
| 编排 | `domain/agent/AgentOrchestrator.kt` | 接收 UI 命令，选择本地或多轮 Agent 流程 |
| L1 兜底 | `domain/agent/L1CommandRouter.kt` | 模型失败时的低风险系统任务确定性兜底 |
| 多轮执行 | `domain/agent/AgentExecutor.kt` | 屏幕观察、模型决策、动作执行、反馈循环 |
| Planner | `domain/agent/multi/PlannerAgent.kt` | 任务分类、目标包名、安全模式、RAG 检索 |
| Reflection | `domain/agent/multi/ReflectionAgent.kt` | 分析失败、重复动作、不可观测状态 |
| Safety | `domain/agent/safety/ActionGuard.kt` | 拦截高风险最终动作 |
| App 策略 | `domain/agent/strategy` | 微信、浏览器、系统设置等专项策略 |
| RAG | `data/rag/LocalRagEngine.kt` | 本地策略检索和 JSONL 持久化 |
| Trace | `data/trace/AgentTraceStore.kt` | Agent 失败日志和回放 |
| 本地模型 | `data/inference` | Gemma / LiteRT-LM 加载、推理、解析 |
| 感知 | `data/perception` | 截图、结构化 UI 树 |
| 执行 | `data/execution` | 点击、滑动、输入、打开 App、系统控制 |
| 云端 | `data/cloud` | 阿里云百炼/千问、DeepSeek 等模型客户端 |
| 服务 | `service` | AccessibilityService 和 MediaProjection 服务 |

## 开发原则

1. 单轮只执行一个最小动作。
2. 执行前必须经过安全检查。
3. Prompt 必须包含任务边界和安全约束。
4. UI 树和截图都不可靠时，禁止猜测页面内容。
5. App 专项流程必须状态机化。
6. 高风险动作必须等待用户确认。
7. 新增策略必须有测试或可验证日志。
8. 本地模型失败必须安全降级，不允许乱点屏幕。

## API 与模块示例

### 初始化云端模型

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

```text
qwen-vl-max
```

### 调用云端推理

```kotlin
val response = cloudFallbackManager.inference(
    image = screenData.bitmap,
    prompt = prompt,
    uiTree = screenData.uiTreeText
)
```

### Agent 单步动作格式

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

### 本地 RAG 检索

```kotlin
val ragContext = LocalRagEngine.getInstance().buildContext(
    query = "打开微信给 Nick 发消息",
    currentPackage = "com.tencent.mm"
)
```

### PlannerAgent

```kotlin
val plan = PlannerAgent.getInstance().plan(
    goal = "打开微信给 Nick 发消息",
    currentPackage = "com.tencent.edgeagent"
)
```

### ReflectionAgent

```kotlin
val reflection = ReflectionAgent.getInstance().reflect(
    history = conversationHistory,
    currentScreenData = screenData
)
```

### ActionGuard

```kotlin
val guarded = ActionGuard.getInstance().guard(
    plan = plan,
    response = response,
    currentPackage = screenData.currentPackage,
    uiTreeText = screenData.uiTreeText
)
```

微信发送按钮会被拦截并转换为 `NO_ACTION`。

### 本地 Gemma 推理

```kotlin
val response = LocalModelEngineProvider.getInstance().inference(
    image = bitmap,
    prompt = "Local model health check",
    uiTree = "UI Tree: health_check"
)
```

模型输出必须被 `AgentResponseJsonParser` 解析为结构化 `AgentResponse`。

## 测试要求

新增以下内容必须补测试：

- RAG 策略匹配。
- 安全拦截规则。
- 任务分类规则。
- 文本解析或模型输出解析。
- 坐标校验逻辑。
- App 专项状态机。

当前已有测试覆盖：

- RAG 命中微信草稿策略。
- RAG 命中高风险确认策略。
- ActionGuard 拦截微信发送点击。
- 本地模型 JSON 输出解析。
- AgentTrace 回放。
- UI 树摘要。
- App 策略库。

## 常见问题

### 无障碍服务不可用

检查：

- 是否在系统设置中开启服务。
- App 是否被系统后台限制。
- 当前设备 ROM 是否限制无障碍服务。

### 截图为空

检查：

- 是否已授权屏幕录制。
- `ScreenCaptureService` 是否在运行。
- 是否刚刚切换页面，等待一轮后再观察。

### 千问接口失败

检查：

- `local.properties` 是否有 `ALIYUN_API_KEY`。
- API Key 是否有效。
- 设备网络是否可用。
- 阿里云百炼账号是否已开通对应模型。

### 本地模型加载慢

这是正常现象。Gemma 4 E2B 模型约 2.4GB，首次 LiteRT-LM 加载可能需要较长时间。当前 App 已将完整加载延迟到首次本地推理，避免启动卡死。

### 出现 `No dispatch library found`

当前 Redmi K60 日志中出现过：

```text
No dispatch library found
Failed to initialize Dispatch API
```

该日志不是致命错误。后续 LiteRT-LM 已成功加载模型并完成推理，当前判断为 NPU/Dispatch 加速库缺失后回退到 CPU/GPU 路径。

### 微信任务失败

这是高难场景。当前策略目标是填草稿，不自动发送。

优先排查：

- 是否进入微信。
- UI 树是否能看到搜索框或输入框。
- 是否命中微信草稿 RAG 策略。
- 是否被 `ActionGuard` 拦截发送按钮。

## 验收记录模板

每次真机验证建议记录：

```text
设备：
Android 版本：
ROM：
App 版本：
任务：
是否真实截图：
UI 树是否可用：
模型返回动作：
执行结果：
失败原因：
```

失败样本后续应进入 `AgentTrace` 和 RAG 记忆。

## Git 工作流

提交前：

```bash
git status -sb
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

提交信息使用简洁中文，例如：

```bash
git commit -m "完善多 Agent 安全策略"
```

推送：

```bash
git push origin main
```

## 给 AI 编程助手的上下文

```text
你正在开发 VisionAgent Android。

项目目标：
构建一个真实 Android Agent。它通过无障碍服务、屏幕截图、结构化 UI 树、本地模型、云端视觉模型、本地 RAG 和多 Agent 协作，在用户授权范围内完成可验证手机操作。

当前原则：
1. 每轮只执行一个最小动作。
2. 产品任务必须由 App 内 Agent / 模型优先决策，ADB 只做开发脚手架。
3. L1 低风险确定性能力只作为模型失败或不可观测状态下的兜底。
4. 复杂 App 任务必须使用策略约束。
5. 禁止自动执行发送、支付、下单、删除、转账、提交等高风险最终动作。
6. 微信任务只允许填草稿，不允许自动点击发送。
7. 截图和 UI 树都不可用时，不能猜测页面内容。
8. 新增策略必须可测试、可解释、可回放。
9. 本地模型失败必须安全降级为 NO_ACTION。

核心模块：
- AgentOrchestrator：UI 到 Agent 的统一入口。
- L1CommandRouter：调音量、Home、打开相机、Wi-Fi 设置等低风险兜底任务。
- AgentExecutor：多轮观察、决策、执行循环。
- PlannerAgent：任务分类、目标包名、安全模式、RAG 检索。
- ReflectionAgent：失败、重复、等待、不可观测状态分析。
- ActionGuard：高风险动作拦截。
- LocalRagEngine：本地策略检索和持久化。
- AgentTraceStore：失败日志和回放。
- GemmaLiteRtModelEngine：Gemma 4 E2B 本地推理。
- UITreeExtractor：文本 UI 树和结构化 UiNode。
- ScreenCaptureService：MediaProjection 截图帧缓存。
- ActionExecutor：无障碍动作执行。
- CloudFallbackManager：云端视觉模型调用。

当前默认云端模型：
阿里云百炼 qwen-vl-max。

当前本地模型：
Gemma 4 E2B + LiteRT-LM 0.12.0。

开发要求：
- 不要把业务流程写进 Activity。
- 不要绕过 ActionGuard。
- 不要提交 local.properties 或 API Key。
- 每次修改后运行完整本地验证。
```
