package com.tencent.edgeagent.data.inference

import com.tencent.edgeagent.domain.model.ActionParams
import com.tencent.edgeagent.domain.model.ActionType
import com.tencent.edgeagent.domain.model.InferenceSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AgentResponseJsonParserTest {

    @Test
    fun parseClickFromFencedJson() {
        val response = AgentResponseJsonParser.parse(
            rawOutput = """
                ```json
                {
                  "action": "CLICK",
                  "params": {"x": 120, "y": 340, "description": "open"},
                  "confidence": 0.91
                }
                ```
            """.trimIndent(),
            source = InferenceSource.LOCAL_VLM,
            inferenceTimeMs = 42L
        )

        assertEquals(ActionType.CLICK, response.action)
        assertEquals(ActionParams.Click(120, 340, "open"), response.actionParams)
        assertEquals(InferenceSource.LOCAL_VLM, response.source)
        assertFalse(response.requiresConfirmation)
    }

    @Test
    fun parseUnknownActionAsNoAction() {
        val response = AgentResponseJsonParser.parse(
            rawOutput = """{"action":"SEND","params":{"message":"blocked"},"confidence":0.2}""",
            source = InferenceSource.LOCAL_VLM,
            inferenceTimeMs = 7L
        )

        assertEquals(ActionType.NO_ACTION, response.action)
        assertEquals(ActionParams.NoAction("blocked"), response.actionParams)
    }
}
