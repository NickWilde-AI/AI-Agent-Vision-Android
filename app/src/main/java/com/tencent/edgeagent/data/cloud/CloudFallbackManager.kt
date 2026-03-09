package com.tencent.edgeagent.data.cloud

import android.graphics.Bitmap
import com.tencent.edgeagent.domain.model.AgentResponse
import timber.log.Timber

/**
 * 云端兜底管理器
 * 
 * 职责：
 * 1. 管理多个云端 API 客户端
 * 2. 自动切换和重试
 * 3. 统一的错误处理
 * 4. 性能监控
 */
class CloudFallbackManager private constructor() {

    private var primaryClient: ICloudClient? = null
    private var fallbackClients: List<ICloudClient> = emptyList()
    
    private var isEnabled = false
    
    /**
     * 初始化云端客户端
     */
    fun initialize(apiKey: String, provider: CloudProvider = CloudProvider.DEEPSEEK) {
        try {
            primaryClient = when (provider) {
                CloudProvider.DEEPSEEK -> DeepSeekClient(apiKey)
                CloudProvider.ALIYUN -> AliyunClient(apiKey)
                CloudProvider.DOUBAO -> {
                    Timber.w("豆包客户端暂未实现，使用 DeepSeek")
                    DeepSeekClient(apiKey)
                }
            }
            
            isEnabled = true
            Timber.i("云端客户端初始化成功: ${primaryClient?.getProviderInfo()?.name}")
            
        } catch (e: Exception) {
            Timber.e(e, "云端客户端初始化失败")
            isEnabled = false
        }
    }
    
    /**
     * 调用云端推理
     */
    suspend fun inference(
        image: Bitmap,
        prompt: String,
        uiTree: String? = null
    ): AgentResponse {
        if (!isEnabled || primaryClient == null) {
            throw CloudApiException.ServerError("云端服务未启用或未初始化")
        }
        
        Timber.d("开始云端推理: prompt='$prompt'")
        
        // 尝试主客户端
        try {
            val response = primaryClient!!.inference(image, prompt, uiTree)
            Timber.d("云端推理成功: action=${response.action}")
            return response
            
        } catch (e: CloudApiException) {
            Timber.e(e, "主客户端调用失败")
            
            // 尝试备用客户端
            for (fallbackClient in fallbackClients) {
                try {
                    Timber.d("尝试备用客户端: ${fallbackClient.getProviderInfo().name}")
                    val response = fallbackClient.inference(image, prompt, uiTree)
                    Timber.d("备用客户端调用成功")
                    return response
                    
                } catch (fallbackException: CloudApiException) {
                    Timber.e(fallbackException, "备用客户端也失败")
                    continue
                }
            }
            
            // 所有客户端都失败
            throw e
        }
    }
    
    /**
     * 检查云端服务是否可用
     */
    suspend fun checkAvailability(): Boolean {
        return try {
            primaryClient?.checkAvailability() ?: false
        } catch (e: Exception) {
            Timber.e(e, "检查云端服务可用性失败")
            false
        }
    }
    
    /**
     * 获取当前提供商信息
     */
    fun getProviderInfo(): CloudProviderInfo? {
        return primaryClient?.getProviderInfo()
    }
    
    /**
     * 是否已启用
     */
    fun isEnabled(): Boolean = isEnabled
    
    /**
     * 禁用云端服务
     */
    fun disable() {
        isEnabled = false
        Timber.i("云端服务已禁用")
    }
    
    /**
     * 启用云端服务
     */
    fun enable() {
        if (primaryClient != null) {
            isEnabled = true
            Timber.i("云端服务已启用")
        } else {
            Timber.w("无法启用云端服务：未初始化")
        }
    }

    companion object {
        @Volatile
        private var instance: CloudFallbackManager? = null
        
        fun getInstance(): CloudFallbackManager {
            return instance ?: synchronized(this) {
                instance ?: CloudFallbackManager().also { instance = it }
            }
        }
    }
}

/**
 * 云端服务提供商
 */
enum class CloudProvider {
    DEEPSEEK,   // DeepSeek API
    ALIYUN,     // 阿里云百炼
    DOUBAO      // 豆包
}
