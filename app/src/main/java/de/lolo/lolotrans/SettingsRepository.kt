package de.lolo.lolotrans

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "translator_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        private val KEY_TARGET_LANGUAGE = stringPreferencesKey("target_language")
        private val KEY_SOURCE_LANGUAGE = stringPreferencesKey("source_language")
        private val KEY_BUBBLE_SIZE_DP = intPreferencesKey("bubble_size_dp")
        private val KEY_BUBBLE_POS_X = intPreferencesKey("bubble_pos_x")
        private val KEY_BUBBLE_POS_Y = intPreferencesKey("bubble_pos_y")
        private val KEY_OVERLAY_POS_X = intPreferencesKey("overlay_pos_x")
        private val KEY_OVERLAY_POS_Y = intPreferencesKey("overlay_pos_y")
        private val KEY_AUTO_CLOSE_SECONDS = intPreferencesKey("auto_close_seconds")
        private val KEY_AUTO_CLOSE_ENABLED = booleanPreferencesKey("auto_close_enabled")
        private val KEY_BUBBLE_ENABLED = booleanPreferencesKey("bubble_enabled")
        private val KEY_ACCESSIBILITY_APPROVED_ONCE = booleanPreferencesKey("accessibility_approved_once")
        private val KEY_SHOW_SERVICE_NOTIFICATION = booleanPreferencesKey("show_service_notification")
        private val KEY_TRANSLATION_PROVIDER = stringPreferencesKey("translation_provider")
        private val KEY_LIBRE_TRANSLATE_URL = stringPreferencesKey("libre_translate_url")
        private val KEY_LIBRE_TRANSLATE_API_KEY = stringPreferencesKey("libre_translate_api_key")
        private val KEY_EXTERNAL_PROVIDER_API_KEY = stringPreferencesKey("external_provider_api_key")
    }

    // --- Zielsprache ---
    val targetLanguage: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_TARGET_LANGUAGE] ?: "de"
    }

    suspend fun setTargetLanguage(lang: String) {
        context.dataStore.edit { it[KEY_TARGET_LANGUAGE] = lang }
    }

    // --- Quellsprache ---
    val sourceLanguage: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SOURCE_LANGUAGE] ?: "en"
    }

    suspend fun setSourceLanguage(lang: String) {
        context.dataStore.edit { it[KEY_SOURCE_LANGUAGE] = lang }
    }

    // --- Übersetzungsdienst ---
    val translationProvider: Flow<TranslationProvider> = context.dataStore.data.map { prefs ->
        val name = prefs[KEY_TRANSLATION_PROVIDER]
            ?: if (BuildConfig.ML_KIT_AVAILABLE) TranslationProvider.ML_KIT.name
               else TranslationProvider.LIBRE_TRANSLATE.name
        try { TranslationProvider.valueOf(name) } catch (_: Exception) {
            if (BuildConfig.ML_KIT_AVAILABLE) TranslationProvider.ML_KIT
            else TranslationProvider.LIBRE_TRANSLATE
        }
    }

    suspend fun setTranslationProvider(provider: TranslationProvider) {
        context.dataStore.edit { it[KEY_TRANSLATION_PROVIDER] = provider.name }
    }

    /** Liefert TranslationProvider als Fluss, für F-Droid kein ML_KIT. */
    val effectiveTranslationProvider: Flow<TranslationProvider> = translationProvider.map {
        when {
            !BuildConfig.ML_KIT_AVAILABLE && it == TranslationProvider.ML_KIT -> TranslationProvider.LIBRE_TRANSLATE
            !BuildConfig.EXTERNAL_PROVIDER_AVAILABLE && it == TranslationProvider.FREETRANSLATIONS -> TranslationProvider.LIBRE_TRANSLATE
            else -> it
        }
    }

    // --- LibreTranslate URL ---
    val libreTranslateBaseUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LIBRE_TRANSLATE_URL] ?: ""
    }

    suspend fun setLibreTranslateBaseUrl(url: String) {
        context.dataStore.edit { it[KEY_LIBRE_TRANSLATE_URL] = url }
    }

    // --- LibreTranslate API-Key ---
    val libreTranslateApiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LIBRE_TRANSLATE_API_KEY] ?: ""
    }

    suspend fun setLibreTranslateApiKey(key: String) {
        context.dataStore.edit { it[KEY_LIBRE_TRANSLATE_API_KEY] = key }
    }

    val externalProviderApiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_EXTERNAL_PROVIDER_API_KEY] ?: ""
    }

    suspend fun setExternalProviderApiKey(key: String) {
        context.dataStore.edit { it[KEY_EXTERNAL_PROVIDER_API_KEY] = key }
    }

    // --- Bubble-Größe ---
    private val KEY_BUBBLE_SIZE_NAME = stringPreferencesKey("bubble_size_name")

    val bubbleSize: Flow<BubbleSize> = context.dataStore.data.map { prefs ->
        val name = prefs[KEY_BUBBLE_SIZE_NAME]
        if (name != null) {
            BubbleSize.fromName(name)
        } else {
            val legacyDp = prefs[KEY_BUBBLE_SIZE_DP]
            if (legacyDp != null) BubbleSize.fromLegacyDp(legacyDp) else BubbleSize.M
        }
    }

    suspend fun setBubbleSize(size: BubbleSize) {
        context.dataStore.edit {
            it[KEY_BUBBLE_SIZE_NAME] = size.name
            it.remove(KEY_BUBBLE_SIZE_DP)
        }
    }

    // --- Bubble-Position ---
    suspend fun getBubblePosX(): Int =
        context.dataStore.data.map { it[KEY_BUBBLE_POS_X] ?: 100 }.first()

    suspend fun getBubblePosY(): Int =
        context.dataStore.data.map { it[KEY_BUBBLE_POS_Y] ?: 200 }.first()

    suspend fun saveBubblePosition(x: Int, y: Int) {
        context.dataStore.edit {
            it[KEY_BUBBLE_POS_X] = x
            it[KEY_BUBBLE_POS_Y] = y
        }
    }

    // --- Overlay-Position ---
    val overlayPosX: Flow<Int> = context.dataStore.data.map { it[KEY_OVERLAY_POS_X] ?: 50 }
    val overlayPosY: Flow<Int> = context.dataStore.data.map { it[KEY_OVERLAY_POS_Y] ?: 300 }

    suspend fun saveOverlayPosition(x: Int, y: Int) {
        context.dataStore.edit {
            it[KEY_OVERLAY_POS_X] = x
            it[KEY_OVERLAY_POS_Y] = y
        }
    }

    // --- Bubble aktiv/inaktiv ---
    val bubbleEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_BUBBLE_ENABLED] ?: false }

    suspend fun setBubbleEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BUBBLE_ENABLED] = enabled }
    }

    // --- Bedienungshilfe einmal genehmigt ---
    val accessibilityApprovedOnce: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_ACCESSIBILITY_APPROVED_ONCE] ?: false
    }

    suspend fun setAccessibilityApprovedOnce(approved: Boolean) {
        context.dataStore.edit { it[KEY_ACCESSIBILITY_APPROVED_ONCE] = approved }
    }

    // --- Service-Benachrichtigung ---
    val showServiceNotification: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_SHOW_SERVICE_NOTIFICATION] ?: false
    }

    suspend fun setShowServiceNotification(show: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_SERVICE_NOTIFICATION] = show }
    }
}
