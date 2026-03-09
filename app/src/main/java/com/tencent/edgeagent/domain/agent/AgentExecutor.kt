package com.tencent.edgeagent.domain.agent

import android.graphics.Bitmap
import com.tencent.edgeagent.data.cloud.CloudFallbackManager
import com.tencent.edgeagent.data.execution.ActionExecutor
import com.tencent.edgeagent.data.execution.ExecutionResult
import com.tencent.edgeagent.domain.model.*
import com.tencent.edgeagent.service.EdgeAgentAccessibilityService
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Agent 执行器 - 多轮对话 + 视觉反馈循环
 * 
 * 核心功能：
 * 1. 多轮对话：支持复杂的多步骤任务
 * 2. 视觉反馈：每次操作后截图验证
 * 3. 智能重试：失败后自动重试
 * 4. 任务分解：将复杂任务分解为多个步骤
 * 
 * 工作流程：
 * 用户："打开微信发送给 Nick Chen 消息"
 *   ↓
 * 第1轮：LLM 分析 → "先回到桌面" → 执行 HOME → 截图验证
 *   ↓
 * 第2轮：LLM 分析桌面 → "点击微信图标" → 执行点击 → 截图验证
 *   ↓
 * 第3轮：LLM 分析微信界面 → "点击搜索" → 执行点击 → 截图验证
 *   ↓
 * ... 持续交互直到任务完成
 */
class AgentExecutor private constructor() {

    private val cloudManager = CloudFallbackManager.getInstance()
    private val actionExecutor = ActionExecutor.getInstance()
    
    companion object {
        private const val MAX_ROUNDS = 10 // 最大对话轮数
        private const val SCREENSHOT_DELAY = 1000L // 截图延迟（等待界面加载）
        private const val OPEN_APP_DELAY = 2500L // 打开应用后的等待时间（让应用完全启动）
        
        @Volatile
        private var instance: AgentExecutor? = null
        
        fun getInstance(): AgentExecutor {
            return instance ?: synchronized(this) {
                instance ?: AgentExecutor().also { instance = it }
            }
        }
    }
    
    /**
     * 执行任务（多轮对话模式）
     * 
     * @param userGoal 用户目标（例如："打开微信发送给 Nick Chen 消息"）
     * @param onProgress 进度回调
     * @return 执行结果
     */
    suspend fun executeTask(
        userGoal: String,
        onProgress: (String) -> Unit = {}
    ): TaskExecutionResult {
        Timber.d("开始执行任务: $userGoal")
        onProgress("🚀 开始执行任务...")
        
        val conversationHistory = mutableListOf<ConversationTurn>()
        var currentRound = 0
        var isTaskComplete = false
        
        // 初始截图
        var currentScreenData = captureScreen()
        if (currentScreenData == null) {
            return TaskExecutionResult.Failure("无法捕获屏幕数据")
        }
        
        while (currentRound < MAX_ROUNDS && !isTaskComplete) {
            currentRound++
            Timber.d("第 $currentRound 轮对话")
            onProgress("🔄 第 $currentRound 轮分析...")
            
            try {
                // 构建提示词（包含历史对话）
                val prompt = buildPrompt(userGoal, conversationHistory, currentRound)
                
                // 调用 LLM 分析当前屏幕
                val response = cloudManager.inference(
                    image = currentScreenData!!.bitmap,
                    prompt = prompt,
                    uiTree = currentScreenData.uiTreeText
                )
                
                Timber.d("LLM 返回: action=${response.action}, reasoning=${response.rawOutput}")
                onProgress("💡 LLM: ${response.action}")
                
                // 记录对话
                conversationHistory.add(
                    ConversationTurn(
                        round = currentRound,
                        screenData = currentScreenData!!,
                        llmResponse = response,
                        executionResult = null
                    )
                )
                
                // 检查是否完成
                if (response.action == ActionType.NO_ACTION) {
                    Timber.d("任务完成")
                    isTaskComplete = true
                    onProgress("✅ 任务完成！")
                    break
                }
                
                // 检测重复操作（防止死循环）
                if (isRepeatingAction(conversationHistory, response)) {
                    Timber.w("检测到重复操作，强制等待...")
                    onProgress("⏸️ 检测到重复操作，等待应用启动...")
                    delay(OPEN_APP_DELAY)
                    
                    // 截图验证
                    currentScreenData = captureScreen()
                    if (currentScreenData == null) {
                        return TaskExecutionResult.Failure("无法捕获屏幕数据")
                    }
                    continue // 跳过执行，直接进入下一轮
                }
                
                // 执行操作
                onProgress("⚙️ 执行: ${response.action}")
                val executionResult = actionExecutor.execute(response)
                
                // 更新对话历史
                conversationHistory.last().executionResult = executionResult
                
                when (executionResult) {
                    is ExecutionResult.Success -> {
                        Timber.d("执行成功: ${executionResult.message}")
                        onProgress("✅ ${executionResult.message}")
                    }
                    is ExecutionResult.Failure -> {
                        Timber.e("执行失败: ${executionResult.message}")
                        onProgress("❌ ${executionResult.message}")
                        // 继续下一轮，让 LLM 处理失败情况
                    }
                }
                
                // 等待界面加载（OPEN_APP 需要更长时间）
                val waitTime = if (response.action == ActionType.OPEN_APP) {
                    OPEN_APP_DELAY
                } else {
                    SCREENSHOT_DELAY
                }
                delay(waitTime)
                
                // 截图验证
                onProgress("📸 截图验证...")
                currentScreenData = captureScreen()
                if (currentScreenData == null) {
                    return TaskExecutionResult.Failure("无法捕获屏幕数据")
                }
                
            } catch (e: Exception) {
                Timber.e(e, "第 $currentRound 轮执行失败")
                onProgress("❌ 错误: ${e.message}")
                return TaskExecutionResult.Failure("执行失败: ${e.message}")
            }
        }
        
        if (currentRound >= MAX_ROUNDS && !isTaskComplete) {
            return TaskExecutionResult.Failure("超过最大轮数限制")
        }
        
        return TaskExecutionResult.Success(
            rounds = currentRound,
            conversationHistory = conversationHistory
        )
    }
    
