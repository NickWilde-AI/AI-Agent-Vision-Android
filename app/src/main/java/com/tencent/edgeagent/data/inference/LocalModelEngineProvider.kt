package com.tencent.edgeagent.data.inference

import timber.log.Timber

object LocalModelEngineProvider {

    @Volatile
    private var instance: ILocalModelEngine? = null

    fun getInstance(): ILocalModelEngine {
        return instance ?: synchronized(this) {
            instance ?: createEngine().also { instance = it }
        }
    }

    fun resetForTests() {
        instance?.release()
        instance = null
    }

    private fun createEngine(): ILocalModelEngine {
        val manager = LocalModelManager.getInstance()
        return when (val status = manager.getGemmaStatus()) {
            is LocalModelStatus.Ready -> {
                Timber.i("[LocalModel] using Gemma LiteRT-LM engine: ${status.file.absolutePath}")
                GemmaLiteRtModelEngine.getInstance(manager)
            }
            else -> {
                Timber.w("[LocalModel] Gemma unavailable, using mock engine: $status")
                MockModelEngine.getInstance()
            }
        }
    }
}
