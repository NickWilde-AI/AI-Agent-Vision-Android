package com.tencent.edgeagent.domain.agent.strategy

import com.tencent.edgeagent.domain.model.ActionParams
import com.tencent.edgeagent.domain.model.ActionType
import com.tencent.edgeagent.domain.model.AgentResponse
import com.tencent.edgeagent.domain.model.InferenceSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WechatStrategyTest {

    private val strategy = WechatStrategy()

    @Test
    fun isFinalSendAction_blocksClickInsideSendButtonBounds() {
        val response = AgentResponse(
            source = InferenceSource.CLOUD_FALLBACK,
            action = ActionType.CLICK,
            actionParams = ActionParams.Click(970, 2070, "点击按钮"),
            confidence = 0.9f,
            inferenceTimeMs = 12L
        )

        val uiTree = "Clickable Elements (1): label='发送' bounds=[900,2000,1040,2140] center=(970,2070)"

        assertTrue(strategy.isFinalSendAction(response, uiTree))
    }

    @Test
    fun isFinalSendAction_allowsInputTextWithSendInGoalReasoning() {
        val response = AgentResponse(
            source = InferenceSource.CLOUD_FALLBACK,
            action = ActionType.INPUT_TEXT,
            actionParams = ActionParams.InputText("今晚八点见"),
            confidence = 0.9f,
            inferenceTimeMs = 12L,
            rawOutput = "用户要发送微信消息，先输入正文"
        )

        assertFalse(strategy.isFinalSendAction(response, "label='发送' bounds=[900,2000,1040,2140]"))
    }

    @Test
    fun isFinalSendAction_allowsInputBoxClickWhenRawMentionsSend() {
        val response = AgentResponse(
            source = InferenceSource.CLOUD_FALLBACK,
            action = ActionType.CLICK,
            actionParams = ActionParams.Click(200, 2070, "点击输入框"),
            confidence = 0.9f,
            inferenceTimeMs = 12L,
            rawOutput = "用户要发送微信消息，先点击输入框"
        )

        val uiTree = """
            label='输入框' bounds=[0,1980,800,2140]
            label='发送' bounds=[900,2000,1040,2140]
        """.trimIndent()

        assertFalse(strategy.isFinalSendAction(response, uiTree))
    }
}
