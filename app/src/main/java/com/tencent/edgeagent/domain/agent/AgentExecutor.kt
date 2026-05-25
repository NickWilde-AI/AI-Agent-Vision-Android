package com.tencent.edgeagent.domain.agent

import android.graphics.Bitmap
import com.tencent.edgeagent.data.cloud.CloudFallbackManager
import com.tencent.edgeagent.data.execution.ActionExecutor
import com.tencent.edgeagent.data.execution.ExecutionResult
import com.tencent.edgeagent.data.trace.AgentTraceStore
import com.tencent.edgeagent.data.vision.BasicLocalVisionEngine
import com.tencent.edgeagent.data.vision.LocalVisionObservation
import com.tencent.edgeagent.domain.agent.multi.AgentPlan
import com.tencent.edgeagent.domain.agent.multi.AgentReflection
import com.tencent.edgeagent.domain.agent.multi.PlannerAgent
import com.tencent.edgeagent.domain.agent.multi.ReflectionAgent
import com.tencent.edgeagent.domain.agent.multi.TaskType
import com.tencent.edgeagent.domain.agent.safety.ActionGuard
import com.tencent.edgeagent.domain.agent.safety.GuardResult
import com.tencent.edgeagent.domain.agent.strategy.AppStrategyRegistry
import com.tencent.edgeagent.domain.agent.strategy.StrategyRewrite
import com.tencent.edgeagent.domain.model.*
import com.tencent.edgeagent.service.EdgeAgentAccessibilityService
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Agent 执行器 - 多轮对话 + 视觉反馈循环
 * 
 * 核心功能：
 * 1. 多轮对话：支持复杂的多步骤任务
 * 2. 视觉反馈：每次操作后屏幕捕获验证
 * 3. 智能重试：失败后自动重试
 * 4. 任务分解：将复杂任务分解为多个步骤
 * 
 * 工作流程：
 * 用户："打开微信发送给 Nick Chen 消息"
 *   ↓
 * 第1轮：LLM 分析 → "先打开微信" → 执行 OPEN_APP → 屏幕捕获验证
 *   ↓
 * 第2轮：LLM 分析微信界面 → "点击搜索" → 执行点击 → 屏幕捕获验证
 *   ↓
 * 第3轮：LLM 分析搜索入口 → "输入联系人" → 执行输入 → 屏幕捕获验证
 *   ↓
 * ... 持续交互直到任务完成
 */
class AgentExecutor private constructor() {

    private val cloudManager = CloudFallbackManager.getInstance()
    private val actionExecutor = ActionExecutor.getInstance()
    private val plannerAgent = PlannerAgent.getInstance()
    private val reflectionAgent = ReflectionAgent.getInstance()
    private val actionGuard = ActionGuard.getInstance()
    private val traceStore = AgentTraceStore.getInstance()
    private val localVisionEngine = BasicLocalVisionEngine.getInstance()
    private val strategyRegistry = AppStrategyRegistry.getInstance()
    
