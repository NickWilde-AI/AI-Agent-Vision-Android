package com.tencent.edgeagent.domain.agent

import android.graphics.Bitmap
import com.tencent.edgeagent.data.cloud.CloudApiException
import com.tencent.edgeagent.data.cloud.CloudFallbackManager
import com.tencent.edgeagent.data.execution.ActionExecutor
import com.tencent.edgeagent.data.execution.ExecutionResult
import com.tencent.edgeagent.data.inference.ILocalModelEngine
import com.tencent.edgeagent.data.inference.LocalModelEngineProvider
import com.tencent.edgeagent.data.trace.AgentTraceStore
import com.tencent.edgeagent.domain.model.ActionType
import com.tencent.edgeagent.domain.model.ActionParams
import com.tencent.edgeagent.domain.model.AgentEvent
import com.tencent.edgeagent.domain.model.AgentIntent
import com.tencent.edgeagent.domain.model.AgentResponse
import com.tencent.edgeagent.domain.model.AgentState
import com.tencent.edgeagent.domain.model.InferenceSource
import com.tencent.edgeagent.domain.model.ScreenCaptureMode
import com.tencent.edgeagent.domain.model.ScreenData
import com.tencent.edgeagent.service.EdgeAgentAccessibilityService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

/**
 * Coordinates the product-facing Agent flow.
 *
 * UI should not decide how perception, reasoning, fallback, and execution are wired.
 * This class owns that orchestration so the Android Agent can evolve independently
 * from the screen used to trigger it.
 */
