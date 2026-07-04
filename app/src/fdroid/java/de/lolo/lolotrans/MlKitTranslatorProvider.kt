package de.lolo.lolotrans

class MlKitTranslatorProvider : TranslatorProvider {
    private val error = IllegalStateException("ML Kit ist im F-Droid-Build nicht verfügbar.")

    override suspend fun detectLanguage(text: String): Result<String> =
        Result.failure(error)

    override suspend fun ensureModelReady(
        sourceLanguage: String, targetLanguage: String
    ): Result<Unit> =
        Result.failure(error)

    override suspend fun translate(
        text: String, sourceLanguage: String, targetLanguage: String
    ): Result<String> =
        Result.failure(error)

    override fun close() {}
}
