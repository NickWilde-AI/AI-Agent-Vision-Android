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
                val success = service.performLongClick(params.x, params.y, params.durationMs)
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
                        val success = focusedNode?.let { node ->
                            val args = android.os.Bundle().apply {
                                putCharSequence(
                                    android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                    params.text
                                )
                            }
                            node.performAction(
                                android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,
                                args
                            )
                        } ?: false

                        focusedNode?.recycle()
                        rootNode.recycle()

                        if (success) {
                            Timber.d("ACTION_SET_TEXT 成功: ${params.text}")
                            return ExecutionResult.Success("输入文本成功: ${params.text}")
                        }

                        Timber.w(
                            if (focusedNode == null) {
                                "未找到焦点节点，降级为剪贴板"
                            } else {
                                "ACTION_SET_TEXT 失败，降级为剪贴板"
                            }
                        )
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
        if (node.isFocused && node.isEditable) return android.view.accessibility.AccessibilityNodeInfo.obtain(node)
        // 次优：当前节点可编辑（即使未显式获焦）
        if (node.isEditable && node.isEnabled) return android.view.accessibility.AccessibilityNodeInfo.obtain(node)

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                val result = findFocusedEditableNode(child)
                if (result != null) {
                    return result
                }
            } finally {
                child.recycle()
            }
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
     * 全无障碍优先策略：
     * 1. 检测是否已在目标应用内 → 直接返回成功
     * 2. 回到桌面
     * 3. 通过 Launcher UI 树查找目标应用图标并点击
     * 4. 如果当前桌面页找不到，左右滑动翻页继续查找
     *
     * 说明：这里刻意不优先使用 Intent，目标是模拟真人操作路径，便于后续演进到
     * “看屏幕 → 决策 → 点击”的豆包手机助手形态。
     */
    private suspend fun executeOpenApp(params: ActionParams): ExecutionResult {
        return when (params) {
            is ActionParams.OpenApp -> {
                try {
                    val service = EdgeAgentAccessibilityService.getInstance()
                        ?: return ExecutionResult.Failure("无障碍服务未启动")

                    val packageName = params.packageName
                    if (packageName.isBlank()) {
                        return ExecutionResult.Failure("打开应用失败：packageName 为空")
                    }

                    val currentPackage = getCurrentPackage(service)
                    if (currentPackage == packageName) {
                        Timber.d("[OPEN_APP] 已在目标应用内: $packageName")
                        return ExecutionResult.Success("已在应用内: $packageName")
                    }

                    val appName = getAppName(service.applicationContext, packageName)
                        ?: inferAppNameFromPackage(packageName)
                    Timber.i("[OPEN_APP] 无障碍打开应用: appName=$appName, package=$packageName")

                    val homeSuccess = service.performHome()
                    if (!homeSuccess) {
                        return ExecutionResult.Failure("打开应用失败：无法回到桌面")
                    }
                    kotlinx.coroutines.delay(900)

                    repeat(MAX_LAUNCHER_SEARCH_PAGES) { pageIndex ->
                        Timber.d("[OPEN_APP] 在桌面第 ${pageIndex + 1} 页查找图标: $appName")
                        val iconFound = findAndClickAppIcon(service, appName, packageName)
                        if (iconFound) {
                            kotlinx.coroutines.delay(1800)
                            val afterPackage = getCurrentPackage(service)
                            if (afterPackage == packageName) {
                                Timber.i("[OPEN_APP] 已进入目标应用: $afterPackage")
                                return ExecutionResult.Success("无障碍点击图标启动: $appName")
                            }
                            Timber.w("[OPEN_APP] 已点击图标，但当前包名尚未切到目标应用: $afterPackage")
                            return ExecutionResult.Success("已点击图标，等待应用启动: $appName")
                        }

                        if (pageIndex < MAX_LAUNCHER_SEARCH_PAGES - 1) {
                            val metrics = service.resources.displayMetrics
                            val startX = (metrics.widthPixels * 0.82f).toInt()
                            val endX = (metrics.widthPixels * 0.18f).toInt()
                            val y = (metrics.heightPixels * 0.55f).toInt()
                            Timber.d("[OPEN_APP] 当前页未找到，滑动到下一桌面页")
                            service.performSwipe(startX, y, endX, y, 350)
                            kotlinx.coroutines.delay(700)
                        }
                    }

                    ExecutionResult.Failure("桌面未找到应用图标: $appName ($packageName)")
                } catch (e: Exception) {
                    Timber.e(e, "无障碍打开应用失败")
                    ExecutionResult.Failure("无障碍打开应用失败: ${e.message}")
                }
            }
            else -> ExecutionResult.Failure("参数类型错误")
        }
    }

    private fun getCurrentPackage(service: EdgeAgentAccessibilityService): String? {
        val rootNode = service.rootInActiveWindow ?: return null
        return try {
            rootNode.packageName?.toString()
        } finally {
            rootNode.recycle()
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
            Timber.w(e, "获取应用名称失败，使用包名推断: $packageName")
            null
        }
    }

    private fun inferAppNameFromPackage(packageName: String): String {
        return when (packageName) {
            "com.tencent.mm" -> "微信"
            "com.sankuai.meituan" -> "美团"
            "com.eg.android.AlipayGphone" -> "支付宝"
            "com.taobao.taobao" -> "淘宝"
            "com.ss.android.ugc.aweme" -> "抖音"
            "com.tencent.mobileqq" -> "QQ"
            "com.android.contacts" -> "电话"
            "com.android.settings" -> "设置"
            else -> packageName.substringAfterLast('.')
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
        val rootNode = service.rootInActiveWindow ?: return false
        return try {
            val iconBounds = findAppIconBounds(rootNode, appName)

            if (iconBounds != null && !iconBounds.isEmpty) {
                val centerX = iconBounds.centerX()
                val centerY = iconBounds.centerY()

                Timber.d("找到应用图标: $appName at ($centerX, $centerY)")
                service.performClick(centerX, centerY, "点击应用图标: $appName")
            } else {
                Timber.w("未找到应用图标节点: $appName ($packageName)")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "查找应用图标失败")
            false
        } finally {
            rootNode.recycle()
        }
    }
    
    /**
     * 递归查找应用图标节点坐标
     */
    private fun findAppIconBounds(
        node: android.view.accessibility.AccessibilityNodeInfo,
        appName: String
    ): android.graphics.Rect? {
        val text = node.text?.toString().orEmpty()
        val contentDesc = node.contentDescription?.toString().orEmpty()
        val matched = text.equals(appName, ignoreCase = true) ||
                contentDesc.equals(appName, ignoreCase = true) ||
                text.contains(appName, ignoreCase = true) ||
                contentDesc.contains(appName, ignoreCase = true)

        if (matched && node.isVisibleToUser) {
            val clickableBounds = findClickableBoundsFromNodeOrParent(node)
            if (clickableBounds != null && !clickableBounds.isEmpty) {
                return clickableBounds
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                val result = findAppIconBounds(child, appName)
                if (result != null) {
                    return result
                }
            } finally {
                child.recycle()
            }
        }

        return null
    }

    private fun findClickableBoundsFromNodeOrParent(
        node: android.view.accessibility.AccessibilityNodeInfo
    ): android.graphics.Rect? {
        if (node.isClickable && node.isVisibleToUser) {
            return android.graphics.Rect().also { node.getBoundsInScreen(it) }
        }

        var parent = node.parent
        while (parent != null) {
            try {
                if (parent.isClickable && parent.isVisibleToUser) {
                    return android.graphics.Rect().also { parent.getBoundsInScreen(it) }
                }
                val nextParent = parent.parent
                parent.recycle()
                parent = nextParent
            } catch (e: Exception) {
                Timber.w(e, "查找可点击父节点失败")
                parent.recycle()
                return null
            }
        }

        return android.graphics.Rect().also { node.getBoundsInScreen(it) }
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
        private const val MAX_LAUNCHER_SEARCH_PAGES = 5

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
