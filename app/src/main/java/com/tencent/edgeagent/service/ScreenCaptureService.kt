package com.tencent.edgeagent.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.tencent.edgeagent.R
import timber.log.Timber
import java.nio.ByteBuffer

/**
 * 屏幕截图前台服务
 * 
 * 职责：
 * 1. 管理 MediaProjection 生命周期
 * 2. 提供屏幕截图功能
 * 3. 维持前台服务状态
 */
class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0
    
    private var captureCallback: ((Bitmap) -> Unit)? = null

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "screen_capture_channel"
        
        const val ACTION_START = "com.tencent.edgeagent.ACTION_START_CAPTURE"
        const val ACTION_STOP = "com.tencent.edgeagent.ACTION_STOP_CAPTURE"
        const val ACTION_CAPTURE = "com.tencent.edgeagent.ACTION_CAPTURE_SCREEN"
        
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        
        @Volatile
        private var instance: ScreenCaptureService? = null
        
        fun getInstance(): ScreenCaptureService? = instance
        
        /**
         * 启动截图服务
         */
        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        /**
         * 停止截图服务
         */
        fun stop(context: Context) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
        
        /**
         * 请求截图
         */
        fun captureScreen(callback: (Bitmap) -> Unit) {
            instance?.captureCallback = callback
            instance?.captureScreenInternal()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Timber.d("ScreenCaptureService 创建")
        
        // 获取屏幕参数
        val displayMetrics = resources.displayMetrics
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
        screenDensity = displayMetrics.densityDpi
        
        Timber.d("屏幕参数: ${screenWidth}x${screenHeight}, density=$screenDensity")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                @Suppress("DEPRECATION")
                val data = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    intent.getParcelableExtra(EXTRA_DATA)
                }
                
                if (resultCode != -1 && data != null) {
                    startForeground(NOTIFICATION_ID, createNotification())
                    startMediaProjection(resultCode, data)
                } else {
                    Timber.e("启动参数无效")
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                stopMediaProjection()
                stopSelf()
            }
            ACTION_CAPTURE -> {
                captureScreenInternal()
            }
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopMediaProjection()
        instance = null
        Timber.d("ScreenCaptureService 销毁")
    }

    /**
     * 创建通知
     */
    private fun createNotification(): Notification {
        // 创建通知渠道（Android 8.0+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "屏幕截图服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "VisionAgent 正在捕获屏幕内容"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
        
        // 创建通知
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VisionAgent 运行中")
            .setContentText("正在捕获屏幕内容")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    /**
     * 启动 MediaProjection
     */
    private fun startMediaProjection(resultCode: Int, data: Intent) {
        try {
            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
            
            if (mediaProjection == null) {
                Timber.e("MediaProjection 创建失败")
                stopSelf()
                return
            }
            
            // 创建 ImageReader
            imageReader = ImageReader.newInstance(
                screenWidth,
                screenHeight,
                PixelFormat.RGBA_8888,
                2
            )
            
            // 创建 VirtualDisplay
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                null
            )
            
            Timber.d("MediaProjection 启动成功")
            
        } catch (e: Exception) {
            Timber.e(e, "启动 MediaProjection 失败")
            stopSelf()
        }
    }

    /**
     * 停止 MediaProjection
     */
    private fun stopMediaProjection() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            
            imageReader?.close()
            imageReader = null
            
            mediaProjection?.stop()
            mediaProjection = null
            
            Timber.d("MediaProjection 已停止")
        } catch (e: Exception) {
            Timber.e(e, "停止 MediaProjection 失败")
        }
    }

    /**
     * 执行截图
     */
    private fun captureScreenInternal() {
        try {
            val reader = imageReader
            if (reader == null) {
                Timber.e("ImageReader 未初始化")
                captureCallback?.invoke(createEmptyBitmap())
                captureCallback = null
                return
            }
            
            // 获取最新的 Image
            val image = reader.acquireLatestImage()
            if (image == null) {
                Timber.e("无法获取 Image")
                captureCallback?.invoke(createEmptyBitmap())
                captureCallback = null
                return
            }
            
            // 转换为 Bitmap
            val bitmap = imageToBitmap(image)
            image.close()
            
            Timber.d("截图成功: ${bitmap.width}x${bitmap.height}")
            captureCallback?.invoke(bitmap)
            captureCallback = null
            
        } catch (e: Exception) {
            Timber.e(e, "截图失败")
            captureCallback?.invoke(createEmptyBitmap())
            captureCallback = null
        }
    }

    /**
     * 将 Image 转换为 Bitmap
     */
    private fun imageToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * screenWidth
        
        // 创建 Bitmap
        val bitmap = Bitmap.createBitmap(
            screenWidth + rowPadding / pixelStride,
            screenHeight,
            Bitmap.Config.ARGB_8888
        )
        
        bitmap.copyPixelsFromBuffer(buffer)
        
        // 如果有 padding，裁剪到正确的尺寸
        return if (rowPadding != 0) {
            Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
        } else {
            bitmap
        }
    }

    /**
     * 创建空白 Bitmap（兜底）
     */
    private fun createEmptyBitmap(): Bitmap {
        return Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
    }
}
