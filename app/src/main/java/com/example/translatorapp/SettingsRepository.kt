package com.example.translatorapp

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
        private val KEY_SHOW_SERVICE_NOTIFICATION = booleanPreferencesKey("show_service_notification")
    }

    // --- Zielsprache ---
    val targetLanguage: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_TARGET_LANGUAGE] ?: "de"
    }

    suspend fun setTargetLanguage(lang: String) {
        context.dataStore.edit { it[KEY_TARGET_LANGUAGE] = lang }
    }

    // --- Quellsprache (Default: Englisch, nicht "auto") ---
    val sourceLanguage: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SOURCE_LANGUAGE] ?: "en"
    }

    suspend fun setSourceLanguage(lang: String) {
        context.dataStore.edit { it[KEY_SOURCE_LANGUAGE] = lang }
    }

    // --- Bubble-Größe (String-Speicherung, rückwärtskompatibel) ---
    private val KEY_BUBBLE_SIZE_NAME = stringPreferencesKey("bubble_size_name")

    /** Liefert die BubbleSize als Flow. Liest neuen String-Key, fällt auf alten Int-Key zurück. */
    val bubbleSize: Flow<BubbleSize> = context.dataStore.data.map { prefs ->
        val name = prefs[KEY_BUBBLE_SIZE_NAME]
        if (name != null) {
            BubbleSize.fromName(name)
        } else {
            // Rückwärtskompatibilität: alter dp-Wert migrieren
            val legacyDp = prefs[KEY_BUBBLE_SIZE_DP]
            if (legacyDp != null) BubbleSize.fromLegacyDp(legacyDp) else BubbleSize.M
        }
    }

    suspend fun setBubbleSize(size: BubbleSize) {
        context.dataStore.edit {
            it[KEY_BUBBLE_SIZE_NAME] = size.name
            // Alten Key löschen, damit er nicht mehr als Fallback dient
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

    // --- Service-Benachrichtigung anzeigen (Standard: AUS) ---
    val showServiceNotification: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_SHOW_SERVICE_NOTIFICATION] ?: false
    }

    suspend fun setShowServiceNotification(show: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_SERVICE_NOTIFICATION] = show }
    }
}
