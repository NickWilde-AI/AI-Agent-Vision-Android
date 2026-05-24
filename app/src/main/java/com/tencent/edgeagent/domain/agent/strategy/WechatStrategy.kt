package com.tencent.edgeagent.domain.agent.strategy

import com.tencent.edgeagent.domain.agent.ConversationTurn
import com.tencent.edgeagent.data.execution.ExecutionResult
import com.tencent.edgeagent.domain.agent.multi.AgentPlan
import com.tencent.edgeagent.domain.agent.multi.SafetyMode
import com.tencent.edgeagent.domain.agent.multi.TaskType
import com.tencent.edgeagent.domain.model.ActionParams
import com.tencent.edgeagent.domain.model.ActionType
import com.tencent.edgeagent.domain.model.AgentResponse
import com.tencent.edgeagent.domain.model.InferenceSource
import com.tencent.edgeagent.domain.model.ScreenData

class WechatStrategy : AppStrategy {
    override val id: String = "wechat_draft_state_machine"

    override fun matches(plan: AgentPlan, screenData: ScreenData): Boolean {
        return plan.taskType == TaskType.WECHAT_DRAFT ||
            plan.targetPackage == WECHAT_PACKAGE ||
            screenData.currentPackage == WECHAT_PACKAGE
    }

    override fun promptHints(
        plan: AgentPlan,
        screenData: ScreenData,
        history: List<ConversationTurn>
    ): List<String> {
        val state = inferState(screenData, history)
        return listOf(
            "当前微信草稿状态: $state。",
            "只允许完成草稿准备，不允许自动点击发送按钮。",
            "如果已经输入正文并看到发送按钮，返回 NO_ACTION，提示用户手动发送。",
            "联系人搜索和聊天页定位失败时，优先使用搜索入口、BACK 或 WAIT，不要反复点击同一坐标。"
        )
    }

    override fun rewriteResponse(
        plan: AgentPlan,
        screenData: ScreenData,
        history: List<ConversationTurn>,
        response: AgentResponse
    ): StrategyRewrite {
        if (plan.safetyMode != SafetyMode.DRAFT_ONLY && plan.taskType != TaskType.WECHAT_DRAFT) {
            return StrategyRewrite.Keep(response)
        }

        val state = inferState(screenData, history)
        if (state == WechatDraftState.DRAFT_READY && response.action == ActionType.CLICK) {
            return StrategyRewrite.Rewritten(
                response = noAction("微信草稿已准备完成，停止在发送前等待用户确认。", response),
                reason = "draft_ready_stop_before_send"
            )
        }

        if (isFinalSendAction(response, screenData.uiTreeText)) {
            return StrategyRewrite.Rewritten(
                response = noAction("已拦截微信发送动作：Agent 只准备草稿，不自动发送。", response),
                reason = "blocked_wechat_send"
            )
        }

        return StrategyRewrite.Keep(response)
    }

    fun inferState(
        screenData: ScreenData,
        history: List<ConversationTurn>
    ): WechatDraftState {
        if (screenData.currentPackage != WECHAT_PACKAGE) return WechatDraftState.NEED_OPEN_WECHAT

        val uiText = screenData.uiTreeText.orEmpty()
        val hasSendButton = containsAny(uiText, SEND_WORDS)
        val hasEditable = uiText.contains("[editable]") ||
            uiText.contains("EditText", ignoreCase = true) ||
            uiText.contains("输入", ignoreCase = true)
        val hasSearch = containsAny(uiText, SEARCH_WORDS)
        val lastInputSuccess = history.lastOrNull()?.let { turn ->
            turn.llmResponse.action == ActionType.INPUT_TEXT &&
                turn.executionResult is ExecutionResult.Success
        } == true

        return when {
            lastInputSuccess && hasSendButton -> WechatDraftState.DRAFT_READY
            hasEditable -> WechatDraftState.NEED_INPUT_TEXT
            hasSearch -> WechatDraftState.NEED_SEARCH_CONTACT
            uiText.isBlank() -> WechatDraftState.UNKNOWN
            else -> WechatDraftState.NEED_NAVIGATE_CHAT
        }
    }

    fun isFinalSendAction(response: AgentResponse, uiTreeText: String?): Boolean {
        val params = response.actionParams
        if (response.action == ActionType.CLICK && params is ActionParams.Click) {
            val description = params.description.lowercase()
            if (SEND_WORDS.any { description.contains(it.lowercase()) }) return true
            if (isCoordinateInsideSendNode(params.x, params.y, uiTreeText)) return true
        }

        if (response.action == ActionType.NO_ACTION) return false
        return false
    }

    private fun isCoordinateInsideSendNode(x: Int, y: Int, uiTreeText: String?): Boolean {
        if (uiTreeText.isNullOrBlank()) return false
        return uiTreeText.lineSequence()
            .filter { line -> SEND_WORDS.any { word -> line.contains(word, ignoreCase = true) } }
            .any { line ->
                val bounds = BOUNDS_REGEX.find(line)?.groupValues ?: return@any false
                val left = bounds[1].toIntOrNull() ?: return@any false
                val top = bounds[2].toIntOrNull() ?: return@any false
                val right = bounds[3].toIntOrNull() ?: return@any false
                val bottom = bounds[4].toIntOrNull() ?: return@any false
                x in left..right && y in top..bottom
            }
    }

    private fun noAction(message: String, original: AgentResponse): AgentResponse {
        return AgentResponse(
            source = InferenceSource.LOCAL_RAG,
            action = ActionType.NO_ACTION,
            actionParams = ActionParams.NoAction(message),
            confidence = 1.0f,
            inferenceTimeMs = 0L,
            rawOutput = "strategy:${id}; original=${original.action}; ${original.rawOutput.orEmpty()}",
            requiresConfirmation = true
        )
    }

    private fun containsAny(text: String, words: List<String>): Boolean {
        return words.any { text.contains(it, ignoreCase = true) }
    }

    companion object {
        private const val WECHAT_PACKAGE = "com.tencent.mm"
        private val SEND_WORDS = listOf("发送", "send")
        private val SEARCH_WORDS = listOf("搜索", "search", "查找")
        private val BOUNDS_REGEX = Regex("bounds=\\[(\\d+),(\\d+),(\\d+),(\\d+)]")
    }
}

enum class WechatDraftState {
    NEED_OPEN_WECHAT,
    NEED_SEARCH_CONTACT,
    NEED_NAVIGATE_CHAT,
    NEED_INPUT_TEXT,
    DRAFT_READY,
    UNKNOWN
}
