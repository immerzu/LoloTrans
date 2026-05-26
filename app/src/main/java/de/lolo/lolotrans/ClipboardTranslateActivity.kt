package de.lolo.lolotrans

import android.app.Activity
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
    private lateinit var closeButton: Button
    private var hasStarted = false

    companion object {
        private const val TAG = "ClipboardActivity"
        private const val PERF = "Perf"
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

        statusText = TextView(this).apply {
            text = getString(R.string.translating_clipboard)
            textSize = 15f
            setTextColor(0xFFB8B8B8.toInt())
            gravity = Gravity.CENTER
        }
        root.addView(statusText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        closeButton = Button(this).apply {
            text = getString(R.string.close_button)
            setBackgroundColor(0xFF222222.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            visibility = android.view.View.GONE
            setOnClickListener { finishAndCleanup() }
        }
        root.addView(closeButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, dpToPx(20), 0, 0)
        })

        setContentView(root)
        setFinishOnTouchOutside(false)
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume hasWindowFocus=${hasWindowFocus()}")
        if (!hasStarted && hasWindowFocus()) {
            startProcessing("onResume+focus")
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

        // 1. Clipboard lesen
        statusText.text = getString(R.string.translating_clipboard)
        val clipboardText = readClipboardText()
        Log.d(PERF, "clipboardRead=${System.currentTimeMillis() - t}ms")
        t = System.currentTimeMillis()
        if (clipboardText.isNullOrEmpty()) {
            Log.d(TAG, "Kein Clipboard-Text")
            showError(getString(R.string.no_text_clipboard))
            return
        }
        Log.d(TAG, "Clipboard-Text: ${clipboardText.length} Zeichen")

        val settingsRepo = SettingsRepository(applicationContext)
        val translationProvider = settingsRepo.effectiveTranslationProvider.first()
        val libreBaseUrl = settingsRepo.libreTranslateBaseUrl.first()
        val libreApiKey = settingsRepo.libreTranslateApiKey.first()
        val freeTranslationsApiKey = settingsRepo.freeTranslationsApiKey.first()
        translationManager = TranslationManager(
            getTranslationProvider = { translationProvider },
            getLibreBaseUrl = { libreBaseUrl },
            getLibreApiKey = { libreApiKey },
            getFreeTranslationsApiKey = { freeTranslationsApiKey }
        )
        val sourceLang = settingsRepo.sourceLanguage.first()
        val targetLang = settingsRepo.targetLanguage.first()

        // 2. Quellsprache bestimmen
        val effectiveSource: String
        if (sourceLang == "auto") {
            if (supportsSourceAuto(translationProvider)) {
                effectiveSource = "auto"
                Log.d(TAG, "Auto-Modus: Provider $translationProvider erkennt die Quellsprache serverseitig")
            } else {
                statusText.text = getString(R.string.identifying_language)
                Log.d(TAG, "Auto-Modus: erkenne Sprache...")
                val detected = translationManager.identifyLanguage(clipboardText)
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
        val sourceTag = TranslationManager.toLanguageTag(effectiveSource)
        val targetTag = TranslationManager.toLanguageTag(targetLang)
        Log.d(TAG, "sourceTag=$sourceTag targetTag=$targetTag")
        if (effectiveSource != "auto" && normalizeTag(sourceTag) == normalizeTag(targetTag)) {
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
            clipboardText, effectiveSource, targetLang)
        Log.d(PERF, "translate=${System.currentTimeMillis() - t}ms")
        t = System.currentTimeMillis()
        if (translationResult.isSuccess) {
            val resultText = translationResult.getOrThrow()
            val equalsSource = resultText == clipboardText
            if (equalsSource) {
                Log.w(TAG, "Ergebnis identisch mit Quelle – zeige Warnung statt Originaltext")
                showTranslationOverlay(getString(R.string.same_language_warning))
            } else {
                showTranslationOverlay(resultText)
            }
        } else {
            val err = translationResult.exceptionOrNull()?.message ?: getString(R.string.translation_failed)
            Log.e(TAG, "Übersetzung fehlgeschlagen: $err")
            showTranslationOverlay(getString(R.string.translation_failed) + ": $err")
        }
        Log.d(PERF, "overlayShow=${System.currentTimeMillis() - t}ms")
        Log.d(PERF, "total=${System.currentTimeMillis() - t0}ms")
        finishAndCleanup()
    }

    private fun showError(message: String) {
        progressBar.visibility = android.view.View.GONE
        statusText.text = message
        closeButton.visibility = android.view.View.VISIBLE
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showTranslationOverlay(text: String) {
        try {
            val wm = getSystemService(WINDOW_SERVICE) as android.view.WindowManager
            val repo = SettingsRepository(applicationContext)
            TranslationOverlayManager(applicationContext, wm, repo).show(text)
        } catch (e: Exception) {
            Log.e(TAG, "Overlay fehlgeschlagen", e)
        }
    }

    private fun readClipboardText(): String? {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(this)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
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
            provider == TranslationProvider.FREETRANSLATIONS

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
