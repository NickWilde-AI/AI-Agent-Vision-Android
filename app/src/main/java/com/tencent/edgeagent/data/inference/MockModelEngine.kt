package com.tencent.edgeagent.data.inference

import android.graphics.Bitmap
import com.tencent.edgeagent.domain.model.ActionParams
import com.tencent.edgeagent.domain.model.ActionType
import com.tencent.edgeagent.domain.model.AgentResponse
import com.tencent.edgeagent.domain.model.InferenceSource
import kotlinx.coroutines.delay
import timber.log.Timber
import kotlin.random.Random

/**
 * Mock 模型引擎实现（单例模式）
 */
class MockModelEngine private constructor() : ILocalModelEngine {

    private var isLoaded = false

    override suspend fun inference(
        image: Bitmap,
        prompt: String,
        uiTree: String?
    ): AgentResponse {
        Timber.d("MockModelEngine 开始推理: prompt='$prompt'")
        
        val startTime = System.currentTimeMillis()
        
        val inferenceDelay = Random.nextLong(1200, 1800)
        delay(inferenceDelay)
        
        val response = generateMockResponse(image, prompt, uiTree)
        
        val endTime = System.currentTimeMillis()
        val actualInferenceTime = endTime - startTime
        
        Timber.d("MockModelEngine 推理完成: action=${response.action}, confidence=${response.confidence}, time=${actualInferenceTime}ms")
        
        return response.copy(inferenceTimeMs = actualInferenceTime)
    }

    override suspend fun warmUp() {
        Timber.d("MockModelEngine 预热中...")
        delay(500)
        isLoaded = true
        Timber.d("MockModelEngine 预热完成")
    }

    override fun release() {
        Timber.d("MockModelEngine 释放资源")
        isLoaded = false
    }

    override fun isModelLoaded(): Boolean = isLoaded

    override fun getModelInfo(): ModelInfo {
        return ModelInfo(
            name = "MockVLM",
            version = "1.0.0-mock",
            sizeInMB = 0f,
            supportsMultimodal = true,
            avgInferenceTimeMs = 1500
        )
    }

    private fun generateMockResponse(
        image: Bitmap,
        prompt: String,
        uiTree: String?
    ): AgentResponse {
        val normalizedPrompt = prompt.lowercase()
        
        val centerX = image.width / 2
        val centerY = image.height / 2
        
        val (actionType, actionParams) = when {
            normalizedPrompt.contains("点击") || normalizedPrompt.contains("click") -> {
                ActionType.CLICK to ActionParams.Click(
                    x = centerX,
                    y = centerY,
                    description = "点击屏幕中心"
                )
            }
            
            normalizedPrompt.contains("滑动") || normalizedPrompt.contains("swipe") -> {
                val direction = when {
                    normalizedPrompt.contains("上") -> "up"
                    normalizedPrompt.contains("下") -> "down"
                    normalizedPrompt.contains("左") -> "left"
                    normalizedPrompt.contains("右") -> "right"
                    else -> "up"
                }
                
                val (startX, startY, endX, endY) = when (direction) {
                    "up" -> listOf(centerX, centerY + 300, centerX, centerY - 300)
                    "down" -> listOf(centerX, centerY - 300, centerX, centerY + 300)
                    "left" -> listOf(centerX + 300, centerY, centerX - 300, centerY)
                    "right" -> listOf(centerX - 300, centerY, centerX + 300, centerY)
                    else -> listOf(centerX, centerY + 300, centerX, centerY - 300)
                }
                
                ActionType.SWIPE to ActionParams.Swipe(
                    startX = startX,
                    startY = startY,
                    endX = endX,
                    endY = endY,
                    durationMs = 300
                )
            }
            
            normalizedPrompt.contains("输入") || normalizedPrompt.contains("发送") -> {
                val textRegex = """(?:输入|发送)(.+)""".toRegex()
                val text = textRegex.find(normalizedPrompt)?.groupValues?.getOrNull(1)?.trim() ?: "测试文本"
                
                ActionType.INPUT_TEXT to ActionParams.InputText(
                    text = text,
                    targetX = centerX,
                    targetY = centerY
                )
            }
            
            normalizedPrompt.contains("打开") -> {
                val appRegex = """打开(.+?)(?:应用|app)?""".toRegex()
                val appName = appRegex.find(normalizedPrompt)?.groupValues?.getOrNull(1)?.trim() ?: "未知应用"
                
                ActionType.OPEN_APP to ActionParams.OpenApp(
                    packageName = "com.example.$appName"
                )
            }
            
            normalizedPrompt.contains("返回") || normalizedPrompt.contains("back") -> {
                ActionType.BACK to ActionParams.NoAction("执行返回操作")
            }
            
            normalizedPrompt.contains("主屏幕") || normalizedPrompt.contains("home") -> {
                ActionType.HOME to ActionParams.NoAction("回到主屏幕")
            }
            
            else -> {
                ActionType.CLICK to ActionParams.Click(
                    x = centerX,
                    y = centerY,
                    description = "默认点击屏幕中心"
                )
            }
        }
        
        val confidence = if (Random.nextFloat() < 0.3f) {
            Random.nextFloat() * 0.15f + 0.60f
        } else {
            Random.nextFloat() * 0.20f + 0.75f
        }
        
        return AgentResponse(
            source = InferenceSource.MOCK,
            action = actionType,
            actionParams = actionParams,
            confidence = confidence,
            inferenceTimeMs = 0,
            rawOutput = "Mock 推理输出: $prompt",
            requiresConfirmation = false
        )
    }

    companion object {
        @Volatile
        private var instance: MockModelEngine? = null
        
        fun getInstance(): MockModelEngine {
            return instance ?: synchronized(this) {
                instance ?: MockModelEngine().also { instance = it }
            }
        }
    }
}
