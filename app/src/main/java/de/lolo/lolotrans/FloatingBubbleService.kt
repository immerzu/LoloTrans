package de.lolo.lolotrans

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "FloatingBubble"

class FloatingBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var translationManager: TranslationManager
    private lateinit var overlayManager: TranslationOverlayManager

    // PNG-Alpha-Bounding-Box (einmalig analysiert, nie verändert)
    private var pngAlphaBounds: PngAlphaBounds? = null

    // Bubble
    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var bubbleIcon: ImageView? = null
    private var isBubbleAttached = false
    private var currentBubbleSize = BubbleSize.M
    private var isStopping = false

    // Trash
    private var trashView: View? = null
    private var trashParams: WindowManager.LayoutParams? = null
    private var isTrashAttached = false
    private var trashHighlighted = false
    private val trashBgNormal = 0x99000000.toInt()     // halbtransparent schwarz
    private val trashBgHighlight = 0xCCD32F2F.toInt()  // rötlich hervorgehoben

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Drag / Tap
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var isDragging = false
    private var touchSlop = 0

    companion object {
        const val ACTION_START = "de.lolo.lolotrans.action.START_BUBBLE"
        const val ACTION_STOP = "de.lolo.lolotrans.action.STOP_BUBBLE"
        const val EXTRA_SHOW_NOTIFICATION = "show_notification"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "translation_bubble_channel"
        private const val TRASH_SIZE_DP = 140
        private const val TRASH_HIT_TOLERANCE_DP = 10   // präzise, nur sanfte Hilfe
        private const val TRASH_HOVER_TOLERANCE_DP = 16 // optische Hervorhebung etwas früher
    }

    // ──────────────────────────────────────────────
    //  Lifecycle
    // ──────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        BubbleRuntimeState.setVisible(false)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        settingsRepository = SettingsRepository(this)
        translationManager = TranslationManager()
        overlayManager = TranslationOverlayManager(this, windowManager, settingsRepository)
        touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        Log.d(TAG, "touchSlop=$touchSlop (System)")
        pngAlphaBounds = analyzePngAlphaBounds(R.drawable.overlay_button_sw)
        createNotificationChannel()
        startBubbleSizeObserver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_STOP -> {
                Log.d(TAG, "Stop requested startId=$startId")
                requestStop(startId)
                return START_NOT_STICKY
            }
            ACTION_START -> Log.d(TAG, "Start requested startId=$startId")
            else -> Log.d(TAG, "Start requested with unknown action=${intent?.action}")
        }

        isStopping = false
        val showNotification = intent?.getBooleanExtra(EXTRA_SHOW_NOTIFICATION, false) ?: false
        if (showNotification) {
            Log.d(TAG, "startForeground: notificationId=$NOTIFICATION_ID")
            startForeground(NOTIFICATION_ID, buildNotification())
        } else {
            Log.d(TAG, "startService: keine Foreground-Notification (Standard)")
        }
        showBubble()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: cleanup started")
        cleanupOverlays()
        overlayManager.destroy()
        translationManager.close()
        serviceScope.cancel()
        persistBubbleDisabledAsync()
        Log.d(TAG, "Bubble service destroyed")
        super.onDestroy()
    }

    private fun requestStop(startId: Int? = null) {
        if (isStopping) {
            Log.d(TAG, "Stop ignored: cleanup already running")
            if (startId != null) stopSelfResult(startId) else stopSelf()
            return
        }
        isStopping = true
        cleanupOverlays()
        persistBubbleDisabledAsync()
        val stopRequested = if (startId != null) stopSelfResult(startId) else run {
            stopSelf()
            true
        }
        Log.d(TAG, "Stop completed requestedStopSelf=$stopRequested")
    }

    private fun cleanupOverlays() {
        stopForegroundSafely()
        removeBubble()
        hideTrashOverlay()
        overlayManager.remove()
    }

    private fun stopForegroundSafely() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.w(TAG, "stopForeground ignored: ${e.message}")
        }
    }

    private fun persistBubbleDisabledAsync() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                settingsRepository.setBubbleEnabled(false)
                Log.d(TAG, "bubbleEnabled=false gespeichert")
            } catch (e: Exception) {
                Log.w(TAG, "bubbleEnabled=false konnte nicht gespeichert werden: ${e.message}")
            }
        }
    }

    private fun persistBubbleEnabledAsync() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                settingsRepository.setBubbleEnabled(true)
                Log.d(TAG, "bubbleEnabled=true gespeichert")
            } catch (e: Exception) {
                Log.w(TAG, "bubbleEnabled=true konnte nicht gespeichert werden: ${e.message}")
            }
        }
    }

    // ──────────────────────────────────────────────
    //  Live-Größenbeobachtung
    // ──────────────────────────────────────────────

    private fun startBubbleSizeObserver() {
        serviceScope.launch {
            settingsRepository.bubbleSize.collectLatest { newSize ->
                Log.d(TAG, "BubbleSize aus DataStore: ${newSize.name} (touch=${newSize.touchDp} visual=${newSize.visualDp})")
                if (newSize == currentBubbleSize) return@collectLatest
                currentBubbleSize = newSize
                if (isBubbleAttached) {
                    applyBubbleSize(newSize)
                }
            }
        }
    }

    private fun applyBubbleSize(size: BubbleSize) {
        val touchPx = dpToPx(size.touchDp)
        val visualPx = dpToPx(size.visualDp)
        val iconPx = dpToPx(size.iconDp)
        Log.d(TAG, "applyBubbleSize: ${size.name} touch=${touchPx}px visual=${visualPx}px icon=${iconPx}px")

        // WindowManager-Größe = Touchfläche
        bubbleParams?.width = touchPx
        bubbleParams?.height = touchPx
        bubbleView?.let { view ->
            try {
                windowManager.updateViewLayout(view, bubbleParams)
            } catch (e: Exception) {
                Log.e(TAG, "updateViewLayout fehlgeschlagen", e)
            }
        }

        // Sichtbarer Button (PNG) zentriert in Touchfläche
        bubbleIcon?.layoutParams = FrameLayout.LayoutParams(visualPx, visualPx).apply {
            gravity = Gravity.CENTER
        }

        // Nach Größenwechsel Position neu begrenzen
        clampBubblePosition()
        Log.d(TAG, "Size changed, reclamped position x=${bubbleParams?.x}")
    }

    // ──────────────────────────────────────────────
    //  Bubble anzeigen / entfernen
    // ──────────────────────────────────────────────

    private fun showBubble() {
        if (isStopping) {
            Log.d(TAG, "Start ignored: stop in progress")
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Start failed: overlay permission missing")
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show()
            persistBubbleDisabledAsync()
            requestStop()
            return
        }
        if (isBubbleAttached) {
            Log.d(TAG, "Start ignored: bubble already attached")
            BubbleRuntimeState.setVisible(true)
            persistBubbleEnabledAsync()
            return
        }

        serviceScope.launch {
            try {
                val size = settingsRepository.bubbleSize.first()
                val posX = settingsRepository.getBubblePosX()
                val posY = settingsRepository.getBubblePosY()
                if (!isStopping) createBubbleView(size, posX, posY)
            } catch (e: Exception) {
                Log.w(TAG, "Start settings load failed, using fallback: ${e.message}")
                if (!isStopping) createBubbleView(BubbleSize.M, 100, 200)
            }
        }
    }

    private fun createBubbleView(size: BubbleSize, posX: Int, posY: Int) {
        if (isStopping) {
            Log.d(TAG, "Bubble add skipped: stop in progress")
            return
        }
        if (isBubbleAttached) return
        currentBubbleSize = size
        val touchPx = dpToPx(size.touchDp)
        val visualPx = dpToPx(size.visualDp)
        val iconPx = dpToPx(size.iconDp)

        // Root-Layout = Touchfläche (vollständig unsichtbar, erlaubt Icon-Überlappung)
        val root = FrameLayout(this).apply {
            id = View.generateViewId()
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            clipChildren = false
            clipToPadding = false
        }

        // Overlay_Button_SW.png als Bubble-Symbol
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.overlay_button_sw)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        root.addView(icon, FrameLayout.LayoutParams(visualPx, visualPx).apply {
            gravity = Gravity.CENTER
        })
        bubbleIcon = icon

        bubbleView = root
        // OnClickListener für korrekte Tap-Erkennung + Accessibility
        bubbleView!!.setOnClickListener {
            Log.d(TAG, "Bubble OnClick ausgelöst → handleBubbleClick")
            handleBubbleClick()
        }
        bubbleView!!.setOnTouchListener { v, event -> handleBubbleTouch(v, event) }

        bubbleParams = WindowManager.LayoutParams(
            touchPx,
            touchPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = posX
            y = posY
        }

        clampBubblePosition()
        Log.d(TAG, "Bubble start position: loaded=($posX,$posY) clamped=(${bubbleParams?.x},${bubbleParams?.y})")
        try {
            windowManager.addView(bubbleView, bubbleParams)
            isBubbleAttached = true
            BubbleRuntimeState.setVisible(true)
            persistBubbleEnabledAsync()
            Log.d(TAG, "Bubble add success")
        } catch (e: Exception) {
            Log.e(TAG, "Bubble add failed", e)
            bubbleView = null
            bubbleParams = null
            bubbleIcon = null
            isBubbleAttached = false
            BubbleRuntimeState.setVisible(false)
            persistBubbleDisabledAsync()
            requestStop()
        }
    }

    private fun removeBubble() {
        val view = bubbleView
        if (view != null) {
            try {
                windowManager.removeView(view)
                Log.d(TAG, "Bubble remove success")
            } catch (e: Exception) {
                Log.w(TAG, "Bubble remove ignored: ${e.message}")
            }
        }
        bubbleView = null
        bubbleParams = null
        bubbleIcon = null
        isBubbleAttached = false
        BubbleRuntimeState.setVisible(false)
    }

    // ──────────────────────────────────────────────
    //  Touch / Drag / Trash
    // ──────────────────────────────────────────────

    private fun handleBubbleTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                Log.d(TAG, "ACTION_DOWN raw=(${event.rawX},${event.rawY})")
                initialX = bubbleParams?.x ?: 0
                initialY = bubbleParams?.y ?: 0
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                lastRawX = event.rawX
                lastRawY = event.rawY
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                val distance = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                lastRawX = event.rawX
                lastRawY = event.rawY

                if (!isDragging && distance > touchSlop) {
                    isDragging = true
                    Log.d(TAG, "Drag gestartet distance=$distance touchSlop=$touchSlop")
                    showTrashOverlay()
                }
                if (isDragging) {
                    val rawX = initialX + dx.toInt()
                    val rawY = initialY + dy.toInt()
                    bubbleParams?.x = rawX
                    bubbleParams?.y = rawY
                    clampBubblePosition()
                    bubbleView?.let { safeUpdateBubbleLayout(it) }

                    // Hover-Hervorhebung: etwas großzügiger für optisches Feedback
                    val hover = isBubbleCenterOverTrash(TRASH_HOVER_TOLERANCE_DP)
                    if (hover != trashHighlighted) {
                        trashHighlighted = hover
                        updateTrashAppearance(hover)
                        Log.d(TAG, "Trash hover changed: $hover")
                    }
                }
                return true
            }

            // ACTION_UP und ACTION_CANCEL gleich behandeln
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                val distance = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                val isTap = !isDragging && distance <= touchSlop * 1.5f
                Log.d(TAG, "ACTION_UP distance=$distance isDragging=$isDragging isTap=$isTap")

                // ── Tap ──
                if (isTap) {
                    hideTrashOverlay()
                    isDragging = false
                    Toast.makeText(this, R.string.translating_clipboard, Toast.LENGTH_SHORT).show()
                    v.performClick()
                    return true
                }

                // ── Drag ──
                if (isDragging) {
                    // Präziser Hit-Test VOR hideTrashOverlay(), nur Bubble-Mittelpunkt
                    val overTrash = isBubbleCenterOverTrash(TRASH_HIT_TOLERANCE_DP)
                    Log.d(TAG, "ACTION_UP final preciseOverTrash=$overTrash")

                    if (overTrash) {
                        Log.d(TAG, "Drop im Trash → stopSelf()")
                        hideTrashOverlay()
                        isDragging = false
                        requestStop()
                        return true
                    }

                    Log.d(TAG, "Drop außerhalb Trash → Bubble bleibt sichtbar")
                    hideTrashOverlay()

                    // Edge-Snap links: params.x → 0, Icon per translationX an den Rand
                    if (bubbleParams != null) {
                        val snapThreshold = dpToPx(24)
                        if (bubbleParams!!.x < snapThreshold) {
                            bubbleParams!!.x = 0
                            bubbleView?.let { safeUpdateBubbleLayout(it) }
                            applyLeftEdgeOffsetIfDocked()
                            Log.d(TAG, "EdgeSnap: paramsX snapped to 0, translationX set")
                        }
                    }

                    serviceScope.launch {
                        settingsRepository.saveBubblePosition(
                            bubbleParams?.x ?: initialX,
                            bubbleParams?.y ?: initialY
                        )
                    }
                    isDragging = false
                    return true
                }

                // Weder Tap noch Drag
                hideTrashOverlay()
                isDragging = false
                Log.d(TAG, "ACTION_UP: weder Tap noch Drag → ignoriert")
                return true
            }
        }
        return false
    }

    // ──────────────────────────────────────────────
    //  Trash-Overlay
    // ──────────────────────────────────────────────

    private fun showTrashOverlay() {
        if (isTrashAttached) {
            updateTrashAppearance(false)
            return
        }

        val sizePx = dpToPx(TRASH_SIZE_DP)
        val container = FrameLayout(this).apply { id = View.generateViewId() }

        // Halbtransparenter runder Hintergrund
        val bg = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(24).toFloat()
                setColor(trashBgNormal)
            }
        }
        container.addView(bg, FrameLayout.LayoutParams(sizePx, sizePx))

        // Inhalt: Icon + Text
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        // Mülleimer-Symbol (Unicode)
        val iconTv = TextView(this).apply {
            text = "🗑️"
            textSize = 28f
            gravity = Gravity.CENTER
        }
        inner.addView(iconTv)
        val hintTv = TextView(this).apply {
            text = getString(R.string.trash_hint)
            textSize = 11f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        inner.addView(hintTv)

        container.addView(inner, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER })

        trashView = container
        trashHighlighted = false

        // Auf Bildschirmgröße reagieren
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        trashParams = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            x = 0
            y = dpToPx(40) // Abstand vom unteren Rand
        }

        try {
            windowManager.addView(trashView, trashParams)
            isTrashAttached = true
            Log.d(TAG, "Trash add success")
        } catch (e: Exception) {
            Log.w(TAG, "Trash add failed: ${e.message}")
            trashView = null
            trashParams = null
            isTrashAttached = false
            trashHighlighted = false
        }
    }

    private fun updateTrashAppearance(highlighted: Boolean) {
        val bg = (trashView as? ViewGroup)?.getChildAt(0) ?: return
        val drawable = bg.background as? GradientDrawable ?: return
        drawable.setColor(if (highlighted) trashBgHighlight else trashBgNormal)
    }

    private fun hideTrashOverlay() {
        val view = trashView
        if (view != null) {
            try {
                windowManager.removeView(view)
                Log.d(TAG, "Trash remove success")
            } catch (e: Exception) {
                Log.w(TAG, "Trash remove ignored: ${e.message}")
            }
        }
        trashView = null
        trashParams = null
        isTrashAttached = false
        trashHighlighted = false
    }

    private fun safeUpdateBubbleLayout(view: View) {
        try {
            windowManager.updateViewLayout(view, bubbleParams)
        } catch (e: Exception) {
            Log.w(TAG, "Bubble update failed, stopping service: ${e.message}")
            requestStop()
        }
    }

    /**
     * Präziser Hit-Test: Nur Bubble-Mittelpunkt gegen Trash-Bounds.
     * @param toleranceDp zusätzlicher Spielraum in dp (10dp final, 16dp Hover)
     */
    private fun isBubbleCenterOverTrash(toleranceDp: Int): Boolean {
        val bubble = bubbleView ?: return false
        val trash = trashView ?: return false
        if (!isTrashAttached || !isBubbleAttached) return false

        val bubbleLoc = IntArray(2)
        val trashLoc = IntArray(2)
        bubble.getLocationOnScreen(bubbleLoc)
        trash.getLocationOnScreen(trashLoc)

        val bubbleCenterX = bubbleLoc[0] + bubble.width / 2
        val bubbleCenterY = bubbleLoc[1] + bubble.height / 2

        val tolerance = dpToPx(toleranceDp)
        val hitLeft = trashLoc[0] - tolerance
        val hitTop = trashLoc[1] - tolerance
        val hitRight = trashLoc[0] + trash.width + tolerance
        val hitBottom = trashLoc[1] + trash.height + tolerance

        val result = bubbleCenterX in hitLeft..hitRight &&
                bubbleCenterY in hitTop..hitBottom

        Log.d(TAG, "HitTest precise: bubbleCenter=($bubbleCenterX,$bubbleCenterY) " +
                "trash=($hitLeft,$hitTop,$hitRight,$hitBottom) tolerance=${tolerance}px result=$result")
        return result
    }

    // ──────────────────────────────────────────────
    //  Übersetzung
    // ──────────────────────────────────────────────

    /**
     * Startet die transparente ClipboardTranslateActivity, die im Vordergrund
     * zuverlässig die Zwischenablage liest und die Übersetzung durchführt.
     * Der direkte Clipboard-Zugriff aus dem Service-Kontext ist ab Android 12+
     * eingeschränkt und funktioniert nicht zuverlässig.
     */
    private fun handleBubbleClick() {
        Log.d(TAG, "handleBubbleClick → starte ClipboardTranslateActivity")

        // 1. Synchron aus dem aktuellen Accessibility-Baum lesen
        var selectedText = SelectionAccessibilityService.getCurrentSelectedText()

        // 2. Fallback: aus dem letzten Event-Cache (mit Package-Prüfung)
        if (selectedText.isNullOrBlank()) {
            val currentPkg = SelectionAccessibilityService.getCurrentPackage()
            selectedText = SelectionAccessibilityService.getRecentSelectedText(currentPackage = currentPkg)
        }

        // 3. Clipboard-Inhalt VOR ACTION_COPY sichern (für spätere Vergleichslogik)
        val clipboardBefore = try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
        } catch (e: Exception) {
            null
        }

        // 4. Cache als verbraucht markieren
        if (!selectedText.isNullOrBlank()) {
            SelectionAccessibilityService.consumeSelection()
        }

        // 5. Clipboard per ACTION_COPY als weiteren Fallback
        val copiedSelection = SelectionAccessibilityService.copyCurrentSelectionToClipboard()

        val intent = Intent(this, ClipboardTranslateActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            putExtra(ClipboardTranslateActivity.EXTRA_SELECTION_COPY_REQUESTED, copiedSelection)
            putExtra(ClipboardTranslateActivity.EXTRA_CLIPBOARD_BEFORE_COPY, clipboardBefore)
            if (!selectedText.isNullOrBlank()) {
                putExtra(ClipboardTranslateActivity.EXTRA_TEXT_TO_TRANSLATE, selectedText)
            }
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Starten der ClipboardTranslateActivity", e)
            Toast.makeText(this, R.string.translation_error, Toast.LENGTH_SHORT).show()
        }
    }

    // ──────────────────────────────────────────────
    //  Notification
    // ──────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = getString(R.string.notification_channel_desc) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val largeIcon = BitmapFactory.decodeResource(resources, R.drawable.overlay_button_sw)
        Log.d(TAG, "buildNotification: smallIcon=R.drawable.ic_notification_lolotrans channel=$CHANNEL_ID notificationId=$NOTIFICATION_ID largeIcon=${largeIcon.width}x${largeIcon.height}")
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_lolotrans)
            .setLargeIcon(largeIcon)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.bubble_active))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(
                PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
            .build()
    }

    // ── PNG-Analyse (nur lesen, nie verändern) ──

    private data class PngAlphaBounds(
        val imgW: Int, val imgH: Int,
        val visLeft: Int, val visTop: Int, val visRight: Int, val visBottom: Int
    )

    private fun analyzePngAlphaBounds(resId: Int): PngAlphaBounds {
        val bmp = BitmapFactory.decodeResource(resources, resId)
        val w = bmp.width; val h = bmp.height
        var l = w; var t = h; var r = 0; var b = 0
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val alpha = (pixels[y * w + x] shr 24) and 0xFF
                if (alpha > 10) {
                    if (x < l) l = x
                    if (y < t) t = y
                    if (x > r) r = x
                    if (y > b) b = y
                }
            }
        }
        bmp.recycle()
        val result = PngAlphaBounds(w, h, l, t, r, b)
        Log.d(TAG, "PNG bounds: ${w}x${h} visible=($l,$t)-($r,$b) paddingLeft=$l paddingRight=${w-1-r}")
        return result
    }

    /**
     * Berechnet, wie viele Pixel das sichtbare Symbol vom ImageView-Rand entfernt ist.
     */
    private fun visibleInsetPx(iconPx: Int, side: Int, imgDimension: Int): Int =
        if (imgDimension > 0) (side * iconPx / imgDimension) else 0

    /**
     * Standard-Clamp: Fenster darf nicht negativ, aber bis zum rechten Rand.
     */
    private fun clampBubblePosition() {
        val params = bubbleParams ?: return
        val touchPx = params.width
        if (touchPx <= 0) return
        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels

        params.x = params.x.coerceIn(0, (screenW - touchPx).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (screenH - touchPx).coerceAtLeast(0))

        // Linker Rand erreicht → Icon nach links versetzen (translationX)
        applyLeftEdgeOffsetIfDocked()
    }

    /**
     * Wenn Bubble am linken Rand steht, verschiebt das sichtbare Icon per translationX
     * weiter nach links, damit der sichtbare Button (nicht nur die ImageView) am Rand steht.
     * translationX wird NUR gesetzt, wenn params.x ≈ 0, sonst auf 0 zurückgesetzt.
     */
    private fun applyLeftEdgeOffsetIfDocked() {
        val icon = bubbleIcon ?: return
        val params = bubbleParams ?: return
        val alpha = pngAlphaBounds ?: return
        val iconPx = (icon.layoutParams as? FrameLayout.LayoutParams)?.width ?: return
        if (iconPx <= 0) return

        val pngPadLeft = visibleInsetPx(iconPx, alpha.visLeft, alpha.imgW)
        val isAtLeftEdge = params.x <= dpToPx(4)  // nahe am linken Rand

        val targetTx = if (isAtLeftEdge) -pngPadLeft.toFloat() else 0f
        if (icon.translationX != targetTx) {
            icon.translationX = targetTx
            val iconLoc = IntArray(2)
            icon.getLocationOnScreen(iconLoc)
            Log.d(TAG, "LeftDock: paramsX=${params.x} isDocked=$isAtLeftEdge " +
                "translationX=$targetTx pngPadLeft=$pngPadLeft " +
                "iconLeft=${iconLoc[0]} visibleContentLeft=${iconLoc[0] + pngPadLeft}")
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
