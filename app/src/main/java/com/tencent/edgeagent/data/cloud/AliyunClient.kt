package com.tencent.edgeagent.data.cloud

import android.graphics.Bitmap
import android.util.Base64
import com.tencent.edgeagent.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.SocketTimeoutException

/**
 * 阿里云百炼 API 客户端
 * 
 * 特点：
 * - 支持多模态（图片 + 文本）
 * - 国内访问速度快
 * - 稳定可靠
 * 
 * API 文档：https://help.aliyun.com/zh/model-studio/developer-reference/
 */
class AliyunClient(
    private val apiKey: String,
    private val modelName: String = "qwen-vl-max",
    private val endpoint: String = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
) : ICloudClient {

    companion object {
        private const val TIMEOUT_MS = 30000 // 30 秒超时
        private const val MAX_IMAGE_SIZE = 1024 // 压缩图片到最大 1024px
    }

    override suspend fun inference(
        image: Bitmap,
        prompt: String,
        uiTree: String?
    ): AgentResponse = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        try {
            Timber.d("阿里云 API 调用开始: prompt='$prompt'")
            
            // 构建系统提示词
            val systemPrompt = buildSystemPrompt()
            
            // 构建用户消息（包含图片和文本）
            val userContent = buildUserContent(image, prompt, uiTree)
            
            // 发送请求
            val response = sendRequest(systemPrompt, userContent)
            
            // 解析响应
            val agentResponse = parseResponse(response, startTime)
            
            Timber.d("阿里云 API 调用成功: action=${agentResponse.action}, confidence=${agentResponse.confidence}")
            
            agentResponse
            
        } catch (e: SocketTimeoutException) {
            Timber.e(e, "阿里云 API 超时")
            throw CloudApiException.Timeout("请求超时，请检查网络连接")
        } catch (e: CloudApiException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "阿里云 API 调用失败")
            throw CloudApiException.ServerError("云端推理失败: ${e.message}", e)
        }
    }

    override suspend fun checkAvailability(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL(endpoint)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            val responseCode = connection.responseCode
            connection.disconnect()
            
            responseCode in 200..299 || responseCode == 401 // 401 说明服务可用，只是需要认证
        } catch (e: Exception) {
            Timber.e(e, "检查阿里云 API 可用性失败")
            false
        }
    }

    override fun getProviderInfo(): CloudProviderInfo {
        return CloudProviderInfo(
            name = "阿里云百炼",
            modelName = modelName,
            supportsMultimodal = true,
            maxContextLength = 30000,
            endpoint = endpoint
        )
    }

    /**
     * 构建系统提示词
     * 
     * 修复点6: 强调UI树的重要性，减少对截图的依赖
     */
    private fun buildSystemPrompt(): String {
        return """
你是一个 Android 手机助手 AI，专门帮助用户操作手机。

你的任务：
1. 理解用户的意图
2. **优先分析 UI 结构文本**（截图可能不准确或为空白）
3. 决定执行什么操作
4. 返回操作指令（JSON 格式）

⚠️ 重要：UI 结构文本比截图更可靠，请优先分析 UI 树！

支持的操作类型：
- CLICK: 点击屏幕某个位置
- SWIPE: 滑动屏幕
- INPUT_TEXT: 输入文本
- OPEN_APP: 打开应用
- BACK: 返回
- HOME: 回到主屏幕
- WAIT: 等待（应用启动、页面加载等）
- NO_ACTION: 无需操作

常见应用包名映射：
- 微信: com.tencent.mm
- 美团: com.sankuai.meituan
- 支付宝: com.eg.android.AlipayGphone
- 淘宝: com.taobao.taobao
- 抖音: com.ss.android.ugc.aweme
- QQ: com.tencent.mobileqq
- 电话/联系人: com.android.contacts

返回格式（必须是有效的 JSON）：
{
  "action": "OPEN_APP",
  "params": {
    "packageName": "com.tencent.mm"
  },
  "confidence": 0.95,
  "reasoning": "用户想要打开微信"
}

不同操作的参数格式：

1. CLICK（点击）:
{
  "action": "CLICK",
  "params": {
    "x": 540,
    "y": 1200,
    "description": "点击搜索框"
  },
  "confidence": 0.95
}

2. SWIPE（滑动）:
{
  "action": "SWIPE",
  "params": {
    "startX": 540,
    "startY": 1500,
    "endX": 540,
    "endY": 500,
    "durationMs": 300
  },
  "confidence": 0.90
}

3. INPUT_TEXT（输入文本）:
{
  "action": "INPUT_TEXT",
  "params": {
    "text": "你好",
    "targetX": 540,
    "targetY": 1200
  },
  "confidence": 0.85
}

4. OPEN_APP（打开应用）:
{
  "action": "OPEN_APP",
  "params": {
    "packageName": "com.tencent.mm"
  },
  "confidence": 0.95
}

5. BACK（返回）:
{
  "action": "BACK",
  "params": {},
  "confidence": 1.0
}

6. HOME（回到主屏幕）:
{
  "action": "HOME",
  "params": {},
  "confidence": 1.0
}

7. WAIT（等待）:
{
  "action": "WAIT",
  "params": {
    "durationMs": 1000
  },
  "confidence": 0.95
}

注意事项：
1. 坐标必须在屏幕范围内（通常是 1080x2400）
2. confidence 范围是 0.0-1.0
3. 如果不确定，confidence 设置为 0.6-0.7
4. 必须返回有效的 JSON，不要有其他文字
5. 如果用户请求涉及多个步骤，只返回第一步操作
6. 如果应用正在启动（黑屏/加载中），使用 WAIT 操作等待，不要重复打开应用
        """.trimIndent()
    }

    /**
     * 构建用户消息内容（阿里云格式）
     */
    private fun buildUserContent(image: Bitmap, prompt: String, uiTree: String?): JSONArray {
        val content = JSONArray()
        
        // 添加文本内容
        val textBuilder = StringBuilder()
        textBuilder.append("用户请求：$prompt\n\n")
        
        if (uiTree != null) {
            textBuilder.append("屏幕 UI 结构：\n$uiTree\n\n")
        }
        
        textBuilder.append("请分析屏幕截图，理解用户意图，返回操作指令（JSON 格式）。")
        
        content.put(JSONObject().apply {
            put("type", "text")
            put("text", textBuilder.toString())
        })
        
        // 添加图片内容
        val compressedImage = compressImage(image)
        val base64Image = bitmapToBase64(compressedImage)
        
        content.put(JSONObject().apply {
            put("type", "image_url")
            put("image_url", JSONObject().apply {
                put("url", "data:image/jpeg;base64,$base64Image")
            })
        })
        
        return content
    }

    /**
     * 发送 HTTP 请求
     */
    private fun sendRequest(systemPrompt: String, userContent: JSONArray): String {
        val url = URL(endpoint)
        val connection = url.openConnection() as HttpURLConnection
        
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.doOutput = true
        
        // 构建请求体（阿里云格式）
        val requestBody = JSONObject().apply {
            put("model", modelName)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userContent)
                })
            })
            put("temperature", 0.7)
            put("max_tokens", 1000)
        }
        
        Timber.d("发送请求到阿里云 API")
        
        // 发送请求
        connection.outputStream.use { os ->
            os.write(requestBody.toString().toByteArray())
        }
        
        // 读取响应
        val responseCode = connection.responseCode
        
        if (responseCode == 401) {
            throw CloudApiException.InvalidApiKey("API 密钥无效")
        }
        
        if (responseCode == 429) {
            throw CloudApiException.RateLimitExceeded("请求频率超限，请稍后再试")
        }
        
        if (responseCode !in 200..299) {
            val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "未知错误"
            Timber.e("阿里云 API 错误: $responseCode, $errorBody")
            throw CloudApiException.ServerError("服务器返回错误: $responseCode, $errorBody")
        }
        
        val response = connection.inputStream.bufferedReader().readText()
        connection.disconnect()
        
        return response
    }

    /**
     * 解析 API 响应
     */
    private fun parseResponse(response: String, startTime: Long): AgentResponse {
        try {
            val jsonResponse = JSONObject(response)
            val choices = jsonResponse.getJSONArray("choices")
            
            if (choices.length() == 0) {
                throw CloudApiException.ParseError("响应中没有 choices")
            }
            
            val firstChoice = choices.getJSONObject(0)
            val message = firstChoice.getJSONObject("message")
            val content = message.getString("content")
            
            Timber.d("阿里云响应内容: $content")
            
            // 解析 JSON 格式的操作指令
            val actionJson = extractJsonFromContent(content)
            
            val action = ActionType.valueOf(actionJson.getString("action"))
            val paramsJson = actionJson.optJSONObject("params") ?: JSONObject()
            val confidence = actionJson.optDouble("confidence", 0.8).toFloat()
            val reasoning = actionJson.optString("reasoning", "")
            
            // 根据 action 类型构建 ActionParams
            val actionParams = parseActionParams(action, paramsJson)
            
            val inferenceTime = System.currentTimeMillis() - startTime
            
            return AgentResponse(
                source = InferenceSource.CLOUD_FALLBACK,
                action = action,
                actionParams = actionParams,
                confidence = confidence,
                inferenceTimeMs = inferenceTime,
                rawOutput = content,
                requiresConfirmation = confidence < 0.75f
            )
            
        } catch (e: Exception) {
            Timber.e(e, "解析阿里云响应失败")
            throw CloudApiException.ParseError("解析响应失败: ${e.message}", e)
        }
    }

    /**
     * 从响应内容中提取 JSON
     */
    private fun extractJsonFromContent(content: String): JSONObject {
        // 尝试直接解析
        try {
            return JSONObject(content)
        } catch (e: Exception) {
            // 如果失败，尝试提取 JSON 代码块
            val jsonRegex = """```json\s*(\{.*?\})\s*```""".toRegex(RegexOption.DOT_MATCHES_ALL)
            val match = jsonRegex.find(content)
            
            if (match != null) {
                return JSONObject(match.groupValues[1])
            }
            
            // 尝试提取任何 JSON 对象
            val anyJsonRegex = """(\{[^{}]*(?:\{[^{}]*\}[^{}]*)*\})""".toRegex(RegexOption.DOT_MATCHES_ALL)
            val anyMatch = anyJsonRegex.find(content)
            
            if (anyMatch != null) {
                return JSONObject(anyMatch.groupValues[1])
            }
            
            throw CloudApiException.ParseError("无法从响应中提取 JSON: $content")
        }
    }

    /**
     * 解析动作参数
     */
    private fun parseActionParams(action: ActionType, paramsJson: JSONObject): ActionParams {
        return when (action) {
            ActionType.CLICK -> {
                ActionParams.Click(
                    x = paramsJson.optInt("x", 540),
                    y = paramsJson.optInt("y", 1200),
                    description = paramsJson.optString("description", "")
                )
            }
            
            ActionType.SWIPE -> {
                ActionParams.Swipe(
                    startX = paramsJson.optInt("startX", 540),
                    startY = paramsJson.optInt("startY", 1500),
                    endX = paramsJson.optInt("endX", 540),
                    endY = paramsJson.optInt("endY", 500),
                    durationMs = paramsJson.optLong("durationMs", 300)
                )
            }
            
            ActionType.INPUT_TEXT -> {
                ActionParams.InputText(
                    text = paramsJson.optString("text", ""),
                    targetX = paramsJson.optInt("targetX"),
                    targetY = paramsJson.optInt("targetY")
                )
            }
            
            ActionType.OPEN_APP -> {
                ActionParams.OpenApp(
                    packageName = paramsJson.optString("packageName", ""),
                    activityName = paramsJson.optString("activityName")
                )
            }
            
            ActionType.WAIT -> {
                ActionParams.Wait(
                    durationMs = paramsJson.optLong("durationMs", 1000) // 默认等待 1 秒
                )
            }
            
            ActionType.BACK, ActionType.HOME, ActionType.NO_ACTION -> {
                ActionParams.NoAction(
                    message = paramsJson.optString("message", "")
                )
            }
            
            ActionType.DEVICE_CONTROL -> {
                val controlTypeStr = paramsJson.optString("controlType", "")
                val controlType = try {
                    DeviceControlType.valueOf(controlTypeStr.uppercase())
                } catch (e: Exception) {
                    DeviceControlType.VOLUME_UP // 默认值
                }
                ActionParams.DeviceControl(
                    controlType = controlType,
                    value = paramsJson.optString("value", "")
                )
            }
            
            ActionType.LONG_CLICK -> {
                ActionParams.LongClick(
                    x = paramsJson.optInt("x", 540),
                    y = paramsJson.optInt("y", 1200),
                    durationMs = paramsJson.optLong("durationMs", 1000)
                )
            }
            
            else -> {
                ActionParams.NoAction(message = "不支持的操作类型")
            }
        }
    }

    /**
     * 压缩图片
     */
    private fun compressImage(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= MAX_IMAGE_SIZE && height <= MAX_IMAGE_SIZE) {
            return bitmap
        }
        
        val scale = MAX_IMAGE_SIZE.toFloat() / maxOf(width, height)
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Bitmap 转 Base64
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
