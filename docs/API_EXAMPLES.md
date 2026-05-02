# 🌐 云端 API 使用示例

本文档展示如何使用 EdgeAgent 的云端 API 功能。

---

## 📝 基础使用

### 1. 初始化云端客户端

```kotlin
// 在 Application 或 ViewModel 中初始化
val cloudManager = CloudFallbackManager.getInstance()

cloudManager.initialize(
    apiKey = "sk-your-deepseek-api-key",
    provider = CloudProvider.DEEPSEEK
)
```

### 2. 调用云端推理

```kotlin
// 准备数据
val bitmap = captureScreen()  // 屏幕截图
val prompt = "打开微信"        // 用户输入
val uiTree = extractUITree()   // UI 树（可选）

// 调用推理
try {
    val response = cloudManager.inference(
        image = bitmap,
        prompt = prompt,
        uiTree = uiTree
    )
    
    // 处理响应
    when (response.action) {
        ActionType.CLICK -> {
            val params = response.actionParams as ActionParams.Click
            performClick(params.x, params.y)
        }
        ActionType.SWIPE -> {
            val params = response.actionParams as ActionParams.Swipe
            performSwipe(params.startX, params.startY, params.endX, params.endY)
        }
        // ... 其他操作
    }
    
} catch (e: CloudApiException) {
    // 错误处理
    when (e) {
        is CloudApiException.NetworkError -> {
            Log.e(TAG, "网络错误: ${e.message}")
        }
        is CloudApiException.InvalidApiKey -> {
            Log.e(TAG, "API Key 无效")
        }
        is CloudApiException.Timeout -> {
            Log.e(TAG, "请求超时")
        }
        else -> {
            Log.e(TAG, "其他错误: ${e.message}")
        }
    }
}
```

---

## 🎯 实际场景示例

### 场景 1：打开应用

```kotlin
suspend fun openApp(appName: String) {
    val bitmap = captureScreen()
    val prompt = "打开 $appName"
    
    val response = cloudManager.inference(bitmap, prompt)
    
    // DeepSeek 可能返回：
    // {
    //   "action": "OPEN_APP",
    //   "params": {
    //     "packageName": "com.tencent.mm"
    //   },
    //   "confidence": 0.95
    // }
    
    if (response.action == ActionType.OPEN_APP) {
        val params = response.actionParams as ActionParams.OpenApp
        launchApp(params.packageName)
    }
}
```

### 场景 2：点击屏幕元素

```kotlin
suspend fun clickElement(description: String) {
    val bitmap = captureScreen()
    val uiTree = extractUITree()
    val prompt = "点击 $description"
    
    val response = cloudManager.inference(bitmap, prompt, uiTree)
    
    // DeepSeek 分析屏幕后返回：
    // {
    //   "action": "CLICK",
    //   "params": {
    //     "x": 540,
    //     "y": 1200,
    //     "description": "搜索框"
    //   },
    //   "confidence": 0.92,
    //   "reasoning": "屏幕中心有一个搜索框，坐标为 (540, 1200)"
    // }
    
    if (response.action == ActionType.CLICK) {
        val params = response.actionParams as ActionParams.Click
        performClick(params.x, params.y)
        Log.d(TAG, "点击了: ${params.description}")
    }
}
```

### 场景 3：滑动操作

```kotlin
suspend fun scrollPage(direction: String) {
    val bitmap = captureScreen()
    val prompt = "向${direction}滑动"
    
    val response = cloudManager.inference(bitmap, prompt)
    
    // DeepSeek 返回：
    // {
    //   "action": "SWIPE",
    //   "params": {
    //     "startX": 540,
    //     "startY": 1800,
    //     "endX": 540,
    //     "endY": 600,
    //     "durationMs": 300
    //   },
    //   "confidence": 0.98
    // }
    
    if (response.action == ActionType.SWIPE) {
        val params = response.actionParams as ActionParams.Swipe
        performSwipe(
            params.startX, params.startY,
            params.endX, params.endY,
            params.durationMs
        )
    }
}
```

### 场景 4：复杂任务（多步骤）

```kotlin
suspend fun sendWeChatMessage(contact: String, message: String) {
    // 步骤 1: 打开微信
    var bitmap = captureScreen()
    var response = cloudManager.inference(bitmap, "打开微信")
    executeAction(response)
    delay(2000)  // 等待应用启动
    
    // 步骤 2: 搜索联系人
    bitmap = captureScreen()
    response = cloudManager.inference(bitmap, "点击搜索框")
    executeAction(response)
    delay(500)
    
    // 步骤 3: 输入联系人名称
    response = cloudManager.inference(bitmap, "输入 $contact")
    executeAction(response)
    delay(1000)
    
    // 步骤 4: 点击联系人
    bitmap = captureScreen()
    response = cloudManager.inference(bitmap, "点击第一个搜索结果")
    executeAction(response)
    delay(1000)
    
    // 步骤 5: 输入消息
    bitmap = captureScreen()
    response = cloudManager.inference(bitmap, "输入消息: $message")
    executeAction(response)
    delay(500)
    
    // 步骤 6: 发送
    bitmap = captureScreen()
    response = cloudManager.inference(bitmap, "点击发送按钮")
    executeAction(response)
}
```

