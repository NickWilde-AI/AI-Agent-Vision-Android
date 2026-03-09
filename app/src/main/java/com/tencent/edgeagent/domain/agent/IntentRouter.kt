package com.tencent.edgeagent.domain.agent

import com.tencent.edgeagent.domain.model.AgentIntent
import com.tencent.edgeagent.domain.model.IntentType
import timber.log.Timber

/**
 * 意图路由器（单例模式）
 */
class IntentRouter private constructor() {

    fun parseIntent(userInput: String): AgentIntent {
        val normalizedInput = userInput.trim().lowercase()
        
        val deviceControlKeywords = listOf(
            "音量", "亮度", "wifi", "蓝牙", "飞行模式", "静音", "震动"
        )
        
        val appOperationKeywords = listOf(
            "打开", "点击", "滑动", "关闭", "切换", "返回"
        )
        
        val queryKeywords = listOf(
            "查询", "搜索", "什么", "怎么", "为什么", "天气", "新闻"
        )
        
        val textInputKeywords = listOf(
            "发送", "输入", "写", "填写", "回复", "短信", "邮件"
        )
        
        val complexKeywords = listOf(
            "帮我", "分析", "总结", "规划", "建议", "比较"
        )

        val intentType = when {
            deviceControlKeywords.any { normalizedInput.contains(it) } -> IntentType.DEVICE_CONTROL
            appOperationKeywords.any { normalizedInput.contains(it) } -> IntentType.APP_OPERATION
            textInputKeywords.any { normalizedInput.contains(it) } -> IntentType.TEXT_INPUT
            queryKeywords.any { normalizedInput.contains(it) } -> IntentType.INFORMATION_QUERY
            complexKeywords.any { normalizedInput.contains(it) } -> IntentType.COMPLEX_REASONING
            else -> IntentType.UNKNOWN
        }

        val allowCloudFallback = when (intentType) {
            IntentType.DEVICE_CONTROL -> false
            IntentType.TEXT_INPUT -> false
            IntentType.APP_OPERATION -> true
            IntentType.INFORMATION_QUERY -> true
            IntentType.COMPLEX_REASONING -> true
            IntentType.UNKNOWN -> true
        }

        val requiresScreenContext = when (intentType) {
            IntentType.DEVICE_CONTROL -> false
            IntentType.INFORMATION_QUERY -> false
            else -> true
        }

        Timber.d("意图识别: $userInput → $intentType (云端兜底: $allowCloudFallback)")

        return AgentIntent(
            type = intentType,
            userInput = userInput,
            parameters = extractParameters(userInput, intentType),
            requiresScreenContext = requiresScreenContext,
            allowCloudFallback = allowCloudFallback,
            priority = calculatePriority(intentType)
        )
    }

    fun shouldUseCloud(intent: AgentIntent, localConfidence: Float): Boolean {
        if (!intent.allowCloudFallback) {
            Timber.d("意图类型 ${intent.type} 不允许云端处理")
            return false
        }

        if (intent.type == IntentType.COMPLEX_REASONING) {
            Timber.d("复杂推理任务，直接使用云端")
            return true
        }

        if (localConfidence < CLOUD_FALLBACK_THRESHOLD) {
            Timber.d("本地置信度不足 ($localConfidence < $CLOUD_FALLBACK_THRESHOLD)，使用云端兜底")
            return true
        }

        return false
    }

    private fun extractParameters(userInput: String, intentType: IntentType): Map<String, String> {
        val params = mutableMapOf<String, String>()

        when (intentType) {
            IntentType.DEVICE_CONTROL -> {
                if (userInput.contains("音量")) {
                    params["control_type"] = "volume"
                    params["action"] = if (userInput.contains("增加") || userInput.contains("调高")) "up" else "down"
                }
                if (userInput.contains("亮度")) {
                    params["control_type"] = "brightness"
                    params["action"] = if (userInput.contains("增加") || userInput.contains("调高")) "up" else "down"
                }
            }
            
            IntentType.APP_OPERATION -> {
                val appNameRegex = """打开(.+?)(?:应用|app)?""".toRegex(RegexOption.IGNORE_CASE)
                appNameRegex.find(userInput)?.groupValues?.getOrNull(1)?.let {
                    params["app_name"] = it.trim()
                }
            }
            
            IntentType.TEXT_INPUT -> {
                val textRegex = """(?:发送|输入|写)(.+)""".toRegex(RegexOption.IGNORE_CASE)
                textRegex.find(userInput)?.groupValues?.getOrNull(1)?.let {
                    params["text"] = it.trim()
                }
            }
            
            else -> {}
        }

        return params
    }

    private fun calculatePriority(intentType: IntentType): Int {
        return when (intentType) {
            IntentType.DEVICE_CONTROL -> 9
            IntentType.TEXT_INPUT -> 8
            IntentType.APP_OPERATION -> 7
            IntentType.INFORMATION_QUERY -> 5
            IntentType.COMPLEX_REASONING -> 3
            IntentType.UNKNOWN -> 1
        }
    }

    companion object {
        private const val CLOUD_FALLBACK_THRESHOLD = 0.75f
        
        @Volatile
        private var instance: IntentRouter? = null
        
        fun getInstance(): IntentRouter {
            return instance ?: synchronized(this) {
                instance ?: IntentRouter().also { instance = it }
            }
        }
    }
}
