package de.lolo.lolotrans

internal fun createTranslatorProvider(
    provider: TranslationProvider,
    getLibreBaseUrl: () -> String,
    getLibreApiKey: () -> String,
    getExternalProviderApiKey: () -> String
): TranslatorProvider = when (provider) {
    TranslationProvider.ML_KIT -> MlKitTranslatorProvider()
    TranslationProvider.LIBRE_TRANSLATE -> LibreTranslateProvider(
        getBaseUrl = getLibreBaseUrl,
        getApiKey = getLibreApiKey
    )
    TranslationProvider.FREETRANSLATIONS -> DisabledTranslatorProvider("Provider not available in this build.")
    TranslationProvider.TELEGRAM -> DisabledTranslatorProvider("Telegram ist in diesem Build nicht verfügbar.")
}