class AgentOrchestrator private constructor(
    private val stateMachine: AgentStateMachine,
    private val intentRouter: IntentRouter,
    private val edgeCloudRouter: EdgeCloudRouter,
    private val l1CommandRouter: L1CommandRouter,
    private val localModelEngine: ILocalModelEngine,
    private val cloudFallbackManager: CloudFallbackManager,
    private val actionExecutor: ActionExecutor,
    private val agentExecutor: AgentExecutor,
    private val traceStore: AgentTraceStore
) {

    val agentState: StateFlow<AgentState> = stateMachine.currentState

    suspend fun executeCommand(
        userInput: String,
        onProgress: (String) -> Unit = {},
        onResponse: (AgentResponse) -> Unit = {}
    ): AgentRunResult {
        return try {
            Timber.i("[AgentFlow] start command=$userInput")
            onProgress("开始执行...")

            // Best-effort: if SystemUI shade is covering the screen, close it before running.
            EdgeAgentAccessibilityService.getInstance()?.dismissNotificationShade()

            stateMachine.handleEvent(AgentEvent.UserTriggered(userInput))

            val intent = intentRouter.parseIntent(userInput)
            val l1Response = l1CommandRouter.resolve(userInput)
            val edgeCloudDecision = edgeCloudRouter.decide(
                intent = intent,
                cloudAvailable = cloudFallbackManager.isEnabled(),
                localModelAvailable = isRealLocalModelAvailable(),
                deterministicFallbackAvailable = l1Response != null
            )
            Timber.i(
                "[AgentFlow] edge-cloud route mode=${edgeCloudDecision.primaryMode} " +
                    "fallback=${edgeCloudDecision.fallbackMode} reason=${edgeCloudDecision.reason}"
            )

            val result = when (edgeCloudDecision.primaryMode) {
                EdgeCloudRouteMode.CLOUD_AGENT -> {
                    executeCloudAgent(userInput, intent, l1Response, edgeCloudDecision, onProgress, onResponse)
                }
                EdgeCloudRouteMode.DETERMINISTIC_L1 -> {
                    if (l1Response != null) {
                        executeDeterministicL1(userInput, l1Response, edgeCloudDecision, onProgress, onResponse)
                    } else {
                        executeLocalSingleRound(userInput, intent, edgeCloudDecision, onProgress, onResponse)
                    }
                }
                EdgeCloudRouteMode.LOCAL_SINGLE_ROUND -> {
                    executeLocalSingleRound(userInput, intent, edgeCloudDecision, onProgress, onResponse)
                }
            }

            stateMachine.handleEvent(AgentEvent.Reset)
            result
        } catch (e: Exception) {
            Timber.e(e, "[AgentFlow] command failed")
            val message = e.message ?: "未知错误"
            stateMachine.handleEvent(AgentEvent.Error(e, message))
            AgentRunResult.Failure("执行异常: $message")
        }
    }

    private suspend fun executeDeterministicL1(
        userInput: String,
        response: AgentResponse,
        edgeCloudDecision: EdgeCloudDecision?,
        onProgress: (String) -> Unit,
        onResponse: (AgentResponse) -> Unit
    ): AgentRunResult {
        Timber.i("[AgentFlow] deterministic L1 fallback mode action=${response.action}")
        val traceId = traceStore.startSession(userInput)
        edgeCloudDecision?.let { traceStore.recordEdgeCloudDecision(traceId, it) }
        var screenData: ScreenData? = null
        return try {
            onProgress("L1 确定性策略执行中...")
            val currentScreenData = captureRealScreenData(ScreenCaptureMode.UI_TREE_ONLY)
                ?: createFallbackScreenData()
            screenData = currentScreenData
            stateMachine.handleEvent(AgentEvent.PerceptionComplete(currentScreenData))

            onResponse(response)
            stateMachine.handleEvent(AgentEvent.LocalReasoningComplete(response))
            onProgress("执行：${response.action}")

            val result = executeAction(response)
            val resultMessage = when (result) {
                is AgentRunResult.Success -> result.message
                is AgentRunResult.Failure -> result.message
            }
            val executionResult = when (result) {
                is AgentRunResult.Success -> ExecutionResult.Success(resultMessage)
                is AgentRunResult.Failure -> ExecutionResult.Failure(resultMessage)
            }

            traceStore.recordStep(
                sessionId = traceId,
                round = 1,
                screenData = currentScreenData,
                response = response,
                executionResult = executionResult,
                reflection = null,
                note = "l1_deterministic"
            )
            if (result is AgentRunResult.Success) {
                cleanupAfterL1OpenApp(traceId, response, onProgress)
            }
            traceStore.finishSession(
                sessionId = traceId,
                success = result is AgentRunResult.Success,
                reason = resultMessage
            )
            result
        } catch (e: Exception) {
            val message = e.message ?: "L1 确定性执行异常"
            traceStore.recordStep(
                sessionId = traceId,
                round = 1,
                screenData = screenData,
                response = response,
                executionResult = ExecutionResult.Failure(message),
                reflection = null,
                note = "l1_deterministic_exception"
            )
            traceStore.finishSession(traceId, success = false, reason = message)
            throw e
        }
    }

    private suspend fun executeCloudAgent(
        userGoal: String,
        intent: AgentIntent,
        deterministicFallback: AgentResponse?,
        edgeCloudDecision: EdgeCloudDecision,
        onProgress: (String) -> Unit,
        onResponse: (AgentResponse) -> Unit
    ): AgentRunResult {
        Timber.i("[AgentFlow] cloud model-first mode intent=${intent.type}")
        val result = agentExecutor.executeTask(
            userGoal = userGoal,
            onProgress = onProgress,
            onDecision = onResponse,
            edgeCloudDecision = edgeCloudDecision
        )

        return when (result) {
            is TaskExecutionResult.Success -> {
                AgentRunResult.Success("任务完成，共 ${result.rounds} 轮对话")
            }
            is TaskExecutionResult.Failure -> {
                if (deterministicFallback == null) {
                    AgentRunResult.Failure("任务失败: ${result.reason}")
                } else {
                    Timber.w("[AgentFlow] cloud task failed, fallback to deterministic L1: ${result.reason}")
                    onProgress("云端任务失败，使用 L1 安全兜底：${result.reason}")
                    executeDeterministicL1(userGoal, deterministicFallback, edgeCloudDecision, onProgress, onResponse)
                }
            }
        }
    }

    private suspend fun executeLocalSingleRound(
        userInput: String,
        intent: AgentIntent,
        edgeCloudDecision: EdgeCloudDecision?,
        onProgress: (String) -> Unit,
        onResponse: (AgentResponse) -> Unit
    ): AgentRunResult {
        Timber.i("[AgentFlow] local single-round mode intent=${intent.type}")
        val traceId = traceStore.startSession(userInput)
        edgeCloudDecision?.let { traceStore.recordEdgeCloudDecision(traceId, it) }
        var screenData: ScreenData? = null
        return try {
            val currentScreenData = captureRealScreenData(ScreenCaptureMode.UI_TREE_AND_SCREENSHOT)
                ?: createFallbackScreenData()
            screenData = currentScreenData
            stateMachine.handleEvent(AgentEvent.PerceptionComplete(currentScreenData))

            val localResponse = localModelEngine.inference(
                image = currentScreenData.bitmap,
                prompt = userInput,
                uiTree = currentScreenData.uiTreeText
            )
            onResponse(localResponse)
            stateMachine.handleEvent(AgentEvent.LocalReasoningComplete(localResponse))

            val finalResponse = resolveFinalResponse(
                userInput,
                intent,
                currentScreenData,
                localResponse,
                onProgress,
                onResponse
            )
            onProgress("执行：${finalResponse.action}")

            val result = executeAction(finalResponse)
            val resultMessage = when (result) {
                is AgentRunResult.Success -> result.message
                is AgentRunResult.Failure -> result.message
            }
            val executionResult = when (result) {
                is AgentRunResult.Success -> ExecutionResult.Success(resultMessage)
                is AgentRunResult.Failure -> ExecutionResult.Failure(resultMessage)
            }

            traceStore.recordStep(
                sessionId = traceId,
                round = 1,
                screenData = currentScreenData,
                response = finalResponse,
                executionResult = executionResult,
                reflection = null,
                note = "local_single_round"
            )
            traceStore.finishSession(
                sessionId = traceId,
                success = result is AgentRunResult.Success,
                reason = resultMessage
            )
            result
        } catch (e: Exception) {
            val message = e.message ?: "本地单轮执行异常"
            traceStore.recordStep(
                sessionId = traceId,
                round = 1,
                screenData = screenData,
                response = null,
                executionResult = ExecutionResult.Failure(message),
                reflection = null,
                note = "local_single_round_exception"
            )
            traceStore.finishSession(traceId, success = false, reason = message)
            throw e
        }
    }

    private suspend fun resolveFinalResponse(
        userInput: String,
        intent: AgentIntent,
        screenData: ScreenData,
        localResponse: AgentResponse,
        onProgress: (String) -> Unit,
        onResponse: (AgentResponse) -> Unit
    ): AgentResponse {
        if (!intentRouter.shouldUseCloud(intent, localResponse.confidence)) {
            return localResponse
        }

        onProgress("调用云端模型...")
        return try {
            if (!cloudFallbackManager.isEnabled()) {
                Timber.w("[AgentFlow] cloud fallback requested but cloud disabled")
                markCloudReasoningCompleteIfNeeded(localResponse)
                localResponse
            } else {
                cloudFallbackManager.inference(
                    image = screenData.bitmap,
                    prompt = userInput,
                    uiTree = screenData.uiTreeText
                ).also { cloudResponse ->
                    onResponse(cloudResponse)
                    stateMachine.handleEvent(AgentEvent.CloudReasoningComplete(cloudResponse))
                }
            }
        } catch (e: CloudApiException) {
            Timber.e(e, "[AgentFlow] cloud fallback failed, using local response")
            onProgress("云端失败，使用本地结果: ${e.message}")
            markCloudReasoningCompleteIfNeeded(localResponse)
            localResponse
        }
    }

    private fun markCloudReasoningCompleteIfNeeded(response: AgentResponse) {
        if (stateMachine.currentState.value == AgentState.REASONING_CLOUD) {
            stateMachine.handleEvent(AgentEvent.CloudReasoningComplete(response))
        }
    }

    private suspend fun executeAction(response: AgentResponse): AgentRunResult {
        if (response.action == ActionType.NO_ACTION) {
            stateMachine.handleEvent(AgentEvent.ExecutionComplete)
            return AgentRunResult.Success("无需操作")
        }

        return when (val result = actionExecutor.execute(response)) {
            is ExecutionResult.Success -> {
                stateMachine.handleEvent(AgentEvent.ExecutionComplete)
                AgentRunResult.Success(result.message)
            }
            is ExecutionResult.Failure -> {
                val error = Exception(result.message)
                stateMachine.handleEvent(AgentEvent.Error(error, result.message))
                AgentRunResult.Failure(result.message)
            }
        }
    }

    private suspend fun cleanupAfterL1OpenApp(
        traceId: String,
        response: AgentResponse,
        onProgress: (String) -> Unit
    ) {
        val params = response.actionParams as? ActionParams.OpenApp ?: return
        if (response.action != ActionType.OPEN_APP) return
        if (params.packageName == VISION_AGENT_PACKAGE) return

        val backResponse = AgentResponse(
            source = InferenceSource.STRATEGY,
            action = ActionType.BACK,
            actionParams = ActionParams.NoAction("L1 打开应用验收完成，返回 VisionAgent"),
            confidence = 1.0f,
            inferenceTimeMs = 0L,
            rawOutput = "l1_cleanup_back_from:${params.packageName}",
            requiresConfirmation = false
        )
        onProgress("L1 验证完成，返回 VisionAgent")
        val beforeCleanup = captureRealScreenData(ScreenCaptureMode.UI_TREE_ONLY)
            ?: createFallbackScreenData()
        val backResult = actionExecutor.execute(backResponse)
        traceStore.recordStep(
            sessionId = traceId,
            round = 2,
            screenData = beforeCleanup,
            response = backResponse,
            executionResult = backResult,
            reflection = null,
            note = "l1_post_task_back_to_agent"
        )
        delay(700)

        val afterBackPackage = captureRealScreenData(ScreenCaptureMode.UI_TREE_ONLY)?.currentPackage
        if (afterBackPackage == VISION_AGENT_PACKAGE) return

        val reopenResponse = AgentResponse(
            source = InferenceSource.STRATEGY,
            action = ActionType.OPEN_APP,
            actionParams = ActionParams.OpenApp(packageName = VISION_AGENT_PACKAGE),
            confidence = 1.0f,
            inferenceTimeMs = 0L,
            rawOutput = "l1_cleanup_reopen_agent_after_back:$afterBackPackage",
            requiresConfirmation = false
        )
        val reopenResult = actionExecutor.execute(reopenResponse)
        traceStore.recordStep(
            sessionId = traceId,
            round = 3,
            screenData = captureRealScreenData(ScreenCaptureMode.UI_TREE_ONLY) ?: createFallbackScreenData(),
            response = reopenResponse,
            executionResult = reopenResult,
            reflection = null,
            note = "l1_post_task_reopen_agent"
        )
    }

    private suspend fun captureRealScreenData(
        captureMode: ScreenCaptureMode = ScreenCaptureMode.UI_TREE_ONLY
    ): ScreenData? {
        return try {
            EdgeAgentAccessibilityService.getInstance()?.captureScreenData(captureMode)
        } catch (e: Exception) {
            Timber.e(e, "[AgentFlow] capture screen failed")
            null
        }
    }

    private fun createFallbackScreenData(): ScreenData {
        val bitmap = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
        return ScreenData(
            bitmap = bitmap,
            uiTreeText = "UI Tree: unavailable",
            screenWidth = 1080,
            screenHeight = 2400,
            currentPackage = "com.tencent.edgeagent",
            hasRealScreenshot = false,
            captureMode = ScreenCaptureMode.UI_TREE_ONLY
        )
    }

    private fun isRealLocalModelAvailable(): Boolean {
        return runCatching {
            val modelInfo = localModelEngine.getModelInfo()
            modelInfo.name.contains("mock", ignoreCase = true).not() &&
                modelInfo.version != "missing"
        }.getOrDefault(false)
    }

    companion object {
        private const val VISION_AGENT_PACKAGE = "com.tencent.edgeagent"

        @Volatile
        private var instance: AgentOrchestrator? = null

        fun getInstance(): AgentOrchestrator {
            return instance ?: synchronized(this) {
                instance ?: AgentOrchestrator(
                    stateMachine = AgentStateMachine.getInstance(),
                    intentRouter = IntentRouter.getInstance(),
                    edgeCloudRouter = EdgeCloudRouter.getInstance(),
                    l1CommandRouter = L1CommandRouter.getInstance(),
                    localModelEngine = LocalModelEngineProvider.getInstance(),
                    cloudFallbackManager = CloudFallbackManager.getInstance(),
                    actionExecutor = ActionExecutor.getInstance(),
                    agentExecutor = AgentExecutor.getInstance(),
                    traceStore = AgentTraceStore.getInstance()
                ).also { instance = it }
            }
        }
    }
}

sealed class AgentRunResult {
    data class Success(val message: String) : AgentRunResult()
    data class Failure(val message: String) : AgentRunResult()
}
