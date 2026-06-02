package de.lolo.lolotrans

class DisabledTranslatorProvider(message: String) : TranslatorProvider {
    private val error = IllegalStateException(message)

    override suspend fun detectLanguage(text: String): Result<String> =
        Result.failure(error)

    override suspend fun ensureModelReady(
        sourceLanguage: String,
        targetLanguage: String
    ): Result<Unit> = Result.failure(error)

    override suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): Result<String> = Result.failure(error)

    override fun close() {}
}
