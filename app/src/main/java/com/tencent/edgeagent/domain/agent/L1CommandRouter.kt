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
                val controlType = when {
                    text.contains("取消静音") || normalized.contains("unmute") -> DeviceControlType.VOLUME_UNMUTE
                    text.contains("静音") || normalized.contains("mute") -> DeviceControlType.VOLUME_MUTE
                    isDecrease(text, normalized) -> DeviceControlType.VOLUME_DOWN
                    else -> DeviceControlType.VOLUME_UP
                }
                deviceControl(controlType, text)
            }

            text.contains("播放") || text.contains("暂停") || normalized.contains("play") || normalized.contains("pause") -> {
                deviceControl(DeviceControlType.MEDIA_PLAY_PAUSE, text)
            }

            text.contains("下一首") || text.contains("下一曲") || normalized.contains("next track") -> {
                deviceControl(DeviceControlType.MEDIA_NEXT, text)
            }

            text.contains("上一首") || text.contains("上一曲") || normalized.contains("previous track") -> {
                deviceControl(DeviceControlType.MEDIA_PREVIOUS, text)
            }

            text.contains("亮度") || normalized.contains("brightness") -> {
                val controlType = when {
                    text.contains("设置") || text.contains("页面") -> DeviceControlType.DISPLAY_SETTINGS
                    isDecrease(text, normalized) -> DeviceControlType.BRIGHTNESS_DOWN
                    else -> DeviceControlType.BRIGHTNESS_UP
                }
                deviceControl(controlType, text)
            }

            text.contains("关闭通知栏") ||
                text.contains("收起通知栏") ||
                text.contains("关闭控制中心") ||
                text.contains("收起控制中心") -> {
                deviceControl(DeviceControlType.DISMISS_SYSTEM_SHADE, text)
            }

            text.contains("通知栏") || text.contains("下拉通知") -> {
                deviceControl(DeviceControlType.NOTIFICATIONS_SHADE, text)
            }

            text.contains("快捷设置") || text.contains("控制中心") -> {
                deviceControl(DeviceControlType.QUICK_SETTINGS, text)
            }

            text.contains("锁屏") || normalized.contains("lock screen") -> {
                deviceControl(DeviceControlType.LOCK_SCREEN, text)
            }

            text.contains("电源菜单") || normalized.contains("power menu") -> {
                deviceControl(DeviceControlType.POWER_DIALOG, text)
            }

            text.contains("分屏") || normalized.contains("split screen") -> {
                deviceControl(DeviceControlType.SPLIT_SCREEN, text)
            }

            normalized.contains("wifi") ||
                normalized.contains("wi-fi") ||
                text.contains("无线网络") ||
                text.contains("WLAN", ignoreCase = true) -> {
                deviceControl(DeviceControlType.WIFI_SETTINGS, text)
            }

            text.contains("蓝牙") || normalized.contains("bluetooth") -> {
                deviceControl(DeviceControlType.BLUETOOTH_SETTINGS, text)
            }

            text.contains("飞行模式") || normalized.contains("airplane") -> {
                deviceControl(DeviceControlType.AIRPLANE_MODE_SETTINGS, text)
            }

            isSettingsEntryIntent(text, normalized) -> {
                resolveSettingsEntry(text, normalized)?.let { deviceControl(it, text) }
            }

            else -> null
        }
    }

    private fun resolveSystemNavigation(text: String, normalized: String): AgentResponse? {
        return when {
            text == "返回" ||
                text.contains("返回上一页") ||
                text.contains("后退") ||
                text.contains("关闭键盘") ||
                text.contains("收起键盘") ||
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
            text.contains("浏览器") -> "com.android.browser"
            normalized.contains("chrome") -> "com.android.chrome"
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

    private fun isSettingsEntryIntent(text: String, normalized: String): Boolean {
        val entryWords = listOf("打开", "进入", "去", "查看", "管理", "页面")
        val hasEntryIntent = entryWords.any { text.contains(it) } ||
            normalized.startsWith("open ") ||
            normalized.contains("settings")
        if (!hasEntryIntent) return false

        val l2MutationWords = listOf("改为", "改成", "设为", "设置为", "换成", "选择", "配对", "连接到", "添加")
        return l2MutationWords.none { text.contains(it) }
    }

    private fun resolveSettingsEntry(text: String, normalized: String): DeviceControlType? {
        return when {
            text.contains("显示") || text.contains("屏幕") -> DeviceControlType.DISPLAY_SETTINGS
            text.contains("声音与触感") || text.contains("声音") || text.contains("振动") -> DeviceControlType.SOUND_SETTINGS
            text.contains("通知") -> DeviceControlType.NOTIFICATION_SETTINGS
            text.contains("网络") -> DeviceControlType.NETWORK_SETTINGS
            text.contains("移动网络") || text.contains("蜂窝") || text.contains("流量") -> DeviceControlType.MOBILE_NETWORK_SETTINGS
            text.contains("热点") -> DeviceControlType.HOTSPOT_SETTINGS
            text.contains("NFC", ignoreCase = true) -> DeviceControlType.NFC_SETTINGS
            text.contains("VPN", ignoreCase = true) -> DeviceControlType.VPN_SETTINGS
            text.contains("定位") || text.contains("位置") || normalized.contains("location") -> DeviceControlType.LOCATION_SETTINGS
            text.contains("日期") || text.contains("时间") || text.contains("时区") -> DeviceControlType.DATE_TIME_SETTINGS
            text.contains("语言") || normalized.contains("language") -> DeviceControlType.LANGUAGE_SETTINGS
            text.contains("壁纸") || normalized.contains("wallpaper") -> DeviceControlType.WALLPAPER_SETTINGS
            text.contains("无障碍") || normalized.contains("accessibility") -> DeviceControlType.ACCESSIBILITY_SETTINGS
            text.contains("应用管理") || text.contains("应用设置") -> DeviceControlType.APP_SETTINGS
            text.contains("电池") || text.contains("省电") || normalized.contains("battery") -> DeviceControlType.BATTERY_SETTINGS
            text.contains("存储") || text.contains("储存") || text.contains("空间") || normalized.contains("storage") -> DeviceControlType.STORAGE_SETTINGS
            text.contains("隐私") || normalized.contains("privacy") -> DeviceControlType.PRIVACY_SETTINGS
            text.contains("安全") || normalized.contains("security") -> DeviceControlType.SECURITY_SETTINGS
            text.contains("勿扰") || text.contains("免打扰") || normalized.contains("do not disturb") -> DeviceControlType.DO_NOT_DISTURB_SETTINGS
            else -> null
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
