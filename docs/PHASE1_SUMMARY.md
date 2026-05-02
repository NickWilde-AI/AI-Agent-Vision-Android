# Phase 1 完成总结

## ✅ 已完成内容

### 1. 项目初始化与依赖配置

**Gradle 配置**：
- Kotlin 2.0.21
- AGP 8.9.0
- Java 17
- minSdk 24, targetSdk 35

**核心依赖**：
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

**架构选择**：
- ❌ 放弃 Hilt（版本兼容问题）
- ❌ 放弃 Compose（简化项目，降低复杂度）
- ✅ 使用单例模式 + 传统 XML 布局
- ✅ Java 17（AGP 8.9 要求）

### 2. Clean Architecture 三层架构

```
EdgeAgentAndroid/
├── domain/               # Domain Layer (业务逻辑核心)
│   ├── agent/
│   │   ├── AgentStateMachine.kt      # 状态机
│   │   └── IntentRouter.kt           # 意图路由
│   └── model/
│       ├── AgentState.kt             # 状态枚举
│       ├── AgentIntent.kt            # 意图模型
│       └── AgentResponse.kt          # 响应模型
│
├── data/                 # Data Layer (数据/推理层)
│   ├── inference/
│   │   ├── ILocalModelEngine.kt      # 模型引擎接口
│   │   └── MockModelEngine.kt        # Mock 实现
│   ├── perception/
│   │   ├── ScreenCaptureManager.kt   # 截图管理
│   │   └── UITreeExtractor.kt        # UI 树提取
│   └── execution/
│       └── GestureExecutor.kt        # 手势执行
│
├── ui/                   # Presentation Layer (UI)
│   ├── MainActivity.kt               # 主界面
│   └── MainViewModel.kt              # ViewModel
│
└── service/              # Service Layer
    └── EdgeAgentAccessibilityService.kt  # 无障碍服务
```

### 3. 状态机设计 (AgentStateMachine)

**7 个状态**：
```kotlin
enum class AgentState {
    IDLE,                    // 空闲
    PERCEIVING,              // 感知中
    REASONING_LOCAL,         // 本地推理
    REASONING_CLOUD,         // 云端推理
    EXECUTING,               // 执行中
    ERROR,                   // 错误
    COMPLETED                // 完成
}
```

**状态转换规则**：
```
IDLE → PERCEIVING → REASONING_LOCAL → EXECUTING → COMPLETED → IDLE
                         ↓ (置信度 < 0.75)
                    REASONING_CLOUD → EXECUTING → COMPLETED → IDLE
```

**特性**：
- 状态转换合法性校验
- StateFlow 响应式状态管理
- 自动从 COMPLETED 回到 IDLE
- 完整的日志记录

**代码亮点**：
```kotlin
private val validTransitions = mapOf(
    AgentState.IDLE to setOf(AgentState.PERCEIVING, AgentState.ERROR),
    AgentState.PERCEIVING to setOf(AgentState.REASONING_LOCAL, AgentState.ERROR, AgentState.IDLE),
    // ... 完整的状态转换映射
)

fun handleEvent(event: AgentEvent) {
    val newState = when (event) {
        is AgentEvent.UserTriggered -> AgentState.PERCEIVING
        is AgentEvent.PerceptionComplete -> AgentState.REASONING_LOCAL
        is AgentEvent.LocalReasoningComplete -> {
            if (event.response.confidence >= 0.75f) {
                AgentState.EXECUTING
            } else {
                AgentState.REASONING_CLOUD  // 云端兜底
            }
        }
        // ...
    }
    transitionTo(newState)
}
```

### 4. 意图路由系统 (IntentRouter)

**6 种意图类型**：
```kotlin
enum class IntentType {
    DEVICE_CONTROL,      // 设备控制（音量、亮度）- 100% 本地
    APP_OPERATION,       // 应用操作（打开、点击）- 优先本地
    INFORMATION_QUERY,   // 信息查询 - 优先本地 RAG
    TEXT_INPUT,          // 文本输入 - 本地，不上云
    COMPLEX_REASONING,   // 复杂推理 - 直接云端
    UNKNOWN              // 未知
}
```

