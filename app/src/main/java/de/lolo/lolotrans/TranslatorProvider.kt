package de.lolo.lolotrans

enum class TranslationProvider { ML_KIT, LIBRE_TRANSLATE }

interface TranslatorProvider {
    suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): Result<String>

    suspend fun detectLanguage(text: String): Result<String>

    suspend fun ensureModelReady(
        sourceLanguage: String,
        targetLanguage: String
    ): Result<Unit>

    fun close()
}
