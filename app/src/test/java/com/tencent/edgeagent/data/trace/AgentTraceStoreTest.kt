package com.tencent.edgeagent.data.trace

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AgentTraceStoreTest {

    @Test
    fun renderReplay_formatsJsonlTrace() {
        val file = File.createTempFile("agent_trace", ".jsonl")
        file.deleteOnExit()
        file.writeText(
            """
            {"type":"session_start","goal":"打开微信","timestamp":0}
            {"type":"edge_cloud_decision","decision":{"primaryMode":"CLOUD_AGENT","fallbackMode":"DETERMINISTIC_L1","intentType":"APP_OPERATION","privacyClass":"screen_context_cloud_allowed","reason":"cloud_available"}}
            {"type":"plan","plan":{"taskType":"WECHAT_DRAFT","safetyMode":"DRAFT_ONLY","targetPackage":"com.tencent.mm"}}
            {"type":"model_diagnostic","success":true,"elapsedMs":18983,"model":{"name":"Gemma 4 E2B","version":"litert-lm"},"response":{"action":"NO_ACTION"}}
            {"type":"step","round":1,"screen":{"packageName":"com.tencent.edgeagent"},"response":{"action":"OPEN_APP"},"execution":{"status":"success","message":"已启动应用"}}
            {"type":"session_finish","success":true,"reason":"任务完成"}
            """.trimIndent()
        )

        val replay = AgentTraceStore.getInstance().renderReplay(file)

        assertTrue(replay.contains("打开微信"))
        assertTrue(replay.contains("端云路由"))
        assertTrue(replay.contains("WECHAT_DRAFT"))
        assertTrue(replay.contains("模型诊断"))
        assertTrue(replay.contains("OPEN_APP"))
        assertTrue(replay.contains("success=true"))
    }
}