**路由决策逻辑**：
```kotlin
fun parseIntent(userInput: String): AgentIntent {
    // 关键词匹配
    val intentType = when {
        deviceControlKeywords.any { input.contains(it) } -> DEVICE_CONTROL
        appOperationKeywords.any { input.contains(it) } -> APP_OPERATION
        // ...
    }
    
    // 决定是否允许云端兜底
    val allowCloudFallback = when (intentType) {
        DEVICE_CONTROL -> false  // 绝不上云
        TEXT_INPUT -> false      // 隐私保护
        APP_OPERATION -> true    // 允许兜底
        // ...
    }
}

fun shouldUseCloud(intent: AgentIntent, localConfidence: Float): Boolean {
    if (!intent.allowCloudFallback) return false
    if (intent.type == COMPLEX_REASONING) return true
    if (localConfidence < 0.75f) return true  // 置信度不足
    return false
}
```

**特性**：
- 关键词匹配识别意图
- 提取意图参数（App 名称、控制类型等）
- 隐私优先：设备控制和文本输入不上云
- 智能兜底：低置信度自动转云端

### 5. 数据模型设计

**AgentResponse** (推理响应)：
```kotlin
data class AgentResponse(
    val source: InferenceSource,        // LOCAL_VLM / CLOUD_FALLBACK / MOCK
    val action: ActionType,             // CLICK / SWIPE / INPUT_TEXT / ...
    val actionParams: ActionParams,     // 动作参数（坐标、文本等）
    val confidence: Float,              // 置信度 0.0-1.0
    val inferenceTimeMs: Long,          // 推理耗时
    val rawOutput: String?,             // 原始输出
    val requiresConfirmation: Boolean   // 是否需要用户确认
)
```

**ActionType** (8 种动作)：
```kotlin
enum class ActionType {
    CLICK,          // 点击
    LONG_CLICK,     // 长按
    SWIPE,          // 滑动
    INPUT_TEXT,     // 输入文本
    BACK,           // 返回
    HOME,           // 主屏幕
    OPEN_APP,       // 打开应用
    DEVICE_CONTROL, // 设备控制
    WAIT,           // 等待
    NO_ACTION       // 无操作
}
```

**ActionParams** (密封类)：
```kotlin
sealed class ActionParams {
    data class Click(val x: Int, val y: Int, val description: String)
    data class Swipe(val startX: Int, val startY: Int, val endX: Int, val endY: Int, val durationMs: Long)
    data class InputText(val text: String, val targetX: Int?, val targetY: Int?)
    // ...
}
```

### 6. Mock 模型引擎 (MockModelEngine)

**功能**：
- 模拟 1.2-1.8 秒推理耗时（接近真实 VLM）
- 根据 prompt 关键词返回不同动作
- 随机生成置信度（0.6-0.95）
- 30% 概率触发低置信度（测试云端兜底）

**代码示例**：
```kotlin
override suspend fun inference(
    image: Bitmap,
    prompt: String,
    uiTree: String?
): AgentResponse {
    // 模拟推理延迟
    delay(Random.nextLong(1200, 1800))
    
    // 根据关键词生成响应
    val (actionType, actionParams) = when {
        prompt.contains("点击") -> ActionType.CLICK to ActionParams.Click(centerX, centerY)
        prompt.contains("滑动") -> ActionType.SWIPE to ActionParams.Swipe(...)
        // ...
    }
    
    // 随机置信度（30% 概率 < 0.75）
    val confidence = if (Random.nextFloat() < 0.3f) {
        Random.nextFloat() * 0.15f + 0.60f  // 0.60-0.75
    } else {
        Random.nextFloat() * 0.20f + 0.75f  // 0.75-0.95
    }
    
    return AgentResponse(...)
}
```

### 7. UI 层实现

**MainActivity** (传统 XML 布局)：
- 显示模型信息
- 显示 Agent 状态（实时更新）
- 显示最后推理响应
- 3 个测试按钮

**MainViewModel**：
- 管理状态机和推理引擎
- 协调数据流
- 使用 StateFlow 响应式更新 UI

