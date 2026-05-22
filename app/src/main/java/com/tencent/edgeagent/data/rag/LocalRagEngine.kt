package com.tencent.edgeagent.data.rag

import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.ln

/**
 * Lightweight local retrieval engine for Agent strategy knowledge.
 *
 * This is intentionally dependency-free. It gives the Agent useful local context
 * before we introduce a heavier vector store such as Room + embeddings.
 */
class LocalRagEngine private constructor() {

    private val documents = CopyOnWriteArrayList<RagDocument>()

    init {
        seedBuiltInDocuments()
    }

    fun upsert(document: RagDocument) {
        documents.removeAll { it.id == document.id }
        documents.add(document)
    }

    fun retrieve(
        query: String,
        currentPackage: String? = null,
        limit: Int = DEFAULT_LIMIT
    ): List<RagHit> {
        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) return emptyList()

        val scored = documents.mapNotNull { document ->
            val score = scoreDocument(queryTokens, currentPackage, document)
            if (score > 0.0) RagHit(document, score) else null
        }

        return scored
            .sortedByDescending { it.score }
            .take(limit)
            .also { Timber.d("[RAG] query='$query' hits=${it.map { hit -> hit.document.id }}") }
    }

    fun buildContext(
        query: String,
        currentPackage: String? = null,
        limit: Int = DEFAULT_LIMIT
    ): String {
        val hits = retrieve(query, currentPackage, limit)
        if (hits.isEmpty()) return "无本地策略命中。"

        return buildString {
            append("本地 RAG 策略命中：\n")
            hits.forEachIndexed { index, hit ->
                append("${index + 1}. [${hit.document.category}] ${hit.document.title}\n")
                append("   ${hit.document.content}\n")
            }
        }
    }

    private fun scoreDocument(
        queryTokens: Set<String>,
        currentPackage: String?,
        document: RagDocument
    ): Double {
        val docTokens = tokenize("${document.title} ${document.content} ${document.tags.joinToString(" ")}")
        if (docTokens.isEmpty()) return 0.0

        val overlap = queryTokens.count { it in docTokens }
        if (overlap == 0) return 0.0

        val packageBoost = if (currentPackage != null && currentPackage in document.packageNames) 1.5 else 1.0
        val tagBoost = 1.0 + document.tags.count { it in queryTokens } * 0.15
        val lengthPenalty = ln(docTokens.size.toDouble() + 2.0)

        return (overlap / lengthPenalty) * packageBoost * tagBoost
    }

    private fun tokenize(text: String): Set<String> {
        val normalized = text.lowercase()
        val wordTokens = Regex("[a-z0-9_./:-]+|[\\u4e00-\\u9fa5]{1,4}")
            .findAll(normalized)
            .map { it.value }
            .toMutableSet()

        val chineseChars = normalized.filter { it in '\u4e00'..'\u9fa5' }
        for (i in 0 until chineseChars.length - 1) {
            wordTokens.add(chineseChars.substring(i, i + 2))
        }
        return wordTokens
    }

    private fun seedBuiltInDocuments() {
        if (documents.isNotEmpty()) return

        DEFAULT_DOCUMENTS.forEach { documents.add(it) }
    }

    companion object {
        private const val DEFAULT_LIMIT = 4

        @Volatile
        private var instance: LocalRagEngine? = null

        fun getInstance(): LocalRagEngine {
            return instance ?: synchronized(this) {
                instance ?: LocalRagEngine().also { instance = it }
            }
        }

        private val DEFAULT_DOCUMENTS = listOf(
            RagDocument(
                id = "policy.high_risk_confirmation",
                title = "高风险动作必须确认",
                content = "发送消息、支付、下单、删除、转账、提交订单等动作不能自动点击最终确认按钮。Agent 可以准备草稿或停在确认页，然后返回 NO_ACTION 等待用户确认。",
                category = "safety",
                tags = setOf("发送", "支付", "下单", "删除", "确认", "安全")
            ),
            RagDocument(
                id = "wechat.draft_only",
                title = "微信策略：只填草稿，不自动发送",
                content = "微信任务应分解为打开微信、搜索联系人、进入聊天页、聚焦输入框、输入草稿。不要点击发送按钮；草稿填好后返回 NO_ACTION。",
                category = "wechat",
                tags = setOf("微信", "wechat", "联系人", "聊天", "草稿", "发送"),
                packageNames = setOf("com.tencent.mm")
            ),
            RagDocument(
                id = "wechat.search_path",
                title = "微信联系人搜索路径",
                content = "在微信首页优先寻找搜索入口或顶部搜索框。进入搜索后输入联系人名称，选择匹配联系人。页面不稳定时 WAIT 一轮，重复失败则 BACK 回到上级。",
                category = "wechat",
                tags = setOf("微信", "搜索", "联系人", "输入"),
                packageNames = setOf("com.tencent.mm")
            ),
            RagDocument(
                id = "settings.device_control",
                title = "系统设置策略",
                content = "设备控制优先走本地 Android API 或系统设置页，不依赖视觉模型自由点击。Wi-Fi、蓝牙、飞行模式应打开对应设置页让用户确认。",
                category = "system",
                tags = setOf("设置", "wifi", "蓝牙", "亮度", "音量"),
                packageNames = setOf("com.android.settings")
            ),
            RagDocument(
                id = "browser.search",
                title = "浏览器搜索策略",
                content = "浏览器任务优先聚焦地址栏或搜索框，输入查询内容后提交。涉及登录、支付、提交隐私信息时停止并等待用户确认。",
                category = "browser",
                tags = setOf("浏览器", "搜索", "网页", "提交")
            )
        )
    }
}

data class RagDocument(
    val id: String,
    val title: String,
    val content: String,
    val category: String,
    val tags: Set<String> = emptySet(),
    val packageNames: Set<String> = emptySet()
)

data class RagHit(
    val document: RagDocument,
    val score: Double
)
