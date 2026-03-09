package com.tencent.edgeagent.domain.model

/**
 * Agent 生命周期状态定义
 * 
 * 状态转换流程：
 * IDLE → PERCEIVING → REASONING_LOCAL → EXECUTING → COMPLETED → IDLE
 *                          ↓ (低置信度)
 *                     REASONING_CLOUD → EXECUTING → COMPLETED → IDLE
 */
enum class AgentState {
    /**
     * 空闲状态，等待用户触发
     */
    IDLE,

    /**
     * 感知中：正在捕获屏幕截图和 UI 树
     */
    PERCEIVING,

    /**
     * 本地推理中：使用端侧 VLM 模型（如 Qwen 3.5）进行推理
     */
    REASONING_LOCAL,

    /**
     * 云端推理中：调用云端大模型（DeepSeek/阿里云）兜底
     */
    REASONING_CLOUD,

    /**
     * 执行中：通过 AccessibilityService 执行操作
     */
    EXECUTING,

    /**
     * 错误状态
     */
    ERROR,

    /**
     * 任务完成
     */
    COMPLETED
}

/**
 * Agent 状态事件（用于驱动状态机转换）
 */
sealed class AgentEvent {
    /**
     * 用户触发事件（语音/文本/手势）
     */
    data class UserTriggered(val userInput: String) : AgentEvent()

    /**
     * 感知完成事件
     */
    data class PerceptionComplete(
        val screenData: ScreenData
    ) : AgentEvent()

    /**
     * 本地推理完成事件
     */
    data class LocalReasoningComplete(
        val response: AgentResponse
    ) : AgentEvent()

    /**
     * 云端推理完成事件
     */
    data class CloudReasoningComplete(
        val response: AgentResponse
    ) : AgentEvent()

    /**
     * 执行完成事件
     */
    object ExecutionComplete : AgentEvent()

    /**
     * 错误事件
     */
    data class Error(val throwable: Throwable, val message: String) : AgentEvent()

    /**
     * 重置事件（回到 IDLE）
     */
    object Reset : AgentEvent()
}
