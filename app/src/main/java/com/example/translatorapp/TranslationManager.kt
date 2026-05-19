package com.example.translatorapp

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val TAG = "TranslationManager"

class TranslationManager {

    data class LanguageOption(val code: String, val labelResId: Int)

    /** Cache: "source→target" → Translator (max. 5 Einträge). */
    private val translatorCache = LinkedHashMap<String, com.google.mlkit.nl.translate.Translator>()
    private var currentPairKey: String = ""
    private val languageIdentifier = LanguageIdentification.getClient()
    private val downloadedPairs = mutableSetOf<String>()

    private fun cacheKey(src: String, tgt: String) = "$src→$tgt"

    companion object {
        private const val MAX_CACHED = 5

        private fun mapToTranslateLanguage(code: String): String? = when (code) {
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

        val supportedLanguages = listOf(
            LanguageOption("auto", R.string.lang_auto),
            LanguageOption("en", R.string.lang_en), LanguageOption("de", R.string.lang_de),
            LanguageOption("fr", R.string.lang_fr), LanguageOption("es", R.string.lang_es),
            LanguageOption("it", R.string.lang_it), LanguageOption("pt", R.string.lang_pt),
            LanguageOption("nl", R.string.lang_nl), LanguageOption("pl", R.string.lang_pl),
            LanguageOption("ru", R.string.lang_ru), LanguageOption("ja", R.string.lang_ja),
            LanguageOption("ko", R.string.lang_ko), LanguageOption("zh", R.string.lang_zh),
            LanguageOption("ar", R.string.lang_ar), LanguageOption("tr", R.string.lang_tr),
            LanguageOption("hi", R.string.lang_hi), LanguageOption("th", R.string.lang_th),
            LanguageOption("vi", R.string.lang_vi), LanguageOption("sv", R.string.lang_sv),
        )

        val targetLanguages = supportedLanguages.filter { it.code != "auto" }
    }

    suspend fun identifyLanguage(text: String): String? = withContext(Dispatchers.IO) {
        try {
            val code = languageIdentifier.identifyLanguage(text).await()
            Log.d(TAG, "Language identification result=$code")
            if (code == "und" || code.isNullOrEmpty()) null else code
        } catch (e: Exception) {
            Log.e(TAG, "Language identification failed: ${e.message}", e)
            null
        }
    }

    suspend fun ensureModelDownloaded(
        sourceLang: String, targetLang: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val srcTag = toLanguageTag(sourceLang)
            val tgtTag = toLanguageTag(targetLang)
            if (srcTag.equals(tgtTag, ignoreCase = true)) {
                return@withContext Result.failure(
                    IllegalStateException("Quell- und Zielsprache identisch ($srcTag)"))
            }

            val key = cacheKey(srcTag, tgtTag)
            currentPairKey = key

            // Cache-Hit: bereits vorhandener Translator
            translatorCache[key]?.let {
                Log.d("Perf", "translatorCache=hit ($key)")
                if (key in downloadedPairs) {
                    Log.d("Perf", "modelCheck=skipped ($key already downloaded)")
                    return@withContext Result.success(Unit)
                }
                // Modell wurde evtl. gelöscht – einmalig neu downloaden
                val conditions = DownloadConditions.Builder().build()
                it.downloadModelIfNeeded(conditions).await()
                downloadedPairs.add(key)
                return@withContext Result.success(Unit)
            }

            // Cache-Miss: neuen Translator erstellen
            Log.d("Perf", "translatorCache=miss ($key)")
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(srcTag)
                .setTargetLanguage(tgtTag)
                .build()
            val translator = Translation.getClient(options)

            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions).await()
            downloadedPairs.add(key)

            // Cache verwalten (max 5)
            if (translatorCache.size >= MAX_CACHED) {
                val oldest = translatorCache.entries.first()
                oldest.value.close()
                translatorCache.remove(oldest.key)
                downloadedPairs.remove(oldest.key)
                Log.d(TAG, "Cache evicted: ${oldest.key}")
            }
            translatorCache[key] = translator
            Log.d(TAG, "Translator created and cached: $key (cache size=${translatorCache.size})")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "ensureModelDownloaded failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun translate(text: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val t = translatorCache[currentPairKey]
                ?: return@withContext Result.failure(IllegalStateException("Kein Translator im Cache"))
            val result = t.translate(text).await()
            val equalsSource = result == text
            Log.d(TAG, "translate: ${text.length}→${result.length} chars, equalsSource=$equalsSource ($currentPairKey)")
            if (equalsSource) Log.w(TAG, "Ergebnis identisch mit Quelle")
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "translate failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun close() {
        translatorCache.values.forEach { it.close() }
        translatorCache.clear()
        downloadedPairs.clear()
        currentPairKey = ""
        try { languageIdentifier.close() } catch (_: Exception) {}
        Log.d(TAG, "All translators closed")
    }
}