    companion object {
        private const val VISION_AGENT_PACKAGE = "com.tencent.edgeagent"
        private const val MAX_ROUNDS = 16 // 最大对话轮数
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
        onProgress: (String) -> Unit = {},
        onDecision: (AgentResponse) -> Unit = {}
    ): TaskExecutionResult {
        Timber.i("[AgentTask] start goal=$userGoal")
        onProgress("[0/$MAX_ROUNDS] 开始任务：$userGoal")
        val traceId = traceStore.startSession(userGoal)

        fun fail(reason: String): TaskExecutionResult.Failure {
            traceStore.finishSession(traceId, success = false, reason = reason)
            return TaskExecutionResult.Failure(reason)
        }
        
        val conversationHistory = mutableListOf<ConversationTurn>()
        var currentRound = 0
        var isTaskComplete = false
        
        // 初始截图
        var currentScreenData = captureScreen()
        if (currentScreenData == null) {
            return fail("无法捕获屏幕数据")
        }

        val plan = plannerAgent.plan(userGoal, currentScreenData.currentPackage)
        traceStore.recordPlan(traceId, plan)
        onProgress("[0/$MAX_ROUNDS] 规划完成：${plan.taskType} / ${plan.safetyMode}")
        
        val maxRounds = minOf(MAX_ROUNDS, plan.maxRounds)
        while (currentRound < maxRounds && !isTaskComplete) {
            currentRound++
            val screenData = currentScreenData ?: return fail("无法捕获屏幕数据")
            Timber.i("[AgentTask] round=$currentRound package=${screenData.currentPackage} uiTree=${hasUsableUiTree(screenData.uiTreeText)} screenshot=${screenData.hasRealScreenshot}")
            onProgress("[$currentRound/$maxRounds] 分析当前屏幕：${screenData.currentPackage ?: "未知包名"}")
            
            try {
                if (isPlanAlreadyComplete(plan, screenData, conversationHistory)) {
                    val response = AgentResponse(
                        source = InferenceSource.STRATEGY,
                        action = ActionType.NO_ACTION,
                        actionParams = ActionParams.NoAction("目标已达成"),
                        confidence = 1.0f,
                        inferenceTimeMs = 0L,
                        rawOutput = "strategy_plan_complete:${plan.taskType}",
                        requiresConfirmation = false
                    )
                    onDecision(response)
                    traceStore.recordStep(
                        sessionId = traceId,
                        round = currentRound,
                        screenData = screenData,
                        response = response,
                        executionResult = ExecutionResult.Success("目标已达成"),
                        reflection = null,
                        note = "plan_complete_before_decision"
                    )
                    onProgress("[$currentRound/$maxRounds] 目标已达成")
                    cleanupAfterCompletedPlan(traceId, currentRound + 1, plan, screenData, onProgress)
                    isTaskComplete = true
                    break
                }

                val reflection = reflectionAgent.reflect(conversationHistory, screenData)
                if (reflection.shouldAbort) {
                    val reason = reflection.abortReason ?: "反思 Agent 停止任务"
                    traceStore.recordStep(
                        sessionId = traceId,
                        round = currentRound,
                        screenData = screenData,
                        response = null,
                        executionResult = null,
                        reflection = reflection,
                        note = reason
                    )
                    return fail(reason)
                }

                val localObservation = localVisionEngine.observe(screenData)
                val strategyHints = strategyRegistry.promptHints(plan, screenData, conversationHistory)
                val deterministicFallback = buildDeterministicFallbackIfNeeded(
                    userGoal,
                    screenData,
                    currentRound,
                    conversationHistory
                )

                // 构建提示词（包含历史对话）
                val rawResponse = run {
                    val prompt = buildPrompt(
                        userGoal = userGoal,
                        plan = plan,
                        reflection = reflection,
                        history = conversationHistory,
                        currentRound = currentRound,
                        currentScreenData = screenData,
                        localObservation = localObservation,
                        strategyHints = strategyHints
                    )

                    // 调用 LLM 分析当前屏幕
                    runCatching {
                        cloudManager.inference(
                            image = screenData.bitmap,
                            prompt = prompt,
                            uiTree = screenData.uiTreeText
                        )
                    }.getOrElse { error ->
                        if (deterministicFallback != null) {
                            Timber.w(error, "[AgentTask] model decision failed, using deterministic fallback")
                            onProgress("[$currentRound/$maxRounds] 模型决策失败，使用安全兜底：${error.message}")
                            deterministicFallback
                        } else {
                            throw error
                        }
                    }
                }
                val guardedResponse = when (val guard = actionGuard.guard(plan, rawResponse, screenData.currentPackage, screenData.uiTreeText)) {
                    is GuardResult.Allowed -> guard.response
                    is GuardResult.Blocked -> {
                        onProgress("[$currentRound/$maxRounds] 安全拦截：${guard.reason}")
                        guard.safeResponse
                    }
                }
                val response = when (
                    val rewrite = strategyRegistry.rewriteResponse(
                        plan,
                        screenData,
                        conversationHistory,
                        guardedResponse
                    )
                ) {
                    is StrategyRewrite.Keep -> rewrite.response
                    is StrategyRewrite.Rewritten -> {
                        onProgress("[$currentRound/$maxRounds] 策略改写：${rewrite.reason}")
                        rewrite.response
                    }
                }
                
                Timber.i("[AgentTask] round=$currentRound llm action=${response.action} confidence=${response.confidence} params=${response.actionParams}")
                onDecision(response)
                onProgress("[$currentRound/$maxRounds] 决策：${describeAction(response)}")
                
                // 检查是否完成
                if (response.action == ActionType.NO_ACTION) {
                    Timber.d("任务完成")
                    isTaskComplete = true
                    traceStore.recordStep(
                        sessionId = traceId,
                        round = currentRound,
                        screenData = screenData,
                        response = response,
                        executionResult = ExecutionResult.Success("无需操作"),
                        reflection = reflection,
                        note = "task_complete_or_waiting_user"
                    )
                    onProgress("[$currentRound/$maxRounds] 任务完成")
                    break
                }
                
                // 检测重复操作（防止死循环）：这里必须使用“加入当前响应之前”的历史
                if (isRepeatingAction(conversationHistory, response)) {
                    Timber.w("检测到重复操作，强制等待...")
                    onProgress("[$currentRound/$maxRounds] 检测到重复动作，等待界面变化")
                    traceStore.recordStep(
                        sessionId = traceId,
                        round = currentRound,
                        screenData = screenData,
                        response = response,
                        executionResult = null,
                        reflection = reflection,
                        note = "repeat_action_skipped"
                    )
                    delay(OPEN_APP_DELAY)

                    // 截图验证
                    currentScreenData = captureScreen()
                    if (currentScreenData == null) {
                        return fail("无法捕获屏幕数据")
                    }
                    continue // 跳过执行，直接进入下一轮
                }

                // 记录对话
                conversationHistory.add(
                    ConversationTurn(
                        round = currentRound,
                        screenData = screenData,
                        llmResponse = response,
                        executionResult = null
                    )
                )
                
                // 执行操作
                onProgress("[$currentRound/$maxRounds] 执行：${describeAction(response)}")
                val executionResult = actionExecutor.execute(response)
                
                // 更新对话历史
                conversationHistory.last().executionResult = executionResult
                traceStore.recordStep(
                    sessionId = traceId,
                    round = currentRound,
                    screenData = screenData,
                    response = response,
                    executionResult = executionResult,
                    reflection = reflection
                )

                if (shouldCleanupOpenAppImmediately(plan, response, executionResult)) {
                    val afterOpenScreen = captureScreen() ?: screenData
                    cleanupAfterCompletedPlan(traceId, currentRound + 1, plan, afterOpenScreen, onProgress)
                    isTaskComplete = true
                    break
                }
                if (shouldCompleteDeviceControlImmediately(plan, response, executionResult)) {
                    onProgress("[$currentRound/$maxRounds] 设备控制已完成")
                    isTaskComplete = true
                    break
                }
                if (shouldCompleteSystemNavigationImmediately(plan, response, executionResult)) {
                    onProgress("[$currentRound/$maxRounds] 系统导航已完成")
                    isTaskComplete = true
                    break
                }
                
                when (executionResult) {
                    is ExecutionResult.Success -> {
                        Timber.d("执行成功: ${executionResult.message}")
                        onProgress("[$currentRound/$maxRounds] 执行成功：${executionResult.message}")
                    }
                    is ExecutionResult.Failure -> {
                        Timber.e("执行失败: ${executionResult.message}")
                        onProgress("[$currentRound/$maxRounds] 执行失败：${executionResult.message}")
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
                onProgress("[$currentRound/$maxRounds] 屏幕捕获验证并提取 UI 树")
                currentScreenData = captureScreen()
                if (currentScreenData == null) {
                    return fail("无法捕获屏幕数据")
                }
                
            } catch (e: Exception) {
                Timber.e(e, "第 $currentRound 轮执行失败")
                onProgress("[$currentRound/$maxRounds] 异常：${e.message}")
                traceStore.recordStep(
                    sessionId = traceId,
                    round = currentRound,
                    screenData = currentScreenData,
                    response = null,
                    executionResult = ExecutionResult.Failure("异常：${e.message}"),
                    reflection = null,
                    note = "exception"
                )
                return fail("执行失败: ${e.message}")
            }
        }
        
        if (currentRound >= maxRounds && !isTaskComplete) {
            return fail("超过最大轮数限制")
        }
        
        if (isTaskComplete) {
            traceStore.finishSession(traceId, success = true, reason = "任务完成")
        }
        return TaskExecutionResult.Success(
            rounds = currentRound,
            conversationHistory = conversationHistory
        )
    }
    
    /**
     * 确定性兜底路由。
     *
     * 模型仍然拥有第一决策权；只有云端决策失败，或目标 App 已进入但连续不可观测时，
     * 才使用这个低风险兜底，避免任务直接中断。
     */
    private fun buildDeterministicFallbackIfNeeded(
        userGoal: String,
        screenData: ScreenData,
        currentRound: Int,
        history: List<ConversationTurn>
    ): AgentResponse? {
        val packageName = resolveTargetPackage(userGoal)
        if (packageName != null &&
            screenData.currentPackage == packageName &&
            !hasUsableUiTree(screenData.uiTreeText) &&
            !screenData.hasRealScreenshot &&
            hasRecentBlankObservations(history, packageName)
        ) {
            Timber.w("[AgentTask] blank observation in target app, BACK to recover package=$packageName round=$currentRound")
            return AgentResponse(
                source = InferenceSource.LOCAL_RAG,
                action = ActionType.BACK,
                actionParams = ActionParams.NoAction(message = "当前目标 App 无有效 UI 树且无真实截图，先返回恢复可观测状态"),
                confidence = 1.0f,
                inferenceTimeMs = 0L,
                rawOutput = "deterministic_recover_blank_target_app:$packageName",
                requiresConfirmation = false
            )
        }

        if (screenData.currentPackage != "com.tencent.edgeagent") return null

        if (packageName == null) return null
        Timber.i("[AgentTask] deterministic fallback OPEN_APP package=$packageName round=$currentRound goal=$userGoal")

        return AgentResponse(
            source = InferenceSource.LOCAL_RAG,
            action = ActionType.OPEN_APP,
            actionParams = ActionParams.OpenApp(packageName = packageName),
            confidence = 1.0f,
            inferenceTimeMs = 0L,
            rawOutput = "deterministic_open_app:$packageName",
            requiresConfirmation = false
        )
    }

    private fun hasRecentBlankObservations(history: List<ConversationTurn>, packageName: String): Boolean {
        return history.asReversed()
            .take(2)
            .count { turn ->
                turn.screenData.currentPackage == packageName &&
                    !turn.screenData.hasRealScreenshot &&
                    !hasUsableUiTree(turn.screenData.uiTreeText)
            } >= 1
    }

    private fun resolveTargetPackage(userGoal: String): String? {
        val normalized = userGoal.lowercase()
        return when {
            userGoal.contains("微信") || normalized.contains("wechat") || userGoal.contains("发消息") -> "com.tencent.mm"
            userGoal.contains("美团") -> "com.sankuai.meituan"
            userGoal.contains("支付宝") -> "com.eg.android.AlipayGphone"
            userGoal.contains("淘宝") -> "com.taobao.taobao"
            userGoal.contains("抖音") -> "com.ss.android.ugc.aweme"
            userGoal.contains("QQ", ignoreCase = true) -> "com.tencent.mobileqq"
            userGoal.contains("相机") || normalized.contains("camera") -> "com.android.camera"
            userGoal.contains("电话") || userGoal.contains("联系人") -> "com.android.contacts"
            userGoal.contains("设置") -> "com.android.settings"
            userGoal.contains("浏览器") -> "com.android.browser"
            normalized.contains("chrome") -> "com.android.chrome"
            else -> null
        }
    }

    private fun isPlanAlreadyComplete(
        plan: AgentPlan,
        screenData: ScreenData,
        history: List<ConversationTurn>
    ): Boolean {
        val targetPackage = plan.targetPackage ?: return false
        if (plan.taskType != TaskType.OPEN_APP) return false
        if (screenData.currentPackage != targetPackage) return false
        return history.isEmpty() || history.any { turn ->
            turn.llmResponse.action == ActionType.OPEN_APP &&
                turn.executionResult is ExecutionResult.Success
        }
    }

    private fun shouldCleanupOpenAppImmediately(
        plan: AgentPlan,
        response: AgentResponse,
        executionResult: ExecutionResult
    ): Boolean {
        if (plan.taskType != TaskType.OPEN_APP) return false
        if (executionResult !is ExecutionResult.Success) return false
        if (response.action != ActionType.OPEN_APP) return false
        val params = response.actionParams as? ActionParams.OpenApp ?: return false
        val targetPackage = plan.targetPackage ?: return false
        return params.packageName == targetPackage && targetPackage != VISION_AGENT_PACKAGE
    }

    private fun shouldCompleteDeviceControlImmediately(
        plan: AgentPlan,
        response: AgentResponse,
        executionResult: ExecutionResult
    ): Boolean {
        return plan.taskType == TaskType.DEVICE_CONTROL &&
            response.action == ActionType.DEVICE_CONTROL &&
            executionResult is ExecutionResult.Success
    }

    private fun shouldCompleteSystemNavigationImmediately(
        plan: AgentPlan,
        response: AgentResponse,
        executionResult: ExecutionResult
    ): Boolean {
        return plan.taskType == TaskType.SYSTEM_NAVIGATION &&
            response.action in setOf(ActionType.BACK, ActionType.HOME, ActionType.RECENTS) &&
            executionResult is ExecutionResult.Success
    }

    private suspend fun cleanupAfterCompletedPlan(
        traceId: String,
        round: Int,
        plan: AgentPlan,
        screenData: ScreenData,
        onProgress: (String) -> Unit
    ) {
        val targetPackage = plan.targetPackage ?: return
        if (plan.taskType != TaskType.OPEN_APP) return
        if (targetPackage == VISION_AGENT_PACKAGE) return
        if (screenData.currentPackage == VISION_AGENT_PACKAGE) return

        val response = AgentResponse(
            source = InferenceSource.STRATEGY,
            action = ActionType.BACK,
            actionParams = ActionParams.NoAction("OPEN_APP 验证完成，返回 Agent 控制台"),
            confidence = 1.0f,
            inferenceTimeMs = 0L,
            rawOutput = "strategy_cleanup_after_open_app:$targetPackage",
            requiresConfirmation = false
        )
        onProgress("[$round] 打开成功，返回 Agent 控制台")
        val result = actionExecutor.execute(response)
        traceStore.recordStep(
            sessionId = traceId,
            round = round,
            screenData = screenData,
            response = response,
            executionResult = result,
            reflection = null,
            note = "post_task_cleanup"
        )
        delay(700)

        val afterPackage = captureScreen()?.currentPackage
        if (afterPackage != VISION_AGENT_PACKAGE) {
            Timber.w("[AgentTask] cleanup BACK did not return to Agent, package=$afterPackage")
            val reopenAgent = AgentResponse(
                source = InferenceSource.STRATEGY,
                action = ActionType.OPEN_APP,
                actionParams = ActionParams.OpenApp(packageName = VISION_AGENT_PACKAGE),
                confidence = 1.0f,
                inferenceTimeMs = 0L,
                rawOutput = "strategy_reopen_agent_after_cleanup:$afterPackage",
                requiresConfirmation = false
            )
            val reopenResult = actionExecutor.execute(reopenAgent)
            traceStore.recordStep(
                sessionId = traceId,
                round = round + 1,
                screenData = captureScreen() ?: screenData,
                response = reopenAgent,
                executionResult = reopenResult,
                reflection = null,
                note = "post_task_reopen_agent"
            )
        }
    }

    private fun describeAction(response: AgentResponse): String {
        val detail = when (val params = response.actionParams) {
            is ActionParams.Click -> "点击 ${params.description.ifBlank { "(${params.x},${params.y})" }}"
            is ActionParams.InputText -> "输入文本：${params.text}"
            is ActionParams.OpenApp -> "打开应用：${params.packageName}"
            is ActionParams.Swipe -> "滑动 (${params.startX},${params.startY})→(${params.endX},${params.endY})"
            is ActionParams.Wait -> "等待 ${params.durationMs}ms"
            is ActionParams.LongClick -> "长按 (${params.x},${params.y})"
            is ActionParams.DeviceControl -> "设备控制：${params.controlType}"
            is ActionParams.NoAction -> params.message.ifBlank { response.action.name }
        }
        return "${response.action} · $detail · 置信度=${"%.2f".format(response.confidence)}"
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
        plan: AgentPlan,
        reflection: AgentReflection,
        history: List<ConversationTurn>,
        currentRound: Int,
        currentScreenData: ScreenData,
        localObservation: LocalVisionObservation,
        strategyHints: List<String>
    ): String {
        val prompt = StringBuilder()

        prompt.append("用户目标：$userGoal\n\n")
        prompt.append("当前是第 $currentRound 轮对话（最多 $MAX_ROUNDS 轮）。\n")
        prompt.append("当前屏幕尺寸: ${currentScreenData.screenWidth}x${currentScreenData.screenHeight}\n")
        prompt.append("当前应用包名: ${currentScreenData.currentPackage ?: "未知"}\n")
        prompt.append("当前 UI 树是否可用: ${hasUsableUiTree(currentScreenData.uiTreeText)}\n")
        prompt.append("当前真实截图是否可用: ${currentScreenData.hasRealScreenshot}\n")
        prompt.append("\n")
        prompt.append(plan.toPromptText())
        prompt.append(reflection.toPromptText())
        prompt.append(localObservation.toPromptText())
        if (strategyHints.isNotEmpty()) {
            prompt.append("【App 专项策略】\n")
            strategyHints.forEach { prompt.append("- $it\n") }
        }
        prompt.append("\n")
        if (!currentScreenData.hasRealScreenshot) {
            prompt.append("重要：当前截图只是空白占位图，不能据此判断页面视觉内容、场景、室内/室外或图片内容；只能依据 UI 树、包名和历史操作决策。\n")
        }
        if (!hasUsableUiTree(currentScreenData.uiTreeText)) {
            prompt.append("重要：当前 UI 树没有有效节点。不要臆测页面内容；除非已有明确坐标依据，否则优先 WAIT、BACK 或使用系统级导航恢复可观测状态。\n")
        }
        prompt.append("\n")

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

        prompt.append("【决策规则】\n")
        prompt.append("1. 你是通用 Android Agent，不要套用固定 App 流程；根据当前屏幕和用户目标动态决定下一步。\n")
        prompt.append("2. 优先使用 UI 树中的可见文本、contentDescription、viewId、bounds/center 来定位元素。\n")
        prompt.append("3. 当前已经在目标 App 内时，不要再 OPEN_APP；直接继续界面内操作。\n")
        prompt.append("4. 需要查找对象时，先找到搜索入口；搜索框获得焦点后，用 INPUT_TEXT 输入搜索关键词。\n")
        prompt.append("5. INPUT_TEXT 执行层会在输入后尝试收起键盘；如果键盘仍遮挡目标区域，下一轮可返回 BACK 收起。\n")
        prompt.append("6. 需要发送/提交内容时，先点击输入框，再 INPUT_TEXT 输入正文；只有安全模式允许时才点击最终发送/提交按钮。\n")
        prompt.append("7. 如果没有看到目标元素，可以 WAIT、SWIPE、BACK 或点击合理的导航入口；不要连续重复同一无效动作。\n")
        prompt.append("8. UI 树为空且没有真实截图时，不要根据空白图或想象判断当前页面；最多等待一轮，之后应 BACK 或使用可确定的系统导航恢复可操作状态。\n")
        prompt.append("9. 如果规划要求草稿模式或用户确认，不要点击最终发送/支付/提交按钮；准备好后返回 NO_ACTION。\n")
        prompt.append("10. 任务完成后返回 NO_ACTION。\n")
        prompt.append("11. 每次只返回一个最小可执行操作，等待下一轮屏幕捕获验证。\n")

        return prompt.toString()
    }

    private fun hasUsableUiTree(uiTreeText: String?): Boolean {
        if (uiTreeText.isNullOrBlank()) return false
        if (uiTreeText.contains("Error:")) return false
        val clickableCount = Regex("Clickable Elements \\((\\d+)\\)")
            .find(uiTreeText)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull() ?: 0
        val hasNodeContent = uiTreeText
            .lineSequence()
            .dropWhile { !it.startsWith("Node Tree:") }
            .drop(1)
            .any { line ->
                val trimmed = line.trim()
                trimmed.startsWith("[") && (
                    trimmed.contains(" text='") ||
                        trimmed.contains(" desc='") ||
                        trimmed.contains(" id='") ||
                        trimmed.contains("[clickable]") ||
                        trimmed.contains("[editable]") ||
                        trimmed.contains("[scrollable]")
                    )
            }
        return clickableCount > 0 || hasNodeContent
    }
    
    /**
     * 捕获屏幕数据
     * 如果 MediaProjection 不可用，返回包含真实 UI 树但空白 Bitmap 的 ScreenData，
     * 而不是 null，避免多轮对话因屏幕捕获失败而中断。
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
                currentPackage = currentPackage,
                hasRealScreenshot = false
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
