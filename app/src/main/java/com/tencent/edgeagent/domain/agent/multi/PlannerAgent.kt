package com.tencent.edgeagent.domain.agent.multi

import com.tencent.edgeagent.data.rag.LocalRagEngine
import com.tencent.edgeagent.domain.agent.L1CommandRouter
import com.tencent.edgeagent.domain.model.ActionType
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
            maxRounds = when (taskType) {
                TaskType.SYSTEM_NAVIGATION,
                TaskType.DEVICE_CONTROL,
                TaskType.OPEN_APP -> 6
                TaskType.WECHAT_DRAFT -> 12
                else -> 16
            },
            localKnowledge = knowledge,
            constraints = constraints
        ).also {
            Timber.i("[PlannerAgent] taskType=${it.taskType} target=${it.targetPackage} safety=${it.safetyMode}")
        }
    }

    private fun resolveTaskType(goal: String, targetPackage: String?): TaskType {
        val normalized = goal.lowercase()
        val l1Response = L1CommandRouter.getInstance().resolve(goal)
        return when {
            targetPackage == "com.tencent.mm" &&
                (goal.contains("发") || goal.contains("消息") || goal.contains("回复")) -> TaskType.WECHAT_DRAFT
            l1Response?.action == ActionType.DEVICE_CONTROL -> TaskType.DEVICE_CONTROL
            l1Response?.action in setOf(ActionType.BACK, ActionType.HOME, ActionType.RECENTS) -> TaskType.SYSTEM_NAVIGATION
            isSystemNavigation(goal, normalized) -> TaskType.SYSTEM_NAVIGATION
            !containsL2TargetMutation(goal, normalized) &&
                (goal.contains("音量") || goal.contains("亮度") || normalized.contains("wifi") ||
                    goal.contains("蓝牙") || goal.contains("飞行模式")) -> TaskType.DEVICE_CONTROL
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
        if (taskType == TaskType.SYSTEM_NAVIGATION) {
            constraints += "系统导航任务应直接返回 BACK、HOME 或 RECENTS，不要点击当前 App 的输入框。"
        }
        if (taskType == TaskType.DEVICE_CONTROL) {
            constraints += "设备控制任务应优先返回 DEVICE_CONTROL，不要点击当前 App 的按钮或输入框。"
        }
        return constraints
    }

    private fun isSystemNavigation(goal: String, normalized: String): Boolean {
        return goal == "返回" ||
            goal.contains("返回上一页") ||
            goal.contains("后退") ||
            goal.contains("回到桌面") ||
            goal.contains("回桌面") ||
            goal.contains("主屏幕") ||
            goal.contains("最近任务") ||
            goal.contains("多任务") ||
            goal.contains("后台应用") ||
            goal.contains("关闭键盘") ||
            goal.contains("收起键盘") ||
            normalized == "back" ||
            normalized == "home" ||
            normalized.contains("recents")
    }

    private fun containsL2TargetMutation(goal: String, normalized: String): Boolean {
        val mutationWords = listOf(
            "改为",
            "改成",
            "设为",
            "设置为",
            "换成",
            "选择",
            "配对",
            "连接到",
            "连接某",
            "具体设备",
            "某个设备",
            "自己的",
            "指定"
        )
        return mutationWords.any { goal.contains(it) || normalized.contains(it) }
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
            goal.contains("相机") || normalized.contains("camera") -> "com.android.camera"
            goal.contains("电话") || goal.contains("联系人") -> "com.android.contacts"
            goal.contains("设置") -> "com.android.settings"
            goal.contains("浏览器") -> "com.android.browser"
            normalized.contains("chrome") -> "com.android.chrome"
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
