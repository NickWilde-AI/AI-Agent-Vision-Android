package com.tencent.edgeagent.domain.agent.strategy

import com.tencent.edgeagent.domain.agent.ConversationTurn
import com.tencent.edgeagent.domain.agent.multi.AgentPlan
import com.tencent.edgeagent.domain.agent.multi.TaskType
import com.tencent.edgeagent.domain.model.ActionParams
import com.tencent.edgeagent.domain.model.ActionType
import com.tencent.edgeagent.domain.model.AgentResponse
import com.tencent.edgeagent.domain.model.InferenceSource
import com.tencent.edgeagent.domain.model.ScreenData

/**
 * Ensures OPEN_APP tasks behave like a real phone Agent:
 * prefer system-level app launching instead of clicking VisionAgent's own UI.
 */
class OpenAppStrategy : AppStrategy {
    override val id: String = "open_app"

    override fun matches(plan: AgentPlan, screenData: ScreenData): Boolean {
        return plan.taskType == TaskType.OPEN_APP &&
            !plan.targetPackage.isNullOrBlank() &&
            screenData.currentPackage == VISION_AGENT_PACKAGE
    }

    override fun promptHints(
        plan: AgentPlan,
        screenData: ScreenData,
        history: List<ConversationTurn>
    ): List<String> {
        val target = plan.targetPackage ?: ""
        return listOf(
            "当任务类型为 OPEN_APP 且当前仍在 VisionAgent 内时，优先返回 OPEN_APP(package=$target)，不要点击本 App 的按钮或输入框。",
            "如果 OPEN_APP 已执行但目标 App 未切换，可 WAIT 一轮后再观察，不要重复点击输入框。"
        )
    }

    override fun rewriteResponse(
        plan: AgentPlan,
        screenData: ScreenData,
        history: List<ConversationTurn>,
        response: AgentResponse
    ): StrategyRewrite {
        val targetPackage = plan.targetPackage ?: return StrategyRewrite.Keep(response)

        if (response.action == ActionType.OPEN_APP) return StrategyRewrite.Keep(response)

        // If model is clicking VisionAgent UI for OPEN_APP task, rewrite to OPEN_APP.
        if (response.action == ActionType.CLICK ||
            response.action == ActionType.LONG_CLICK ||
            response.action == ActionType.INPUT_TEXT) {
            val rewritten = AgentResponse(
                source = InferenceSource.STRATEGY,
                action = ActionType.OPEN_APP,
                actionParams = ActionParams.OpenApp(packageName = targetPackage),
                confidence = 1.0f,
                inferenceTimeMs = 0L,
                rawOutput = "strategy_rewrite_open_app:$targetPackage",
                requiresConfirmation = false
            )
            return StrategyRewrite.Rewritten(rewritten, "OPEN_APP 任务不点击 VisionAgent UI，改为系统级 OPEN_APP($targetPackage)")
        }

        return StrategyRewrite.Keep(response)
    }

    companion object {
        private const val VISION_AGENT_PACKAGE = "com.tencent.edgeagent"
    }
}
