package com.tencent.edgeagent.domain.agent.strategy

import com.tencent.edgeagent.domain.agent.ConversationTurn
import com.tencent.edgeagent.domain.agent.multi.AgentPlan
import com.tencent.edgeagent.domain.model.AgentResponse
import com.tencent.edgeagent.domain.model.ScreenData
import timber.log.Timber

class AppStrategyRegistry private constructor(
    private val strategies: List<AppStrategy>
) {

    fun promptHints(
        plan: AgentPlan,
        screenData: ScreenData,
        history: List<ConversationTurn>
    ): List<String> {
        return strategies
            .filter { it.matches(plan, screenData) }
            .flatMap { strategy ->
                strategy.promptHints(plan, screenData, history)
                    .map { hint -> "[${strategy.id}] $hint" }
            }
    }

    fun rewriteResponse(
        plan: AgentPlan,
        screenData: ScreenData,
        history: List<ConversationTurn>,
        response: AgentResponse
    ): StrategyRewrite {
        var current = response
        strategies
            .filter { it.matches(plan, screenData) }
            .forEach { strategy ->
                when (val rewrite = strategy.rewriteResponse(plan, screenData, history, current)) {
                    is StrategyRewrite.Keep -> current = rewrite.response
                    is StrategyRewrite.Rewritten -> {
                        Timber.i("[AppStrategy] ${strategy.id} rewrite: ${rewrite.reason}")
                        return rewrite
                    }
                }
            }
        return StrategyRewrite.Keep(current)
    }

    companion object {
        @Volatile
        private var instance: AppStrategyRegistry? = null

        fun getInstance(): AppStrategyRegistry {
            return instance ?: synchronized(this) {
                instance ?: AppStrategyRegistry(
                    strategies = listOf(
                        SystemNavigationStrategy(),
                        DeviceControlStrategy(),
                        OpenAppStrategy(),
                        WechatStrategy(),
                        SystemSettingsStrategy(),
                        BrowserStrategy()
                    )
                ).also { instance = it }
            }
        }
    }
}
