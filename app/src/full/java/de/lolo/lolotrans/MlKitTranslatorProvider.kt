package de.lolo.lolotrans

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val TAG = "MlKitProvider"

class MlKitTranslatorProvider : TranslatorProvider {

    private val translatorCache = LinkedHashMap<String, com.google.mlkit.nl.translate.Translator>()
    private val languageIdentifier = LanguageIdentification.getClient()
    private val downloadedPairs = mutableSetOf<String>()
    private var currentPairKey: String = ""

    private fun cacheKey(src: String, tgt: String) = "$src→$tgt"

    companion object {
        private const val MAX_CACHED = 5

        fun mapToTranslateLanguage(code: String): String? = when (code) {
            "en" -> TranslateLanguage.ENGLISH
            "de" -> TranslateLanguage.GERMAN
            "fr" -> TranslateLanguage.FRENCH
            "es" -> TranslateLanguage.SPANISH
            "it" -> TranslateLanguage.ITALIAN
            "pt" -> TranslateLanguage.PORTUGUESE
            "nl" -> TranslateLanguage.DUTCH
            "pl" -> TranslateLanguage.POLISH
            "ru" -> TranslateLanguage.RUSSIAN
            "uk" -> TranslateLanguage.UKRAINIAN
            "tr" -> TranslateLanguage.TURKISH
            "ar" -> TranslateLanguage.ARABIC
            "zh", "zh-Hans", "zh-Hant" -> TranslateLanguage.CHINESE
            "ja" -> TranslateLanguage.JAPANESE
            "ko" -> TranslateLanguage.KOREAN
            "hi" -> TranslateLanguage.HINDI
            "th" -> TranslateLanguage.THAI
            "vi" -> TranslateLanguage.VIETNAMESE
            "sv" -> TranslateLanguage.SWEDISH
            "ro" -> TranslateLanguage.ROMANIAN
            "no", "nb", "nn" -> TranslateLanguage.NORWEGIAN
            "da" -> TranslateLanguage.DANISH
            "fi" -> TranslateLanguage.FINNISH
            "cs" -> TranslateLanguage.CZECH
            "el" -> TranslateLanguage.GREEK
            "hu" -> TranslateLanguage.HUNGARIAN
            "id" -> TranslateLanguage.INDONESIAN
            "ms" -> TranslateLanguage.MALAY
            "sk" -> TranslateLanguage.SLOVAK
            "bg" -> TranslateLanguage.BULGARIAN
            "hr" -> TranslateLanguage.CROATIAN
            "ca" -> TranslateLanguage.CATALAN
            "iw", "he" -> TranslateLanguage.HEBREW
            else -> null
        }

        fun toLanguageTag(internalCode: String): String = mapToTranslateLanguage(internalCode)
            ?: TranslateLanguage.ENGLISH

        fun isLanguageSupported(code: String): Boolean = mapToTranslateLanguage(code) != null
    }

    override suspend fun detectLanguage(text: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val code = languageIdentifier.identifyLanguage(text).await()
            Log.d(TAG, "Language identification result=$code")
            if (code == "und" || code.isNullOrEmpty()) {
                Result.failure(IllegalStateException("Sprache konnte nicht erkannt werden"))
            } else {
                Result.success(code)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Language identification failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun ensureModelReady(
        sourceLanguage: String, targetLanguage: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val srcTag = toLanguageTag(sourceLanguage)
            val tgtTag = toLanguageTag(targetLanguage)
            if (srcTag.equals(tgtTag, ignoreCase = true)) {
                return@withContext Result.failure(
                    IllegalStateException("Quell- und Zielsprache identisch ($srcTag)"))
            }

            val key = cacheKey(srcTag, tgtTag)
            currentPairKey = key

            translatorCache[key]?.let {
                if (key in downloadedPairs) return@withContext Result.success(Unit)
                val conditions = DownloadConditions.Builder().build()
                it.downloadModelIfNeeded(conditions).await()
                downloadedPairs.add(key)
                return@withContext Result.success(Unit)
            }

            val options = TranslatorOptions.Builder()
                .setSourceLanguage(srcTag)
                .setTargetLanguage(tgtTag)
                .build()
            val translator = Translation.getClient(options)

            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions).await()
            downloadedPairs.add(key)

            if (translatorCache.size >= MAX_CACHED) {
                val oldest = translatorCache.entries.first()
                oldest.value.close()
                translatorCache.remove(oldest.key)
                downloadedPairs.remove(oldest.key)
            }
            translatorCache[key] = translator
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "ensureModelDownloaded failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun translate(
        text: String, sourceLanguage: String, targetLanguage: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val t = translatorCache[currentPairKey]
                ?: return@withContext Result.failure(IllegalStateException("Kein Translator im Cache"))
            val result = t.translate(text).await()
            if (result == text) Log.w(TAG, "Ergebnis identisch mit Quelle")
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "translate failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    override fun close() {
        translatorCache.values.forEach { it.close() }
        translatorCache.clear()
        downloadedPairs.clear()
        currentPairKey = ""
        try { languageIdentifier.close() } catch (_: Exception) {}
    }
}
