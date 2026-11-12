package de.lolo.lolotrans

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val OVERLAY_TAG = "TranslationOverlay"

class TranslationOverlayManager(
    private val context: Context,
    private val windowManager: WindowManager,
    private val settingsRepository: SettingsRepository
) {
    private var overlayView: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var isAttached = false
    private val scopeJob = SupervisorJob()
    private val scope = CoroutineScope(scopeJob + Dispatchers.Main)
    private var containerParams: FrameLayout.LayoutParams? = null

    private var initialCardLeft = 0
    private var initialCardTop = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    /** SystemUI‑Callback‑Referenz, bei remove() zu deregistrieren. */
    private var systemUiCallback: (() -> Unit)? = null

    fun show(translatedText: String) {
        remove()
        scope.launch {
            val posX = try {
                settingsRepository.overlayPosX.first()
            } catch (_: Exception) { 50 }
            val posY = try {
                settingsRepository.overlayPosY.first()
            } catch (_: Exception) { 300 }
            createOverlayView(translatedText, posX, posY)
        }
    }

    private fun createOverlayView(translatedText: String, posX: Int, posY: Int) {
        // ── SystemUI‑Callback registrieren (Navigation/Home schliesst Overlay) ──
        val closeOnSystemUi: () -> Unit = { remove() }
        systemUiCallback = closeOnSystemUi
        SelectionAccessibilityService.addSystemUiWindowCallback(closeOnSystemUi)

        val cardWidth = dpToPx(320)

        // ── Haupt-Container (sichtbare Karte) VOR dem Root erstellen,
        //     damit der Touch-Listener die Kartenposition ermitteln kann ──
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
            val bg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(14).toFloat()
                setColor(0xFF0A0A0A.toInt())
                setStroke(dpToPx(1), 0xFF333333.toInt())
            }
            background = bg
            elevation = dpToPx(6).toFloat()
            // Karte frisst Klicks, damit der transparente Root sie nicht schliesst
            isClickable = true
        }

        // ── Kopfzeile mit Titel + X-Button ──
        val titleBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF111111.toInt())
            setPadding(dpToPx(14), dpToPx(6), dpToPx(6), dpToPx(6))
            setOnTouchListener { _, event -> handleDrag(event) }
        }

        val titleText = TextView(context).apply {
            text = context.getString(R.string.notification_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(0xFFFFFFFF.toInt())
        }
        titleBar.addView(titleText, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        // Kleiner X-Button oben rechts
        val closeX = TextView(context).apply {
            text = "✕"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(dpToPx(8), dpToPx(2), dpToPx(8), dpToPx(2))
            setOnClickListener { remove() }
        }
        titleBar.addView(closeX, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        container.addView(titleBar)

        // ── Textbereich (scrollbar) ──
        val scrollView = ScrollView(context).apply {
            isVerticalScrollBarEnabled = true
            setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
        }
        val textView = TextView(context).apply {
            text = translatedText
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(0xFFFFFFFF.toInt())
            movementMethod = ScrollingMovementMethod()
        }
        scrollView.addView(textView, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        container.addView(scrollView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // ── Trenner ──
        val divider = View(context).apply {
            setBackgroundColor(0xFF333333.toInt())
        }
        container.addView(divider, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1)))

        // ── Kompakte Button-Zeile ──
        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(8))
        }

        val copyBtn = createCompactButton(
            text = context.getString(R.string.copy_button),
            bgColor = 0xFF222222.toInt(),
            onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("translation", translatedText))
                Toast.makeText(context, R.string.translation_copied, Toast.LENGTH_SHORT).show()
            }
        )

        val closeBtn = createCompactButton(
            text = context.getString(R.string.close_button),
            bgColor = 0xFF222222.toInt(),
            onClick = { remove() }
        )

        buttonRow.addView(copyBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, dpToPx(8), 0)
        })
        buttonRow.addView(closeBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        container.addView(buttonRow)

        // ── Root (fullscreen, transparent) mit Touch-Listener für Außentipp ──
        val parent = FrameLayout(context).apply {
            id = View.generateViewId()
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            // Touch-Handling: Tipp außerhalb der Karte schließt das Overlay.
            // Der Listener vergleicht die Touch-Koordinaten mit den Bildschirmkoordinaten
            // der Karte. Ein Tipp auf transparentem Bereich (außerhalb der Karte) ruft
            // remove() auf.
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    val touchX = event.rawX.toInt()
                    val touchY = event.rawY.toInt()
                    val cardLoc = IntArray(2)
                    try {
                        container.getLocationOnScreen(cardLoc)
                    } catch (e: Exception) {
                        return@setOnTouchListener false
                    }
                    val insideCard = touchX in cardLoc[0]..(cardLoc[0] + container.width) &&
                        touchY in cardLoc[1]..(cardLoc[1] + container.height)
                    if (!insideCard) {
                        remove()
                        return@setOnTouchListener true
                    }
                }
                false // Touch innerhalb der Karte nicht konsumieren
            }
        }

        // Karte im Root positionieren
        val cardLp = FrameLayout.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = posX.coerceIn(0, (getScreenWidth() - cardWidth).coerceAtLeast(0))
            topMargin = posY.coerceIn(0, (getScreenHeight() - dpToPx(200)).coerceAtLeast(0))
        }
        parent.addView(container, cardLp)
        containerParams = cardLp
        overlayView = parent

        // ── WindowManager-Parameter (fullscreen, transparent) ──
        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        try {
            windowManager.addView(overlayView, overlayParams)
            isAttached = true
            Log.d(OVERLAY_TAG, "Translation overlay add success")
        } catch (e: Exception) {
            Log.w(OVERLAY_TAG, "Translation overlay add failed: ${e.message}")
            cleanup()
        }
    }

    /**
     * Erzeugt einen kompakten Button (ca. 1/3 Standardhöhe, halbe Breite).
     */
    private fun createCompactButton(
        text: String,
        bgColor: Int,
        onClick: () -> Unit
    ): Button = Button(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        setTextColor(0xFFB8B8B8.toInt())
        setBackgroundColor(0xFF222222.toInt())
        minHeight = 0
        minimumHeight = 0
        setPadding(dpToPx(14), dpToPx(3), dpToPx(14), dpToPx(4))
        includeFontPadding = false
        setOnClickListener { onClick() }
    }

    fun remove() {
        cleanup()
        val view = overlayView
        if (view != null) {
            try {
                windowManager.removeView(view)
                Log.d(OVERLAY_TAG, "Translation overlay remove success")
            } catch (e: Exception) {
                Log.w(OVERLAY_TAG, "Translation overlay remove ignored: ${e.message}")
            }
        }
        overlayView = null
        overlayParams = null
        containerParams = null
        isAttached = false
    }

    private fun cleanup() {
        systemUiCallback?.let { SelectionAccessibilityService.removeSystemUiWindowCallback(it) }
        systemUiCallback = null
    }

    fun destroy() {
        remove()
        scopeJob.cancel()
    }

    fun isShowing(): Boolean = isAttached

    private fun handleDrag(event: MotionEvent): Boolean {
        val cardParams = containerParams ?: return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialCardLeft = cardParams.leftMargin
                initialCardTop = cardParams.topMargin
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = Math.abs(event.rawX - initialTouchX)
                val dy = Math.abs(event.rawY - initialTouchY)
                if (dx > 10f || dy > 10f) isDragging = true
                if (isDragging) {
                    val newLeft = (initialCardLeft + (event.rawX - initialTouchX).toInt())
                        .coerceIn(0, (getScreenWidth() - cardParams.width).coerceAtLeast(0))
                    val newTop = (initialCardTop + (event.rawY - initialTouchY).toInt())
                        .coerceIn(0, (getScreenHeight() - cardParams.height).coerceAtLeast(0))
                    cardParams.leftMargin = newLeft
                    cardParams.topMargin = newTop
                    // Layout im Parent FrameLayout aktualisieren
                    (overlayView as? ViewGroup)?.requestLayout()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    scope.launch {
                        settingsRepository.saveOverlayPosition(
                            cardParams.leftMargin,
                            cardParams.topMargin
                        )
                    }
                }
                return isDragging
            }
        }
        return false
    }

    private fun getScreenWidth(): Int =
        context.resources.displayMetrics.widthPixels

    private fun getScreenHeight(): Int =
        context.resources.displayMetrics.heightPixels

    private fun dpToPx(dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()
}
