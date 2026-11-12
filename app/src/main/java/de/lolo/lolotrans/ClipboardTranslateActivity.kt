package de.lolo.lolotrans

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Dialog-Activity für zuverlässigen Clipboard-Zugriff mit Fokus.
 * Erscheint als kleines Fenster, liest die Zwischenablage, erkennt bei Bedarf
 * die Quellsprache automatisch, übersetzt und zeigt das Ergebnis im Overlay.
 */
class ClipboardTranslateActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var translationManager: TranslationManager
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var copyButton: Button
    private lateinit var closeButton: Button
    private var hasStarted = false
    private var displayedResult: String? = null

    companion object {
        const val EXTRA_TEXT_TO_TRANSLATE = "de.lolo.lolotrans.extra.TEXT_TO_TRANSLATE"
        const val MAX_TRANSLATE_TEXT_LENGTH = 100_000
        private const val TAG = "ClipboardActivity"
        private const val PERF = "Perf"

        /** Referenz auf das aktuell aktive Übersetzungs-Overlay. */
        @Volatile
        private var activeOverlay: TranslationOverlayManager? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(16))
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(0xFF0A0A0A.toInt())
        }

        val title = TextView(this).apply {
            text = getString(R.string.notification_title)
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        }
        root.addView(title, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        progressBar = ProgressBar(this).apply { isIndeterminate = true }
        root.addView(progressBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, dpToPx(20), 0, dpToPx(12))
        })

        val statusScroll = object : ScrollView(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val maxHeight = (resources.displayMetrics.heightPixels * 0.55f).toInt()
                super.onMeasure(
                    widthMeasureSpec,
                    android.view.View.MeasureSpec.makeMeasureSpec(maxHeight, android.view.View.MeasureSpec.AT_MOST)
                )
            }
        }
        statusText = TextView(this).apply {
            text = getString(R.string.translating_clipboard)
            textSize = 15f
            setTextColor(0xFFB8B8B8.toInt())
            gravity = Gravity.CENTER
        }
        statusScroll.addView(statusText, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(statusScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        copyButton = Button(this).apply {
            text = getString(R.string.copy_button)
            setBackgroundColor(0xFF222222.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            visibility = android.view.View.GONE
            setOnClickListener {
                val text = displayedResult ?: return@setOnClickListener
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("translation", text))
                Toast.makeText(this@ClipboardTranslateActivity, R.string.translation_copied, Toast.LENGTH_SHORT).show()
            }
        }
        closeButton = Button(this).apply {
            text = getString(R.string.close_button)
            setBackgroundColor(0xFF222222.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            visibility = android.view.View.GONE
            setOnClickListener { finishAndCleanup() }
        }
        buttonRow.addView(copyButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, dpToPx(8), 0)
        })
        buttonRow.addView(closeButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(buttonRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, dpToPx(20), 0, 0)
        })

        setContentView(root)
        setFinishOnTouchOutside(true)
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume hasWindowFocus=${hasWindowFocus()}")
        if (!hasStarted) {
            if (hasWindowFocus()) {
                startProcessing("onResume+focus")
            } else {
                // Auf Geräten, die per NEW_TASK gestarteten Activities nicht sofort
                // Fokus geben (z.B. Vivo), trotzdem nach kurzer Verzögerung starten.
                scope.launch {
                    delay(500)
                    if (!hasStarted) {
                        Log.d(TAG, "onResume delayed start (no focus after 500ms)")
                        startProcessing("onResume+delayed")
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Log.d(TAG, "onWindowFocusChanged hasFocus=$hasFocus hasStarted=$hasStarted")
        if (hasFocus && !hasStarted) {
            startProcessing("onWindowFocus")
        }
    }

    private fun startProcessing(source: String) {
        hasStarted = true
        Log.d(TAG, "startProcessing from $source")
        val t0 = System.currentTimeMillis()
        scope.launch {
            if (!hasWindowFocus()) delay(100)
            Log.d(PERF, "focus=${System.currentTimeMillis() - t0}ms")
            processClipboard(t0)
        }
    }

    private suspend fun processClipboard(t0: Long) {
        var t = System.currentTimeMillis()

        // 1. Markierten Text bevorzugen, Clipboard nur als Fallback.
        statusText.text = getString(R.string.translating_selection)
        val requestedText = readRequestedText()
        Log.w(TAG, "readRequestedText: ${if (requestedText != null) "${requestedText.length} Zeichen" else "null"}")
        val textToTranslate = when {
            requestedText != null -> {
                Log.w(TAG, "Verwende requestedText (${requestedText.length} Zeichen)")
                requestedText
            }
            else -> {
                statusText.text = getString(R.string.translating_clipboard)
                Log.w(TAG, "Keine Selection-Quelle → Clipboard-Fallback")
                val clip = readClipboardTextWithRetry()?.take(MAX_TRANSLATE_TEXT_LENGTH)
                if (clip != null) {
                    if (SelectionAccessibilityService.isTextConsumed(clip)) {
                        Log.w(TAG, "Clipboard-Fallback: Text bereits verbraucht → überspringe")
                        null
                    } else {
                        Log.w(TAG, "Clipboard-Fallback: ${clip.length} Zeichen")
                        clip
                    }
                } else {
                    Log.w(TAG, "Clipboard-Fallback: nichts in der Zwischenablage")
                    null
                }
            }
        }
        Log.w(TAG, "textToTranslate: ${if (textToTranslate != null) "${textToTranslate.length} Zeichen" else "null"}")
        Log.d(PERF, "textRead=${System.currentTimeMillis() - t}ms")
        t = System.currentTimeMillis()
        if (textToTranslate.isNullOrEmpty()) {
            Log.d(TAG, "Kein markierter Text und kein Clipboard-Text")
            val accessibilityActive = AccessibilityStatus.isSelectionServiceEnabled(this@ClipboardTranslateActivity)
            val message = if (!accessibilityActive) {
                getString(R.string.accessibility_service_not_active)
            } else {
                if (!SelectionAccessibilityService.isConnected()) {
                    Log.w(TAG, "Accessibility enabled in Android settings, but active service reference is unavailable")
                }
                getString(R.string.no_text_selected_or_clipboard)
            }
            showError(message, showToast = false)
            return
        }
        Log.d(TAG, "Text: ${textToTranslate.length} Zeichen")

        val settingsRepo = SettingsRepository(applicationContext)
        val translationProvider = settingsRepo.effectiveTranslationProvider.first()
        val libreBaseUrl = settingsRepo.libreTranslateBaseUrl.first()
        val libreApiKey = settingsRepo.libreTranslateApiKey.first()
        val externalProviderApiKey = settingsRepo.externalProviderApiKey.first()
        translationManager = TranslationManager(
            getTranslationProvider = { translationProvider },
            getLibreBaseUrl = { libreBaseUrl },
            getLibreApiKey = { libreApiKey },
            getExternalProviderApiKey = { externalProviderApiKey }
        )
        val sourceLang = settingsRepo.sourceLanguage.first()
        val targetLang = settingsRepo.targetLanguage.first()

        // 2. Quellsprache bestimmen
        val effectiveSource: String
        if (translationProvider == TranslationProvider.TELEGRAM) {
            effectiveSource = "auto"
            Log.d(TAG, "Telegram-Modus: Quellsprache wird durch Telegram erkannt")
        } else if (sourceLang == "auto") {
            if (supportsSourceAuto(translationProvider)) {
                effectiveSource = "auto"
                Log.d(TAG, "Auto-Modus: Provider $translationProvider erkennt die Quellsprache serverseitig")
            } else {
                statusText.text = getString(R.string.identifying_language)
                Log.d(TAG, "Auto-Modus: erkenne Sprache...")
                val detected = translationManager.identifyLanguage(textToTranslate)
                Log.d(PERF, "languageId=${System.currentTimeMillis() - t}ms")
                t = System.currentTimeMillis()
                if (detected == null) {
                    Log.w(TAG, "Spracherkennung fehlgeschlagen")
                    showError(getString(R.string.language_not_identified))
                    return
                }
                val mapped = TranslationManager.toLanguageTag(detected)
                if (!TranslationManager.isLanguageSupported(detected)) {
                    Log.w(TAG, "Erkannte Sprache '$detected' nicht für Übersetzung unterstützt")
                    showError(getString(R.string.language_not_supported, detected))
                    return
                }
                effectiveSource = detected
                statusText.text = getString(R.string.identified_language, detected)
                Log.d(TAG, "Erkannte Sprache: $detected -> mapped=$mapped")
            }
        } else {
            effectiveSource = sourceLang
            Log.d(TAG, "Feste Quellsprache: $effectiveSource")
        }

        // 3. Gleiche Sprache prüfen (mit Normalisierung)
        val manualSourceSelected = sourceLang != "auto" && translationProvider != TranslationProvider.TELEGRAM
        val sourceTag = TranslationManager.toLanguageTag(effectiveSource)
        val targetTag = TranslationManager.toLanguageTag(targetLang)
        Log.d(TAG, "sourceTag=$sourceTag targetTag=$targetTag")
        val manuallySameLanguage = manualSourceSelected &&
            effectiveSource != "auto" &&
            normalizeTag(sourceTag) == normalizeTag(targetTag)
        if (manuallySameLanguage) {
            Log.w(TAG, "Quell- und Zielsprache identisch ($sourceTag == $targetTag)")
            showError(getString(R.string.same_language_warning))
            return
        }

        // 4. Übersetzen
        statusText.text = getString(R.string.translation_running)
        Log.d(TAG, "Starte Übersetzung: $effectiveSource → $targetLang")

        val downloadResult = translationManager.ensureModelDownloaded(effectiveSource, targetLang)
        Log.d(PERF, "modelCheck=${System.currentTimeMillis() - t}ms " +
                "(cache=${if (System.currentTimeMillis() - t < 20) "hit" else "miss/download"})")
        t = System.currentTimeMillis()
        if (downloadResult.isFailure) {
            val err = downloadResult.exceptionOrNull()?.message ?: "unbekannt"
            Log.e(TAG, "Modelldownload fehlgeschlagen: $err")
            showTranslationOverlay(getString(R.string.translation_failed) + ": $err")
            return
        }

        val translationResult = translationManager.translate(
            textToTranslate, effectiveSource, targetLang)
        Log.d(PERF, "translate=${System.currentTimeMillis() - t}ms")
        t = System.currentTimeMillis()
        val shouldFinish = if (translationResult.isSuccess) {
            val resultText = translationResult.getOrThrow()
            SelectionAccessibilityService.setConsumedText(textToTranslate)
            val equalsSource = resultText == textToTranslate
            val overlayShown = if (equalsSource && manuallySameLanguage) {
                Log.w(TAG, "Ergebnis identisch mit Quelle – zeige Warnung statt Originaltext")
                showTranslationOverlay(getString(R.string.same_language_warning))
            } else {
                showTranslationOverlay(resultText)
            }
            SelectionAccessibilityService.clearConsumedSelectionAfterDelay()
            overlayShown
        } else {
            val err = translationResult.exceptionOrNull()?.message ?: getString(R.string.translation_failed)
            Log.e(TAG, "Übersetzung fehlgeschlagen: $err")
            showTranslationOverlay(getString(R.string.translation_failed) + ": $err")
        }
        Log.d(PERF, "overlayShow=${System.currentTimeMillis() - t}ms")
        Log.d(PERF, "total=${System.currentTimeMillis() - t0}ms")
        if (shouldFinish) {
            finishAndCleanup()
        }
    }

    private fun showError(message: String, showToast: Boolean = true) {
        progressBar.visibility = android.view.View.GONE
        statusText.text = message
        closeButton.visibility = android.view.View.VISIBLE
        if (showToast) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun showTranslationOverlay(text: String): Boolean {
        val overlayHost = SelectionAccessibilityService.getOverlayHost()
        if (overlayHost == null) {
            Log.w(TAG, "Accessibility overlay host unavailable, keeping result in dialog")
            showResultInDialog(text)
            return false
        }
        try {
            val repo = SettingsRepository(applicationContext)
            // Vorheriges Overlay entfernen (z.B. von einer vorherigen Activity-Instanz)
            activeOverlay?.remove()
            val newOverlay = TranslationOverlayManager(
                overlayHost.context,
                overlayHost.windowManager,
                repo
            )
            newOverlay.show(text)
            activeOverlay = newOverlay
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Overlay fehlgeschlagen", e)
            showResultInDialog(text)
            return false
        }
    }

    private fun showResultInDialog(text: String) {
        displayedResult = text
        progressBar.visibility = android.view.View.GONE
        statusText.gravity = Gravity.START
        statusText.text = text
        copyButton.visibility = android.view.View.VISIBLE
        closeButton.visibility = android.view.View.VISIBLE
    }

    /**
     * Liest die Zwischenablage mit kurzen Wiederholungen. Direkt nach dem Oeffnen der
     * Activity ist der Zwischenablage-Inhalt gelegentlich noch nicht lesbar; ein einzelner
     * Versuch fuehrte dann faelschlich zu "kein Text erkannt".
     */
    private suspend fun readClipboardTextWithRetry(): String? {
        repeat(3) { attempt ->
            readClipboardText()?.let { return it }
            if (attempt < 2) delay(120)
        }
        Log.w(TAG, "Zwischenablage nach 3 Versuchen leer oder nicht lesbar")
        return null
    }

    private fun readClipboardText(): String? {
        return try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
            val clip = cm.primaryClip ?: return null
            if (clip.itemCount == 0) return null
            clip.getItemAt(0).coerceToText(this)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.w(TAG, "Zwischenablage nicht lesbar: ${e.message}")
            null
        }
    }

    private fun readRequestedText(): String? {
        return intent.getStringExtra(EXTRA_TEXT_TO_TRANSLATE)
            ?.trim()
            ?.take(MAX_TRANSLATE_TEXT_LENGTH)
            ?.takeIf { it.isNotEmpty() }
    }

    private fun finishAndCleanup() {
        if (::translationManager.isInitialized) translationManager.close()
        scope.cancel()
        if (!isFinishing) finish()
        Log.d(TAG, "beendet")
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** Normalisiert Sprach-Tags für robusten Vergleich (lowercase, trim). */
    private fun normalizeTag(tag: String): String = tag.lowercase().trim()

    private fun supportsSourceAuto(provider: TranslationProvider): Boolean =
        provider == TranslationProvider.LIBRE_TRANSLATE ||
            provider == TranslationProvider.FREETRANSLATIONS ||
            provider == TranslationProvider.TELEGRAM

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
