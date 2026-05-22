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
}
