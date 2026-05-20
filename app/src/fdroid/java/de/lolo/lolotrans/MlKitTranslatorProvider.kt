package de.lolo.lolotrans

class MlKitTranslatorProvider : TranslatorProvider {
    init {
        throw IllegalStateException(
            "ML Kit ist im F-Droid-Build nicht verfügbar. Bitte LibreTranslate verwenden.")
    }

    override suspend fun detectLanguage(text: String): Result<String> =
        Result.failure(IllegalStateException("ML Kit nicht verfügbar."))

    override suspend fun ensureModelReady(
        sourceLanguage: String, targetLanguage: String
    ): Result<Unit> =
        Result.failure(IllegalStateException("ML Kit nicht verfügbar."))

    override suspend fun translate(
        text: String, sourceLanguage: String, targetLanguage: String
    ): Result<String> =
        Result.failure(IllegalStateException("ML Kit nicht verfügbar."))

    override fun close() {}
}
