package com.tencent.edgeagent.data.perception

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import timber.log.Timber

/**
 * UI 树提取器
 *
 * 职责：
 * 1. 从 AccessibilityNodeInfo 提取关键信息
 * 2. 生成带坐标的 UI 树文本表示，供云端/本地模型直接定位元素
 * 3. 输出可点击元素摘要，减少模型在复杂 UI 中的搜索成本
 */
class UITreeExtractor private constructor() {

    /**
     * 提取 UI 树文本表示
     */
    fun extractUITree(rootNode: AccessibilityNodeInfo?): String {
        if (rootNode == null) {
            return "UI Tree: null"
        }

        val builder = StringBuilder()
        builder.append("UI Tree with bounds:\n")
        builder.append("Format: [Class] text desc id bounds=[l,t,r,b] center=(x,y) states\n")

        try {
            val clickableElements = extractClickableElements(rootNode)
            appendClickableSummary(builder, clickableElements)
            builder.append("\nNode Tree:\n")
            traverseNode(rootNode, builder, 0)
        } catch (e: Exception) {
            Timber.e(e, "提取 UI 树失败")
            builder.append("Error: ${e.message}\n")
        }

        return builder.toString()
    }

    private fun appendClickableSummary(
        builder: StringBuilder,
        elements: List<ClickableElement>
    ) {
        builder.append("Clickable Elements (${elements.size}):\n")
        elements.take(MAX_CLICKABLE_SUMMARY).forEachIndexed { index, element ->
            val label = element.primaryLabel().ifEmpty { "无文本元素" }
            builder.append("  #${index + 1} ")
                .append("label='").append(sanitize(label)).append("' ")
                .append("class='").append(element.className).append("' ")
                .append("id='").append(element.viewId).append("' ")
                .append(formatBounds(element.bounds))
                .append(" center=(${element.bounds.centerX()},${element.bounds.centerY()})")
                .append("\n")
        }
    }

    /**
     * 递归遍历节点
     */
    private fun traverseNode(node: AccessibilityNodeInfo, builder: StringBuilder, depth: Int) {
        if (depth > MAX_DEPTH) return
        if (!isUsefulNode(node)) return

        val indent = "  ".repeat(depth)
        val className = node.className?.toString() ?: "Unknown"
        val text = node.text?.toString().orEmpty()
        val contentDesc = node.contentDescription?.toString().orEmpty()
        val viewId = node.viewIdResourceName.orEmpty()
        val bounds = Rect().also { node.getBoundsInScreen(it) }

        val nodeDesc = buildString {
            append("$indent[$className]")
            if (text.isNotEmpty()) append(" text='").append(sanitize(text)).append("'")
            if (contentDesc.isNotEmpty()) append(" desc='").append(sanitize(contentDesc)).append("'")
            if (viewId.isNotEmpty()) append(" id='").append(viewId).append("'")
            append(" ").append(formatBounds(bounds))
            append(" center=(${bounds.centerX()},${bounds.centerY()})")
            appendStateFlags(node)
        }

        builder.append(nodeDesc).append("\n")

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                traverseNode(child, builder, depth + 1)
                child.recycle()
            }
        }
    }

    private fun StringBuilder.appendStateFlags(node: AccessibilityNodeInfo) {
        if (node.isClickable) append(" [clickable]")
        if (node.isLongClickable) append(" [longClickable]")
        if (node.isEditable) append(" [editable]")
        if (node.isFocused) append(" [focused]")
        if (node.isSelected) append(" [selected]")
        if (node.isCheckable) append(" [checkable]")
        if (node.isChecked) append(" [checked]")
        if (node.isScrollable) append(" [scrollable]")
        if (!node.isEnabled) append(" [disabled]")
        if (!node.isVisibleToUser) append(" [notVisible]")
    }

    /**
     * 判断节点是否有用
     */
    private fun isUsefulNode(node: AccessibilityNodeInfo): Boolean {
        if (!node.text.isNullOrEmpty()) return true
        if (!node.contentDescription.isNullOrEmpty()) return true
        if (node.isClickable) return true
        if (node.isEditable) return true
        if (node.isScrollable) return true
        if (!node.viewIdResourceName.isNullOrEmpty()) return true
        if (node.childCount > 0) return true
        return false
    }

    /**
     * 提取可点击元素列表
     */
    fun extractClickableElements(rootNode: AccessibilityNodeInfo?): List<ClickableElement> {
        val elements = mutableListOf<ClickableElement>()
        if (rootNode == null) return elements

        try {
            collectClickableElements(rootNode, elements)
        } catch (e: Exception) {
            Timber.e(e, "提取可点击元素失败")
        }

        return elements
    }

    private fun collectClickableElements(
        node: AccessibilityNodeInfo,
        elements: MutableList<ClickableElement>
    ) {
        if ((node.isClickable || node.isLongClickable || node.isEditable) && node.isVisibleToUser) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (!bounds.isEmpty) {
                elements.add(
                    ClickableElement(
                        text = node.text?.toString().orEmpty(),
                        contentDesc = node.contentDescription?.toString().orEmpty(),
                        className = node.className?.toString().orEmpty(),
                        viewId = node.viewIdResourceName.orEmpty(),
                        bounds = bounds,
                        isEditable = node.isEditable,
                        isScrollable = node.isScrollable
                    )
                )
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectClickableElements(child, elements)
                child.recycle()
            }
        }
    }

    private fun formatBounds(bounds: Rect): String {
        return "bounds=[${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}]"
    }

    private fun sanitize(value: String): String {
        return value
            .replace("\n", " ")
            .replace("\r", " ")
            .replace("'", "’")
            .take(MAX_TEXT_LENGTH)
    }

    companion object {
        private const val MAX_DEPTH = 12
        private const val MAX_CLICKABLE_SUMMARY = 80
        private const val MAX_TEXT_LENGTH = 80

        @Volatile
        private var instance: UITreeExtractor? = null

        fun getInstance(): UITreeExtractor {
            return instance ?: synchronized(this) {
                instance ?: UITreeExtractor().also { instance = it }
            }
        }
    }
}

/**
 * 可点击元素
 */
data class ClickableElement(
    val text: String,
    val contentDesc: String,
    val className: String,
    val viewId: String,
    val bounds: Rect,
    val isEditable: Boolean = false,
    val isScrollable: Boolean = false
) {
    fun primaryLabel(): String {
        return when {
            text.isNotBlank() -> text
            contentDesc.isNotBlank() -> contentDesc
            viewId.isNotBlank() -> viewId.substringAfterLast('/')
            else -> className
        }
    }
}
