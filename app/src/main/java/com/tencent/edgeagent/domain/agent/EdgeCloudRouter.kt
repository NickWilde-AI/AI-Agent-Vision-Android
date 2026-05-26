package com.tencent.edgeagent.domain.agent

import com.tencent.edgeagent.domain.model.AgentIntent
import com.tencent.edgeagent.domain.model.IntentType

/**
 * Makes the edge-cloud execution policy explicit and testable.
 *
 * The router does not run models by itself. It only decides the preferred
 * execution lane based on intent sensitivity and runtime availability.
 */
class EdgeCloudRouter private constructor() {

    fun decide(
        intent: AgentIntent,
        cloudAvailable: Boolean,
        localModelAvailable: Boolean,
        deterministicFallbackAvailable: Boolean
    ): EdgeCloudDecision {
        val primaryMode = when {
            shouldStayLocal(intent) && deterministicFallbackAvailable -> EdgeCloudRouteMode.DETERMINISTIC_L1
            shouldStayLocal(intent) -> EdgeCloudRouteMode.LOCAL_SINGLE_ROUND
            cloudAvailable -> EdgeCloudRouteMode.CLOUD_AGENT
            deterministicFallbackAvailable -> EdgeCloudRouteMode.DETERMINISTIC_L1
            else -> EdgeCloudRouteMode.LOCAL_SINGLE_ROUND
        }

        val fallbackMode = when (primaryMode) {
            EdgeCloudRouteMode.CLOUD_AGENT -> {
                if (deterministicFallbackAvailable) {
                    EdgeCloudFallbackMode.DETERMINISTIC_L1
                } else {
                    EdgeCloudFallbackMode.LOCAL_SINGLE_ROUND
                }
            }
            EdgeCloudRouteMode.LOCAL_SINGLE_ROUND -> {
                if (deterministicFallbackAvailable) {
                    EdgeCloudFallbackMode.DETERMINISTIC_L1
                } else {
                    EdgeCloudFallbackMode.NONE
                }
            }
            EdgeCloudRouteMode.DETERMINISTIC_L1 -> {
                if (localModelAvailable) {
                    EdgeCloudFallbackMode.LOCAL_SINGLE_ROUND
                } else {
                    EdgeCloudFallbackMode.NONE
                }
            }
        }

        return EdgeCloudDecision(
            primaryMode = primaryMode,
            fallbackMode = fallbackMode,
            reason = buildReason(intent, cloudAvailable, localModelAvailable, deterministicFallbackAvailable, primaryMode),
            intentType = intent.type.name,
            cloudAvailable = cloudAvailable,
            cloudAllowedByIntent = intent.allowCloudFallback,
            localModelAvailable = localModelAvailable,
            deterministicFallbackAvailable = deterministicFallbackAvailable,
            requiresScreenContext = intent.requiresScreenContext,
            privacyClass = privacyClass(intent),
            tags = buildTags(intent, primaryMode, fallbackMode)
        )
    }

    private fun shouldStayLocal(intent: AgentIntent): Boolean {
        return !intent.allowCloudFallback ||
            intent.type == IntentType.DEVICE_CONTROL ||
            intent.type == IntentType.TEXT_INPUT
    }

    private fun privacyClass(intent: AgentIntent): String {
        return when (intent.type) {
            IntentType.TEXT_INPUT -> "sensitive_local_first"
            IntentType.DEVICE_CONTROL -> "low_risk_local_control"
            IntentType.INFORMATION_QUERY -> "cloud_allowed_query"
            IntentType.COMPLEX_REASONING -> "cloud_preferred_reasoning"
            IntentType.APP_OPERATION -> "screen_context_cloud_allowed"
            IntentType.UNKNOWN -> "unknown_cloud_allowed"
        }
    }

    private fun buildReason(
        intent: AgentIntent,
        cloudAvailable: Boolean,
        localModelAvailable: Boolean,
        deterministicFallbackAvailable: Boolean,
        primaryMode: EdgeCloudRouteMode
    ): String {
        val availability = "cloud=$cloudAvailable,local=$localModelAvailable,l1=$deterministicFallbackAvailable"
        return when (primaryMode) {
            EdgeCloudRouteMode.CLOUD_AGENT -> "cloud_available_and_intent_allows_cloud:$availability"
            EdgeCloudRouteMode.DETERMINISTIC_L1 -> {
                if (shouldStayLocal(intent)) {
                    "intent_requires_local_low_risk_fallback:$availability"
                } else {
                    "cloud_unavailable_use_l1_fallback:$availability"
                }
            }
            EdgeCloudRouteMode.LOCAL_SINGLE_ROUND -> {
                if (shouldStayLocal(intent)) {
                    "intent_requires_local_model:$availability"
                } else {
                    "cloud_and_l1_unavailable_use_local_model:$availability"
                }
            }
        }
    }

    private fun buildTags(
        intent: AgentIntent,
        primaryMode: EdgeCloudRouteMode,
        fallbackMode: EdgeCloudFallbackMode
    ): List<String> {
        val tags = mutableListOf(
            "intent:${intent.type.name}",
            "primary:${primaryMode.name}",
            "fallback:${fallbackMode.name}"
        )
        if (!intent.allowCloudFallback) tags += "cloud_blocked_by_intent"
        if (intent.requiresScreenContext) tags += "needs_screen_context"
        return tags
    }

    companion object {
        @Volatile
        private var instance: EdgeCloudRouter? = null

        fun getInstance(): EdgeCloudRouter {
            return instance ?: synchronized(this) {
                instance ?: EdgeCloudRouter().also { instance = it }
            }
        }
    }
}

data class EdgeCloudDecision(
    val primaryMode: EdgeCloudRouteMode,
    val fallbackMode: EdgeCloudFallbackMode,
    val reason: String,
    val intentType: String,
    val cloudAvailable: Boolean,
    val cloudAllowedByIntent: Boolean,
    val localModelAvailable: Boolean,
    val deterministicFallbackAvailable: Boolean,
    val requiresScreenContext: Boolean,
    val privacyClass: String,
    val tags: List<String> = emptyList()
)

enum class EdgeCloudRouteMode {
    CLOUD_AGENT,
    LOCAL_SINGLE_ROUND,
    DETERMINISTIC_L1
}

enum class EdgeCloudFallbackMode {
    NONE,
    LOCAL_SINGLE_ROUND,
    DETERMINISTIC_L1
}
