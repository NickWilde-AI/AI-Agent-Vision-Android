package com.tencent.edgeagent.data.vision

import com.tencent.edgeagent.domain.model.ScreenData

interface ILocalVisionEngine {
    fun observe(screenData: ScreenData): LocalVisionObservation
}

data class LocalVisionObservation(
    val packageName: String?,
    val hasRealScreenshot: Boolean,
    val visibleTexts: List<String>,
    val clickableLabels: List<String>,
    val editableHints: List<String>,
    val summary: String
) {
    fun toPromptText(): String {
        return buildString {
            append("【本地视觉/OCR 观察】\n")
            append("包名: ${packageName ?: "未知"}\n")
            append("真实截图: $hasRealScreenshot\n")
            if (visibleTexts.isNotEmpty()) {
                append("可见文本: ${visibleTexts.take(20).joinToString(" / ")}\n")
            }
            if (clickableLabels.isNotEmpty()) {
                append("可点击标签: ${clickableLabels.take(20).joinToString(" / ")}\n")
            }
            if (editableHints.isNotEmpty()) {
                append("输入区域: ${editableHints.take(8).joinToString(" / ")}\n")
            }
            append("摘要: $summary\n")
        }
    }
}
