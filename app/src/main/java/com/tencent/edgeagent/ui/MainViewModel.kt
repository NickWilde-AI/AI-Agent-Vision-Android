package com.tencent.edgeagent.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tencent.edgeagent.data.cloud.CloudConfig
import com.tencent.edgeagent.data.cloud.CloudFallbackManager
import com.tencent.edgeagent.data.inference.ILocalModelEngine
import com.tencent.edgeagent.data.inference.MockModelEngine
import com.tencent.edgeagent.data.inference.ModelInfo
import com.tencent.edgeagent.domain.agent.AgentOrchestrator
import com.tencent.edgeagent.domain.agent.AgentRunResult
import com.tencent.edgeagent.domain.model.AgentResponse
import com.tencent.edgeagent.domain.model.AgentState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Main screen state holder.
 *
 * The product Agent flow lives in AgentOrchestrator; this ViewModel only exposes
 * UI state and forwards user commands.
 */
class MainViewModel : ViewModel() {

    private val orchestrator = AgentOrchestrator.getInstance()
    private val localModelEngine: ILocalModelEngine = MockModelEngine.getInstance()
    private val cloudFallbackManager = CloudFallbackManager.getInstance()
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

    private fun warmUpLocalEngine() {
        viewModelScope.launch {
            try {
                localModelEngine.warmUp()
                _modelInfo.value = localModelEngine.getModelInfo()
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
}
