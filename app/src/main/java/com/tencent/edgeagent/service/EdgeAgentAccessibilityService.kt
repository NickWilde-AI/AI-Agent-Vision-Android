package com.tencent.edgeagent.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import com.tencent.edgeagent.data.execution.GestureExecutor
import com.tencent.edgeagent.data.perception.ScreenCaptureManager
import com.tencent.edgeagent.data.perception.UITreeExtractor
import com.tencent.edgeagent.domain.model.ScreenData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * EdgeAgent 无障碍服务
 * 
 * 职责：
 * 1. 捕获屏幕录制帧（高性能，内存安全）
 * 2. 提取 UI 树信息
 * 3. 执行手势操作
 * 4. 与 Domain 层通信
 * 
 * 特性：
 * - Bitmap 复用池，避免频繁 GC
 * - 协程异步处理，不阻塞主线程
 * - 内存安全保护
 */
class EdgeAgentAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private lateinit var gestureExecutor: GestureExecutor
    private val screenCaptureManager = ScreenCaptureManager.getInstance()
    private val uiTreeExtractor = UITreeExtractor.getInstance()
    
    private var isServiceReady = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        Timber.d("EdgeAgentAccessibilityService 已连接")
        
        gestureExecutor = GestureExecutor(this)
        isServiceReady = true
        
        Timber.i("无障碍服务初始化完成")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 这里可以监听屏幕变化事件
        // 暂时不处理，后续可以用于自动触发
    }

    override fun onInterrupt() {
        Timber.w("无障碍服务被中断")
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("EdgeAgentAccessibilityService 销毁")
        
        isServiceReady = false
        serviceScope.cancel()
        screenCaptureManager.clearPool()
    }

    /**
     * 捕获当前屏幕数据（屏幕录制帧 + UI 树）
     * 优先使用 MediaProjection 录制帧，不可用时降级为空白 Bitmap
     */
    suspend fun captureScreenData(): ScreenData? {
        if (!isServiceReady) {
            Timber.e("服务未就绪")
            return null
        }
        
        return try {
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            
            // 提取 UI 树
            val rootNode = rootInActiveWindow
            val uiTreeText: String?
            val currentPackage: String?
            
            if (rootNode != null) {
                uiTreeText = uiTreeExtractor.extractUITree(rootNode)
                currentPackage = rootNode.packageName?.toString()
                rootNode.recycle()
            } else {
                Timber.w("rootInActiveWindow 为 null，UI 树不可用")
                uiTreeText = null
                currentPackage = null
            }
            
            val hasRealScreenshot = screenCaptureManager.isScreenCaptureAvailable()
            val bitmap = if (hasRealScreenshot) {
                Timber.d("使用 MediaProjection 录制帧")
                screenCaptureManager.captureScreen()
            } else {
                Timber.w("MediaProjection 不可用，使用空白 Bitmap（请授权屏幕录制权限）")
                screenCaptureManager.obtainBitmap(screenWidth, screenHeight)
            }
            
            ScreenData(
                bitmap = bitmap,
                uiTreeText = uiTreeText,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                currentPackage = currentPackage,
                hasRealScreenshot = hasRealScreenshot
            )
        } catch (e: Exception) {
            Timber.e(e, "捕获屏幕数据失败")
            null
        }
    }

    /**
     * 执行点击
     */
    suspend fun performClick(x: Int, y: Int, description: String = ""): Boolean {
        return gestureExecutor.click(x, y, description)
    }

    /**
     * 执行长按
     */
    suspend fun performLongClick(x: Int, y: Int, durationMs: Long = 1000): Boolean {
        return gestureExecutor.longClick(x, y, durationMs)
    }

    /**
     * 执行滑动
     */
    suspend fun performSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long = 300): Boolean {
        return gestureExecutor.swipe(startX, startY, endX, endY, durationMs)
    }

    /**
     * 执行返回
     */
    fun performBack(): Boolean {
        return gestureExecutor.performBack()
    }

    /**
     * 执行 Home
     */
    fun performHome(): Boolean {
        return gestureExecutor.performHome()
    }

    /**
     * 执行最近任务
     */
    fun performRecents(): Boolean {
        return gestureExecutor.performRecents()
    }

    companion object {
        @Volatile
        private var instance: EdgeAgentAccessibilityService? = null
        
        fun getInstance(): EdgeAgentAccessibilityService? = instance
        
        internal fun setInstance(service: EdgeAgentAccessibilityService?) {
            instance = service
        }
    }

    init {
        setInstance(this)
    }
}
