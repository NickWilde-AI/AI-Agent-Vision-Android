package com.tencent.edgeagent.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tencent.edgeagent.data.cloud.CloudConfig
import com.tencent.edgeagent.data.cloud.CloudFallbackManager
import com.tencent.edgeagent.data.inference.GemmaLiteRtModelEngine
import com.tencent.edgeagent.data.inference.ILocalModelEngine
import com.tencent.edgeagent.data.inference.LocalModelEngineProvider
import com.tencent.edgeagent.data.inference.LocalModelManager
import com.tencent.edgeagent.data.inference.ModelInfo
import com.tencent.edgeagent.data.trace.AgentTraceStore
import com.tencent.edgeagent.domain.agent.AgentOrchestrator
import com.tencent.edgeagent.domain.agent.AgentRunResult
import com.tencent.edgeagent.domain.model.AgentResponse
import com.tencent.edgeagent.domain.model.AgentState
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import timber.log.Timber

/**
 * Main screen state holder.
 *
 * The product Agent flow lives in AgentOrchestrator; this ViewModel only exposes
 * UI state and forwards user commands.
 */
class MainViewModel : ViewModel() {

    private val orchestrator = AgentOrchestrator.getInstance()
    private val localModelEngine: ILocalModelEngine = LocalModelEngineProvider.getInstance()
    private val localModelManager = LocalModelManager.getInstance()
    private val cloudFallbackManager = CloudFallbackManager.getInstance()
    private val traceStore = AgentTraceStore.getInstance()
    private var activeCommandJob: Job? = null

