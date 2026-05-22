package com.tencent.edgeagent.data.cloud

import com.tencent.edgeagent.BuildConfig

/**
 * 云端 API 配置
 * 
 * 使用说明：
 * 1. 将你的 API Key 填入下方
 * 2. 选择要使用的云端服务提供商
 * 3. 如果不想使用云端服务，设置 ENABLE_CLOUD = false
 * 
 * 获取 API Key：
 * - DeepSeek: https://platform.deepseek.com/
 * - 阿里云: https://bailian.console.aliyun.com/
 * - 豆包: https://console.volcengine.com/ark
 */
object CloudConfig {
    
    /**
     * 是否启用云端服务
     * 
     * true: 启用云端兜底（推荐）
     * false: 纯本地模式（隐私优先）
     */
    const val ENABLE_CLOUD = true
    
    /**
     * 云端服务提供商
     * 
     * 可选值：
     * - CloudProvider.DEEPSEEK（DeepSeek 文本模型）
     * - CloudProvider.ALIYUN（阿里云百炼 / 千问视觉模型，推荐用于屏幕理解）
     * - CloudProvider.DOUBAO（豆包）
     */
    val PROVIDER = CloudProvider.ALIYUN
    
    /**
     * DeepSeek API Key
     * 
     * 获取地址：https://platform.deepseek.com/api_keys
     * 
     * 注意：请不要将 API Key 提交到 Git 仓库！
     * 建议使用环境变量或本地配置文件
     */
    val DEEPSEEK_API_KEY = BuildConfig.DEEPSEEK_API_KEY
    
    /**
     * 阿里云 API Key
     */
    val ALIYUN_API_KEY = BuildConfig.ALIYUN_API_KEY
    
    /**
     * 豆包 API Key
     */
    val DOUBAO_API_KEY = BuildConfig.DOUBAO_API_KEY
    
    /**
     * 获取当前配置的 API Key
     */
    fun getApiKey(): String {
        return when (PROVIDER) {
            CloudProvider.DEEPSEEK -> DEEPSEEK_API_KEY
            CloudProvider.ALIYUN -> ALIYUN_API_KEY
            CloudProvider.DOUBAO -> DOUBAO_API_KEY
        }
    }
    
    /**
     * 检查 API Key 是否已配置
     */
    fun isApiKeyConfigured(): Boolean {
        val apiKey = getApiKey()
        return apiKey.isNotEmpty() && !apiKey.startsWith("YOUR_")
    }
}
