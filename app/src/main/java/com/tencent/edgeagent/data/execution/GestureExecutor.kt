package com.tencent.edgeagent.data.execution

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.Build
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * 手势执行器
 * 
 * 职责：
 * 1. 封装 AccessibilityService 的 dispatchGesture
 * 2. 提供点击、滑动、长按等原子操作
 * 3. 线程安全 + 主线程保护
 */
class GestureExecutor(private val accessibilityService: AccessibilityService) {

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 点击指定坐标
     */
    suspend fun click(x: Int, y: Int, description: String = ""): Boolean {
        Timber.d("执行点击: ($x, $y) - $description")
        
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        
        return dispatchGesture(gesture)
    }

    /**
     * 长按指定坐标
     */
    suspend fun longClick(x: Int, y: Int, durationMs: Long = 1000): Boolean {
        Timber.d("执行长按: ($x, $y) 持续 ${durationMs}ms")
        
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        
        return dispatchGesture(gesture)
    }

    /**
     * 滑动
     */
    suspend fun swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMs: Long = 300
    ): Boolean {
        Timber.d("执行滑动: ($startX, $startY) → ($endX, $endY) 持续 ${durationMs}ms")
        
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        
        return dispatchGesture(gesture)
    }

    /**
     * 返回键
     */
    fun performBack(): Boolean {
        Timber.d("执行返回")
        return accessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    /**
     * Home 键
     */
    fun performHome(): Boolean {
        Timber.d("执行 Home")
        return accessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    /**
     * 最近任务
     */
    fun performRecents(): Boolean {
        Timber.d("执行最近任务")
        return accessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
    }

    /**
     * 通知栏
     */
    fun performNotifications(): Boolean {
        Timber.d("执行通知栏")
        return accessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
    }

    /**
     * 关闭通知栏（Android 12+ 支持）
     */
    fun dismissNotificationShade(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Timber.d("关闭通知栏")
            accessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        } else {
            // 低版本没有专用 global action，回退为 BACK（多数 ROM 可关闭系统遮罩）
            performBack()
        }
    }

    /**
     * 快捷设置
     */
    fun performQuickSettings(): Boolean {
        Timber.d("执行快捷设置")
        return accessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
    }

    /**
     * 分发手势（协程版本）
     */
    private suspend fun dispatchGesture(gesture: GestureDescription): Boolean = suspendCoroutine { continuation ->
        // 确保在主线程执行
        mainHandler.post {
            try {
                val result = accessibilityService.dispatchGesture(
                    gesture,
                    object : AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            Timber.d("手势执行成功")
                            continuation.resume(true)
                        }

                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            Timber.w("手势执行被取消")
                            continuation.resume(false)
                        }
                    },
                    null
                )
                
                if (!result) {
                    Timber.e("手势分发失败")
                    continuation.resume(false)
                }
            } catch (e: Exception) {
                Timber.e(e, "手势执行异常")
                continuation.resumeWithException(e)
            }
        }
    }
}
