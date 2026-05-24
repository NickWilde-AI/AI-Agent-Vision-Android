package com.tencent.edgeagent.data.inference

import android.graphics.Bitmap
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.tencent.edgeagent.domain.model.ActionParams
import com.tencent.edgeagent.domain.model.ActionType
import com.tencent.edgeagent.domain.model.AgentResponse
import com.tencent.edgeagent.domain.model.InferenceSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

class GemmaLiteRtModelEngine private constructor(
    private val modelManager: LocalModelManager
) : ILocalModelEngine {

    private val engineMutex = Mutex()
    private var engine: Engine? = null
    private var loaded = false
    private var lastInferenceTimeMs = 0L
    private var lastLoadError: String? = null

    override suspend fun inference(
        image: Bitmap,
        prompt: String,
        uiTree: String?
    ): AgentResponse {
        val startTime = System.currentTimeMillis()
        return try {
            warmUp()
            val modelOutput = withContext(Dispatchers.Default) {
                engineMutex.withLock {
                    val activeEngine = requireNotNull(engine) { "LiteRT-LM engine is not initialized" }
                    activeEngine.createConversation().use { conversation ->
                        conversation.sendMessage(buildAgentPrompt(prompt, uiTree, image)).toString()
                    }
                }
            }
            lastInferenceTimeMs = System.currentTimeMillis() - startTime
            AgentResponseJsonParser.parse(
                rawOutput = modelOutput,
                source = InferenceSource.LOCAL_VLM,
                inferenceTimeMs = lastInferenceTimeMs
            )
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            Timber.e(e, "[LocalGemma] inference failed")
            safeFailureResponse(
                message = "本地 Gemma 推理失败: ${e.message}",
                elapsedMs = elapsed
            )
        }
    }

    override suspend fun warmUp() {
        if (loaded) return

        engineMutex.withLock {
            if (loaded) return
            val status = modelManager.getGemmaStatus()
            if (status !is LocalModelStatus.Ready) {
                val message = "Gemma model is not ready: $status"
                lastLoadError = message
                throw IllegalStateException(message)
            }

            try {
                Timber.i("[LocalGemma] loading LiteRT-LM model: ${status.file.absolutePath}")
                val loadedEngine = withContext(Dispatchers.Default) {
                    Engine(
                        EngineConfig(
                            modelPath = status.file.absolutePath,
                            backend = Backend.CPU()
                        )
                    ).also { it.initialize() }
                }
                engine = loadedEngine
                loaded = true
                lastLoadError = null
                Timber.i("[LocalGemma] LiteRT-LM model loaded")
            } catch (e: Exception) {
                lastLoadError = e.message
                loaded = false
                engine = null
                Timber.e(e, "[LocalGemma] load failed")
                throw e
            }
        }
    }

    override fun release() {
        runCatching {
            engine?.close()
        }.onFailure { error ->
            Timber.w(error, "[LocalGemma] release failed")
        }
        engine = null
        loaded = false
    }

    override fun isModelLoaded(): Boolean = loaded

    override fun getModelInfo(): ModelInfo {
        val status = modelManager.getGemmaStatus()
        val sizeInMB = (status as? LocalModelStatus.Ready)?.sizeBytes?.div(BYTES_PER_MB) ?: 0f
        val version = when {
            loaded -> "litert-lm-0.12.0-cpu-loaded"
            lastLoadError != null -> "litert-lm-0.12.0-load-failed"
            status is LocalModelStatus.Ready -> "litert-lm-0.12.0-cpu-ready"
            else -> "missing"
        }
        return ModelInfo(
            name = "Gemma 4 E2B",
            version = version,
            sizeInMB = sizeInMB,
            supportsMultimodal = true,
            avgInferenceTimeMs = lastInferenceTimeMs
        )
    }

    private fun buildAgentPrompt(userGoal: String, uiTree: String?, image: Bitmap): String {
        val boundedUiTree = uiTree.orEmpty().take(MAX_UI_TREE_CHARS)
        return """
            You are the local reasoning engine for an Android Agent.
            Decide exactly one next safe action for the current screen.
            Return only one JSON object and no markdown.

            Allowed actions:
            CLICK, LONG_CLICK, SWIPE, INPUT_TEXT, BACK, HOME, RECENTS, OPEN_APP, DEVICE_CONTROL, WAIT, NO_ACTION

            JSON schema:
            {
              "action": "NO_ACTION",
              "params": {"message": "reason"},
              "confidence": 0.0,
              "reasoning": "short reason"
            }

            Parameter examples:
            CLICK: {"x": 540, "y": 1200, "description": "target"}
            LONG_CLICK: {"x": 540, "y": 1200, "durationMs": 1000}
            SWIPE: {"startX": 540, "startY": 1700, "endX": 540, "endY": 700, "durationMs": 300}
            INPUT_TEXT: {"text": "content", "targetX": 540, "targetY": 2000}
            OPEN_APP: {"packageName": "com.android.settings"}
            DEVICE_CONTROL: {"controlType": "VOLUME_UP", "value": "1"}
            WAIT: {"durationMs": 1000}
            NO_ACTION: {"message": "reason"}

            Safety rules:
            - If the requested action may send money, publish content, delete data, or send a final message, return NO_ACTION unless the user explicitly asks for a draft-only step.
            - Prefer DEVICE_CONTROL for volume, brightness, Wi-Fi, Bluetooth, and airplane-mode requests.
            - INPUT_TEXT execution will try to dismiss the keyboard after typing; use BACK next if the keyboard still blocks the target.
            - Prefer WAIT or NO_ACTION when the UI tree is missing or ambiguous.
            - Coordinates must stay inside screen ${image.width}x${image.height}.

            User goal:
            $userGoal

            Current UI tree:
            $boundedUiTree
        """.trimIndent()
    }

    private fun safeFailureResponse(message: String, elapsedMs: Long): AgentResponse {
        return AgentResponse(
            source = InferenceSource.LOCAL_VLM,
            action = ActionType.NO_ACTION,
            actionParams = ActionParams.NoAction(message),
            confidence = 0.05f,
            inferenceTimeMs = elapsedMs,
            rawOutput = "local_gemma_error:$message",
            requiresConfirmation = true
        )
    }

    companion object {
        private const val MAX_UI_TREE_CHARS = 12_000
        private const val BYTES_PER_MB = 1024f * 1024f

        @Volatile
        private var instance: GemmaLiteRtModelEngine? = null

        fun getInstance(
            modelManager: LocalModelManager = LocalModelManager.getInstance()
        ): GemmaLiteRtModelEngine {
            return instance ?: synchronized(this) {
                instance ?: GemmaLiteRtModelEngine(modelManager).also { instance = it }
            }
        }
    }
}
