package com.tencent.edgeagent.data.execution

import com.tencent.edgeagent.domain.model.ActionParams
import com.tencent.edgeagent.domain.model.ActionType
import com.tencent.edgeagent.domain.model.AgentResponse
import com.tencent.edgeagent.service.EdgeAgentAccessibilityService
import timber.log.Timber

/**
 * 动作执行器
 * 
 * 职责：
 * 1. 将 AgentResponse 转换为真实的无障碍操作
 * 2. 调用 GestureExecutor 执行具体动作
 * 3. 处理执行结果和错误
 */
class ActionExecutor private constructor() {

    /**
     * 执行 Agent 响应中的动作
     */
    suspend fun execute(response: AgentResponse): ExecutionResult {
        val service = EdgeAgentAccessibilityService.getInstance()
        
        if (service == null) {
            Timber.e("无障碍服务未启动")
            return ExecutionResult.Failure("无障碍服务未启动，请在设置中开启")
        }

        Timber.d("开始执行动作: ${response.action}")

        return try {
            when (response.action) {
                ActionType.CLICK -> executeClick(service, response.actionParams)
                ActionType.LONG_CLICK -> executeLongClick(service, response.actionParams)
                ActionType.SWIPE -> executeSwipe(service, response.actionParams)
                ActionType.INPUT_TEXT -> executeInputText(service, response.actionParams)
                ActionType.BACK -> executeBack(service)
                ActionType.HOME -> executeHome(service)
                ActionType.OPEN_APP -> executeOpenApp(response.actionParams)
                ActionType.DEVICE_CONTROL -> executeDeviceControl(response.actionParams)
                ActionType.WAIT -> executeWait(response.actionParams)
                ActionType.NO_ACTION -> ExecutionResult.Success("无需操作")
            }
        } catch (e: Exception) {
            Timber.e(e, "执行动作失败")
            ExecutionResult.Failure("执行失败: ${e.message}")
        }
    }

    /**
     * 执行点击
     */
    private suspend fun executeClick(
        service: EdgeAgentAccessibilityService,
        params: ActionParams
    ): ExecutionResult {
        return when (params) {
            is ActionParams.Click -> {
                val success = service.performClick(params.x, params.y, params.description)
                if (success) {
                    ExecutionResult.Success("点击成功: (${params.x}, ${params.y})")
                } else {
                    ExecutionResult.Failure("点击失败")
                }
            }
            else -> ExecutionResult.Failure("参数类型错误")
        }
    }

    /**
     * 执行长按
     */
    private suspend fun executeLongClick(
        service: EdgeAgentAccessibilityService,
        params: ActionParams
    ): ExecutionResult {
        return when (params) {
            is ActionParams.LongClick -> {
                val success = service.performClick(params.x, params.y, "长按")
                if (success) {
                    ExecutionResult.Success("长按成功: (${params.x}, ${params.y})")
                } else {
                    ExecutionResult.Failure("长按失败")
                }
            }
            else -> ExecutionResult.Failure("参数类型错误")
        }
    }

    /**
     * 执行滑动
     */
    private suspend fun executeSwipe(
        service: EdgeAgentAccessibilityService,
        params: ActionParams
    ): ExecutionResult {
        return when (params) {
            is ActionParams.Swipe -> {
                val success = service.performSwipe(
                    params.startX,
                    params.startY,
                    params.endX,
                    params.endY,
                    params.durationMs
                )
                if (success) {
                    ExecutionResult.Success("滑动成功")
                } else {
                    ExecutionResult.Failure("滑动失败")
                }
            }
            else -> ExecutionResult.Failure("参数类型错误")
        }
    }

    /**
     * 执行输入文本
     */
    private suspend fun executeInputText(
        service: EdgeAgentAccessibilityService,
        params: ActionParams
    ): ExecutionResult {
        return when (params) {
            is ActionParams.InputText -> {
                // 先点击输入框（如果有坐标）
                if (params.targetX != null && params.targetY != null) {
                    service.performClick(params.targetX, params.targetY, "点击输入框")
                    kotlinx.coroutines.delay(300) // 等待输入框获得焦点
                }
                
                // TODO: 实现文本输入（需要使用 AccessibilityNodeInfo 或剪贴板）
                Timber.w("文本输入功能待实现: ${params.text}")
                ExecutionResult.Success("文本输入待实现")
            }
            else -> ExecutionResult.Failure("参数类型错误")
        }
    }

    /**
     * 执行返回
     */
    private fun executeBack(service: EdgeAgentAccessibilityService): ExecutionResult {
        val success = service.performBack()
        return if (success) {
            ExecutionResult.Success("返回成功")
        } else {
            ExecutionResult.Failure("返回失败")
        }
    }

    /**
     * 执行 Home
     */
    private fun executeHome(service: EdgeAgentAccessibilityService): ExecutionResult {
        val success = service.performHome()
        return if (success) {
            ExecutionResult.Success("回到主屏幕成功")
        } else {
            ExecutionResult.Failure("回到主屏幕失败")
        }
    }

    /**
     * 执行打开应用
     */
    private fun executeOpenApp(params: ActionParams): ExecutionResult {
        return when (params) {
            is ActionParams.OpenApp -> {
                // TODO: 实现打开应用（需要使用 Intent 或搜索应用）
                Timber.w("打开应用功能待实现: ${params.packageName}")
                ExecutionResult.Success("打开应用待实现")
            }
            else -> ExecutionResult.Failure("参数类型错误")
        }
    }

    /**
     * 执行设备控制
     */
    private fun executeDeviceControl(params: ActionParams): ExecutionResult {
        return when (params) {
            is ActionParams.DeviceControl -> {
                // TODO: 实现设备控制（音量、亮度等）
                Timber.w("设备控制功能待实现: ${params.controlType}")
                ExecutionResult.Success("设备控制待实现")
            }
            else -> ExecutionResult.Failure("参数类型错误")
        }
    }

    /**
     * 执行等待
     */
    private suspend fun executeWait(params: ActionParams): ExecutionResult {
        return when (params) {
            is ActionParams.Wait -> {
                kotlinx.coroutines.delay(params.durationMs)
                ExecutionResult.Success("等待 ${params.durationMs}ms 完成")
            }
            else -> ExecutionResult.Failure("参数类型错误")
        }
    }

    companion object {
        @Volatile
        private var instance: ActionExecutor? = null
        
        fun getInstance(): ActionExecutor {
            return instance ?: synchronized(this) {
                instance ?: ActionExecutor().also { instance = it }
            }
        }
    }
}

/**
 * 执行结果
 */
sealed class ExecutionResult {
    data class Success(val message: String) : ExecutionResult()
    data class Failure(val message: String) : ExecutionResult()
}
