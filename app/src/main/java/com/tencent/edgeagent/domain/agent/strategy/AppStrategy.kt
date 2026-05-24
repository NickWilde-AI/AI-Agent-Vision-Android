package com.tencent.edgeagent.domain.agent.strategy

import com.tencent.edgeagent.domain.agent.ConversationTurn
import com.tencent.edgeagent.domain.agent.multi.AgentPlan
import com.tencent.edgeagent.domain.model.AgentResponse
import com.tencent.edgeagent.domain.model.ScreenData

/**
 * App-specific policy layer.
 *
 * Strategies do not replace the generic Agent loop. They add prompt hints and
 * can rewrite unsafe decisions for apps with deep navigation or strict safety
 * requirements.
 */
interface AppStrategy {
    val id: String

    fun matches(plan: AgentPlan, screenData: ScreenData): Boolean

    fun promptHints(
        plan: AgentPlan,
        screenData: ScreenData,
        history: List<ConversationTurn>
    ): List<String> = emptyList()

    fun rewriteResponse(
        plan: AgentPlan,
        screenData: ScreenData,
        history: List<ConversationTurn>,
        response: AgentResponse
    ): StrategyRewrite = StrategyRewrite.Keep(response)
}

sealed class StrategyRewrite {
    data class Keep(val response: AgentResponse) : StrategyRewrite()
    data class Rewritten(val response: AgentResponse, val reason: String) : StrategyRewrite()
}
