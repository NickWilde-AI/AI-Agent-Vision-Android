package com.tencent.edgeagent.data.inference

import com.tencent.edgeagent.domain.model.ActionParams
import com.tencent.edgeagent.domain.model.ActionType
import com.tencent.edgeagent.domain.model.AgentResponse
import com.tencent.edgeagent.domain.model.DeviceControlType
import com.tencent.edgeagent.domain.model.InferenceSource
import org.json.JSONObject

internal object AgentResponseJsonParser {

    fun parse(
        rawOutput: String,
        source: InferenceSource,
        inferenceTimeMs: Long
    ): AgentResponse {
        val json = extractJson(rawOutput)
        val action = parseAction(json.optString("action", ActionType.NO_ACTION.name))
        val paramsJson = json.optJSONObject("params") ?: JSONObject()
        val confidence = json.optDouble("confidence", DEFAULT_CONFIDENCE.toDouble())
            .toFloat()
            .coerceIn(0f, 1f)

        return AgentResponse(
            source = source,
            action = action,
            actionParams = parseActionParams(action, paramsJson),
            confidence = confidence,
            inferenceTimeMs = inferenceTimeMs,
            rawOutput = rawOutput,
            requiresConfirmation = confidence < CONFIRMATION_THRESHOLD
        )
    }

    private fun extractJson(content: String): JSONObject {
        runCatching { return JSONObject(content.trim()) }

        val fencedJson = """```json\s*(\{.*?})\s*```"""
            .toRegex(RegexOption.DOT_MATCHES_ALL)
            .find(content)
            ?.groupValues
            ?.getOrNull(1)
        if (!fencedJson.isNullOrBlank()) {
            return JSONObject(fencedJson)
        }

        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        require(start >= 0 && end > start) {
            "No JSON object found in model output"
        }
        return JSONObject(content.substring(start, end + 1))
    }

    private fun parseAction(action: String): ActionType {
        return runCatching { ActionType.valueOf(action.trim().uppercase()) }
            .getOrDefault(ActionType.NO_ACTION)
    }

    private fun parseActionParams(action: ActionType, paramsJson: JSONObject): ActionParams {
        return when (action) {
            ActionType.CLICK -> ActionParams.Click(
                x = paramsJson.optInt("x", 0),
                y = paramsJson.optInt("y", 0),
                description = paramsJson.optString("description", "")
            )
            ActionType.LONG_CLICK -> ActionParams.LongClick(
                x = paramsJson.optInt("x", 0),
                y = paramsJson.optInt("y", 0),
                durationMs = paramsJson.optLong("durationMs", 1000L)
            )
            ActionType.SWIPE -> ActionParams.Swipe(
                startX = paramsJson.optInt("startX", 0),
                startY = paramsJson.optInt("startY", 0),
                endX = paramsJson.optInt("endX", 0),
                endY = paramsJson.optInt("endY", 0),
                durationMs = paramsJson.optLong("durationMs", 300L)
            )
            ActionType.INPUT_TEXT -> ActionParams.InputText(
                text = paramsJson.optString("text", ""),
                targetX = optionalInt(paramsJson, "targetX"),
                targetY = optionalInt(paramsJson, "targetY")
            )
            ActionType.OPEN_APP -> ActionParams.OpenApp(
                packageName = paramsJson.optString("packageName", ""),
                activityName = paramsJson.optString("activityName").ifBlank { null }
            )
            ActionType.DEVICE_CONTROL -> ActionParams.DeviceControl(
                controlType = parseDeviceControl(paramsJson.optString("controlType")),
                value = paramsJson.optString("value", "")
            )
            ActionType.WAIT -> ActionParams.Wait(
                durationMs = paramsJson.optLong("durationMs", 1000L)
            )
            ActionType.BACK,
            ActionType.HOME,
            ActionType.RECENTS,
            ActionType.NO_ACTION -> ActionParams.NoAction(
                message = paramsJson.optString("message", action.name)
            )
        }
    }

    private fun optionalInt(json: JSONObject, key: String): Int? {
        return if (json.has(key) && !json.isNull(key)) json.optInt(key) else null
    }

    private fun parseDeviceControl(value: String): DeviceControlType {
        return runCatching { DeviceControlType.valueOf(value.trim().uppercase()) }
            .getOrDefault(DeviceControlType.BRIGHTNESS_UP)
    }

    private const val DEFAULT_CONFIDENCE = 0.55f
    private const val CONFIRMATION_THRESHOLD = 0.75f
}
