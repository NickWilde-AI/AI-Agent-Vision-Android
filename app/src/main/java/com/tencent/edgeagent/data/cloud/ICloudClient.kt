package com.tencent.edgeagent.data.cloud

import android.graphics.Bitmap
import com.tencent.edgeagent.domain.model.AgentResponse

/**
 * 云端 API 客户端接口
 * 
 * 支持多种云端大模型 API：
 * - DeepSeek API
 * - 阿里云百炼 API
 * - 豆包 API
 * 
 * 设计原则：
 * 1. 统一接口，易于切换不同的云端服务
 * 2. 支持多模态输入（图片 + 文本）
 * 3. 异步调用，不阻塞主线程
 * 4. 完善的错误处理和重试机制
 */
interface ICloudClient {

    /**
     * 调用云端 API 进行推理
     * 
     * @param image 屏幕截图
     * @param prompt 用户输入或系统提示词
     * @param uiTree UI 树文本（可选，帮助 AI 理解屏幕结构）
     * @return Agent 响应
     */
    suspend fun inference(
        image: Bitmap,
        prompt: String,
        uiTree: String? = null
    ): AgentResponse

    /**
     * 检查 API 是否可用
     */
    suspend fun checkAvailability(): Boolean

    /**
     * 获取 API 提供商信息
     */
    fun getProviderInfo(): CloudProviderInfo
}

/**
 * 云端服务提供商信息
 */
data class CloudProviderInfo(
    /**
     * 提供商名称
     */
    val name: String,

    /**
     * 模型名称
     */
    val modelName: String,

    /**
     * 是否支持多模态（图片 + 文本）
     */
    val supportsMultimodal: Boolean,

    /**
     * 最大上下文长度
     */
    val maxContextLength: Int,

    /**
     * API 端点
     */
    val endpoint: String
)

/**
 * 云端 API 异常
 */
sealed class CloudApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /**
     * 网络错误
     */
    class NetworkError(message: String, cause: Throwable? = null) : CloudApiException(message, cause)

    /**
     * API 密钥无效
     */
    class InvalidApiKey(message: String) : CloudApiException(message)

    /**
     * 请求超时
     */
    class Timeout(message: String) : CloudApiException(message)

    /**
     * 速率限制
     */
    class RateLimitExceeded(message: String) : CloudApiException(message)

    /**
     * 服务器错误
     */
    class ServerError(message: String, cause: Throwable? = null) : CloudApiException(message, cause)

    /**
     * 响应解析错误
     */
    class ParseError(message: String, cause: Throwable? = null) : CloudApiException(message, cause)
}
