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
 * DeepSeek API 客户端
 * 
 * 特点：
 * - 支持多模态（图片 + 文本）
 * - 100k+ 上下文长度
 * - 性价比高
 * - 响应速度快
 * 
 * API 文档：https://platform.deepseek.com/api-docs/
 */
class DeepSeekClient(
    private val apiKey: String,
    private val modelName: String = "deepseek-chat",
    private val endpoint: String = "https://api.deepseek.com/v1/chat/completions"
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
            Timber.d("DeepSeek API 调用开始: prompt='$prompt'")
            
            // 构建系统提示词
            val systemPrompt = buildSystemPrompt()
            
            // 构建用户消息（包含图片和文本）
            val userMessage = buildUserMessage(image, prompt, uiTree)
            
            // 发送请求
            val response = sendRequest(systemPrompt, userMessage)
            
            // 解析响应
            val agentResponse = parseResponse(response, startTime)
            
            Timber.d("DeepSeek API 调用成功: action=${agentResponse.action}, confidence=${agentResponse.confidence}")
            
            agentResponse
            
        } catch (e: SocketTimeoutException) {
            Timber.e(e, "DeepSeek API 超时")
            throw CloudApiException.Timeout("请求超时，请检查网络连接")
        } catch (e: CloudApiException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "DeepSeek API 调用失败")
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
            Timber.e(e, "检查 DeepSeek API 可用性失败")
            false
        }
    }

    override fun getProviderInfo(): CloudProviderInfo {
        return CloudProviderInfo(
            name = "DeepSeek",
            modelName = modelName,
            supportsMultimodal = true,
            maxContextLength = 100000,
            endpoint = endpoint
        )
    }

    /**
     * 构建系统提示词
     */
    private fun buildSystemPrompt(): String {
        return """
你是一个 Android 手机助手 AI，专门帮助用户操作手机。

你的任务：
1. 理解用户的意图
2. 分析当前屏幕截图
3. 决定执行什么操作
4. 返回操作指令（JSON 格式）

支持的操作类型：
- CLICK: 点击屏幕某个位置
- SWIPE: 滑动屏幕
- INPUT_TEXT: 输入文本
- OPEN_APP: 打开应用
- BACK: 返回
- HOME: 回到主屏幕
- NO_ACTION: 无需操作

返回格式（必须是有效的 JSON）：
{
  "action": "CLICK",
  "params": {
    "x": 540,
    "y": 1200,
    "description": "点击搜索框"
  },
  "confidence": 0.95,
  "reasoning": "用户想要搜索，屏幕中心有搜索框"
}

注意事项：
1. 坐标必须在屏幕范围内
2. confidence 范围是 0.0-1.0
3. 如果不确定，confidence 设置为 0.6-0.7
4. 必须返回有效的 JSON，不要有其他文字
        """.trimIndent()
    }

    /**
     * 构建用户消息
     */
    private fun buildUserMessage(image: Bitmap, prompt: String, uiTree: String?): String {
        val compressedImage = compressImage(image)
        val base64Image = bitmapToBase64(compressedImage)
        
        val messageBuilder = StringBuilder()
        messageBuilder.append("用户请求：$prompt\n\n")
        
        if (uiTree != null) {
            messageBuilder.append("屏幕 UI 结构：\n$uiTree\n\n")
        }
        
        messageBuilder.append("屏幕截图（Base64）：\n")
        messageBuilder.append("data:image/jpeg;base64,$base64Image")
        
        return messageBuilder.toString()
    }

    /**
     * 发送 HTTP 请求
     */
    private fun sendRequest(systemPrompt: String, userMessage: String): String {
        val url = URL(endpoint)
        val connection = url.openConnection() as HttpURLConnection
        
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.doOutput = true
        
        // 构建请求体
        val requestBody = JSONObject().apply {
            put("model", modelName)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userMessage)
                })
            })
            put("temperature", 0.7)
            put("max_tokens", 500)
        }
        
        Timber.d("发送请求到 DeepSeek API")
        
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
            
            Timber.d("DeepSeek 响应内容: $content")
            
            // 解析 JSON 格式的操作指令
            val actionJson = extractJsonFromContent(content)
            
            val action = ActionType.valueOf(actionJson.getString("action"))
            val paramsJson = actionJson.getJSONObject("params")
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
            Timber.e(e, "解析 DeepSeek 响应失败")
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
            val anyJsonRegex = """(\{.*?\})""".toRegex(RegexOption.DOT_MATCHES_ALL)
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
                    x = paramsJson.getInt("x"),
                    y = paramsJson.getInt("y"),
                    description = paramsJson.optString("description", "")
                )
            }
            
            ActionType.SWIPE -> {
                ActionParams.Swipe(
                    startX = paramsJson.getInt("startX"),
                    startY = paramsJson.getInt("startY"),
                    endX = paramsJson.getInt("endX"),
                    endY = paramsJson.getInt("endY"),
                    durationMs = paramsJson.optLong("durationMs", 300)
                )
            }
            
            ActionType.INPUT_TEXT -> {
                ActionParams.InputText(
                    text = paramsJson.getString("text"),
                    targetX = paramsJson.optInt("targetX"),
                    targetY = paramsJson.optInt("targetY")
                )
            }
            
            ActionType.OPEN_APP -> {
                ActionParams.OpenApp(
                    packageName = paramsJson.getString("packageName"),
                    activityName = paramsJson.optString("activityName")
                )
            }
            
            ActionType.BACK, ActionType.HOME, ActionType.NO_ACTION -> {
                ActionParams.NoAction(
                    message = paramsJson.optString("message", "")
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
