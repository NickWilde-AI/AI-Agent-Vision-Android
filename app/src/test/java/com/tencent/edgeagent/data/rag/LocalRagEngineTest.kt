package com.tencent.edgeagent.data.rag

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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

    @Test
    fun retrieve_customAppStrategy_returnsPersistedStyleDocument() {
        val engine = LocalRagEngine.getInstance()
        engine.upsert(
            RagDocument(
                id = "test.meituan.takeout",
                title = "美团外卖策略",
                content = "查找店铺时优先使用首页搜索框，涉及提交订单和支付时停止等待用户确认。",
                category = "app_strategy",
                tags = setOf("美团", "外卖", "搜索", "订单"),
                packageNames = setOf("com.sankuai.meituan")
            )
        )

        val hits = engine.retrieve(
            query = "美团里面搜索一家外卖店",
            currentPackage = "com.sankuai.meituan",
            limit = 3
        )

        assertTrue(hits.any { it.document.id == "test.meituan.takeout" })
    }

    @Test
    fun persist_writesUserDocumentToJsonl() {
        val file = File.createTempFile("rag_documents", ".jsonl")
        file.deleteOnExit()

        val engine = LocalRagEngine.getInstance()
        engine.initializeStorage(file)
        engine.persist(
            RagDocument(
                id = "test.browser.form",
                title = "浏览器表单策略",
                content = "提交表单前必须等待用户确认。",
                category = "browser",
                tags = setOf("浏览器", "表单", "提交")
            )
        )

        val text = file.readText()
        assertTrue(text.contains("test.browser.form"))
        assertTrue(text.contains("提交表单前必须等待用户确认"))
    }
}
