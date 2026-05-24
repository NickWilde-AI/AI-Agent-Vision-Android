package com.tencent.edgeagent.data.inference

import android.content.Context
import timber.log.Timber
import java.io.File

class LocalModelManager private constructor() {

    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        Timber.i("[LocalModel] initialized")
    }

    fun getGemmaStatus(): LocalModelStatus {
        val context = appContext ?: return LocalModelStatus.NotInitialized
        val candidates = buildGemmaCandidateFiles(context)
        val existingFile = candidates.firstOrNull { it.exists() }
            ?: return LocalModelStatus.Missing(candidates)

        val size = existingFile.length()
        return if (size == GEMMA_4_E2B_EXPECTED_SIZE_BYTES) {
            LocalModelStatus.Ready(
                modelId = GEMMA_4_E2B_MODEL_ID,
                file = existingFile,
                sizeBytes = size,
                expectedSha256 = GEMMA_4_E2B_SHA256
            )
        } else {
            LocalModelStatus.InvalidSize(
                file = existingFile,
                sizeBytes = size,
                expectedSizeBytes = GEMMA_4_E2B_EXPECTED_SIZE_BYTES
            )
        }
    }

    fun buildModelInfoOrFallback(fallback: ModelInfo): ModelInfo {
        if (fallback.name == "Gemma 4 E2B") return fallback

        return when (val status = getGemmaStatus()) {
            is LocalModelStatus.Ready -> ModelInfo(
                name = "Gemma 4 E2B detected",
                version = "litert-lm-file-ready",
                sizeInMB = status.sizeBytes / BYTES_PER_MB,
                supportsMultimodal = true,
                avgInferenceTimeMs = 0L
            )
            is LocalModelStatus.InvalidSize -> ModelInfo(
                name = "Gemma 4 E2B invalid file",
                version = "${status.sizeBytes}/${status.expectedSizeBytes} bytes",
                sizeInMB = status.sizeBytes / BYTES_PER_MB,
                supportsMultimodal = false,
                avgInferenceTimeMs = 0L
            )
            else -> fallback
        }
    }

    private fun buildGemmaCandidateFiles(context: Context): List<File> {
        val relativePath = "models/$GEMMA_4_E2B_DIR/$GEMMA_4_E2B_FILE_NAME"
        val candidates = mutableListOf<File>()
        candidates += File(context.filesDir, relativePath)
        context.getExternalFilesDir(null)?.let { externalFilesDir ->
            candidates += File(externalFilesDir, relativePath)
        }
        return candidates
    }

    companion object {
        const val GEMMA_4_E2B_MODEL_ID = "litert-community/gemma-4-E2B-it-litert-lm"
        const val GEMMA_4_E2B_DIR = "gemma-4-e2b-it"
        const val GEMMA_4_E2B_FILE_NAME = "gemma-4-E2B-it.litertlm"
        const val GEMMA_4_E2B_EXPECTED_SIZE_BYTES = 2_588_147_712L
        const val GEMMA_4_E2B_SHA256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
        private const val BYTES_PER_MB = 1024f * 1024f

        @Volatile
        private var instance: LocalModelManager? = null

        fun getInstance(): LocalModelManager {
            return instance ?: synchronized(this) {
                instance ?: LocalModelManager().also { instance = it }
            }
        }
    }
}

sealed class LocalModelStatus {
    object NotInitialized : LocalModelStatus()
    data class Missing(val checkedFiles: List<File>) : LocalModelStatus()
    data class InvalidSize(
        val file: File,
        val sizeBytes: Long,
        val expectedSizeBytes: Long
    ) : LocalModelStatus()
    data class Ready(
        val modelId: String,
        val file: File,
        val sizeBytes: Long,
        val expectedSha256: String
    ) : LocalModelStatus()
}
