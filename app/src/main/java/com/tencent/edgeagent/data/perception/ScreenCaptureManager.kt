package com.tencent.edgeagent.data.perception

import android.graphics.Bitmap
import com.tencent.edgeagent.service.ScreenCaptureService
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.resume

/**
 * 屏幕截图管理器（带 Bitmap 复用池）
 * 
 * 职责：
 * 1. 管理 Bitmap 对象池，避免频繁 GC
 * 2. 提供线程安全的 Bitmap 获取和回收
 * 3. 提供真实的屏幕截图功能
 * 4. 内存优化
 */
class ScreenCaptureManager private constructor() {

    // Bitmap 对象池
    private val bitmapPool = ConcurrentLinkedQueue<Bitmap>()
    
    // 池大小限制
    private val maxPoolSize = 3
    
    // 最后一次截图的缓存
    private var lastScreenshot: Bitmap? = null
    
    /**
     * 捕获屏幕截图（异步）
     * 
     * @return 屏幕截图 Bitmap，如果失败返回空白 Bitmap
     */
    suspend fun captureScreen(): Bitmap = suspendCancellableCoroutine { continuation ->
        val service = ScreenCaptureService.getInstance()
        
        if (service == null) {
            Timber.w("ScreenCaptureService 未启动，返回空白 Bitmap")
            val emptyBitmap = createEmptyBitmap()
            continuation.resume(emptyBitmap)
            return@suspendCancellableCoroutine
        }
        
        // 请求截图
        ScreenCaptureService.captureScreen { bitmap ->
            // 缓存最后一次截图
            lastScreenshot?.recycle()
            lastScreenshot = bitmap
            
            continuation.resume(bitmap)
        }
    }
    
    /**
     * 获取最后一次截图（同步）
     * 
     * @return 最后一次截图，如果没有则返回空白 Bitmap
     */
    fun getLastScreenshot(): Bitmap {
        return lastScreenshot ?: createEmptyBitmap()
    }
    
    /**
     * 创建空白 Bitmap（兜底）
     */
    private fun createEmptyBitmap(): Bitmap {
        // 使用常见的屏幕尺寸
        return Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
    }
    
    /**
     * 从池中获取 Bitmap，如果池为空则创建新的
     */
    fun obtainBitmap(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap {
        // 尝试从池中获取
        val pooledBitmap = bitmapPool.poll()
        
        return if (pooledBitmap != null && 
                   pooledBitmap.width == width && 
                   pooledBitmap.height == height &&
                   pooledBitmap.config == config) {
            Timber.d("从池中复用 Bitmap: ${width}x${height}")
            pooledBitmap
        } else {
            // 如果池中的 Bitmap 不符合要求，回收它
            pooledBitmap?.recycle()
            
            // 创建新的 Bitmap
            Timber.d("创建新 Bitmap: ${width}x${height}")
            Bitmap.createBitmap(width, height, config)
        }
    }
    
    /**
     * 回收 Bitmap 到池中
     */
    fun recycleBitmap(bitmap: Bitmap?) {
        if (bitmap == null || bitmap.isRecycled) {
            return
        }
        
        // 如果池未满，放入池中
        if (bitmapPool.size < maxPoolSize) {
            bitmapPool.offer(bitmap)
            Timber.d("Bitmap 回收到池中，当前池大小: ${bitmapPool.size}")
        } else {
            // 池已满，直接回收
            bitmap.recycle()
            Timber.d("池已满，直接回收 Bitmap")
        }
    }
    
    /**
     * 清空池
     */
    fun clearPool() {
        Timber.d("清空 Bitmap 池")
        while (bitmapPool.isNotEmpty()) {
            bitmapPool.poll()?.recycle()
        }
        
        // 清空最后一次截图
        lastScreenshot?.recycle()
        lastScreenshot = null
    }
    
    /**
     * 获取池状态
     */
    fun getPoolStatus(): String {
        return "Bitmap 池大小: ${bitmapPool.size}/$maxPoolSize"
    }
    
    /**
     * 检查截图服务是否可用
     */
    fun isScreenCaptureAvailable(): Boolean {
        return ScreenCaptureService.getInstance() != null
    }

    companion object {
        @Volatile
        private var instance: ScreenCaptureManager? = null
        
        fun getInstance(): ScreenCaptureManager {
            return instance ?: synchronized(this) {
                instance ?: ScreenCaptureManager().also { instance = it }
            }
        }
    }
}
