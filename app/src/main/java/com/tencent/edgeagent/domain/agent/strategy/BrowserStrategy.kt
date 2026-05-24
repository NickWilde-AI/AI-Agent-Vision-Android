package com.tencent.edgeagent.domain.agent.strategy

import com.tencent.edgeagent.domain.agent.ConversationTurn
import com.tencent.edgeagent.domain.agent.multi.AgentPlan
import com.tencent.edgeagent.domain.agent.multi.TaskType
import com.tencent.edgeagent.domain.model.ScreenData

class BrowserStrategy : AppStrategy {
    override val id: String = "browser_search"

    override fun matches(plan: AgentPlan, screenData: ScreenData): Boolean {
        return plan.taskType == TaskType.BROWSER_SEARCH ||
            (plan.targetPackage != null && plan.targetPackage in BROWSER_PACKAGES) ||
            (screenData.currentPackage != null && screenData.currentPackage in BROWSER_PACKAGES)
    }

    override fun promptHints(
        plan: AgentPlan,
        screenData: ScreenData,
        history: List<ConversationTurn>
    ): List<String> {
        return listOf(
            "浏览器任务优先聚焦地址栏或搜索框，再 INPUT_TEXT 输入查询内容。",
            "遇到登录、支付、隐私授权、提交表单等高风险页面，返回 NO_ACTION 等待用户确认。",
            "搜索结果页已出现目标信息时，不要继续无意义滑动；直接 NO_ACTION 总结状态。"
        )
    }

    companion object {
        private val BROWSER_PACKAGES = setOf(
            "com.android.chrome",
            "com.android.browser",
            "com.sec.android.app.sbrowser",
            "com.huawei.browser",
            "com.mi.globalbrowser",
            "com.UCMobile"
        )
    }
}
