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
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import com.tencent.edgeagent.R
import timber.log.Timber
import java.nio.ByteBuffer

/**
 * 屏幕录制前台服务
 * 
 * 职责：
 * 1. 管理 MediaProjection 生命周期
 * 2. 通过录制帧提供当前屏幕图像
 * 3. 维持前台服务状态
 */
class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReaderThread: HandlerThread? = null
    private var imageReaderHandler: Handler? = null
    private var mediaProjectionCallback: MediaProjection.Callback? = null
    
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0
    
    private var captureCallback: ((Bitmap) -> Unit)? = null
    private val frameLock = Any()
    private var latestFrame: Bitmap? = null

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
         * 启动屏幕录制服务
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
         * 请求当前屏幕帧
         */
        fun captureScreen(callback: (Bitmap) -> Unit) {
            val service = instance
            if (service == null) {
                callback(Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888))
                return
            }

            service.captureCallback = callback
            service.captureScreenInternal()
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
        if (intent?.action == ACTION_START) {
            promoteToForeground()
        }

        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                @Suppress("DEPRECATION")
                val data = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    intent.getParcelableExtra(EXTRA_DATA)
                }
                
                Timber.d("收到屏幕录制授权参数: resultCode=$resultCode, data=${data != null}")

                if (data != null) {
                    startMediaProjection(resultCode, data)
                } else {
                    Timber.e("启动参数无效：MediaProjection data 为空")
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
     * 立即提升为前台服务，避免 Android 判定 startForegroundService 后未及时调用 startForeground。
     */
    private fun promoteToForeground() {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        Timber.d("ScreenCaptureService 已进入前台")
    }

    /**
     * 创建通知
     */
    private fun createNotification(): Notification {
        // 创建通知渠道（Android 8.0+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "屏幕录制服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "VisionAgent 正在录制屏幕以分析当前界面"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
        
        // 创建通知
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VisionAgent 运行中")
            .setContentText("正在录制屏幕以分析当前界面")
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
            stopMediaProjection()

            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
            
            if (mediaProjection == null) {
                Timber.e("MediaProjection 创建失败")
                stopSelf()
                return
            }

            imageReaderThread = HandlerThread("VisionAgentScreenCapture").apply { start() }
            imageReaderHandler = Handler(imageReaderThread!!.looper)
            
            // 创建 ImageReader
            imageReader = ImageReader.newInstance(
                screenWidth,
                screenHeight,
                PixelFormat.RGBA_8888,
                2
            ).apply {
                setOnImageAvailableListener(
                    { reader -> cacheLatestFrame(reader) },
                    imageReaderHandler
                )
            }

            mediaProjectionCallback = object : MediaProjection.Callback() {
                override fun onStop() {
                    Timber.w("MediaProjection 已被系统停止")
                    releaseCaptureResources(stopProjection = false)
                }
            }
            mediaProjection?.registerCallback(mediaProjectionCallback!!, imageReaderHandler)
            
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
            releaseCaptureResources(stopProjection = true)
            Timber.d("MediaProjection 已停止")
        } catch (e: Exception) {
            Timber.e(e, "停止 MediaProjection 失败")
        }
    }

    private fun releaseCaptureResources(stopProjection: Boolean) {
        virtualDisplay?.release()
        virtualDisplay = null

        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null

        mediaProjectionCallback?.let { callback ->
            try {
                mediaProjection?.unregisterCallback(callback)
            } catch (e: Exception) {
                Timber.w(e, "注销 MediaProjection callback 失败")
            }
        }
        mediaProjectionCallback = null

        if (stopProjection) {
            mediaProjection?.stop()
        }
        mediaProjection = null

        imageReaderThread?.quitSafely()
        imageReaderThread = null
        imageReaderHandler = null

        synchronized(frameLock) {
            latestFrame?.recycle()
            latestFrame = null
        }
    }

    /**
     * 执行截图
     */
    private fun captureScreenInternal() {
        try {
            val bitmap = synchronized(frameLock) {
                latestFrame?.copy(Bitmap.Config.ARGB_8888, false)
            } ?: drainLatestFrameOnce() ?: createEmptyBitmap()

            Timber.d("返回截图: ${bitmap.width}x${bitmap.height}, cached=${latestFrame != null}")
            captureCallback?.invoke(bitmap)
            captureCallback = null
            
        } catch (e: Exception) {
            Timber.e(e, "截图失败")
            captureCallback?.invoke(createEmptyBitmap())
            captureCallback = null
        }
    }

    private fun cacheLatestFrame(reader: ImageReader) {
        val image = try {
            reader.acquireLatestImage()
        } catch (e: Exception) {
            Timber.e(e, "读取最新屏幕帧失败")
            null
        } ?: return

        try {
            val bitmap = imageToBitmap(image)
            synchronized(frameLock) {
                latestFrame?.recycle()
                latestFrame = bitmap
            }
        } catch (e: Exception) {
            Timber.e(e, "缓存屏幕帧失败")
        } finally {
            image.close()
        }
    }

    private fun drainLatestFrameOnce(): Bitmap? {
        val reader = imageReader ?: return null
        val image = try {
            reader.acquireLatestImage()
        } catch (e: Exception) {
            Timber.e(e, "主动读取屏幕帧失败")
            null
        } ?: return null

        return try {
            imageToBitmap(image).also { bitmap ->
                synchronized(frameLock) {
                    latestFrame?.recycle()
                    latestFrame = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "主动转换屏幕帧失败")
            null
        } finally {
            image.close()
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
            Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight).also {
                bitmap.recycle()
            }
        } else {
            bitmap
        }
    }

    /**
     * 创建空白 Bitmap（兜底）
     */
    private fun createEmptyBitmap(): Bitmap {
        val width = screenWidth.takeIf { it > 0 } ?: 1080
        val height = screenHeight.takeIf { it > 0 } ?: 2400
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }
}
