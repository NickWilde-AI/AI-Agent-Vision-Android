package com.tencent.edgeagent.domain.agent.strategy

import android.graphics.Bitmap
import com.tencent.edgeagent.domain.agent.multi.AgentPlan
import com.tencent.edgeagent.domain.agent.multi.SafetyMode
import com.tencent.edgeagent.domain.agent.multi.TaskType
import com.tencent.edgeagent.domain.model.ActionParams
import com.tencent.edgeagent.domain.model.ActionType
import com.tencent.edgeagent.domain.model.AgentResponse
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
class OpenAppStrategyTest {

    private val strategy = OpenAppStrategy()

    @Test
    fun rewriteResponse_clickInVisionAgent_rewritesToOpenApp() {
        val plan = AgentPlan(
            goal = "打开相机",
            taskType = TaskType.OPEN_APP,
            targetPackage = "com.android.camera",
            safetyMode = SafetyMode.AUTO,
            maxRounds = 8,
            localKnowledge = "",
            constraints = emptyList()
        )

        val screenData = ScreenData(
            bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
            uiTreeText = "label='输入框' bounds=[0,0,100,100]",
            screenWidth = 1080,
            screenHeight = 2400,
            currentPackage = "com.tencent.edgeagent",
            hasRealScreenshot = false
        )

        val modelResponse = AgentResponse(
            source = InferenceSource.CLOUD_FALLBACK,
            action = ActionType.CLICK,
            actionParams = ActionParams.Click(10, 10, "点击输入框"),
            confidence = 0.9f,
            inferenceTimeMs = 12L
        )

        assertTrue(strategy.matches(plan, screenData))

        val rewrite = strategy.rewriteResponse(plan, screenData, emptyList(), modelResponse)
        assertTrue(rewrite is StrategyRewrite.Rewritten)

        val rewritten = (rewrite as StrategyRewrite.Rewritten).response
        assertEquals(InferenceSource.STRATEGY, rewritten.source)
        assertEquals(ActionType.OPEN_APP, rewritten.action)
        assertEquals(ActionParams.OpenApp("com.android.camera"), rewritten.actionParams)
    }

    @Test
    fun rewriteResponse_alreadyOpenApp_keeps() {
        val plan = AgentPlan(
            goal = "打开相机",
            taskType = TaskType.OPEN_APP,
            targetPackage = "com.android.camera",
            safetyMode = SafetyMode.AUTO,
            maxRounds = 8,
            localKnowledge = "",
            constraints = emptyList()
        )

        val screenData = ScreenData(
            bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
            uiTreeText = null,
            screenWidth = 1080,
            screenHeight = 2400,
            currentPackage = "com.tencent.edgeagent",
            hasRealScreenshot = false
        )

        val response = AgentResponse(
            source = InferenceSource.CLOUD_FALLBACK,
            action = ActionType.OPEN_APP,
            actionParams = ActionParams.OpenApp("com.android.camera"),
            confidence = 0.9f,
            inferenceTimeMs = 12L
        )

        val rewrite = strategy.rewriteResponse(plan, screenData, emptyList(), response)
        assertTrue(rewrite is StrategyRewrite.Keep)
        assertEquals(ActionType.OPEN_APP, (rewrite as StrategyRewrite.Keep).response.action)
    }
}