    val agentState: StateFlow<AgentState> = orchestrator.agentState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AgentState.IDLE
        )

    private val _lastResponse = MutableStateFlow<AgentResponse?>(null)
    val lastResponse: StateFlow<AgentResponse?> = _lastResponse.asStateFlow()

    private val _modelInfo = MutableStateFlow<ModelInfo?>(null)
    val modelInfo: StateFlow<ModelInfo?> = _modelInfo.asStateFlow()

    private val _executionResult = MutableStateFlow<String?>(null)
    val executionResult: StateFlow<String?> = _executionResult.asStateFlow()

    private val _cloudStatus = MutableStateFlow("未初始化")
    val cloudStatus: StateFlow<String> = _cloudStatus.asStateFlow()

    init {
        Timber.d("MainViewModel 初始化")
        warmUpLocalEngine()
        initializeCloudProvider()
        observeAgentStateForLogs()
    }

    fun executeCommand(userInput: String) {
        if (activeCommandJob?.isActive == true) {
            _executionResult.value = "已有任务正在执行"
            return
        }

        activeCommandJob = viewModelScope.launch {
            _executionResult.value = "开始执行..."
            val result = orchestrator.executeCommand(
                userInput = userInput,
                onProgress = { progress -> _executionResult.value = progress },
                onResponse = { response -> _lastResponse.value = response }
            )

            _executionResult.value = when (result) {
                is AgentRunResult.Success -> "成功：${result.message}"
                is AgentRunResult.Failure -> "失败：${result.message}"
            }
        }
    }

    fun runLocalModelHealthCheck() {
        if (activeCommandJob?.isActive == true) {
            _executionResult.value = "已有任务正在执行"
            return
        }

        activeCommandJob = viewModelScope.launch {
            _executionResult.value = "本地模型健康检查中..."
            val traceId = traceStore.startSession(LOCAL_MODEL_HEALTH_GOAL)
            val startTime = System.currentTimeMillis()
            val healthBitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            try {
                val response = withTimeout(LOCAL_MODEL_HEALTH_TIMEOUT_MS) {
                    localModelEngine.inference(
                        image = healthBitmap,
                        prompt = "Local model health check. Return a safe NO_ACTION JSON response.",
                        uiTree = "UI Tree: health_check"
                    )
                }
                val elapsedMs = System.currentTimeMillis() - startTime
                _lastResponse.value = response
                val modelInfo = localModelManager.buildModelInfoOrFallback(localModelEngine.getModelInfo())
                _modelInfo.value = modelInfo
                traceStore.recordModelDiagnostic(
                    sessionId = traceId,
                    modelInfo = modelInfo,
                    response = response,
                    success = true,
                    errorMessage = null,
                    elapsedMs = elapsedMs
                )
                traceStore.finishSession(traceId, success = true, reason = "本地模型健康检查完成")
                _executionResult.value = "本地模型检查完成：${response.action}，${response.inferenceTimeMs}ms"
                Timber.i("本地模型健康检查完成: $response")
            } catch (e: TimeoutCancellationException) {
                val elapsedMs = System.currentTimeMillis() - startTime
                traceStore.recordModelDiagnostic(
                    sessionId = traceId,
                    modelInfo = runCatching { localModelEngine.getModelInfo() }.getOrNull(),
                    response = null,
                    success = false,
                    errorMessage = "timeout:${LOCAL_MODEL_HEALTH_TIMEOUT_MS / 1000}s",
                    elapsedMs = elapsedMs
                )
                traceStore.finishSession(traceId, success = false, reason = "本地模型检查超时")
                _executionResult.value = "本地模型检查超时：${LOCAL_MODEL_HEALTH_TIMEOUT_MS / 1000}s"
                Timber.e(e, "本地模型健康检查超时")
            } catch (e: Exception) {
                val elapsedMs = System.currentTimeMillis() - startTime
                traceStore.recordModelDiagnostic(
                    sessionId = traceId,
                    modelInfo = runCatching { localModelEngine.getModelInfo() }.getOrNull(),
                    response = null,
                    success = false,
                    errorMessage = e.message,
                    elapsedMs = elapsedMs
                )
                traceStore.finishSession(traceId, success = false, reason = "本地模型检查失败：${e.message}")
                _executionResult.value = "本地模型检查失败：${e.message}"
                Timber.e(e, "本地模型健康检查失败")
            } finally {
                healthBitmap.recycle()
            }
        }
    }

    private fun warmUpLocalEngine() {
        viewModelScope.launch {
            try {
                _modelInfo.value = localModelManager.buildModelInfoOrFallback(localModelEngine.getModelInfo())
                if (localModelEngine is GemmaLiteRtModelEngine) {
                    Timber.i("本地 Gemma 模型已就绪，运行时加载延迟到首次本地推理")
                    return@launch
                }

                localModelEngine.warmUp()
                _modelInfo.value = localModelManager.buildModelInfoOrFallback(localModelEngine.getModelInfo())
                Timber.d("模型预热完成: ${_modelInfo.value}")
            } catch (e: Exception) {
                Timber.e(e, "模型预热失败")
            }
        }
    }

    private fun initializeCloudProvider() {
        viewModelScope.launch {
            try {
                if (!CloudConfig.ENABLE_CLOUD) {
                    _cloudStatus.value = "已禁用（纯本地模式）"
                    Timber.i("云端服务已禁用")
                    return@launch
                }

                if (!CloudConfig.isApiKeyConfigured()) {
                    _cloudStatus.value = "未配置 API Key"
                    Timber.w("云端服务未配置 API Key")
                    return@launch
                }

                cloudFallbackManager.initialize(
                    apiKey = CloudConfig.getApiKey(),
                    provider = CloudConfig.PROVIDER
                )
                _cloudStatus.value = "已启用: ${cloudFallbackManager.getProviderInfo()?.name}"
                Timber.i("云端服务初始化成功")
            } catch (e: Exception) {
                _cloudStatus.value = "初始化失败: ${e.message}"
                Timber.e(e, "云端服务初始化失败")
            }
        }
    }

    private fun observeAgentStateForLogs() {
        viewModelScope.launch {
            orchestrator.agentState.collect { state ->
                Timber.d("状态变化: $state")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("MainViewModel 清理")
        localModelEngine.release()
    }

    companion object {
        private const val LOCAL_MODEL_HEALTH_TIMEOUT_MS = 180_000L
        private const val LOCAL_MODEL_HEALTH_GOAL = "本地模型健康检查"
    }
}
