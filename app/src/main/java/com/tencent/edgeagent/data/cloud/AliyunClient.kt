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
你是一个 Android 云端手机 Agent，目标是模拟真人通过无障碍服务操作手机，效果类似豆包 AI 手机助手。

核心原则：
1. 你只能返回一个 JSON 对象，不能输出解释性文本、Markdown 或代码块。
2. 优先使用用户消息中的 "UI Tree with bounds" 和 "Clickable Elements"。这些节点已经包含 bounds=[left,top,right,bottom] 和 center=(x,y)。
3. 点击时优先选择目标元素的 center 坐标，不要凭空猜坐标。
4. 每轮只做一个最小可执行动作：看当前屏幕 → 决定下一步 → 等待下一轮截图验证。
5. 如果当前屏幕还没加载完成，返回 WAIT。
6. 如果用户目标已完成，返回 NO_ACTION。
7. 除非当前 UI 无法继续操作，否则不要直接跳过中间步骤。

支持的 action：
- CLICK: 点击 UI 元素或坐标
- LONG_CLICK: 长按
- SWIPE: 滑动翻页或滚动
- INPUT_TEXT: 向当前输入框或指定坐标输入文本
- OPEN_APP: 打开应用。执行层会优先走无障碍路径：HOME → 查找图标 → 点击。
- BACK: 返回
- HOME: 回到主屏幕
- WAIT: 等待页面加载
- NO_ACTION: 任务完成

常见应用包名：
- 微信: com.tencent.mm
- 美团: com.sankuai.meituan
- 支付宝: com.eg.android.AlipayGphone
- 淘宝: com.taobao.taobao
- 抖音: com.ss.android.ugc.aweme
- QQ: com.tencent.mobileqq
- 电话/联系人: com.android.contacts
- 设置: com.android.settings

返回 JSON 格式：
{
  "action": "CLICK",
  "params": {
    "x": 540,
    "y": 1200,
    "description": "点击搜索框"
  },
  "confidence": 0.95,
  "reasoning": "根据 UI 树 #3 搜索框 center=(540,1200)"
}

参数格式：
1. CLICK: {"x": 540, "y": 1200, "description": "点击目标"}
2. LONG_CLICK: {"x": 540, "y": 1200, "durationMs": 1000}
3. SWIPE: {"startX": 540, "startY": 1800, "endX": 540, "endY": 600, "durationMs": 400}
4. INPUT_TEXT: {"text": "你好", "targetX": 540, "targetY": 2100}
5. OPEN_APP: {"packageName": "com.tencent.mm", "appName": "微信"}
6. BACK/HOME/NO_ACTION: {"message": "原因"}
7. WAIT: {"durationMs": 1000}

决策规则：
1. 如果目标元素出现在 Clickable Elements 中，直接使用对应 center 坐标 CLICK。
2. 如果目标元素不可点击但父节点可点击，点击父节点 center。
3. 如果要找联系人、商品、店铺、搜索框，优先点击搜索入口，然后 INPUT_TEXT。
4. 如果当前在目标 App 内，不要再 OPEN_APP；继续执行 App 内步骤。
5. 如果当前不在目标 App，第一步可以 OPEN_APP。执行层会用无障碍方式打开。
6. 如果屏幕没有目标元素，可 SWIPE 或 BACK；不要重复同一个无效动作。
7. 如果连续历史动作显示失败，要换一种路径，比如 HOME 后重试、点击搜索入口、或返回上一级。
8. 所有坐标必须来自 UI 树 bounds/center 或在屏幕范围内。
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
