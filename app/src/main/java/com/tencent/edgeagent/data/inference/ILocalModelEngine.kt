package com.tencent.edgeagent.data.inference

import android.graphics.Bitmap
import com.tencent.edgeagent.domain.model.AgentResponse

/**
 * 本地模型引擎接口
 * 
 * 这是端侧 AI 推理的核心抽象接口，支持多种实现：
 * - MockModelEngine: Mock 实现（开发阶段）
 * - QwenVLMEngine: Qwen 3.5 多模态模型实现（生产环境）
 * - MediaPipeEngine: MediaPipe 实现
 * - MLCLLMEngine: MLC LLM 实现
 * 
 * 设计原则：
 * 1. 接口简洁，易于 Mock 和测试
 * 2. 支持异步推理（Kotlin Coroutines）
 * 3. 输入输出类型明确（Bitmap + String → AgentResponse）
 */
interface ILocalModelEngine {

    /**
     * 执行推理
     * 
     * @param image 屏幕截图
     * @param prompt 用户输入或系统提示词
     * @param uiTree UI 树文本表示（可选，用于辅助定位）
     * @return Agent 响应（包含执行动作和置信度）
     */
    suspend fun inference(
        image: Bitmap,
        prompt: String,
        uiTree: String? = null
    ): AgentResponse

    /**
     * 预热模型（首次加载到内存）
     * 
     * 在应用启动或用户首次使用时调用，避免首次推理耗时过长
     */
    suspend fun warmUp()

    /**
     * 释放模型资源
     * 
     * 在应用退出或内存不足时调用
     */
    fun release()

    /**
     * 检查模型是否已加载
     */
    fun isModelLoaded(): Boolean

    /**
     * 获取模型信息
     */
    fun getModelInfo(): ModelInfo
}

/**
 * 模型信息
 */
data class ModelInfo(
    /**
     * 模型名称
     */
    val name: String,

    /**
     * 模型版本
     */
    val version: String,

    /**
     * 模型大小（MB）
     */
    val sizeInMB: Float,

    /**
     * 是否支持多模态
     */
    val supportsMultimodal: Boolean,

    /**
     * 平均推理耗时（毫秒）
     */
    val avgInferenceTimeMs: Long
)
