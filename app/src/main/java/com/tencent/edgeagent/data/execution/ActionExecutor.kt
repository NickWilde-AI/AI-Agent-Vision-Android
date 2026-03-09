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
     * 
     * 策略：
     * 通过无障碍服务在桌面查找应用图标并点击（真正的 Agent 行为）
     * 
     * ⚠️ 注意：不要每次都按 Home 键，避免打断应用启动
     */
    private suspend fun executeOpenApp(params: ActionParams): ExecutionResult {
        return when (params) {
            is ActionParams.OpenApp -> {
                try {
                    val service = EdgeAgentAccessibilityService.getInstance()
                    if (service == null) {
                        return ExecutionResult.Failure("无障碍服务未启动")
                    }
                    
                    // 获取应用名称（用于在桌面查找）
                    val context = service.applicationContext
                    val appName = getAppName(context, params.packageName)
                    
                    if (appName != null) {
                        Timber.d("尝试在桌面查找应用图标: $appName (${params.packageName})")
                        
                        // 检查当前是否已经在目标应用
                        val currentPackage = service.rootInActiveWindow?.packageName?.toString()
                        if (currentPackage == params.packageName) {
                            Timber.d("已经在目标应用内: $appName，无需重复打开")
                            return ExecutionResult.Success("已在应用内: $appName")
                        }
                        
                        // 先确保在桌面（按 Home 键）
                        service.performHome()
                        kotlinx.coroutines.delay(800) // 等待桌面加载
                        
                        // 在桌面查找应用图标
                        val iconFound = findAndClickAppIcon(service, appName, params.packageName)
                        
                        if (iconFound) {
                            Timber.d("通过无障碍服务点击应用图标成功: $appName")
                            return ExecutionResult.Success("通过点击图标打开应用: $appName")
                        } else {
                            Timber.w("未在桌面找到应用图标: $appName")
                            return ExecutionResult.Failure("未在桌面找到应用图标: $appName")
                        }
                    } else {
                        Timber.w("无法获取应用名称: ${params.packageName}")
                        return ExecutionResult.Failure("无法获取应用名称: ${params.packageName}")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "打开应用失败")
                    ExecutionResult.Failure("打开应用失败: ${e.message}")
                }
            }
            else -> ExecutionResult.Failure("参数类型错误")
        }
    }
    
    /**
     * 获取应用名称
     */
    private fun getAppName(context: android.content.Context, packageName: String): String? {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            Timber.e(e, "获取应用名称失败: $packageName")
            null
        }
    }
    
    /**
     * 在桌面查找并点击应用图标
     * 
     * @return true 如果找到并点击成功，false 如果未找到
     */
    private suspend fun findAndClickAppIcon(
        service: EdgeAgentAccessibilityService,
        appName: String,
        packageName: String
    ): Boolean {
        try {
            val rootNode = service.rootInActiveWindow ?: return false
            
            // 查找匹配的应用图标节点
            val iconNode = findAppIconNode(rootNode, appName, packageName)
            
            if (iconNode != null) {
                // 获取图标的屏幕坐标
                val bounds = android.graphics.Rect()
                iconNode.getBoundsInScreen(bounds)
                
                // 计算中心点
                val centerX = bounds.centerX()
                val centerY = bounds.centerY()
                
                Timber.d("找到应用图标: $appName at ($centerX, $centerY)")
                
                // 点击图标
                val success = service.performClick(centerX, centerY, "点击应用图标: $appName")
                
                iconNode.recycle()
                rootNode.recycle()
                
                return success
            } else {
                Timber.w("未找到应用图标节点: $appName")
                rootNode.recycle()
                return false
            }
        } catch (e: Exception) {
            Timber.e(e, "查找应用图标失败")
            return false
        }
    }
    
    /**
     * 递归查找应用图标节点
     */
    private fun findAppIconNode(
        node: android.view.accessibility.AccessibilityNodeInfo,
        appName: String,
        packageName: String
    ): android.view.accessibility.AccessibilityNodeInfo? {
        // 检查当前节点
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        
        // 匹配应用名称
        if (text.contains(appName, ignoreCase = true) || 
            contentDesc.contains(appName, ignoreCase = true)) {
            // 确保节点可点击且可见
            if (node.isClickable && node.isVisibleToUser) {
                return node
            }
            // 如果当前节点不可点击，尝试找到可点击的父节点
            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable && parent.isVisibleToUser) {
                    return parent
                }
                parent = parent.parent
            }
        }
        
        // 递归查找子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val result = findAppIconNode(child, appName, packageName)
                if (result != null) {
                    child.recycle()
                    return result
                }
                child.recycle()
            }
        }
        
        return null
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