    /**
     * 检测是否重复操作（防止死循环）
     */
    private fun isRepeatingAction(
        history: List<ConversationTurn>,
        currentResponse: AgentResponse
    ): Boolean {
        if (history.size < 2) return false
        
        // 检查最近 2 轮是否都是相同的 OPEN_APP 操作
        val lastTurn = history.lastOrNull() ?: return false
        val secondLastTurn = history.getOrNull(history.size - 2) ?: return false
        
        if (currentResponse.action == ActionType.OPEN_APP &&
            lastTurn.llmResponse.action == ActionType.OPEN_APP &&
            secondLastTurn.llmResponse.action == ActionType.OPEN_APP) {
            
            // 检查是否是同一个应用
            val currentParams = currentResponse.actionParams as? ActionParams.OpenApp
            val lastParams = lastTurn.llmResponse.actionParams as? ActionParams.OpenApp
            val secondLastParams = secondLastTurn.llmResponse.actionParams as? ActionParams.OpenApp
            
            if (currentParams != null && lastParams != null && secondLastParams != null) {
                if (currentParams.packageName == lastParams.packageName &&
                    lastParams.packageName == secondLastParams.packageName) {
                    Timber.w("检测到连续 3 次打开同一应用: ${currentParams.packageName}")
                    return true
                }
            }
        }
        
        return false
    }
    
    /**
     * 构建提示词（包含历史对话）
     */
    private fun buildPrompt(
        userGoal: String,
        history: List<ConversationTurn>,
        currentRound: Int
    ): String {
        val prompt = StringBuilder()
        
        prompt.append("用户目标：$userGoal\n\n")
        prompt.append("当前是第 $currentRound 轮对话。\n\n")
        
        if (history.isNotEmpty()) {
            prompt.append("历史操作：\n")
            history.forEach { turn ->
                prompt.append("第 ${turn.round} 轮：${turn.llmResponse.action}")
                when (turn.executionResult) {
                    is ExecutionResult.Success -> prompt.append(" ✅ 成功")
                    is ExecutionResult.Failure -> prompt.append(" ❌ 失败: ${(turn.executionResult as ExecutionResult.Failure).message}")
                    null -> prompt.append(" ⏳ 执行中")
                }
                prompt.append("\n")
            }
            prompt.append("\n")
        }
        
        prompt.append("请分析当前屏幕截图和 UI 结构，决定下一步操作。\n")
        prompt.append("如果任务已完成，返回 NO_ACTION。\n")
        prompt.append("如果需要继续，返回下一步操作。\n")
        prompt.append("\n")
        prompt.append("⚠️ 重要提示：\n")
        prompt.append("1. 如果应用正在启动（黑屏/加载中），请返回 WAIT 等待，不要重复打开应用\n")
        prompt.append("2. 如果已经在目标应用内，请继续下一步操作，不要返回 OPEN_APP\n")
        prompt.append("3. 避免重复相同的操作\n")
        
        return prompt.toString()
    }
    
    /**
     * 捕获屏幕数据
     */
    private suspend fun captureScreen(): ScreenData? {
        return try {
            val service = EdgeAgentAccessibilityService.getInstance()
            service?.captureScreenData()
        } catch (e: Exception) {
            Timber.e(e, "捕获屏幕失败")
            null
        }
    }
}

/**
 * 对话轮次
 */
data class ConversationTurn(
    val round: Int,
    val screenData: ScreenData,
    val llmResponse: AgentResponse,
    var executionResult: ExecutionResult?
)

/**
 * 任务执行结果
 */
sealed class TaskExecutionResult {
    data class Success(
        val rounds: Int,
        val conversationHistory: List<ConversationTurn>
    ) : TaskExecutionResult()
    
    data class Failure(
        val reason: String
    ) : TaskExecutionResult()
}
