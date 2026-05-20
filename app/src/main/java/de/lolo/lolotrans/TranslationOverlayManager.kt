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

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

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
        // ── Root ──
        val parent = FrameLayout(context).apply { id = View.generateViewId() }

        // ── Haupt-Container ──
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
        parent.addView(container)
        overlayView = parent

        // ── WindowManager-Parameter ──
        overlayParams = WindowManager.LayoutParams(
            dpToPx(320),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = posX
            y = posY
            width = dpToPx(320)
        }

        // Outside-Touch → Overlay schließen
        parent.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                Log.d("Overlay", "Outside touch → schließen")
                remove()
                true
            } else false
        }

        windowManager.addView(overlayView, overlayParams)
        isAttached = true
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
        if (isAttached && overlayView != null) {
            try { windowManager.removeView(overlayView) } catch (_: Exception) {}
            overlayView = null
            overlayParams = null
            isAttached = false
        }
    }

    fun destroy() {
        remove()
        scopeJob.cancel()
    }

    fun isShowing(): Boolean = isAttached

    private fun handleDrag(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = overlayParams?.x ?: 0
                initialY = overlayParams?.y ?: 0
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
                    overlayParams?.apply {
                        x = initialX + (event.rawX - initialTouchX).toInt()
                        y = initialY + (event.rawY - initialTouchY).toInt()
                    }
                    overlayView?.let { windowManager.updateViewLayout(it, overlayParams) }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    scope.launch {
                        settingsRepository.saveOverlayPosition(
                            overlayParams?.x ?: initialX,
                            overlayParams?.y ?: initialY
                        )
                    }
                }
                return isDragging
            }
        }
        return false
    }

    private fun dpToPx(dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()
}
