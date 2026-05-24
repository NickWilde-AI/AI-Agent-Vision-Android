package com.tencent.edgeagent.data.vision

import com.tencent.edgeagent.domain.model.ScreenData

/**
 * Dependency-free local perception layer.
 *
 * It currently extracts OCR-like text from Accessibility UI trees. The same
 * interface can later be backed by ML Kit/PaddleOCR or an on-device VLM.
 */
class BasicLocalVisionEngine private constructor() : ILocalVisionEngine {

    override fun observe(screenData: ScreenData): LocalVisionObservation {
        return observeUiTree(
            packageName = screenData.currentPackage,
            hasRealScreenshot = screenData.hasRealScreenshot,
            uiTreeText = screenData.uiTreeText
        )
    }

    fun observeUiTree(
        packageName: String?,
        hasRealScreenshot: Boolean,
        uiTreeText: String?
    ): LocalVisionObservation {
        val treeText = uiTreeText.orEmpty()
        val visibleTexts = extractValues(treeText, TEXT_REGEX) +
            extractValues(treeText, DESC_REGEX)
        val clickableLabels = extractClickableLabels(treeText)
        val editableHints = treeText.lineSequence()
            .filter { it.contains("[editable]") || it.contains("EditText", ignoreCase = true) }
            .mapNotNull { line ->
                extractPrimaryLabel(line).ifBlank { line.take(80) }.takeIf { it.isNotBlank() }
            }
            .distinct()
            .take(MAX_EDITABLE_HINTS)
            .toList()

        val summary = buildSummary(
            hasUiTree = treeText.isNotBlank() && !treeText.contains("Error:"),
            visibleTexts = visibleTexts,
            clickableLabels = clickableLabels,
            editableHints = editableHints
        )

        return LocalVisionObservation(
            packageName = packageName,
            hasRealScreenshot = hasRealScreenshot,
            visibleTexts = visibleTexts.distinct().take(MAX_VISIBLE_TEXTS),
            clickableLabels = clickableLabels.distinct().take(MAX_CLICKABLE_LABELS),
            editableHints = editableHints,
            summary = summary
        )
    }

    private fun extractClickableLabels(uiTreeText: String): List<String> {
        return uiTreeText.lineSequence()
            .filter { it.contains("Clickable Elements") || it.trim().startsWith("#") || it.trim().startsWith("[") }
            .filter { it.contains("label='") || it.contains("[clickable]") || it.contains("[editable]") }
            .mapNotNull { extractPrimaryLabel(it).takeIf { label -> label.isNotBlank() } }
            .distinct()
            .take(MAX_CLICKABLE_LABELS)
            .toList()
    }

    private fun extractPrimaryLabel(line: String): String {
        return LABEL_REGEX.find(line)?.groupValues?.getOrNull(1)
            ?: TEXT_REGEX.find(line)?.groupValues?.getOrNull(1)
            ?: DESC_REGEX.find(line)?.groupValues?.getOrNull(1)
            ?: ""
    }

    private fun extractValues(text: String, regex: Regex): List<String> {
        return regex.findAll(text)
            .mapNotNull { match -> match.groupValues.getOrNull(1) }
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "无文本元素" }
            .distinct()
            .take(MAX_VISIBLE_TEXTS)
            .toList()
    }

    private fun buildSummary(
        hasUiTree: Boolean,
        visibleTexts: List<String>,
        clickableLabels: List<String>,
        editableHints: List<String>
    ): String {
        if (!hasUiTree) return "当前只能获得弱观察结果；优先依赖包名和历史，避免猜测页面内容。"
        val fragments = mutableListOf<String>()
        if (visibleTexts.isNotEmpty()) fragments += "识别到 ${visibleTexts.size} 个可见文本"
        if (clickableLabels.isNotEmpty()) fragments += "${clickableLabels.size} 个可点击标签"
        if (editableHints.isNotEmpty()) fragments += "${editableHints.size} 个输入区域"
        return fragments.joinToString("，").ifBlank { "UI 树可用，但未提取到明确文本标签。" }
    }

    companion object {
        private const val MAX_VISIBLE_TEXTS = 40
        private const val MAX_CLICKABLE_LABELS = 40
        private const val MAX_EDITABLE_HINTS = 8
        private val LABEL_REGEX = Regex("label='([^']*)'")
        private val TEXT_REGEX = Regex("text='([^']*)'")
        private val DESC_REGEX = Regex("desc='([^']*)'")

        @Volatile
        private var instance: BasicLocalVisionEngine? = null

        fun getInstance(): BasicLocalVisionEngine {
            return instance ?: synchronized(this) {
                instance ?: BasicLocalVisionEngine().also { instance = it }
            }
        }
    }
}
