package com.tencent.edgeagent.domain.agent.safety

import com.tencent.edgeagent.domain.agent.multi.AgentPlan
import com.tencent.edgeagent.domain.agent.multi.SafetyMode
import com.tencent.edgeagent.domain.agent.multi.TaskType
import com.tencent.edgeagent.domain.model.ActionParams
import com.tencent.edgeagent.domain.model.ActionType
import com.tencent.edgeagent.domain.model.AgentResponse
import com.tencent.edgeagent.domain.model.InferenceSource
import timber.log.Timber

/**
 * Safety gate for model-generated actions.
 *
 * It prevents high-risk final actions from being executed automatically.
 */
class ActionGuard private constructor() {

    fun guard(
        plan: AgentPlan,
        response: AgentResponse,
        currentPackage: String?,
        uiTreeText: String?
    ): GuardResult {
        if (isBlockedFinalAction(plan, response, currentPackage, uiTreeText)) {
            val safeResponse = AgentResponse(
                source = InferenceSource.LOCAL_RAG,
                action = ActionType.NO_ACTION,
                actionParams = ActionParams.NoAction("已阻止高风险最终动作，等待用户手动确认"),
                confidence = 1.0f,
                inferenceTimeMs = 0L,
                rawOutput = "action_guard_blocked:${response.action}:${response.actionParams}",
                requiresConfirmation = true
            )
            Timber.w("[ActionGuard] blocked action=${response.action} params=${response.actionParams}")
            return GuardResult.Blocked(safeResponse, "高风险动作需要用户确认")
        }

        return GuardResult.Allowed(response)
    }

    private fun isBlockedFinalAction(
        plan: AgentPlan,
        response: AgentResponse,
        currentPackage: String?,
        uiTreeText: String?
    ): Boolean {
        if (response.action != ActionType.CLICK) return false

        val click = response.actionParams as? ActionParams.Click ?: return false
        val description = click.description.lowercase()
        val highRiskKeyword = HIGH_RISK_KEYWORDS.any { keyword -> description.contains(keyword) } ||
            isClickInsideHighRiskNode(click.x, click.y, uiTreeText)

        if (!highRiskKeyword) return false

        if (plan.safetyMode == SafetyMode.REQUIRE_CONFIRMATION || plan.safetyMode == SafetyMode.DRAFT_ONLY) {
            return true
        }

        if (plan.taskType == TaskType.WECHAT_DRAFT && currentPackage == "com.tencent.mm") {
            return true
        }

        return false
    }

    private fun isClickInsideHighRiskNode(x: Int, y: Int, uiTreeText: String?): Boolean {
        if (uiTreeText.isNullOrBlank()) return false
        return uiTreeText.lineSequence()
            .filter { line -> HIGH_RISK_KEYWORDS.any { keyword -> line.contains(keyword, ignoreCase = true) } }
            .any { line ->
                val bounds = BOUNDS_REGEX.find(line)?.groupValues ?: return@any false
                val left = bounds[1].toIntOrNull() ?: return@any false
                val top = bounds[2].toIntOrNull() ?: return@any false
                val right = bounds[3].toIntOrNull() ?: return@any false
                val bottom = bounds[4].toIntOrNull() ?: return@any false
                x in left..right && y in top..bottom
            }
    }

    companion object {
        private val HIGH_RISK_KEYWORDS = listOf(
            "发送", "send", "支付", "付款", "下单", "提交", "确认", "删除", "转账", "购买"
        )
        private val BOUNDS_REGEX = Regex("bounds=\\[(\\d+),(\\d+),(\\d+),(\\d+)]")

        @Volatile
        private var instance: ActionGuard? = null

        fun getInstance(): ActionGuard {
            return instance ?: synchronized(this) {
                instance ?: ActionGuard().also { instance = it }
            }
        }
    }
}

sealed class GuardResult {
    data class Allowed(val response: AgentResponse) : GuardResult()
    data class Blocked(val safeResponse: AgentResponse, val reason: String) : GuardResult()
}
