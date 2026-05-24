package com.tencent.edgeagent.data.trace

import android.content.Context
import com.tencent.edgeagent.data.execution.ExecutionResult
import com.tencent.edgeagent.domain.agent.multi.AgentPlan
import com.tencent.edgeagent.domain.agent.multi.AgentReflection
import com.tencent.edgeagent.domain.model.ActionParams
import com.tencent.edgeagent.domain.model.AgentResponse
import com.tencent.edgeagent.domain.model.ScreenData
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Persistent trace store for every Agent run.
 *
 * A trace is written as JSON Lines so a failed run can still be inspected even
 * if the app process dies before the session footer is written.
 */
class AgentTraceStore private constructor() {

    @Volatile
    private var traceDir: File? = null

    @Volatile
    private var latestFile: File? = null

    fun initialize(context: Context) {
        val dir = File(context.applicationContext.filesDir, TRACE_DIR_NAME)
        if (!dir.exists() && !dir.mkdirs()) {
            Timber.w("[AgentTrace] cannot create trace dir: ${dir.absolutePath}")
            return
        }
        traceDir = dir
        latestFile = File(dir, LATEST_FILE_NAME)
        Timber.i("[AgentTrace] initialized dir=${dir.absolutePath}")
    }

    fun startSession(goal: String): String {
        val sessionId = buildSessionId()
        val file = traceFile(sessionId) ?: return sessionId
        writeLatestPointer(file)
        pruneOldTraces()
        appendEvent(
            file,
            JSONObject()
                .put("type", "session_start")
                .put("sessionId", sessionId)
                .put("goal", goal)
                .put("timestamp", System.currentTimeMillis())
        )
        return sessionId
    }

    fun recordPlan(sessionId: String, plan: AgentPlan) {
        val file = traceFile(sessionId) ?: return
        appendEvent(
            file,
            JSONObject()
                .put("type", "plan")
                .put("sessionId", sessionId)
                .put("timestamp", System.currentTimeMillis())
                .put("plan", planToJson(plan))
        )
    }

    fun recordStep(
        sessionId: String,
        round: Int,
        screenData: ScreenData?,
        response: AgentResponse?,
        executionResult: ExecutionResult?,
        reflection: AgentReflection?,
        note: String? = null
    ) {
        val file = traceFile(sessionId) ?: return
        appendEvent(
            file,
            JSONObject()
                .put("type", "step")
                .put("sessionId", sessionId)
                .put("round", round)
                .put("timestamp", System.currentTimeMillis())
                .put("screen", screenData?.let(::screenToJson) ?: JSONObject.NULL)
                .put("reflection", reflection?.let(::reflectionToJson) ?: JSONObject.NULL)
                .put("response", response?.let(::responseToJson) ?: JSONObject.NULL)
                .put("execution", executionResult?.let(::executionToJson) ?: JSONObject.NULL)
                .put("note", note ?: "")
        )
    }

    fun finishSession(sessionId: String, success: Boolean, reason: String) {
        val file = traceFile(sessionId) ?: return
        appendEvent(
            file,
            JSONObject()
                .put("type", "session_finish")
                .put("sessionId", sessionId)
                .put("success", success)
                .put("reason", reason)
                .put("timestamp", System.currentTimeMillis())
        )
    }