---

## 🔄 端云协同示例

### 智能路由决策

```kotlin
suspend fun smartInference(prompt: String): AgentResponse {
    // 1. 先尝试本地推理
    val localResponse = localModelEngine.inference(
        image = captureScreen(),
        prompt = prompt
    )
    
    // 2. 判断是否需要云端
    val intent = intentRouter.parseIntent(prompt)
    val needCloud = intentRouter.shouldUseCloud(intent, localResponse.confidence)
    
    if (needCloud) {
        Log.d(TAG, "本地置信度不足，调用云端")
        
        try {
            // 3. 调用云端
            val cloudResponse = cloudManager.inference(
                image = captureScreen(),
                prompt = prompt
            )
            
            Log.d(TAG, "云端推理成功，置信度: ${cloudResponse.confidence}")
            return cloudResponse
            
        } catch (e: CloudApiException) {
            Log.e(TAG, "云端失败，使用本地结果")
            return localResponse
        }
    } else {
        Log.d(TAG, "本地置信度足够，直接使用")
        return localResponse
    }
}
```

### 自动降级策略

```kotlin
class RobustInferenceEngine {
    
    suspend fun inference(
        image: Bitmap,
        prompt: String,
        maxRetries: Int = 2
    ): AgentResponse {
        
        // 策略 1: 本地推理
        val localResponse = tryLocalInference(image, prompt)
        if (localResponse.confidence >= 0.75f) {
            return localResponse
        }
        
        // 策略 2: 云端推理（带重试）
        repeat(maxRetries) { attempt ->
            try {
                val cloudResponse = cloudManager.inference(image, prompt)
                if (cloudResponse.confidence >= 0.75f) {
                    return cloudResponse
                }
            } catch (e: CloudApiException) {
                Log.w(TAG, "云端尝试 ${attempt + 1} 失败: ${e.message}")
                if (attempt == maxRetries - 1) {
                    // 最后一次重试也失败，使用本地结果
                    return localResponse
                }
                delay(1000)  // 等待后重试
            }
        }
        
        // 策略 3: 降级到本地
        return localResponse
    }
}
```

---

## 🎨 自定义提示词

### 基础提示词模板

```kotlin
fun buildPrompt(
    userIntent: String,
    screenContext: String? = null,
    history: List<String>? = null
): String {
    val builder = StringBuilder()
    
    // 用户意图
    builder.append("用户请求：$userIntent\n\n")
    
    // 屏幕上下文
    if (screenContext != null) {
        builder.append("当前屏幕：$screenContext\n\n")
    }
    
    // 历史操作
    if (history != null && history.isNotEmpty()) {
        builder.append("最近操作：\n")
        history.takeLast(3).forEach {
            builder.append("- $it\n")
        }
        builder.append("\n")
    }
    
    // 指令
    builder.append("请分析屏幕并返回操作指令（JSON 格式）")
    
    return builder.toString()
}

// 使用
val prompt = buildPrompt(
    userIntent = "打开微信",
    screenContext = "当前在主屏幕",
    history = listOf("打开了设置", "返回主屏幕")
)
```

### 高级提示词（带约束）

```kotlin
fun buildConstrainedPrompt(
    userIntent: String,
    allowedActions: List<ActionType>,
    screenBounds: Rect
): String {
    return """
用户请求：$userIntent

允许的操作类型：${allowedActions.joinToString(", ")}

屏幕范围：
- 宽度：${screenBounds.width()}
- 高度：${screenBounds.height()}

约束条件：
1. 坐标必须在屏幕范围内
2. 只能使用允许的操作类型
3. 置信度必须 >= 0.7

请返回 JSON 格式的操作指令。
    """.trimIndent()
}
```

---

## 📊 性能监控

### 记录推理耗时

```kotlin
class PerformanceMonitor {
    
    suspend fun monitoredInference(
        image: Bitmap,
        prompt: String
    ): Pair<AgentResponse, PerformanceMetrics> {
        
        val startTime = System.currentTimeMillis()
        
        // 图片压缩耗时
        val compressStart = System.currentTimeMillis()
        val compressedImage = compressImage(image)
        val compressTime = System.currentTimeMillis() - compressStart
        
        // 网络请求耗时
        val networkStart = System.currentTimeMillis()
        val response = cloudManager.inference(compressedImage, prompt)
        val networkTime = System.currentTimeMillis() - networkStart
        
        val totalTime = System.currentTimeMillis() - startTime
        
        val metrics = PerformanceMetrics(
            totalTimeMs = totalTime,
            compressTimeMs = compressTime,
            networkTimeMs = networkTime,
            imageSize = compressedImage.byteCount,
            confidence = response.confidence
        )
        
        Log.d(TAG, "性能指标: $metrics")
        
        return response to metrics
    }
}

data class PerformanceMetrics(
    val totalTimeMs: Long,
    val compressTimeMs: Long,
    val networkTimeMs: Long,
    val imageSize: Int,
    val confidence: Float
)
```

