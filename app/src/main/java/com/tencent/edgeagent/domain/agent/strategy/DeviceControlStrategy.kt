package com.tencent.edgeagent.domain.agent.strategy

import com.tencent.edgeagent.domain.agent.ConversationTurn
import com.tencent.edgeagent.domain.agent.L1CommandRouter
import com.tencent.edgeagent.domain.agent.multi.AgentPlan
import com.tencent.edgeagent.domain.agent.multi.TaskType
import com.tencent.edgeagent.domain.model.ActionParams
import com.tencent.edgeagent.domain.model.ActionType
import com.tencent.edgeagent.domain.model.AgentResponse
import com.tencent.edgeagent.domain.model.DeviceControlType
import com.tencent.edgeagent.domain.model.InferenceSource
import com.tencent.edgeagent.domain.model.ScreenData

class DeviceControlStrategy : AppStrategy {
    override val id: String = "device_control"

    override fun matches(plan: AgentPlan, screenData: ScreenData): Boolean {
        return plan.taskType == TaskType.DEVICE_CONTROL
    }

    override fun promptHints(
        plan: AgentPlan,
        screenData: ScreenData,
        history: List<ConversationTurn>
    ): List<String> {
        val controlType = resolveControlType(plan.goal)?.name ?: "UNKNOWN"
        return listOf(
            "当前是设备控制任务，优先返回 DEVICE_CONTROL(controlType=$controlType)，不要点击 VisionAgent 的输入框或按钮。",
            "L1 只处理低风险系统控制和设置入口；具体配对、选择时区、选择壁纸等多步骤目标应进入 L2 页面状态机。"
        )
    }

    override fun rewriteResponse(
        plan: AgentPlan,
        screenData: ScreenData,
        history: List<ConversationTurn>,
        response: AgentResponse
    ): StrategyRewrite {
        val controlType = resolveControlType(plan.goal) ?: return StrategyRewrite.Keep(response)
        val expectedParams = ActionParams.DeviceControl(controlType = controlType, value = "1")

        if (response.action == ActionType.DEVICE_CONTROL && response.actionParams == expectedParams) {
            return StrategyRewrite.Keep(response)
        }

        val rewritten = AgentResponse(
            source = InferenceSource.STRATEGY,
            action = ActionType.DEVICE_CONTROL,
            actionParams = expectedParams,
            confidence = 1.0f,
            inferenceTimeMs = 0L,
            rawOutput = "strategy_rewrite_device_control:${controlType.name}",
            requiresConfirmation = false
        )
        return StrategyRewrite.Rewritten(rewritten, "L1 设备控制改为 DEVICE_CONTROL(${controlType.name})")
    }

    private fun resolveControlType(goal: String): DeviceControlType? {
        val l1Response = L1CommandRouter.getInstance().resolve(goal)
        val l1Params = l1Response?.actionParams as? ActionParams.DeviceControl
        if (l1Params != null) return l1Params.controlType

        val normalized = goal.lowercase()
        return when {
            goal.contains("音量") || normalized.contains("volume") -> {
                if (isDecrease(goal, normalized)) DeviceControlType.VOLUME_DOWN else DeviceControlType.VOLUME_UP
            }
            goal.contains("亮度") || normalized.contains("brightness") -> {
                if (isDecrease(goal, normalized)) DeviceControlType.BRIGHTNESS_DOWN else DeviceControlType.BRIGHTNESS_UP
            }
            normalized.contains("wifi") ||
                normalized.contains("wi-fi") ||
                goal.contains("无线网络") ||
                goal.contains("WLAN", ignoreCase = true) -> DeviceControlType.WIFI_TOGGLE
            goal.contains("蓝牙") || normalized.contains("bluetooth") -> DeviceControlType.BLUETOOTH_TOGGLE
            goal.contains("飞行模式") || normalized.contains("airplane") -> DeviceControlType.AIRPLANE_MODE_TOGGLE
            else -> null
        }
    }

    private fun isDecrease(goal: String, normalized: String): Boolean {
        val decreaseWords = listOf("调低", "降低", "减小", "小一点", "下调", "down", "lower", "reduce")
        return decreaseWords.any { word ->
            goal.contains(word, ignoreCase = true) || normalized.contains(word)
        }
    }
}
