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
            {"type":"plan","plan":{"taskType":"WECHAT_DRAFT","safetyMode":"DRAFT_ONLY","targetPackage":"com.tencent.mm"}}
            {"type":"step","round":1,"screen":{"packageName":"com.tencent.edgeagent"},"response":{"action":"OPEN_APP"},"execution":{"status":"success","message":"已启动应用"}}
            {"type":"session_finish","success":true,"reason":"任务完成"}
            """.trimIndent()
        )

        val replay = AgentTraceStore.getInstance().renderReplay(file)

        assertTrue(replay.contains("打开微信"))
        assertTrue(replay.contains("WECHAT_DRAFT"))
        assertTrue(replay.contains("OPEN_APP"))
        assertTrue(replay.contains("success=true"))
    }
}
