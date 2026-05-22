package com.tencent.edgeagent.domain.agent.multi

/**
 * High-level task plan produced before the step loop starts.
 */
data class AgentPlan(
    val goal: String,
    val taskType: TaskType,
    val targetPackage: String?,
    val safetyMode: SafetyMode,
    val maxRounds: Int,
    val localKnowledge: String,
    val constraints: List<String>
) {
    fun toPromptText(): String {
        return buildString {
            append("【多 Agent 规划】\n")
            append("任务类型: $taskType\n")
            append("目标包名: ${targetPackage ?: "未知"}\n")
            append("安全模式: $safetyMode\n")
            append("最大轮数: $maxRounds\n")
            if (constraints.isNotEmpty()) {
                append("约束:\n")
                constraints.forEach { append("- $it\n") }
            }
            append(localKnowledge)
            append("\n")
        }
    }
}

enum class TaskType {
    OPEN_APP,
    DEVICE_CONTROL,
    WECHAT_DRAFT,
    BROWSER_SEARCH,
    APP_NAVIGATION,
    GENERAL
}

enum class SafetyMode {
    AUTO,
    REQUIRE_CONFIRMATION,
    DRAFT_ONLY
}
