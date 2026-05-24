package com.tencent.edgeagent.domain.agent.strategy

import com.tencent.edgeagent.domain.agent.ConversationTurn
import com.tencent.edgeagent.domain.agent.multi.AgentPlan
import com.tencent.edgeagent.domain.agent.multi.TaskType
import com.tencent.edgeagent.domain.model.ActionParams
import com.tencent.edgeagent.domain.model.ActionType
import com.tencent.edgeagent.domain.model.AgentResponse
import com.tencent.edgeagent.domain.model.InferenceSource
import com.tencent.edgeagent.domain.model.ScreenData

class SystemNavigationStrategy : AppStrategy {
    override val id: String = "system_navigation"

    override fun matches(plan: AgentPlan, screenData: ScreenData): Boolean {
        return plan.taskType == TaskType.SYSTEM_NAVIGATION
    }

    override fun promptHints(
        plan: AgentPlan,
        screenData: ScreenData,
        history: List<ConversationTurn>
    ): List<String> {
        val targetAction = resolveNavigationAction(plan.goal)?.name ?: "UNKNOWN"
        return listOf(
            "当前是系统导航任务，优先直接返回 $targetAction，不要点击当前 App 的输入框或按钮。",
            "关闭键盘/收起键盘等价于 BACK，由执行层通过无障碍返回键完成。"
        )
    }

    override fun rewriteResponse(
        plan: AgentPlan,
        screenData: ScreenData,
        history: List<ConversationTurn>,
        response: AgentResponse
    ): StrategyRewrite {
        val targetAction = resolveNavigationAction(plan.goal) ?: return StrategyRewrite.Keep(response)
        if (response.action == targetAction) return StrategyRewrite.Keep(response)

        val rewritten = AgentResponse(
            source = InferenceSource.STRATEGY,
            action = targetAction,
            actionParams = ActionParams.NoAction(messageFor(targetAction, plan.goal)),
            confidence = 1.0f,
            inferenceTimeMs = 0L,
            rawOutput = "strategy_rewrite_system_navigation:${targetAction.name}",
            requiresConfirmation = false
        )
        return StrategyRewrite.Rewritten(rewritten, "L1 系统导航改为 ${targetAction.name}")
    }

    private fun resolveNavigationAction(goal: String): ActionType? {
        val normalized = goal.lowercase()
        return when {
            goal == "返回" ||
                goal.contains("返回上一页") ||
                goal.contains("后退") ||
                goal.contains("关闭键盘") ||
                goal.contains("收起键盘") ||
                normalized == "back" -> ActionType.BACK
            goal.contains("回到桌面") ||
                goal.contains("回桌面") ||
                goal.contains("主屏幕") ||
                normalized == "home" -> ActionType.HOME
            goal.contains("最近任务") ||
                goal.contains("多任务") ||
                goal.contains("后台应用") ||
                normalized.contains("recents") -> ActionType.RECENTS
            else -> null
        }
    }

    private fun messageFor(action: ActionType, goal: String): String {
        return when (action) {
            ActionType.BACK -> if (goal.contains("键盘")) "收起键盘" else "返回上一页"
            ActionType.HOME -> "回到主屏幕"
            ActionType.RECENTS -> "打开最近任务"
            else -> goal
        }
    }
}

