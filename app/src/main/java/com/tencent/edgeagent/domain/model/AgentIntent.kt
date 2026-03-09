package com.tencent.edgeagent.domain.model

/**
 * Agent 意图类型定义
 * 用于分类用户请求的类型，决定后续处理流程
 */
enum class IntentType {
    /**
     * 设备控制类（音量、亮度、WiFi 等）
     * 特点：100% 本地处理，不上云
     */
    DEVICE_CONTROL,

    /**
     * 应用操作类（打开 App、点击按钮、滑动等）
     * 特点：优先本地 VLM 识别，复杂场景云端兜底
     */
    APP_OPERATION,

    /**
     * 信息查询类（天气、新闻、知识问答）
     * 特点：优先本地 RAG 检索，未命中则云端
     */
    INFORMATION_QUERY,

    /**
     * 文本输入类（发短信、写邮件、填表单）
     * 特点：本地执行，敏感内容不上云
     */
    TEXT_INPUT,

    /**
     * 复杂推理类（多步骤任务、长文本理解）
     * 特点：直接调用云端大模型
     */
    COMPLEX_REASONING,

    /**
     * 未知意图
     */
    UNKNOWN
}

/**
 * Agent 意图模型
 */
data class AgentIntent(
    /**
     * 意图类型
     */
    val type: IntentType,

    /**
     * 用户原始输入
     */
    val userInput: String,

    /**
     * 提取的关键参数（如 App 名称、目标文本等）
     */
    val parameters: Map<String, String> = emptyMap(),

    /**
     * 是否需要屏幕上下文
     */
    val requiresScreenContext: Boolean = true,

    /**
     * 是否允许云端处理
     */
    val allowCloudFallback: Boolean = true,

    /**
     * 优先级（1-10，10 最高）
     */
    val priority: Int = 5
)
