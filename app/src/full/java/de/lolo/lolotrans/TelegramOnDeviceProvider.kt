package de.lolo.lolotrans

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TelegramOnDeviceProvider : TranslatorProvider {

    override suspend fun detectLanguage(text: String): Result<String> = withContext(Dispatchers.IO) {
        TelegramTdlibClient.start()
        Result.success("auto")
    }

    override suspend fun ensureModelReady(
        sourceLanguage: String,
        targetLanguage: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        TelegramTdlibClient.start()
        if (TelegramTdlibClient.authStatus.value == TelegramAuthStatus.Ready) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Telegram ist noch nicht angemeldet. Bitte in den Einstellungen anmelden."))
        }
    }

    override suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): Result<String> = TelegramTdlibClient.translate(text, targetLanguage)

    override fun close() { TelegramTdlibClient.stop() }
}
