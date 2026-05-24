package com.tencent.edgeagent.domain.agent.strategy

import com.tencent.edgeagent.domain.agent.ConversationTurn
import com.tencent.edgeagent.domain.agent.multi.AgentPlan
import com.tencent.edgeagent.domain.agent.multi.TaskType
import com.tencent.edgeagent.domain.model.ScreenData

class SystemSettingsStrategy : AppStrategy {
    override val id: String = "system_settings"

    override fun matches(plan: AgentPlan, screenData: ScreenData): Boolean {
        return plan.taskType == TaskType.DEVICE_CONTROL ||
            plan.targetPackage == SETTINGS_PACKAGE ||
            screenData.currentPackage == SETTINGS_PACKAGE
    }

    override fun promptHints(
        plan: AgentPlan,
        screenData: ScreenData,
        history: List<ConversationTurn>
    ): List<String> {
        return listOf(
            "设备控制优先打开对应系统设置页，不要在未知页面盲点。",
            "涉及 Wi-Fi、蓝牙、飞行模式等系统开关时，进入确认页或目标设置页后返回 NO_ACTION 等待用户确认。",
            "如果 UI 树不可用，优先 BACK 或 HOME 恢复到可观测页面。"
        )
    }

    companion object {
        private const val SETTINGS_PACKAGE = "com.android.settings"
    }
}
