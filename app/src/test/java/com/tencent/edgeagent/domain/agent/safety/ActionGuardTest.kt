package com.tencent.edgeagent.domain.agent.safety

import com.tencent.edgeagent.domain.agent.multi.AgentPlan
import com.tencent.edgeagent.domain.agent.multi.SafetyMode
import com.tencent.edgeagent.domain.agent.multi.TaskType
import com.tencent.edgeagent.domain.model.ActionParams
import com.tencent.edgeagent.domain.model.ActionType
import com.tencent.edgeagent.domain.model.AgentResponse
import com.tencent.edgeagent.domain.model.InferenceSource
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionGuardTest {

    @Test
    fun guard_wechatDraft_blocksSendClick() {
        val plan = AgentPlan(
            goal = "给 Nick 发微信消息",
            taskType = TaskType.WECHAT_DRAFT,
            targetPackage = "com.tencent.mm",
            safetyMode = SafetyMode.DRAFT_ONLY,
            maxRounds = 12,
            localKnowledge = "",
            constraints = emptyList()
        )
        val response = AgentResponse(
            source = InferenceSource.CLOUD_FALLBACK,
            action = ActionType.CLICK,
            actionParams = ActionParams.Click(900, 2100, "点击发送"),
            confidence = 0.9f,
            inferenceTimeMs = 10L
        )

        val result = ActionGuard.getInstance().guard(
            plan = plan,
            response = response,
            currentPackage = "com.tencent.mm",
            uiTreeText = "Clickable Elements (1): label='发送' center=(900,2100)"
        )

        assertTrue(result is GuardResult.Blocked)
    }

    @Test
    fun guard_wechatDraft_blocksClickInsideSendBounds() {
        val plan = wechatDraftPlan()
        val response = AgentResponse(
            source = InferenceSource.CLOUD_FALLBACK,
            action = ActionType.CLICK,
            actionParams = ActionParams.Click(970, 2070, "点击按钮"),
            confidence = 0.9f,
            inferenceTimeMs = 10L
        )

        val result = ActionGuard.getInstance().guard(
            plan = plan,
            response = response,
            currentPackage = "com.tencent.mm",
            uiTreeText = "Clickable Elements (1): label='发送' bounds=[900,2000,1040,2140] center=(970,2070)"
        )

        assertTrue(result is GuardResult.Blocked)
    }

    @Test
    fun guard_wechatDraft_allowsInputBoxClickWhenSendButtonVisible() {
        val plan = wechatDraftPlan()
        val response = AgentResponse(
            source = InferenceSource.CLOUD_FALLBACK,
            action = ActionType.CLICK,
            actionParams = ActionParams.Click(200, 2070, "点击输入框"),
            confidence = 0.9f,
            inferenceTimeMs = 10L
        )

        val uiTreeText = """
            Clickable Elements (2):
              #1 label='输入框' bounds=[0,1980,800,2140] center=(400,2060)
              #2 label='发送' bounds=[900,2000,1040,2140] center=(970,2070)
        """.trimIndent()

        val result = ActionGuard.getInstance().guard(
            plan = plan,
            response = response,
            currentPackage = "com.tencent.mm",
            uiTreeText = uiTreeText
        )

        assertTrue(result is GuardResult.Allowed)
    }

    private fun wechatDraftPlan(): AgentPlan {
        return AgentPlan(
            goal = "给 Nick 发微信消息",
            taskType = TaskType.WECHAT_DRAFT,
            targetPackage = "com.tencent.mm",
            safetyMode = SafetyMode.DRAFT_ONLY,
            maxRounds = 12,
            localKnowledge = "",
            constraints = emptyList()
        )
    }
}
