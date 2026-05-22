package com.tencent.edgeagent.domain.agent.multi

import com.tencent.edgeagent.data.rag.LocalRagEngine
import timber.log.Timber

/**
 * Planner Agent: classifies the user goal and retrieves local strategy memory.
 */
class PlannerAgent private constructor(
    private val ragEngine: LocalRagEngine
) {

    fun plan(goal: String, currentPackage: String?): AgentPlan {
        val targetPackage = resolveTargetPackage(goal)
        val taskType = resolveTaskType(goal, targetPackage)
        val safetyMode = resolveSafetyMode(goal, taskType)
        val queryPackage = currentPackage ?: targetPackage
        val knowledge = ragEngine.buildContext(goal, queryPackage)
        val constraints = buildConstraints(taskType, safetyMode)

        return AgentPlan(
            goal = goal,
            taskType = taskType,
            targetPackage = targetPackage,
            safetyMode = safetyMode,
            maxRounds = if (taskType == TaskType.WECHAT_DRAFT) 12 else 16,
            localKnowledge = knowledge,
            constraints = constraints
        ).also {
            Timber.i("[PlannerAgent] taskType=${it.taskType} target=${it.targetPackage} safety=${it.safetyMode}")
        }
    }

    private fun resolveTaskType(goal: String, targetPackage: String?): TaskType {
        val normalized = goal.lowercase()
        return when {
            targetPackage == "com.tencent.mm" &&
                (goal.contains("发") || goal.contains("消息") || goal.contains("回复")) -> TaskType.WECHAT_DRAFT
            goal.contains("音量") || goal.contains("亮度") || normalized.contains("wifi") ||
                goal.contains("蓝牙") || goal.contains("飞行模式") -> TaskType.DEVICE_CONTROL
            goal.contains("搜索") || normalized.contains("search") -> TaskType.BROWSER_SEARCH
            goal.contains("打开") && targetPackage != null -> TaskType.OPEN_APP
            targetPackage != null -> TaskType.APP_NAVIGATION
            else -> TaskType.GENERAL
        }
    }

    private fun resolveSafetyMode(goal: String, taskType: TaskType): SafetyMode {
        val highRisk = listOf("发送", "发消息", "支付", "下单", "删除", "转账", "提交", "确认")
        return when {
            taskType == TaskType.WECHAT_DRAFT -> SafetyMode.DRAFT_ONLY
            highRisk.any { goal.contains(it) } -> SafetyMode.REQUIRE_CONFIRMATION
            else -> SafetyMode.AUTO
        }
    }

    private fun buildConstraints(taskType: TaskType, safetyMode: SafetyMode): List<String> {
        val constraints = mutableListOf<String>()
        constraints += "每轮只允许一个最小可执行动作。"
        constraints += "优先使用 UI 树的 bounds/center，不要猜坐标。"

        if (safetyMode == SafetyMode.REQUIRE_CONFIRMATION || safetyMode == SafetyMode.DRAFT_ONLY) {
            constraints += "不要自动点击发送、支付、下单、删除、提交、确认等最终动作。"
        }
        if (taskType == TaskType.WECHAT_DRAFT) {
            constraints += "微信任务只允许输入草稿；草稿完成后返回 NO_ACTION 等待用户手动发送。"
        }
        return constraints
    }

    private fun resolveTargetPackage(goal: String): String? {
        val normalized = goal.lowercase()
        return when {
            goal.contains("微信") || normalized.contains("wechat") -> "com.tencent.mm"
            goal.contains("美团") -> "com.sankuai.meituan"
            goal.contains("支付宝") -> "com.eg.android.AlipayGphone"
            goal.contains("淘宝") -> "com.taobao.taobao"
            goal.contains("抖音") -> "com.ss.android.ugc.aweme"
            goal.contains("QQ", ignoreCase = true) -> "com.tencent.mobileqq"
            goal.contains("电话") || goal.contains("联系人") -> "com.android.contacts"
            goal.contains("设置") -> "com.android.settings"
            normalized.contains("chrome") || goal.contains("浏览器") -> "com.android.chrome"
            else -> null
        }
    }

    companion object {
        @Volatile
        private var instance: PlannerAgent? = null

        fun getInstance(): PlannerAgent {
            return instance ?: synchronized(this) {
                instance ?: PlannerAgent(LocalRagEngine.getInstance()).also { instance = it }
            }
        }
    }
}
