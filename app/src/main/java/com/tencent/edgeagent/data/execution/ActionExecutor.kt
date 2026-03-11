package com.tencent.edgeagent.data.execution

import com.tencent.edgeagent.domain.model.ActionParams
import com.tencent.edgeagent.domain.model.ActionType
import com.tencent.edgeagent.domain.model.AgentResponse
import com.tencent.edgeagent.domain.model.DeviceControlType
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
     *
     * 策略：
     * 1. 如果有坐标，先点击对应位置让输入框获取焦点
     * 2. 遍历 UI 树找到焦点节点，使用 AccessibilityNodeInfo.ACTION_SET_TEXT 输入（API 21+）
     * 3. 若 ACTION_SET_TEXT 失败，降级为剪贴板方案：写入剪贴板 → ACTION_PASTE
     */
    private suspend fun executeInputText(
        service: EdgeAgentAccessibilityService,
        params: ActionParams
    ): ExecutionResult {
        return when (params) {
            is ActionParams.InputText -> {
                try {
                    // Step 1: 点击目标位置让输入框获取焦点
                    if (params.targetX != null && params.targetY != null) {
                        service.performClick(params.targetX, params.targetY, "点击输入框")
                        kotlinx.coroutines.delay(400)
                    }

                    // Step 2: 查找焦点节点并直接设置文本
                    val rootNode = service.rootInActiveWindow
                    if (rootNode != null) {
                        val focusedNode = findFocusedEditableNode(rootNode)
                        if (focusedNode != null) {
                            val args = android.os.Bundle().apply {
                                putCharSequence(
                                    android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                    params.text
                                )
                            }
                            val success = focusedNode.performAction(
                                android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,
                                args
                            )
                            focusedNode.recycle()
                            rootNode.recycle()
                            if (success) {
                                Timber.d("ACTION_SET_TEXT 成功: ${params.text}")
                                return ExecutionResult.Success("输入文本成功: ${params.text}")
                            }
                            Timber.w("ACTION_SET_TEXT 失败，降级为剪贴板")
                        } else {
                            rootNode.recycle()
                            Timber.w("未找到焦点节点，降级为剪贴板")
                        }
                    }

                    // Step 3: 降级 — 剪贴板粘贴方案
                    val clipboardSuccess = pasteViaClipboard(service, params.text)
                    if (clipboardSuccess) {
                        ExecutionResult.Success("通过剪贴板输入文本: ${params.text}")
                    } else {
                        ExecutionResult.Failure("文本输入失败")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "输入文本异常")
                    ExecutionResult.Failure("输入文本异常: ${e.message}")
                }
            }
            else -> ExecutionResult.Failure("参数类型错误")
        }
    }

    /**
     * 在 UI 树中递归查找已获焦点的可编辑节点
     */
    private fun findFocusedEditableNode(
        node: android.view.accessibility.AccessibilityNodeInfo
    ): android.view.accessibility.AccessibilityNodeInfo? {
        // 优先：当前节点已获焦点且可编辑
        if (node.isFocused && node.isEditable) return node
        // 次优：当前节点可编辑（即使未显式获焦）
        if (node.isEditable && node.isEnabled) return node

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFocusedEditableNode(child)
            if (result != null) {
                child.recycle()
                return result
            }
            child.recycle()
        }
        return null
    }

    /**
     * 剪贴板粘贴降级方案
     */
    private suspend fun pasteViaClipboard(
        service: EdgeAgentAccessibilityService,
        text: String
    ): Boolean {
        return try {
            val context = service.applicationContext
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("VisionAgent", text)
            clipboard.setPrimaryClip(clip)
            kotlinx.coroutines.delay(200)

            // 找到焦点节点执行粘贴
            val rootNode = service.rootInActiveWindow
            if (rootNode != null) {
                val focusedNode = findFocusedEditableNode(rootNode)
                val success = focusedNode?.performAction(
                    android.view.accessibility.AccessibilityNodeInfo.ACTION_PASTE
                ) ?: false
                focusedNode?.recycle()
                rootNode.recycle()
                Timber.d("剪贴板粘贴结果: $success")
                success
            } else {
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "剪贴板粘贴失败")
            false
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
     * 策略优先级：
     * 1. 检测是否已在目标应用内 → 直接返回成功
     * 2. 使用 Intent 直接启动（最可靠）
     * 3. 在桌面查找图标点击（Agent 感知行为，作为备选）
     */
    private suspend fun executeOpenApp(params: ActionParams): ExecutionResult {
        return when (params) {
            is ActionParams.OpenApp -> {
                try {
                    val service = EdgeAgentAccessibilityService.getInstance()
                        ?: return ExecutionResult.Failure("无障碍服务未启动")

                    val context = service.applicationContext

                    // Step 1: 检测是否已在目标应用内
                    var currentPackage: String? = null
                    for (i in 0..2) {
                        currentPackage = service.rootInActiveWindow?.packageName?.toString()
                        if (currentPackage != null) break
                        kotlinx.coroutines.delay(200)
                    }
                    if (currentPackage == params.packageName) {
                        Timber.d("已在目标应用内: ${params.packageName}")
                        return ExecutionResult.Success("已在应用内: ${params.packageName}")
                    }

                    // Step 2: Intent 直接启动（最可靠）
                    val pm = context.packageManager
                    val launchIntent = pm.getLaunchIntentForPackage(params.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        Timber.d("Intent 启动应用成功: ${params.packageName}")
                        kotlinx.coroutines.delay(1500) // 等待应用启动
                        return ExecutionResult.Success("Intent 启动: ${params.packageName}")
                    }

                    // Step 3: 降级 — 在桌面查找图标（保留 Agent 感知行为）
                    val appName = getAppName(context, params.packageName)
                    if (appName != null) {
                        service.performHome()
                        kotlinx.coroutines.delay(800)
                        val iconFound = findAndClickAppIcon(service, appName, params.packageName)
                        if (iconFound) {
                            kotlinx.coroutines.delay(1500)
                            return ExecutionResult.Success("点击图标启动: $appName")
                        }
                    }

                    ExecutionResult.Failure("无法启动应用: ${params.packageName}")
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
     * 执行设备控制（音量、亮度）
     *
     * 100% 本地执行，绝不上云（隐私合规）
     */
    private fun executeDeviceControl(params: ActionParams): ExecutionResult {
        return when (params) {
            is ActionParams.DeviceControl -> {
                try {
                    val service = EdgeAgentAccessibilityService.getInstance()
                        ?: return ExecutionResult.Failure("无障碍服务未启动")
                    val context = service.applicationContext
                    val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE)
                            as android.media.AudioManager

                    when (params.controlType) {
                        DeviceControlType.VOLUME_UP -> {
                            audioManager.adjustStreamVolume(
                                android.media.AudioManager.STREAM_MUSIC,
                                android.media.AudioManager.ADJUST_RAISE,
                                android.media.AudioManager.FLAG_SHOW_UI
                            )
                            Timber.d("音量增大")
                            ExecutionResult.Success("音量增大")
                        }
                        DeviceControlType.VOLUME_DOWN -> {
                            audioManager.adjustStreamVolume(
                                android.media.AudioManager.STREAM_MUSIC,
                                android.media.AudioManager.ADJUST_LOWER,
                                android.media.AudioManager.FLAG_SHOW_UI
                            )
                            Timber.d("音量减小")
                            ExecutionResult.Success("音量减小")
                        }
                        DeviceControlType.BRIGHTNESS_UP -> {
                            val current = android.provider.Settings.System.getInt(
                                context.contentResolver,
                                android.provider.Settings.System.SCREEN_BRIGHTNESS, 128
                            )
                            val newVal = (current + 30).coerceAtMost(255)
                            android.provider.Settings.System.putInt(
                                context.contentResolver,
                                android.provider.Settings.System.SCREEN_BRIGHTNESS,
                                newVal
                            )
                            Timber.d("亮度增加: $current -> $newVal")
                            ExecutionResult.Success("亮度增加至 $newVal")
                        }
                        DeviceControlType.BRIGHTNESS_DOWN -> {
                            val current = android.provider.Settings.System.getInt(
                                context.contentResolver,
                                android.provider.Settings.System.SCREEN_BRIGHTNESS, 128
                            )
                            val newVal = (current - 30).coerceAtLeast(10)
                            android.provider.Settings.System.putInt(
                                context.contentResolver,
                                android.provider.Settings.System.SCREEN_BRIGHTNESS,
                                newVal
                            )
                            Timber.d("亮度减少: $current -> $newVal")
                            ExecutionResult.Success("亮度减少至 $newVal")
                        }
                        DeviceControlType.WIFI_TOGGLE -> {
                            // Android 10+ 不允许直接开关 WiFi，引导用户到设置
                            val intent = android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            ExecutionResult.Success("已打开 WiFi 设置")
                        }
                        DeviceControlType.BLUETOOTH_TOGGLE -> {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            ExecutionResult.Success("已打开蓝牙设置")
                        }
                        DeviceControlType.AIRPLANE_MODE_TOGGLE -> {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_AIRPLANE_MODE_SETTINGS)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            ExecutionResult.Success("已打开飞行模式设置")
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "设备控制失败")
                    ExecutionResult.Failure("设备控制失败: ${e.message}")
                }
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