**数据流**：
```
用户点击按钮
  ↓
MainViewModel.testInference()
  ↓
1. stateMachine.handleEvent(UserTriggered)
2. intentRouter.parseIntent()
3. 创建 Mock ScreenData
4. stateMachine.handleEvent(PerceptionComplete)
5. localModelEngine.inference()
6. stateMachine.handleEvent(LocalReasoningComplete)
7. 判断是否需要云端兜底
8. stateMachine.handleEvent(ExecutionComplete)
  ↓
UI 更新（StateFlow）
```

## 📊 架构亮点

### 1. 单例模式替代依赖注入
**原因**：Hilt 与 Kotlin 2.0 + Java 17 有兼容性问题

**实现**：
```kotlin
class AgentStateMachine private constructor() {
    companion object {
        @Volatile
        private var instance: AgentStateMachine? = null
        
        fun getInstance(): AgentStateMachine {
            return instance ?: synchronized(this) {
                instance ?: AgentStateMachine().also { instance = it }
            }
        }
    }
}
```

**优点**：
- 简单可靠，无版本兼容问题
- 线程安全（双重检查锁）
- 易于测试和替换

### 2. 状态机驱动
**优点**：
- 状态转换清晰可控
- 易于调试和追踪
- 防止非法状态转换

### 3. 端侧优先设计
**策略**：
- 设备控制：100% 本地
- 文本输入：本地，隐私保护
- 应用操作：优先本地，低置信度云端兜底
- 复杂推理：直接云端

### 4. Mock 先行
**价值**：
- 快速验证数据流
- 不依赖真实模型
- 模拟真实场景（延迟、置信度）

## 🎯 面试亮点

### Q1: 为什么不用 Hilt？
**A**: Hilt 2.48-2.52 与 Kotlin 2.0 + Java 17 存在 JavaPoet 版本冲突。为了项目稳定性，我选择了单例模式，使用双重检查锁保证线程安全。这是工程化思维：在遇到工具链问题时，选择更稳定的方案。

### Q2: 状态机的设计思路？
**A**: Agent 的工作流是典型的状态机模型。我定义了 7 个状态和明确的转换规则，使用 Map 存储合法转换关系。每次状态转换都会校验合法性，避免状态混乱。比如只有在 REASONING_LOCAL 状态下，才能根据置信度决定是执行还是云端兜底。

### Q3: 如何保证隐私合规？
**A**: 我在 IntentRouter 中为每种意图设置了 `allowCloudFallback` 标志。设备控制和文本输入的意图，这个标志是 false，意味着数据绝不上云。这是在架构层面做的隐私保护，而不是事后补救。

### Q4: Mock 实现的价值？
**A**: Mock 实现让我能在真实模型集成前，快速验证整个 Android 端的数据流。它模拟了真实推理的耗时（1.5 秒）和置信度分布，甚至有 30% 概率触发低置信度来测试云端兜底逻辑。这是工程化思维，而不是等模型好了再写代码。

## 🔧 技术难点与解决方案

### 难点 1: Hilt 版本冲突
**问题**：Hilt 2.48-2.52 与 Kotlin 2.0.21 + Java 17 存在 JavaPoet 兼容性问题

**解决**：
1. 尝试降级 Hilt → 失败
2. 尝试使用 KAPT → 插件冲突
3. 最终方案：移除 Hilt，使用单例模式

### 难点 2: Compose 复杂度
**问题**：Compose 需要额外的插件和配置，增加项目复杂度

**解决**：
- 使用传统 XML 布局
- ViewBinding 简化 View 访问
- 降低学习成本和维护成本

### 难点 3: Java 版本选择
**问题**：AGP 8.9 要求 Java 17，但 Kotlin 2.0 在 Java 25 上有问题

**解决**：
- 在 `gradle.properties` 中指定 Java 17 路径
- 统一 JVM Target 为 17

## 📝 项目配置总结

**关键配置**：
```kotlin
// build.gradle.kts
android {
    compileSdk = 35
    defaultConfig {
        minSdk = 24
        targetSdk = 35
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}
```

**依赖策略**：
- 只引入必需依赖
- 避免复杂的依赖注入框架
- 使用稳定版本

---

**Phase 1 完成！架构清晰，代码简洁，可维护性强。**
