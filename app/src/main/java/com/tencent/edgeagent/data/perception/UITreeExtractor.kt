package com.tencent.edgeagent.data.perception

import android.view.accessibility.AccessibilityNodeInfo
import timber.log.Timber

/**
 * UI 树提取器
 * 
 * 职责：
 * 1. 从 AccessibilityNodeInfo 提取关键信息
 * 2. 生成简化的 UI 树文本表示
 * 3. 过滤无用节点，减少数据量
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
        builder.append("UI Tree:\n")
        
        try {
            traverseNode(rootNode, builder, 0)
        } catch (e: Exception) {
            Timber.e(e, "提取 UI 树失败")
            builder.append("Error: ${e.message}\n")
        }
        
        return builder.toString()
    }
    
    /**
     * 递归遍历节点
     */
    private fun traverseNode(node: AccessibilityNodeInfo, builder: StringBuilder, depth: Int) {
        // 限制深度，避免过深
        if (depth > MAX_DEPTH) {
            return
        }
        
        // 过滤无用节点
        if (!isUsefulNode(node)) {
            return
        }
        
        // 添加缩进
        val indent = "  ".repeat(depth)
        
        // 提取节点信息
        val className = node.className?.toString() ?: "Unknown"
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""
        val isClickable = node.isClickable
        val isEnabled = node.isEnabled
        
        // 构建节点描述
        val nodeDesc = buildString {
            append("$indent[$className]")
            if (text.isNotEmpty()) append(" text='$text'")
            if (contentDesc.isNotEmpty()) append(" desc='$contentDesc'")
            if (viewId.isNotEmpty()) append(" id='$viewId'")
            if (isClickable) append(" [clickable]")
            if (!isEnabled) append(" [disabled]")
        }
        
        builder.append(nodeDesc).append("\n")
        
        // 遍历子节点
        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i)
            if (child != null) {
                traverseNode(child, builder, depth + 1)
                child.recycle()
            }
        }
    }
    
    /**
     * 判断节点是否有用
     */
    private fun isUsefulNode(node: AccessibilityNodeInfo): Boolean {
        // 有文本内容
        if (!node.text.isNullOrEmpty()) return true
        
        // 有描述
        if (!node.contentDescription.isNullOrEmpty()) return true
        
        // 可点击
        if (node.isClickable) return true
        
        // 有 ViewId
        if (!node.viewIdResourceName.isNullOrEmpty()) return true
        
        // 有子节点
        if (node.childCount > 0) return true
        
        return false
    }
    
    /**
     * 提取可点击元素列表
     */
    fun extractClickableElements(rootNode: AccessibilityNodeInfo?): List<ClickableElement> {
        val elements = mutableListOf<ClickableElement>()
        
        if (rootNode == null) {
            return elements
        }
        
        try {
            collectClickableElements(rootNode, elements)
        } catch (e: Exception) {
            Timber.e(e, "提取可点击元素失败")
        }
        
        return elements
    }
    
    private fun collectClickableElements(node: AccessibilityNodeInfo, elements: MutableList<ClickableElement>) {
        if (node.isClickable && node.isVisibleToUser) {
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            
            elements.add(
                ClickableElement(
                    text = node.text?.toString() ?: "",
                    contentDesc = node.contentDescription?.toString() ?: "",
                    className = node.className?.toString() ?: "",
                    viewId = node.viewIdResourceName ?: "",
                    bounds = bounds
                )
            )
        }
        
        // 递归子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectClickableElements(child, elements)
                child.recycle()
            }
        }
    }

    companion object {
        private const val MAX_DEPTH = 10
        
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
    val bounds: android.graphics.Rect
)