---

## 🔐 安全最佳实践

### 1. API Key 管理

```kotlin
// ❌ 不要硬编码
const val API_KEY = "sk-1234567890"

// ✅ 使用环境变量或本地配置
object SecureConfig {
    fun getApiKey(): String {
        // 从本地加密存储读取
        return EncryptedSharedPreferences.getString("api_key", "")
    }
}
```

### 2. 敏感数据过滤

```kotlin
fun sanitizeUITree(uiTree: String): String {
    return uiTree
        .replace(Regex("\\d{11}"), "[手机号]")  // 手机号
        .replace(Regex("\\d{15,18}"), "[身份证]")  // 身份证
        .replace(Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"), "[邮箱]")  // 邮箱
}

// 使用
val sanitizedTree = sanitizeUITree(rawUITree)
val response = cloudManager.inference(bitmap, prompt, sanitizedTree)
```

### 3. 用户确认机制

```kotlin
suspend fun safeExecute(response: AgentResponse) {
    if (response.requiresConfirmation) {
        // 显示确认对话框
        val confirmed = showConfirmDialog(
            "即将执行：${response.action}",
            "是否继续？"
        )
        
        if (confirmed) {
            actionExecutor.execute(response)
        }
    } else {
        actionExecutor.execute(response)
    }
}
```

---

## 🐛 调试技巧

### 1. 打印完整请求和响应

```kotlin
class DebugCloudClient(private val delegate: ICloudClient) : ICloudClient {
    
    override suspend fun inference(
        image: Bitmap,
        prompt: String,
        uiTree: String?
    ): AgentResponse {
        Log.d(TAG, "=== 云端推理开始 ===")
        Log.d(TAG, "Prompt: $prompt")
        Log.d(TAG, "图片尺寸: ${image.width}x${image.height}")
        Log.d(TAG, "UI 树长度: ${uiTree?.length ?: 0}")
        
        val startTime = System.currentTimeMillis()
        val response = delegate.inference(image, prompt, uiTree)
        val duration = System.currentTimeMillis() - startTime
        
        Log.d(TAG, "响应: ${response.rawOutput}")
        Log.d(TAG, "动作: ${response.action}")
        Log.d(TAG, "置信度: ${response.confidence}")
        Log.d(TAG, "耗时: ${duration}ms")
        Log.d(TAG, "=== 云端推理结束 ===")
        
        return response
    }
    
    // ... 其他方法
}
```

### 2. 保存失败案例

```kotlin
suspend fun inferenceWithLogging(
    image: Bitmap,
    prompt: String
): AgentResponse {
    try {
        val response = cloudManager.inference(image, prompt)
        
        // 记录成功案例
        if (response.confidence < 0.7f) {
            saveCase("low_confidence", image, prompt, response)
        }
        
        return response
        
    } catch (e: CloudApiException) {
        // 保存失败案例用于分析
        saveCase("error", image, prompt, null, e)
        throw e
    }
}

fun saveCase(
    type: String,
    image: Bitmap,
    prompt: String,
    response: AgentResponse?,
    error: Exception? = null
) {
    val timestamp = System.currentTimeMillis()
    val filename = "${type}_${timestamp}.json"
    
    val data = JSONObject().apply {
        put("timestamp", timestamp)
        put("prompt", prompt)
        put("response", response?.rawOutput)
        put("error", error?.message)
    }
    
    // 保存到文件
    File(context.filesDir, filename).writeText(data.toString())
}
```

---

## 💡 高级技巧

### 1. 批量推理

```kotlin
suspend fun batchInference(
    tasks: List<Pair<Bitmap, String>>
): List<AgentResponse> {
    return tasks.map { (image, prompt) ->
        async {
            cloudManager.inference(image, prompt)
        }
    }.awaitAll()
}
```

### 2. 缓存机制

```kotlin
class CachedCloudClient(private val delegate: ICloudClient) : ICloudClient {
    
    private val cache = LruCache<String, AgentResponse>(50)
    
    override suspend fun inference(
        image: Bitmap,
        prompt: String,
        uiTree: String?
    ): AgentResponse {
        val cacheKey = generateCacheKey(image, prompt)
        
        // 检查缓存
        cache.get(cacheKey)?.let {
            Log.d(TAG, "使用缓存结果")
            return it
        }
        
        // 调用 API
        val response = delegate.inference(image, prompt, uiTree)
        
        // 缓存结果
        if (response.confidence >= 0.8f) {
            cache.put(cacheKey, response)
        }
        
        return response
    }
    
    private fun generateCacheKey(image: Bitmap, prompt: String): String {
        val imageHash = image.hashCode()
        val promptHash = prompt.hashCode()
        return "$imageHash-$promptHash"
    }
}
```

---

**更多示例和最佳实践，请参考项目源码和各 Phase 文档。**
