package com.tencent.edgeagent.data.rag

import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRagEngineTest {

    @Test
    fun retrieve_wechatGoal_returnsWechatDraftStrategy() {
        val hits = LocalRagEngine.getInstance().retrieve(
            query = "打开微信给 Nick 发消息",
            currentPackage = "com.tencent.mm",
            limit = 3
        )

        assertTrue(hits.any { it.document.id == "wechat.draft_only" })
    }

    @Test
    fun buildContext_highRiskGoal_containsConfirmationPolicy() {
        val context = LocalRagEngine.getInstance().buildContext(
            query = "支付并提交订单",
            currentPackage = null,
            limit = 3
        )

        assertTrue(context.contains("高风险动作必须确认"))
    }
}
