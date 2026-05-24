package com.tencent.edgeagent.domain.model

import android.graphics.Bitmap

/**
 * Agent 推理响应模型
 * 包含模型推理结果和执行指令
 */
data class AgentResponse(
    /**
     * 推理来源
     */
    val source: InferenceSource,

    /**
     * 执行动作类型
     */
    val action: ActionType,

    /**
     * 动作参数
     */
    val actionParams: ActionParams,

    /**
     * 置信度分数（0.0 - 1.0）
     */
    val confidence: Float,

    /**
     * 推理耗时（毫秒）
     */
    val inferenceTimeMs: Long,

    /**
     * 模型输出的原始文本（用于调试）
     */
    val rawOutput: String? = null,

    /**
     * 是否需要用户确认
     */
    val requiresConfirmation: Boolean = false
)

/**
 * 推理来源
 */
enum class InferenceSource {
    LOCAL_VLM,      // 本地多模态模型
    LOCAL_RAG,      // 本地 RAG 检索
    STRATEGY,       // 策略改写（非模型推理）
    CLOUD_FALLBACK, // 云端兜底
    MOCK            // Mock 数据（开发阶段）
}

/**
 * 执行动作类型
 */
enum class ActionType {
    CLICK,          // 点击
    LONG_CLICK,     // 长按
    SWIPE,          // 滑动
    INPUT_TEXT,     // 输入文本
    BACK,           // 返回
    HOME,           // 回到主屏幕
    RECENTS,        // 最近任务
    OPEN_APP,       // 打开应用
    DEVICE_CONTROL, // 设备控制（音量、亮度等）
    WAIT,           // 等待
    NO_ACTION       // 无需操作（仅返回信息）
}

/**
 * 动作参数（根据 ActionType 不同而不同）
 */
sealed class ActionParams {
    /**
     * 点击参数
     */
    data class Click(
        val x: Int,
        val y: Int,
        val description: String = ""
    ) : ActionParams()

    /**
     * 长按参数
     */
    data class LongClick(
        val x: Int,
        val y: Int,
        val durationMs: Long = 1000
    ) : ActionParams()

    /**
     * 滑动参数
     */
    data class Swipe(
        val startX: Int,
        val startY: Int,
        val endX: Int,
        val endY: Int,
        val durationMs: Long = 300
    ) : ActionParams()

    /**
     * 输入文本参数
     */
    data class InputText(
        val text: String,
        val targetX: Int? = null,
        val targetY: Int? = null
    ) : ActionParams()

    /**
     * 打开应用参数
     */
    data class OpenApp(
        val packageName: String,
        val activityName: String? = null
    ) : ActionParams()

    /**
     * 设备控制参数
     */
    data class DeviceControl(
        val controlType: DeviceControlType,
        val value: String
    ) : ActionParams()

    /**
     * 等待参数
     */
    data class Wait(
        val durationMs: Long
    ) : ActionParams()

    /**
     * 无操作（仅返回信息）
     */
    data class NoAction(
        val message: String
    ) : ActionParams()
}

/**
 * 设备控制类型
 */
enum class DeviceControlType {
    VOLUME_UP,
    VOLUME_DOWN,
    VOLUME_MUTE,
    VOLUME_UNMUTE,
    MEDIA_PLAY_PAUSE,
    MEDIA_NEXT,
    MEDIA_PREVIOUS,
    BRIGHTNESS_UP,
    BRIGHTNESS_DOWN,
    DISPLAY_SETTINGS,
    SOUND_SETTINGS,
    NOTIFICATION_SETTINGS,
    WIFI_SETTINGS,
    WIFI_TOGGLE,
    BLUETOOTH_SETTINGS,
    BLUETOOTH_TOGGLE,
    AIRPLANE_MODE_SETTINGS,
    AIRPLANE_MODE_TOGGLE,
    NETWORK_SETTINGS,
    MOBILE_NETWORK_SETTINGS,
    HOTSPOT_SETTINGS,
    NFC_SETTINGS,
    VPN_SETTINGS,
    LOCATION_SETTINGS,
    DATE_TIME_SETTINGS,
    LANGUAGE_SETTINGS,
    WALLPAPER_SETTINGS,
    ACCESSIBILITY_SETTINGS,
    APP_SETTINGS,
    BATTERY_SETTINGS,
    STORAGE_SETTINGS,
    PRIVACY_SETTINGS,
    SECURITY_SETTINGS,
    DO_NOT_DISTURB_SETTINGS,
    NOTIFICATIONS_SHADE,
    QUICK_SETTINGS,
    DISMISS_SYSTEM_SHADE,
    LOCK_SCREEN,
    POWER_DIALOG,
    SPLIT_SCREEN
}

/**
 * 屏幕数据（感知层输出）
 */
data class ScreenData(
    /**
     * 屏幕截图
     */
    val bitmap: Bitmap,

    /**
     * UI 树文本表示（简化版）
     */
    val uiTreeText: String?,

    /**
     * 屏幕宽度
     */
    val screenWidth: Int,

    /**
     * 屏幕高度
     */
    val screenHeight: Int,

    /**
     * 当前应用包名
     */
    val currentPackage: String?,

    /**
     * 是否是真实屏幕截图；false 表示仅为空白占位图，不能用于视觉判断。
     */
    val hasRealScreenshot: Boolean = true,

    /**
     * 捕获时间戳
     */
    val timestamp: Long = System.currentTimeMillis()
)
