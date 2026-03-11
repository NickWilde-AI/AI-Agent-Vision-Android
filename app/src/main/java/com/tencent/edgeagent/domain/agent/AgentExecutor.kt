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
     * 
     * 修复点1: 支持检测 WAIT 重复
     * 修复点2: 降低检测阈值从3次到2次
     */
    private fun isRepeatingAction(
        history: List<ConversationTurn>,
        currentResponse: AgentResponse
    ): Boolean {
        if (history.isEmpty()) return false
        
        val lastTurn = history.lastOrNull() ?: return false
        
        // 检测1: 连续 2 次相同的 OPEN_APP（修改：从3次降到2次）
        if (history.size >= 1) {
            if (currentResponse.action == ActionType.OPEN_APP &&
                lastTurn.llmResponse.action == ActionType.OPEN_APP) {
                
                val currentParams = currentResponse.actionParams as? ActionParams.OpenApp
                val lastParams = lastTurn.llmResponse.actionParams as? ActionParams.OpenApp
                
                if (currentParams != null && lastParams != null) {
                    if (currentParams.packageName == lastParams.packageName) {
                        Timber.w("检测到连续 2 次打开同一应用: ${currentParams.packageName}")
                        return true
                    }
                }
            }
        }
        
        // 检测2: 连续 3 次 WAIT（新增：防止 WAIT 死循环）
        if (history.size >= 2) {
            val secondLastTurn = history.getOrNull(history.size - 2)
            
            if (currentResponse.action == ActionType.WAIT &&
                lastTurn.llmResponse.action == ActionType.WAIT &&
                secondLastTurn?.llmResponse?.action == ActionType.WAIT) {
                Timber.w("检测到连续 3 次 WAIT 操作，可能陷入死循环")
                return true
            }
        }
        
        return false
    }
    
    /**
     * 构建提示词（包含历史对话 + 当前 UI 树摘要）
     */
    private fun buildPrompt(
        userGoal: String,
        history: List<ConversationTurn>,
        currentRound: Int
    ): String {
        val prompt = StringBuilder()

        prompt.append("用户目标：$userGoal\n\n")
        prompt.append("当前是第 $currentRound 轮对话（最多 $MAX_ROUNDS 轮）。\n\n")

        if (history.isNotEmpty()) {
            prompt.append("历史操作记录：\n")
            history.forEach { turn ->
                val resultStr = when (turn.executionResult) {
                    is ExecutionResult.Success -> "✅ ${(turn.executionResult as ExecutionResult.Success).message}"
                    is ExecutionResult.Failure -> "❌ ${(turn.executionResult as ExecutionResult.Failure).message}"
                    null -> "⏳ 执行中"
                }
                // 附带当时的 UI 树摘要，帮助 LLM 理解前后变化
                val uiSummary = turn.screenData.uiTreeText
                    ?.lines()
                    ?.filter { it.contains("text='") || it.contains("desc='") }
                    ?.take(5)
                    ?.joinToString(" | ")
                    ?.take(120) ?: "(无 UI 数据)"
                prompt.append("  第 ${turn.round} 轮: ${turn.llmResponse.action} → $resultStr\n")
                prompt.append("    UI 摘要: $uiSummary\n")
            }
            prompt.append("\n")
        }

        // 当前屏幕包名（如有）
        val lastPackage = history.lastOrNull()?.screenData?.currentPackage
        if (lastPackage != null) {
            prompt.append("当前应用包名: $lastPackage\n\n")
        }

        prompt.append("【决策规则】\n")
        prompt.append("1. 优先分析 UI 结构文本（比截图更可靠）\n")
        prompt.append("2. 如果当前包名已是目标应用，不要再执行 OPEN_APP，直接在界面内操作\n")
        prompt.append("3. 如果已连续 WAIT 超过 2 次，改用其他操作（如 HOME 或 CLICK）\n")
        prompt.append("4. 如果 UI 树为空，执行 HOME 回到桌面重新开始\n")
        prompt.append("5. 任务完成后返回 NO_ACTION\n")
        prompt.append("6. 每次只返回一个操作步骤\n")

        return prompt.toString()
    }
    
    /**
     * 捕获屏幕数据
     * 如果 MediaProjection 不可用，返回包含真实 UI 树但空白 Bitmap 的 ScreenData，
     * 而不是 null，避免多轮对话因截图失败而中断。
     */
    private suspend fun captureScreen(): ScreenData? {
        return try {
            val service = EdgeAgentAccessibilityService.getInstance() ?: return null
            val data = service.captureScreenData()
            if (data != null) return data

            // 降级：直接从无障碍服务提取 UI 树，用空白 Bitmap
            Timber.w("captureScreenData 返回 null，降级为纯 UI 树模式")
            val rootNode = service.rootInActiveWindow
            val uiTreeText = rootNode?.let {
                com.tencent.edgeagent.data.perception.UITreeExtractor.getInstance()
                    .extractUITree(it)
            }
            val currentPackage = rootNode?.packageName?.toString()
            rootNode?.recycle()
            val displayMetrics = service.resources.displayMetrics
            val bitmap = android.graphics.Bitmap.createBitmap(
                displayMetrics.widthPixels, displayMetrics.heightPixels,
                android.graphics.Bitmap.Config.ARGB_8888
            )
            com.tencent.edgeagent.domain.model.ScreenData(
                bitmap = bitmap,
                uiTreeText = uiTreeText,
                screenWidth = displayMetrics.widthPixels,
                screenHeight = displayMetrics.heightPixels,
                currentPackage = currentPackage
            )
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
