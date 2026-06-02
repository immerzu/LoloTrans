package de.lolo.lolotrans

import android.util.Log
import de.lolo.lolotrans.BuildConfig

private const val TAG = "TranslationManager"

class TranslationManager(
    private val getTranslationProvider: () -> TranslationProvider = {
        when (BuildConfig.DEFAULT_PROVIDER) {
            "ML_KIT" -> TranslationProvider.ML_KIT
            "FREETRANSLATIONS" -> TranslationProvider.FREETRANSLATIONS
            else -> TranslationProvider.LIBRE_TRANSLATE
        }
    },
    private val getLibreBaseUrl: () -> String = { "" },
    private val getLibreApiKey: () -> String = { "" },
    private val getExternalProviderApiKey: () -> String = { "" }
) {
    data class LanguageOption(val code: String, val labelResId: Int)

    private var currentProvider: TranslatorProvider? = null
    private var currentProviderType: TranslationProvider? = null

    companion object {
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

        @JvmStatic
        fun toLanguageTag(code: String): String = code.lowercase().trim()

        private val supportedLangSet = setOf(
            "auto", "en", "de", "fr", "es", "it", "pt", "nl", "pl", "ru",
            "uk", "tr", "ar", "zh", "ja", "ko", "hi", "th", "vi", "sv",
            "ro", "no", "da", "fi", "cs", "el", "hu", "id", "ms",
            "sk", "bg", "hr", "ca", "he"
        )

        @JvmStatic
        fun isLanguageSupported(code: String): Boolean = code in supportedLangSet
    }

    private fun requireProvider(): TranslatorProvider {
        val desired = getTranslationProvider()
        val effective = when {
            !BuildConfig.ML_KIT_AVAILABLE && desired == TranslationProvider.ML_KIT -> TranslationProvider.LIBRE_TRANSLATE
            !BuildConfig.EXTERNAL_PROVIDER_AVAILABLE && desired == TranslationProvider.FREETRANSLATIONS -> TranslationProvider.LIBRE_TRANSLATE
            else -> desired
        }
        if (currentProvider == null || currentProviderType != effective) {
            close()
            currentProviderType = effective
            currentProvider = createTranslatorProvider(
                provider = effective,
                getLibreBaseUrl = getLibreBaseUrl,
                getLibreApiKey = getLibreApiKey,
                getExternalProviderApiKey = getExternalProviderApiKey
            )
            Log.d(TAG, "Provider created: $effective")
        }
        return currentProvider!!
    }

    suspend fun identifyLanguage(text: String): String? {
        return requireProvider().detectLanguage(text).getOrNull()
    }

    suspend fun ensureModelDownloaded(
        sourceLang: String, targetLang: String
    ): Result<Unit> {
        return requireProvider().ensureModelReady(sourceLang, targetLang)
    }

    suspend fun translate(
        text: String, sourceLanguage: String, targetLanguage: String
    ): Result<String> {
        return requireProvider().translate(text, sourceLanguage, targetLanguage)
    }

    fun close() {
        currentProvider?.close()
        currentProvider = null
        currentProviderType = null
    }
}
