package com.tencent.edgeagent.domain.agent

import com.tencent.edgeagent.domain.model.ActionParams
import com.tencent.edgeagent.domain.model.ActionType
import com.tencent.edgeagent.domain.model.AgentResponse
import com.tencent.edgeagent.domain.model.DeviceControlType
import com.tencent.edgeagent.domain.model.InferenceSource

/**
 * L1 deterministic router for low-risk phone operations.
 *
 * These commands should not depend on a VLM guessing the next action. The
 * router only handles tasks that can be mapped directly to a safe system action.
 */
class L1CommandRouter private constructor() {

    fun resolve(userInput: String): AgentResponse? {
        val text = userInput.trim()
        if (text.isBlank()) return null

        val normalized = text.lowercase()

        resolveDeviceControl(text, normalized)?.let { return it }
        resolveSystemNavigation(text, normalized)?.let { return it }
        resolveOpenApp(text, normalized)?.let { return it }

        return null
    }

    private fun resolveDeviceControl(text: String, normalized: String): AgentResponse? {
        return when {
            text.contains("音量") || normalized.contains("volume") -> {
                val controlType = if (isDecrease(text, normalized)) {
                    DeviceControlType.VOLUME_DOWN
                } else {
                    DeviceControlType.VOLUME_UP
                }
                deviceControl(controlType, text)
            }

            text.contains("亮度") || normalized.contains("brightness") -> {
                val controlType = if (isDecrease(text, normalized)) {
                    DeviceControlType.BRIGHTNESS_DOWN
                } else {
                    DeviceControlType.BRIGHTNESS_UP
                }
                deviceControl(controlType, text)
            }

            normalized.contains("wifi") ||
                normalized.contains("wi-fi") ||
                text.contains("无线网络") ||
                text.contains("WLAN", ignoreCase = true) -> {
                deviceControl(DeviceControlType.WIFI_TOGGLE, text)
            }

            text.contains("蓝牙") || normalized.contains("bluetooth") -> {
                deviceControl(DeviceControlType.BLUETOOTH_TOGGLE, text)
            }

            text.contains("飞行模式") || normalized.contains("airplane") -> {
                deviceControl(DeviceControlType.AIRPLANE_MODE_TOGGLE, text)
            }

            else -> null
        }
    }

    private fun resolveSystemNavigation(text: String, normalized: String): AgentResponse? {
        return when {
            text == "返回" ||
                text.contains("返回上一页") ||
                text.contains("后退") ||
                normalized == "back" -> {
                response(
                    action = ActionType.BACK,
                    params = ActionParams.NoAction("返回上一页"),
                    raw = text
                )
            }

            text.contains("回到桌面") ||
                text.contains("回桌面") ||
                text.contains("主屏幕") ||
                normalized == "home" -> {
                response(
                    action = ActionType.HOME,
                    params = ActionParams.NoAction("回到主屏幕"),
                    raw = text
                )
            }

            text.contains("最近任务") ||
                text.contains("多任务") ||
                text.contains("后台应用") ||
                normalized.contains("recents") -> {
                response(
                    action = ActionType.RECENTS,
                    params = ActionParams.NoAction("打开最近任务"),
                    raw = text
                )
            }

            else -> null
        }
    }

    private fun resolveOpenApp(text: String, normalized: String): AgentResponse? {
        if (!isOpenIntent(text, normalized)) return null
        if (containsComplexFinalAction(text)) return null

        val packageName = when {
            text.contains("相机") || normalized.contains("camera") -> "com.android.camera"
            text.contains("微信") || normalized.contains("wechat") -> "com.tencent.mm"
            text.contains("美团") -> "com.sankuai.meituan"
            text.contains("支付宝") -> "com.eg.android.AlipayGphone"
            text.contains("淘宝") -> "com.taobao.taobao"
            text.contains("抖音") -> "com.ss.android.ugc.aweme"
            text.contains("QQ", ignoreCase = true) -> "com.tencent.mobileqq"
            text.contains("电话") || text.contains("联系人") -> "com.android.contacts"
            text.contains("设置") -> "com.android.settings"
            text.contains("浏览器") || normalized.contains("chrome") -> "com.android.chrome"
            else -> null
        } ?: return null

        return response(
            action = ActionType.OPEN_APP,
            params = ActionParams.OpenApp(packageName = packageName),
            raw = text
        )
    }

    private fun isOpenIntent(text: String, normalized: String): Boolean {
        return text.contains("打开") ||
            text.contains("启动") ||
            text.contains("开启") ||
            normalized.startsWith("open ")
    }

    private fun containsComplexFinalAction(text: String): Boolean {
        val highRiskOrMultiStep = listOf(
            "发送",
            "发消息",
            "回复",
            "支付",
            "下单",
            "删除",
            "转账",
            "提交",
            "确认",
            "输入",
            "填写"
        )
        return highRiskOrMultiStep.any { text.contains(it) }
    }

    private fun isDecrease(text: String, normalized: String): Boolean {
        val decreaseWords = listOf("调低", "降低", "减小", "小一点", "下调", "down", "lower", "reduce")
        return decreaseWords.any { word ->
            text.contains(word, ignoreCase = true) || normalized.contains(word)
        }
    }

    private fun deviceControl(controlType: DeviceControlType, raw: String): AgentResponse {
        return response(
            action = ActionType.DEVICE_CONTROL,
            params = ActionParams.DeviceControl(controlType = controlType, value = "1"),
            raw = raw
        )
    }

    private fun response(
        action: ActionType,
        params: ActionParams,
        raw: String
    ): AgentResponse {
        return AgentResponse(
            source = InferenceSource.LOCAL_RAG,
            action = action,
            actionParams = params,
            confidence = 1.0f,
            inferenceTimeMs = 0L,
            rawOutput = "l1_deterministic_router:$raw",
            requiresConfirmation = false
        )
    }

    companion object {
        @Volatile
        private var instance: L1CommandRouter? = null

        fun getInstance(): L1CommandRouter {
            return instance ?: synchronized(this) {
                instance ?: L1CommandRouter().also { instance = it }
            }
        }
    }
}