    fun latestTraceFile(): File? {
        val pointer = latestFile ?: return null
        val path = pointer.takeIf { it.exists() }?.readText()?.trim().orEmpty()
        return path.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() }
    }

    fun readLatestTraceText(): String {
        return latestTraceFile()?.readText().orEmpty()
    }

    fun renderLatestReplay(): String {
        val file = latestTraceFile() ?: return "暂无 AgentTrace。"
        return renderReplay(file)
    }

    fun renderReplay(file: File): String {
        if (!file.exists()) return "Trace 文件不存在：${file.absolutePath}"

        val lines = file.readLines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return "Trace 文件为空：${file.absolutePath}"

        val output = StringBuilder()
        output.append("AgentTrace Replay: ${file.name}\n")
        lines.forEach { line ->
            runCatching { JSONObject(line) }.getOrNull()?.let { event ->
                when (event.optString("type")) {
                    "session_start" -> {
                        output.append("开始: ${event.optString("goal")} (${formatTime(event.optLong("timestamp"))})\n")
                    }
                    "plan" -> {
                        val plan = event.optJSONObject("plan")
                        output.append("规划: ${plan?.optString("taskType")} / ${plan?.optString("safetyMode")} / target=${plan?.optString("targetPackage")}\n")
                    }
                    "step" -> {
                        val response = event.optJSONObject("response")
                        val execution = event.optJSONObject("execution")
                        val screen = event.optJSONObject("screen")
                        output.append(
                            "第 ${event.optInt("round")} 轮: " +
                                "pkg=${screen?.optString("packageName")} " +
                                "action=${response?.optString("action")} " +
                                "result=${execution?.optString("status") ?: "none"} " +
                                "${execution?.optString("message").orEmpty()}\n"
                        )
                    }
                    "session_finish" -> {
                        output.append("结束: success=${event.optBoolean("success")} reason=${event.optString("reason")}\n")
                    }
                }
            }
        }
        return output.toString()
    }

    fun traceDirectory(): File? = traceDir

    private fun traceFile(sessionId: String): File? {
        val dir = traceDir ?: return null
        return File(dir, "$sessionId.jsonl")
    }

    private fun writeLatestPointer(file: File) {
        runCatching {
            latestFile?.writeText(file.absolutePath)
        }.onFailure {
            Timber.w(it, "[AgentTrace] write latest pointer failed")
        }
    }

    private fun pruneOldTraces() {
        val dir = traceDir ?: return
        runCatching {
            val traceFiles = dir.listFiles { file ->
                file.isFile && file.name.startsWith("trace_") && file.name.endsWith(".jsonl")
            }?.sortedByDescending { it.lastModified() }.orEmpty()

            traceFiles.drop(MAX_TRACE_FILES).forEach { oldFile ->
                if (!oldFile.delete()) {
                    Timber.w("[AgentTrace] cannot delete old trace: ${oldFile.absolutePath}")
                }
            }
        }.onFailure {
            Timber.w(it, "[AgentTrace] prune old traces failed")
        }
    }

    @Synchronized
    private fun appendEvent(file: File, event: JSONObject) {
        runCatching {
            file.appendText(event.toString() + "\n")
        }.onFailure {
            Timber.w(it, "[AgentTrace] append failed")
        }
    }

    private fun planToJson(plan: AgentPlan): JSONObject {
        return JSONObject()
            .put("goal", plan.goal)
            .put("taskType", plan.taskType.name)
            .put("targetPackage", plan.targetPackage ?: "")
            .put("safetyMode", plan.safetyMode.name)
            .put("maxRounds", plan.maxRounds)
            .put("constraints", JSONArray(plan.constraints))
            .put("localKnowledge", plan.localKnowledge.take(MAX_FIELD_CHARS))
    }

    private fun reflectionToJson(reflection: AgentReflection): JSONObject {
        return JSONObject()
            .put("hints", JSONArray(reflection.hints))
            .put("shouldAbort", reflection.shouldAbort)
            .put("abortReason", reflection.abortReason ?: "")
    }

    private fun screenToJson(screenData: ScreenData): JSONObject {
        return JSONObject()
            .put("packageName", screenData.currentPackage ?: "")
            .put("screenWidth", screenData.screenWidth)
            .put("screenHeight", screenData.screenHeight)
            .put("hasRealScreenshot", screenData.hasRealScreenshot)
            .put("timestamp", screenData.timestamp)
            .put("uiSummary", summarizeUiTree(screenData.uiTreeText))
    }

    private fun responseToJson(response: AgentResponse): JSONObject {
        return JSONObject()
            .put("source", response.source.name)
            .put("action", response.action.name)
            .put("confidence", response.confidence.toDouble())
            .put("inferenceTimeMs", response.inferenceTimeMs)
            .put("requiresConfirmation", response.requiresConfirmation)
            .put("params", actionParamsToJson(response.actionParams))
            .put("rawOutput", response.rawOutput.orEmpty().take(MAX_FIELD_CHARS))
    }

    private fun actionParamsToJson(params: ActionParams): JSONObject {
        val json = JSONObject().put("type", params::class.java.simpleName)
        when (params) {
            is ActionParams.Click -> json
                .put("x", params.x)
                .put("y", params.y)
                .put("description", params.description)
            is ActionParams.LongClick -> json
                .put("x", params.x)
                .put("y", params.y)
                .put("durationMs", params.durationMs)
            is ActionParams.Swipe -> json
                .put("startX", params.startX)
                .put("startY", params.startY)
                .put("endX", params.endX)
                .put("endY", params.endY)
                .put("durationMs", params.durationMs)
            is ActionParams.InputText -> json
                .put("text", params.text)
                .put("targetX", params.targetX ?: JSONObject.NULL)
                .put("targetY", params.targetY ?: JSONObject.NULL)
            is ActionParams.OpenApp -> json
                .put("packageName", params.packageName)
                .put("activityName", params.activityName ?: "")
            is ActionParams.DeviceControl -> json
                .put("controlType", params.controlType.name)
                .put("value", params.value)
            is ActionParams.Wait -> json.put("durationMs", params.durationMs)
            is ActionParams.NoAction -> json.put("message", params.message)
        }
        return json
    }

    private fun executionToJson(result: ExecutionResult): JSONObject {
        return when (result) {
            is ExecutionResult.Success -> JSONObject()
                .put("status", "success")
                .put("message", result.message)
            is ExecutionResult.Failure -> JSONObject()
                .put("status", "failure")
                .put("message", result.message)
        }
    }

    private fun summarizeUiTree(uiTreeText: String?): String {
        if (uiTreeText.isNullOrBlank()) return ""
        return uiTreeText
            .lineSequence()
            .filter { line ->
                line.contains("text='") ||
                    line.contains("desc='") ||
                    line.contains("id='") ||
                    line.contains("[clickable]") ||
                    line.contains("[editable]")
            }
            .take(TRACE_UI_LINES)
            .joinToString("\n")
            .take(MAX_FIELD_CHARS)
    }

    private fun buildSessionId(): String {
        val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "trace_${time}_${UUID.randomUUID().toString().take(8)}"
    }

    private fun formatTime(timestamp: Long): String {
        if (timestamp <= 0L) return "unknown"
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestamp))
    }

    companion object {
        private const val TRACE_DIR_NAME = "agent_traces"
        private const val LATEST_FILE_NAME = "latest.txt"
        private const val TRACE_UI_LINES = 40
        private const val MAX_FIELD_CHARS = 8000
        private const val MAX_TRACE_FILES = 50

        @Volatile
        private var instance: AgentTraceStore? = null

        fun getInstance(): AgentTraceStore {
            return instance ?: synchronized(this) {
                instance ?: AgentTraceStore().also { instance = it }
            }
        }
    }
}
