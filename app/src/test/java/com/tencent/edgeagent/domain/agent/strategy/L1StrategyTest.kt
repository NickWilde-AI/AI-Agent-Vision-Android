package com.tencent.edgeagent.domain.agent.strategy

import android.graphics.Bitmap
import com.tencent.edgeagent.domain.agent.multi.AgentPlan
import com.tencent.edgeagent.domain.agent.multi.SafetyMode
import com.tencent.edgeagent.domain.agent.multi.TaskType
import com.tencent.edgeagent.domain.model.ActionParams
import com.tencent.edgeagent.domain.model.ActionType
import com.tencent.edgeagent.domain.model.AgentResponse
import com.tencent.edgeagent.domain.model.DeviceControlType
import com.tencent.edgeagent.domain.model.InferenceSource
import com.tencent.edgeagent.domain.model.ScreenData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class L1StrategyTest {

    @Test
    fun deviceControlStrategy_rewritesClickToDeviceControl() {
        val plan = AgentPlan(
            goal = "把音量调低一点",
            taskType = TaskType.DEVICE_CONTROL,
            targetPackage = null,
            safetyMode = SafetyMode.AUTO,
            maxRounds = 6,
            localKnowledge = "",
            constraints = emptyList()
        )
        val response = clickResponse()

        val rewrite = DeviceControlStrategy().rewriteResponse(plan, screenData(), emptyList(), response)
        assertTrue(rewrite is StrategyRewrite.Rewritten)

        val rewritten = (rewrite as StrategyRewrite.Rewritten).response
        assertEquals(InferenceSource.STRATEGY, rewritten.source)
        assertEquals(ActionType.DEVICE_CONTROL, rewritten.action)
        assertEquals(
            ActionParams.DeviceControl(DeviceControlType.VOLUME_DOWN, "1"),
            rewritten.actionParams
        )
    }

    @Test
    fun deviceControlStrategy_rewritesSettingsEntryToDeviceControl() {
        val plan = AgentPlan(
            goal = "打开声音与触感设置",
            taskType = TaskType.DEVICE_CONTROL,
            targetPackage = null,
            safetyMode = SafetyMode.AUTO,
            maxRounds = 6,
            localKnowledge = "",
            constraints = emptyList()
        )

        val rewrite = DeviceControlStrategy().rewriteResponse(plan, screenData(), emptyList(), clickResponse())
        assertTrue(rewrite is StrategyRewrite.Rewritten)

        val rewritten = (rewrite as StrategyRewrite.Rewritten).response
        assertEquals(ActionType.DEVICE_CONTROL, rewritten.action)
        assertEquals(
            ActionParams.DeviceControl(DeviceControlType.SOUND_SETTINGS, "1"),
            rewritten.actionParams
        )
    }

    @Test
    fun systemNavigationStrategy_rewritesClickToBackForKeyboard() {
        val plan = AgentPlan(
            goal = "关闭键盘",
            taskType = TaskType.SYSTEM_NAVIGATION,
            targetPackage = null,
            safetyMode = SafetyMode.AUTO,
            maxRounds = 6,
            localKnowledge = "",
            constraints = emptyList()
        )
        val response = clickResponse()

        val rewrite = SystemNavigationStrategy().rewriteResponse(plan, screenData(), emptyList(), response)
        assertTrue(rewrite is StrategyRewrite.Rewritten)

        val rewritten = (rewrite as StrategyRewrite.Rewritten).response
        assertEquals(InferenceSource.STRATEGY, rewritten.source)
        assertEquals(ActionType.BACK, rewritten.action)
        assertEquals(ActionParams.NoAction("收起键盘"), rewritten.actionParams)
    }

    private fun screenData(): ScreenData {
        return ScreenData(
            bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
            uiTreeText = "label='输入框' bounds=[0,0,100,100]",
            screenWidth = 1080,
            screenHeight = 2400,
            currentPackage = "com.tencent.edgeagent",
            hasRealScreenshot = false
        )
    }

    private fun clickResponse(): AgentResponse {
        return AgentResponse(
            source = InferenceSource.CLOUD_FALLBACK,
            action = ActionType.CLICK,
            actionParams = ActionParams.Click(10, 10, "点击输入框"),
            confidence = 0.9f,
            inferenceTimeMs = 12L
        )
    }
}
