package com.tencent.edgeagent.domain.agent

import com.tencent.edgeagent.domain.model.AgentIntent
import com.tencent.edgeagent.domain.model.IntentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeCloudRouterTest {

    private val router = EdgeCloudRouter.getInstance()

    @Test
    fun decide_usesCloudForCloudAllowedComplexReasoning() {
        val decision = router.decide(
            intent = intent(IntentType.COMPLEX_REASONING, allowCloudFallback = true),
            cloudAvailable = true,
            localModelAvailable = true,
            deterministicFallbackAvailable = false
        )

        assertEquals(EdgeCloudRouteMode.CLOUD_AGENT, decision.primaryMode)
        assertEquals(EdgeCloudFallbackMode.LOCAL_SINGLE_ROUND, decision.fallbackMode)
        assertTrue(decision.cloudAllowedByIntent)
    }

    @Test
    fun decide_keepsDeviceControlLocalWhenL1FallbackExists() {
        val decision = router.decide(
            intent = intent(IntentType.DEVICE_CONTROL, allowCloudFallback = false),
            cloudAvailable = true,
            localModelAvailable = true,
            deterministicFallbackAvailable = true
        )

        assertEquals(EdgeCloudRouteMode.DETERMINISTIC_L1, decision.primaryMode)
        assertEquals("low_risk_local_control", decision.privacyClass)
        assertFalse(decision.cloudAllowedByIntent)
        assertTrue(decision.tags.contains("cloud_blocked_by_intent"))
    }

    @Test
    fun decide_keepsTextInputOnLocalModelWithoutL1Fallback() {
        val decision = router.decide(
            intent = intent(IntentType.TEXT_INPUT, allowCloudFallback = false),
            cloudAvailable = true,
            localModelAvailable = true,
            deterministicFallbackAvailable = false
        )

        assertEquals(EdgeCloudRouteMode.LOCAL_SINGLE_ROUND, decision.primaryMode)
        assertEquals(EdgeCloudFallbackMode.NONE, decision.fallbackMode)
        assertEquals("sensitive_local_first", decision.privacyClass)
    }

    @Test
    fun decide_usesL1FallbackWhenCloudUnavailableForAppOperation() {
        val decision = router.decide(
            intent = intent(IntentType.APP_OPERATION, allowCloudFallback = true),
            cloudAvailable = false,
            localModelAvailable = true,
            deterministicFallbackAvailable = true
        )

        assertEquals(EdgeCloudRouteMode.DETERMINISTIC_L1, decision.primaryMode)
        assertEquals(EdgeCloudFallbackMode.LOCAL_SINGLE_ROUND, decision.fallbackMode)
    }

    private fun intent(
        type: IntentType,
        allowCloudFallback: Boolean
    ): AgentIntent {
        return AgentIntent(
            type = type,
            userInput = "test",
            allowCloudFallback = allowCloudFallback,
            requiresScreenContext = type != IntentType.DEVICE_CONTROL
        )
    }
}
