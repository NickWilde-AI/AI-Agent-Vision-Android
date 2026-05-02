# Phase 5 完成总结 - 云端优先全无障碍 AI Agent

## 目标

本阶段目标是把项目从“能打开 App 的半成品”推进到类似豆包 AI 手机助手的执行形态：

> 云端大模型负责理解目标和规划下一步，Android 端只通过无障碍能力完成真实操作，形成“看屏幕 → 思考 → 点击/输入/滑动 → 再看屏幕”的闭环。

当前阶段先采用 **云端优先**，确保完整 Agent 工作流跑通；后续再将云端推理替换或下沉到本地 VLM。

---

## 新工作流

```text
用户输入任务
  ↓
MainViewModel.testInference()
  ↓
云端启用？
  ├─ 是：进入 AgentExecutor 多轮云端全无障碍模式
  └─ 否：保留旧的本地 Mock 单轮模式用于离线调试
  ↓
AgentExecutor.executeTask()
  ↓
第 N 轮循环：
  1. EdgeAgentAccessibilityService.captureScreenData()
  2. ScreenCaptureManager 获取截图
  3. UITreeExtractor 输出带坐标 UI 树
  4. CloudFallbackManager 调用阿里云/DeepSeek
  5. 云端返回一个 JSON 动作
  6. ActionExecutor 执行动作
  7. 等待页面变化
  8. 截图验证，进入下一轮
  ↓
云端返回 NO_ACTION 或达到最大轮数
```

---

## 本阶段代码改动

### 1. UI 树增强

文件：`app/src/main/java/com/tencent/edgeagent/data/perception/UITreeExtractor.kt`

改动内容：
- UI 树输出新增 `bounds=[left,top,right,bottom]`。
- UI 树输出新增 `center=(x,y)`。
- 输出节点状态：`clickable`、`editable`、`scrollable`、`focused`、`selected`、`checked` 等。
- 新增 `Clickable Elements` 摘要，直接列出可点击/可编辑元素及坐标。

意义：
- 云端 LLM 不再只能“猜坐标”，而是可以直接使用 UI 树里的元素中心点。
- 后续本地 VLM 或本地策略引擎也可以复用同一份结构化 UI 表示。

---

### 2. 云端 Prompt 升级

文件：
- `app/src/main/java/com/tencent/edgeagent/data/cloud/AliyunClient.kt`
- `app/src/main/java/com/tencent/edgeagent/data/cloud/DeepSeekClient.kt`

改动内容：
- System Prompt 明确角色为 Android 云端手机 Agent。
- 要求模型优先使用 `UI Tree with bounds` 和 `Clickable Elements`。
- 要求每轮只返回一个 JSON 动作。
- 明确支持 `CLICK`、`LONG_CLICK`、`SWIPE`、`INPUT_TEXT`、`OPEN_APP`、`BACK`、`HOME`、`WAIT`、`NO_ACTION`。
- 明确 `OPEN_APP` 由执行层通过无障碍路径完成，而不是依赖模型直接操作 Intent。

意义：
- 输出更稳定。
- 多步骤任务更符合“逐步执行、逐轮验证”的 Agent 模式。

---

### 3. 打开应用改为全无障碍优先

文件：`app/src/main/java/com/tencent/edgeagent/data/execution/ActionExecutor.kt`

旧逻辑：

```text
检测是否已在目标应用
  ↓
PackageManager.getLaunchIntentForPackage()
  ↓
startActivity(Intent)
  ↓
失败后才回桌面找图标
```

新逻辑：

```text
检测是否已在目标应用
  ↓
performHome()
  ↓
通过 Launcher UI 树查找 App 图标
  ↓
点击图标中心点
  ↓
如果当前页找不到，滑动桌面翻页继续查找
```

意义：
- 打开 App 也纳入无障碍闭环。
- 行为更接近真人操作路径。
- 为“全流程只走无障碍”打基础。

---

### 4. 云端启用时统一进入多轮 Agent

文件：`app/src/main/java/com/tencent/edgeagent/ui/MainViewModel.kt`

改动内容：
- 云端服务启用时，所有用户任务统一走 `AgentExecutor.executeTask()`。
- 云端关闭时，才保留旧的本地 Mock 单轮流程。
- 多轮任务完成/失败后使用 `AgentEvent.Reset` 回到 `IDLE`，避免状态机停留在 `PERCEIVING`。

意义：
- 当前阶段优先打通云端 Agent 商业化闭环。
- 单轮 Mock 不再干扰主流程。

---

### 5. 多轮 Agent 日志结构化

文件：`app/src/main/java/com/tencent/edgeagent/domain/agent/AgentExecutor.kt`

新增日志形态：

```text
[AgentTask] start goal=打开微信给张三发消息
[AgentTask] round=1 package=com.miui.home uiTree=true
[AgentTask] round=1 llm action=OPEN_APP confidence=0.95 params=...
[1/10] 执行：OPEN_APP
[1/10] 执行成功：无障碍点击图标启动: 微信
[1/10] 截图验证并提取 UI 树
```

意义：
- 可以清晰判断当前执行到第几轮。
- 可以看到当前包名、UI 树是否可用、LLM 返回的动作和置信度。
- 遇到问题时可以快速定位是感知、推理还是执行层失败。

---

## 当前能力边界

已具备：
- 云端多轮任务循环。
- 带坐标 UI 树。
- 基于无障碍的点击、滑动、返回、Home、文本输入。
- 打开应用全无障碍优先路径。
- 结构化执行日志。

仍需继续增强：
- Launcher 图标查找在不同厂商桌面上需要适配更多节点形态。
- 微信/美团等复杂 App 内流程依赖 UI 树质量和云端模型稳定性。
- 文本输入目前以 `ACTION_SET_TEXT` + 剪贴板降级为主，部分 App 输入框可能有兼容问题。
- 多轮历史中仍保存 `ScreenData`，后续需要做 Bitmap 生命周期治理。
- API Key 仍不应硬编码，应迁移到 `local.properties` 或 BuildConfig 注入。

---

## 下一步建议

1. 针对“打开微信给某人发消息”做专项调优：
   - 微信首页识别
   - 搜索入口识别
   - 联系人搜索
   - 输入框定位
   - 发送按钮点击

2. 针对“点外卖”做专项调优：
   - 打开美团
   - 搜索商家/商品
   - 列表滚动
   - 商品选择
   - 下单前用户确认

3. 加入高风险动作确认机制：
   - 支付、下单、删除、发消息前弹出确认。

4. 将云端 Prompt 和 App 特定策略配置化：
   - 微信策略
   - 美团策略
   - 支付宝策略

5. 后续替换为本地 VLM：
   - 复用当前 `ICloudClient` 的输出协议。
   - 本地模型只要能输出相同 `AgentResponse` 即可接入。

---

## 结论

Phase 5 将项目主流程从“本地 Mock + 少量云端兜底”切换为“云端优先 + 全无障碍执行闭环”。这是实现豆包 AI 手机助手效果的关键一步：先把端到端能力跑通，再逐步优化 App 内任务成功率，最后再把推理能力本地化。
