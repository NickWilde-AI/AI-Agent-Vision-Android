package com.tencent.edgeagent.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tencent.edgeagent.data.cloud.CloudApiException
import com.tencent.edgeagent.data.cloud.CloudConfig
import com.tencent.edgeagent.data.cloud.CloudFallbackManager
import com.tencent.edgeagent.data.execution.ActionExecutor
import com.tencent.edgeagent.data.execution.ExecutionResult
import com.tencent.edgeagent.data.inference.ILocalModelEngine
import com.tencent.edgeagent.data.inference.MockModelEngine
import com.tencent.edgeagent.data.inference.ModelInfo
import com.tencent.edgeagent.domain.agent.AgentStateMachine
import com.tencent.edgeagent.domain.agent.IntentRouter
import com.tencent.edgeagent.domain.model.*
import com.tencent.edgeagent.service.EdgeAgentAccessibilityService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 主界面 ViewModel
 */
class MainViewModel : ViewModel() {

    private val stateMachine: AgentStateMachine = AgentStateMachine.getInstance()
    private val intentRouter: IntentRouter = IntentRouter.getInstance()
    private val localModelEngine: ILocalModelEngine = MockModelEngine.getInstance()
    private val cloudFallbackManager: CloudFallbackManager = CloudFallbackManager.getInstance()
    private val actionExecutor: ActionExecutor = ActionExecutor.getInstance()

    val agentState: StateFlow<AgentState> = stateMachine.currentState
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
    
    private val _cloudStatus = MutableStateFlow<String>("未初始化")
    val cloudStatus: StateFlow<String> = _cloudStatus.asStateFlow()

    init {
        Timber.d("MainViewModel 初始化")
        
        // 初始化本地模型
        viewModelScope.launch {
            try {
                localModelEngine.warmUp()
                _modelInfo.value = localModelEngine.getModelInfo()
                Timber.d("模型预热完成: ${_modelInfo.value}")
            } catch (e: Exception) {
                Timber.e(e, "模型预热失败")
            }
        }
        
        // 初始化云端服务
        viewModelScope.launch {
            try {
                if (CloudConfig.ENABLE_CLOUD) {
                    if (CloudConfig.isApiKeyConfigured()) {
                        cloudFallbackManager.initialize(
                            apiKey = CloudConfig.getApiKey(),
                            provider = CloudConfig.PROVIDER
                        )
                        _cloudStatus.value = "已启用: ${cloudFallbackManager.getProviderInfo()?.name}"
                        Timber.i("云端服务初始化成功")
                    } else {
                        _cloudStatus.value = "未配置 API Key"
                        Timber.w("云端服务未配置 API Key")
                    }
                } else {
                    _cloudStatus.value = "已禁用（纯本地模式）"
                    Timber.i("云端服务已禁用")
                }
            } catch (e: Exception) {
                _cloudStatus.value = "初始化失败: ${e.message}"
                Timber.e(e, "云端服务初始化失败")
            }
        }

        viewModelScope.launch {
            stateMachine.currentState.collect { state ->
                Timber.d("状态变化: $state")
            }
        }
    }

    fun testInference(userInput: String) {
        viewModelScope.launch {
            try {
                Timber.d("开始测试推理: $userInput")

                stateMachine.handleEvent(AgentEvent.UserTriggered(userInput))

                val intent = intentRouter.parseIntent(userInput)
                Timber.d("意图解析: ${intent.type}")

                // 尝试获取真实屏幕数据
                val screenData = captureRealScreenData() ?: createMockScreenData()

                stateMachine.handleEvent(AgentEvent.PerceptionComplete(screenData))

                // 本地推理
                val localResponse = localModelEngine.inference(
                    image = screenData.bitmap,
                    prompt = userInput,
                    uiTree = screenData.uiTreeText
                )

                _lastResponse.value = localResponse
                Timber.d("本地推理完成: action=${localResponse.action}, confidence=${localResponse.confidence}")

                stateMachine.handleEvent(AgentEvent.LocalReasoningComplete(localResponse))

                // 判断是否需要云端兜底
                var finalResponse = localResponse
                
                if (intentRouter.shouldUseCloud(intent, localResponse.confidence)) {
                    Timber.d("置信度不足或需要云端处理，调用云端 API")
                    _executionResult.value = "🌐 调用云端 API..."
                    
                    try {
                        if (cloudFallbackManager.isEnabled()) {
                            val cloudResponse = cloudFallbackManager.inference(
                                image = screenData.bitmap,
                                prompt = userInput,
                                uiTree = screenData.uiTreeText
                            )
                            
                            finalResponse = cloudResponse
                            _lastResponse.value = cloudResponse
                            Timber.d("云端推理完成: action=${cloudResponse.action}, confidence=${cloudResponse.confidence}")
                            
                            stateMachine.handleEvent(AgentEvent.CloudReasoningComplete(cloudResponse))
                            _executionResult.value = "✅ 云端推理成功"
                        } else {
                            Timber.w("云端服务未启用，使用本地推理结果")
                            _executionResult.value = "⚠️ 云端未启用，使用本地结果"
                        }
                    } catch (e: CloudApiException) {
                        Timber.e(e, "云端推理失败，使用本地结果")
                        _executionResult.value = "❌ 云端失败: ${e.message}，使用本地结果"
                    }
                }
                
                // 执行真实操作
                Timber.d("开始执行动作: ${finalResponse.action}")
                val executionResult = actionExecutor.execute(finalResponse)
                
                when (executionResult) {
                    is ExecutionResult.Success -> {
                        Timber.d("执行成功: ${executionResult.message}")
                        _executionResult.value = "✅ ${executionResult.message}"
                        stateMachine.handleEvent(AgentEvent.ExecutionComplete)
                    }
                    is ExecutionResult.Failure -> {
                        Timber.e("执行失败: ${executionResult.message}")
                        _executionResult.value = "❌ ${executionResult.message}"
                        stateMachine.handleEvent(AgentEvent.Error(
                            Exception(executionResult.message),
                            executionResult.message
                        ))
                    }
                }

            } catch (e: Exception) {
                Timber.e(e, "推理测试失败")
                _executionResult.value = "❌ 异常: ${e.message}"
                stateMachine.handleEvent(AgentEvent.Error(e, e.message ?: "未知错误"))
            }
        }
    }
    
    /**
     * 尝试捕获真实屏幕数据
     */
    private suspend fun captureRealScreenData(): ScreenData? {
        return try {
            val service = EdgeAgentAccessibilityService.getInstance()
            service?.captureScreenData()
        } catch (e: Exception) {
            Timber.e(e, "捕获真实屏幕数据失败")
            null
        }
    }
    
    /**
     * 创建 Mock 屏幕数据
     */
    private fun createMockScreenData(): ScreenData {
        val testBitmap = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
        return ScreenData(
            bitmap = testBitmap,
            uiTreeText = "Mock UI Tree",
            screenWidth = 1080,
            screenHeight = 2400,
            currentPackage = "com.tencent.edgeagent"
        )
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("MainViewModel 清理")
        localModelEngine.release()
    }
}
