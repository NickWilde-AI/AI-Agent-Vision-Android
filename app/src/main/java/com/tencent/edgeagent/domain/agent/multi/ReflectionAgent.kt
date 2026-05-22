package com.tencent.edgeagent.domain.agent.multi

import com.tencent.edgeagent.data.execution.ExecutionResult
import com.tencent.edgeagent.domain.agent.ConversationTurn
import com.tencent.edgeagent.domain.model.ActionType
import com.tencent.edgeagent.domain.model.ScreenData
import timber.log.Timber

/**
 * Reflection Agent: inspects recent observations and tells the decision loop what to avoid.
 */
class ReflectionAgent private constructor() {

    fun reflect(
        history: List<ConversationTurn>,
        currentScreenData: ScreenData
    ): AgentReflection {
        val hints = mutableListOf<String>()
        var shouldAbort = false
        var abortReason: String? = null

        val recentFailures = history.takeLast(3).count { it.executionResult is ExecutionResult.Failure }
        if (recentFailures >= 2) {
            hints += "最近连续失败，下一步必须换路径：BACK、HOME、搜索入口或等待页面稳定。"
        }

        val repeatedAction = history.takeLast(2).map { it.llmResponse.action }.distinct().size == 1 &&
            history.size >= 2
        if (repeatedAction) {
            hints += "检测到重复动作，不要继续重复同一 action。"
        }

        val lastThreeWaits = history.takeLast(3).all { it.llmResponse.action == ActionType.WAIT } &&
            history.size >= 3
        if (lastThreeWaits) {
            hints += "已经连续 WAIT 三轮，必须 BACK 或使用系统导航恢复可观测状态。"
        }

        if (!currentScreenData.hasRealScreenshot && currentScreenData.uiTreeText.isNullOrBlank()) {
            hints += "当前没有真实截图且 UI 树为空，不能猜页面内容。"
        }

        if (history.size >= HARD_FAILURE_HISTORY && recentFailures >= HARD_FAILURE_THRESHOLD) {
            shouldAbort = true
            abortReason = "连续执行失败，停止任务避免误操作"
        }

        return AgentReflection(
            hints = hints,
            shouldAbort = shouldAbort,
            abortReason = abortReason
        ).also {
            if (it.hints.isNotEmpty()) Timber.i("[ReflectionAgent] hints=${it.hints}")
        }
    }

    companion object {
        private const val HARD_FAILURE_HISTORY = 5
        private const val HARD_FAILURE_THRESHOLD = 3

        @Volatile
        private var instance: ReflectionAgent? = null

        fun getInstance(): ReflectionAgent {
            return instance ?: synchronized(this) {
                instance ?: ReflectionAgent().also { instance = it }
            }
        }
    }
}

data class AgentReflection(
    val hints: List<String>,
    val shouldAbort: Boolean,
    val abortReason: String?
) {
    fun toPromptText(): String {
        if (hints.isEmpty()) return "【反思 Agent】暂无额外提示。\n"
        return buildString {
            append("【反思 Agent】\n")
            hints.forEach { append("- $it\n") }
        }
    }
}
