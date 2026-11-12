package de.lolo.lolotrans

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val SELECTION_TAG = "SelectionAccessibility"

data class AccessibilityOverlayHost(
    val context: Context,
    val windowManager: WindowManager
)

class SelectionAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pendingSelectionJob: Job? = null

    companion object {
        private const val MAX_SELECTED_TEXT_LENGTH = 10_000
        private const val MAX_NODES_TO_SCAN = 250
        private const val MAX_PARENT_LEVELS = 12
        private const val PENDING_SELECTION_DELAY_MS = 200L
        private const val CLIPBOARD_WAIT_STEP_MS = 40L
        private const val CLIPBOARD_WAIT_TRIES = 4
        private const val DEFAULT_MAX_AGE_MS = 30_000L
        private const val CONSUMED_TTL_MS = 60_000L
        private val lock = Any()

        @Volatile
        private var connected = false
        private var activeService: SelectionAccessibilityService? = null
        private var lastSelectedText: String? = null
        private var lastSelectedAtMs: Long = 0L
        private var lastSelectedPackage: String? = null
        private var lastSelectionConsumed: Boolean = false
        private val systemUiCallbacks = CopyOnWriteArraySet<() -> Unit>()

        /** Text, der nach erfolgreicher Übersetzung nicht erneut aus Clipboard verwendet werden darf. */
        @Volatile
        private var consumedText: String? = null
        private var consumedTextAtMs: Long = 0L

        fun isConnected(): Boolean = connected

        fun getOverlayHost(): AccessibilityOverlayHost? {
            if (!connected) return null
            val service = activeService ?: return null
            val manager = service.getSystemService(WINDOW_SERVICE) as? WindowManager ?: return null
            return AccessibilityOverlayHost(service, manager)
        }

        /**
         * Liest markierten Text ausschliesslich aus dem Accessibility-Baum.
         * Anders als [getCurrentSelectedText] wird die Zwischenablage NICHT angefasst –
         * wichtig beim Bubble-Tipp, damit ein bereits kopierter Text erhalten bleibt.
         */
        fun getTreeSelectedText(): String? {
            val service = activeService ?: return null
            val root = try { service.rootInActiveWindow } catch (e: Exception) { null } ?: return null
            val focus = try {
                root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            } catch (e: Exception) {
                null
            }
            service.collectSelectedText(focus)?.takeIf { it.isNotBlank() }?.let { return it }
            return service.collectSelectedText(root)?.takeIf { it.isNotBlank() }
        }

        fun getCurrentSelectedText(): String? {
            val service = activeService
            if (service == null) {
                Log.w(SELECTION_TAG, "getCurrentSelectedText: activeService is NULL")
                return null
            }
            val root = service.rootInActiveWindow
            val rootPkg = try { root?.packageName?.toString() } catch (e: Exception) { "error" }

            // 1. Fokussiertes Feld: dort steht die echte Auswahl.
            val focus = try { root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) } catch (e: Exception) { null }
            val fromFocus = service.collectSelectedText(focus)
            if (!fromFocus.isNullOrBlank()) {
                Log.d(SELECTION_TAG, "getCurrentSelectedText: OK (focus) package=$rootPkg length=${fromFocus.length}")
                return fromFocus
            }

            // 2. Sonst der restliche Baum.
            val fromRoot = service.collectSelectedText(root)
            if (!fromRoot.isNullOrBlank()) {
                Log.d(SELECTION_TAG, "getCurrentSelectedText: OK (root) package=$rootPkg length=${fromRoot.length}")
                return fromRoot
            }

            // 3. Letzter Ausweg: Auswahl selbst kopieren lassen – liefert exakt den markierten Text.
            val copied = service.copySelectionToClipboard()
            if (!copied.isNullOrBlank()) {
                Log.d(SELECTION_TAG, "getCurrentSelectedText: OK (copy) package=$rootPkg length=${copied.length}")
                return copied
            }

            Log.w(SELECTION_TAG, "getCurrentSelectedText: NULL (package=$rootPkg, no selected text found)")
            return null
        }

        /**
         * Liefert den gecachten markierten Text, wenn:
         * - Text vorhanden ist
         * - nicht bereits per [consumeSelection] verbraucht
         * - innerhalb der TTL (default 30s)
         * - [currentPackage] stimmt mit dem Paket der Selection überein (oder einer von beiden ist null)
         */
        fun getRecentSelectedText(maxAgeMs: Long = DEFAULT_MAX_AGE_MS, currentPackage: String? = null): String? {
            synchronized(lock) {
                val text = lastSelectedText?.trim().orEmpty()
                val ageMs = System.currentTimeMillis() - lastSelectedAtMs
                val hasText = text.isNotBlank()
                val withinAge = ageMs <= maxAgeMs
                val notConsumed = !lastSelectionConsumed
                val pkgOk = currentPackage == null || lastSelectedPackage == null ||
                    currentPackage == lastSelectedPackage

                Log.d(SELECTION_TAG, "getRecentSelectedText: " +
                    "hasText=$hasText age=${ageMs}ms TTL=$maxAgeMs " +
                    "withinAge=$withinAge consumed=$lastSelectionConsumed " +
                    "pkg(${currentPackage ?: "?"})==cached(${lastSelectedPackage ?: "?"}) => $pkgOk")

                if (!hasText) {
                    Log.w(SELECTION_TAG, "getRecentSelectedText: kein gecachter Text")
                    return null
                }
                if (!withinAge) {
                    Log.w(SELECTION_TAG, "getRecentSelectedText: Text zu alt (${ageMs}ms > ${maxAgeMs}ms)")
                    return null
                }
                if (lastSelectionConsumed) {
                    Log.d(SELECTION_TAG, "getRecentSelectedText: bereits verbraucht → null")
                    return null
                }
                if (!pkgOk) {
                    Log.w(SELECTION_TAG, "getRecentSelectedText: Paket mismatch " +
                        "(current=$currentPackage, cached=$lastSelectedPackage)")
                    return null
                }
                Log.d(SELECTION_TAG, "getRecentSelectedText: OK length=${text.length}")
                return text
            }
        }

        /** Liefert das Package des aktuell aktiven Fensters. */
        fun getCurrentPackage(): String? {
            val service = activeService ?: return null
            val root = service.rootInActiveWindow ?: return null
            return try { root.packageName?.toString() } catch (e: Exception) { null }
        }

        /**
         * Liefert den Inhalt der Zwischenablage, wenn er NEU ist (noch nicht uebersetzt).
         * Ein bereits uebersetzter Text gilt als alt – damit eine neue Markierung nicht vom
         * alten kopierten Text ueberstimmt wird.
         */
        fun getFreshClipboardText(): String? {
            val service = activeService ?: return null
            val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return null
            val text = service.readClipboard(clipboard)?.trim()?.takeIf { it.isNotBlank() } ?: return null
            return if (isTextConsumed(text)) {
                Log.d(SELECTION_TAG, "Zwischenablage ist alt (bereits uebersetzt) → ignorieren")
                null
            } else {
                text
            }
        }

        /** Markiert den aktuellen Cache als verbraucht (nach Bubble-Tap). */
        fun consumeSelection() {
            synchronized(lock) {
                lastSelectionConsumed = true
                Log.d(SELECTION_TAG, "consumeSelection: cache consumed")
            }
        }

        /**
         * Liest den markierten Text aus einem Bildschirmfoto. Gedacht fuer Apps (vor allem
         * WebViews), die ihre Auswahl nicht an Accessibility weitergeben.
         * Liefert null, wenn keine Markierung erkennbar ist oder der Flavor keine
         * Texterkennung mitbringt.
         */
        suspend fun getMarkedTextFromScreen(): String? {
            val service = activeService ?: return null
            if (!ScreenTextScanner.isAvailable) {
                Log.w(SELECTION_TAG, "Bildschirm-Scan: Texterkennung in diesem Flavor nicht verfuegbar")
                return null
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                Log.w(SELECTION_TAG, "Bildschirm-Scan: benoetigt Android 11+")
                return null
            }
            val bitmap = withContext(Dispatchers.IO) { service.grabScreenBitmap() }
            if (bitmap == null) {
                Log.w(SELECTION_TAG, "Bildschirm-Scan: kein Bildschirmfoto")
                return null
            }
            return try {
                ScreenTextScanner.recognizeMarkedText(service, bitmap)
            } catch (e: Exception) {
                Log.w(SELECTION_TAG, "Bildschirm-Scan fehlgeschlagen: ${e.message}")
                null
            } finally {
                bitmap.recycle()
            }
        }

        fun clearConsumedSelectionAfterDelay(delayMs: Long = 2_500L) {
            val service = activeService ?: return
            val selectedAtSnapshot: Long
            val selectedTextSnapshot: String?
            synchronized(lock) {
                if (!lastSelectionConsumed || lastSelectedText.isNullOrBlank()) return
                selectedAtSnapshot = lastSelectedAtMs
                selectedTextSnapshot = lastSelectedText
            }
            service.serviceScope.launch {
                delay(delayMs)
                synchronized(lock) {
                    if (lastSelectionConsumed &&
                        lastSelectedAtMs == selectedAtSnapshot &&
                        lastSelectedText == selectedTextSnapshot
                    ) {
                        lastSelectedText = null
                        lastSelectedAtMs = 0L
                        lastSelectedPackage = null
                        lastSelectionConsumed = false
                        Log.d(SELECTION_TAG, "Selection cache cleared after successful translation")
                    }
                }
            }
        }

        /**
         * Prueft, ob [text] gerade eben verbraucht wurde (Clipboard-Wiederverwendung verhindern).
         * Die Sperre gilt nur [CONSUMED_TTL_MS] lang: danach darf derselbe Text bewusst erneut
         * uebersetzt werden. Ohne diese Frist blockierte ein einmal uebersetzter Text dauerhaft —
         * auch nach erneutem Kopieren.
         */
        fun isTextConsumed(text: String): Boolean {
            val ct = consumedText ?: return false
            if (text != ct) return false
            val age = System.currentTimeMillis() - consumedTextAtMs
            val consumed = age <= CONSUMED_TTL_MS
            Log.d(SELECTION_TAG, "isTextConsumed: age=${age}ms TTL=$CONSUMED_TTL_MS -> $consumed")
            return consumed
        }

        /** Markiert [text] als verbraucht, nachdem er erfolgreich übersetzt wurde. */
        fun setConsumedText(text: String?) {
            consumedText = text
            consumedTextAtMs = System.currentTimeMillis()
            Log.d(SELECTION_TAG, "setConsumedText: length=${text?.length ?: 0} consumed=${text != null}")
        }

        fun addSystemUiWindowCallback(callback: () -> Unit) {
            systemUiCallbacks.add(callback)
        }

        fun removeSystemUiWindowCallback(callback: () -> Unit) {
            systemUiCallbacks.remove(callback)
        }

        private fun notifySystemUiWindowChanged() {
            systemUiCallbacks.forEach { callback ->
                try {
                    callback()
                } catch (e: Exception) {
                    Log.w(SELECTION_TAG, "SystemUI callback failed: ${e.message}")
                }
            }
        }

        private fun storeSelectedText(text: String?, sourcePackage: String? = null) {
            val normalized = text?.trim()?.take(MAX_SELECTED_TEXT_LENGTH).orEmpty()
            synchronized(lock) {
                if (normalized.isBlank()) {
                    return
                }
                lastSelectedText = normalized
                lastSelectedAtMs = System.currentTimeMillis()
                lastSelectedPackage = sourcePackage
                lastSelectionConsumed = false
                consumedText = null // neue Auswahl → alter verbrauchter Clipboard-Text ist ungültig
                Log.d(SELECTION_TAG, "storeSelectedText: length=${normalized.length} pkg=$sourcePackage consumed=reset")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        activeService = this
        connected = false
        Log.d(SELECTION_TAG, "Accessibility service created")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = this
        connected = true
        persistAccessibilityApprovedOnce()
        Log.d(SELECTION_TAG, "Accessibility service connected")
        restoreBubbleForConnectedHost()
    }

    override fun onDestroy() {
        if (activeService === this) {
            activeService = null
        }
        connected = false
        BubbleRuntimeState.setVisible(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun restoreBubbleForConnectedHost() {
        serviceScope.launch {
            try {
                val shouldRestore = SettingsRepository(applicationContext).bubbleEnabled.first()
                if (!shouldRestore) return@launch
                startService(Intent(this@SelectionAccessibilityService, FloatingBubbleService::class.java).apply {
                    action = FloatingBubbleService.ACTION_REATTACH
                })
                Log.d(SELECTION_TAG, "Bubble reattach requested for connected accessibility host")
            } catch (e: Exception) {
                Log.w(SELECTION_TAG, "Bubble reattach request failed: ${e.message}")
            }
        }
    }

    private fun persistAccessibilityApprovedOnce() {
        serviceScope.launch {
            try {
                SettingsRepository(applicationContext).setAccessibilityApprovedOnce(true)
                Log.d(SELECTION_TAG, "accessibilityApprovedOnce=true gespeichert")
            } catch (e: Exception) {
                Log.w(SELECTION_TAG, "accessibilityApprovedOnce konnte nicht gespeichert werden: ${e.message}")
            }
        }
    }

    override fun onInterrupt() {
        Log.d(SELECTION_TAG, "Accessibility service interrupted")
    }

    /** Holt das Bildschirmfoto als Software-Bitmap (blockierend, nur aus IO-Kontext aufrufen). */
    private fun grabScreenBitmap(): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val latch = CountDownLatch(1)
        var result: Bitmap? = null
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        try {
                            val hardware = Bitmap.wrapHardwareBuffer(
                                screenshot.hardwareBuffer,
                                screenshot.colorSpace
                            )
                            result = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                        } catch (e: Exception) {
                            Log.w(SELECTION_TAG, "Screenshot kopieren fehlgeschlagen: ${e.message}")
                        } finally {
                            screenshot.hardwareBuffer.close()
                            latch.countDown()
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.w(SELECTION_TAG, "Bildschirmfoto fehlgeschlagen: code=$errorCode")
                        latch.countDown()
                    }
                }
            )
            latch.await(1500, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            Log.w(SELECTION_TAG, "Bildschirmfoto nicht moeglich: ${e.message}")
        }
        return result
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        try {
            handleAccessibilityEvent(event)
        } catch (e: Exception) {
            Log.e(SELECTION_TAG, "onAccessibilityEvent failed: ${e.message}", e)
        }
    }

    private fun handleAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString().orEmpty()
        val type = event.eventType
        val isSelection = type == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED
        Log.w(
            SELECTION_TAG,
            "onAccessibilityEvent type=$type pkg=$pkg isSelection=$isSelection " +
                "window=${event.windowId} class=${event.className ?: "?"} " +
                "from=${event.fromIndex} to=${event.toIndex} items=${event.text.size}"
        )
        if (notifySystemUiNavigationIfNeeded(event)) return
        if (!isSelection) return

        val fromSource = collectSelectedText(event.source)
        val fromRoot = collectSelectedText(rootInActiveWindow)
        val fromEvent = extractSelectedTextFromEvent(event)
        // Reihenfolge ist Absicht: Der Quellknoten des Ereignisses traegt die echte Auswahl.
        // Der Baum meldet oft zusaetzlich den ganzen Absatz – deshalb nicht "laengster gewinnt",
        // sonst landet zu viel Text im Uebersetzungsfenster.
        val selected = listOfNotNull(fromSource, fromRoot, fromEvent)
            .firstOrNull { it.isNotBlank() }

        Log.w(
            SELECTION_TAG,
            "Selection candidates source=${fromSource?.length ?: 0} " +
                "root=${fromRoot?.length ?: 0} event=${fromEvent?.length ?: 0} " +
                "chosen=${selected?.length ?: 0} " +
                "window=${event.windowId} class=${event.className ?: "?"} " +
                "from=${event.fromIndex} to=${event.toIndex} " +
                "itemLengths=${event.text.joinToString(",") { it?.length?.toString() ?: "0" }}"
        )

        if (selected != null) {
            Log.w(SELECTION_TAG, "onAccessibilityEvent: selectedText length=${selected.length} pkg=$pkg")
        } else {
            Log.w(SELECTION_TAG, "onAccessibilityEvent: no selection found pkg=$pkg")
        }
        scheduleStoreSelectedText(selected, pkg)
    }

    /**
     * Speichert die Auswahl erst nach kurzer Ruhe. Beim Ziehen mit dem Finger meldet Android
     * laufend Zwischenstaende (3, 8, 135 Zeichen); ohne diese Bremse blieb gelegentlich ein
     * halber Stand im Cache und wurde spaeter uebersetzt.
     */
    private fun scheduleStoreSelectedText(text: String?, sourcePackage: String) {
        if (text.isNullOrBlank()) return
        pendingSelectionJob?.cancel()
        pendingSelectionJob = serviceScope.launch {
            delay(PENDING_SELECTION_DELAY_MS)
            storeSelectedText(text, sourcePackage)
        }
    }

    /**
     * Laesst Android die aktuelle Auswahl selbst kopieren und liest die Zwischenablage.
     * Das ist der einzige Weg zu exakt dem markierten Text; die Baumrekonstruktion kann
     * Zeichen verlieren oder zu viel liefern. Die Zwischenablage wird dabei ueberschrieben.
     */
    private fun copySelectionToClipboard(): String? {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) {
            Log.w(SELECTION_TAG, "copy: kein ClipboardManager")
            return null
        }
        val root = try { rootInActiveWindow } catch (e: Exception) { null }
        if (root == null) {
            Log.w(SELECTION_TAG, "copy: rootInActiveWindow == null")
            return null
        }

        val before = readClipboard(clipboard)
        val webView = findNodeByClass(root, "android.webkit.WebView")
        val webChildren = try { webView?.childCount ?: -1 } catch (e: Exception) { -1 }
        Log.d(SELECTION_TAG, "copy: webview=${webView != null} childCount=$webChildren")
        val copyTarget = findCopyTarget(root) ?: return null
        val copyClass = copyTarget.className?.toString()
        Log.d(
            SELECTION_TAG,
            "copy: root=${root.className} pkg=${root.packageName} target=$copyClass " +
                "clipBefore=${before?.length ?: 0}"
        )
        val copied = try {
            copyTarget.performAction(AccessibilityNodeInfo.ACTION_COPY)
        } catch (e: Exception) {
            Log.w(SELECTION_TAG, "ACTION_COPY failed: ${e.message}")
            false
        }
        Log.d(SELECTION_TAG, "copy: performAction ACTION_COPY -> $copied on $copyClass")
        if (!copied) {
            Log.d(SELECTION_TAG, "copySelectionToClipboard: ACTION_COPY not supported")
            return null
        }

        // Das System setzt die Zwischenablage kurz nach dem Aufruf.
        repeat(CLIPBOARD_WAIT_TRIES) {
            try {
                Thread.sleep(CLIPBOARD_WAIT_STEP_MS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return null
            }
            val now = readClipboard(clipboard)
            if (!now.isNullOrBlank() && now != before) {
                return now.trim().take(MAX_SELECTED_TEXT_LENGTH).takeIf { it.isNotBlank() }
            }
        }
        Log.d(SELECTION_TAG, "copySelectionToClipboard: clipboard unchanged")
        return null
    }

    /**
     * Sucht den Knoten, der ACTION_COPY sinnvoll entgegennimmt.
     * WebView-Apps (Browser, RSS-Reader) melden Auswahl-Ereignisse ohne Text; der markierte
     * Text steckt in virtuellen Knoten. Dort hilft ACTION_COPY auf dem WebView selbst.
     */
    private fun findCopyTarget(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        try {
            root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { return it }
        } catch (e: Exception) {
            Log.w(SELECTION_TAG, "findFocus failed: ${e.message}")
        }
        findNodeByClass(root, "android.webkit.WebView")?.let { return it }
        return root
    }

    private fun findNodeByClass(root: AccessibilityNodeInfo, className: String): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES_TO_SCAN) {
            val node = queue.removeFirst()
            visited++
            try {
                if (node.className?.toString() == className) return node
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let(queue::addLast)
                }
            } catch (e: Exception) {
                Log.w(SELECTION_TAG, "class search failed: ${e.message}")
            }
        }
        return null
    }

    private fun readClipboard(clipboard: ClipboardManager): String? {
        return try {
            val clip = clipboard.primaryClip
            if (clip == null || clip.itemCount == 0) null
            else clip.getItemAt(0).coerceToText(this)?.toString()
        } catch (e: Exception) {
            Log.w(SELECTION_TAG, "clipboard read failed: ${e.message}")
            null
        }
    }

    private fun notifySystemUiNavigationIfNeeded(event: AccessibilityEvent): Boolean {
        val packageName = event.packageName?.toString().orEmpty()
        if (packageName != "com.android.systemui") return false
        val isWindowEvent = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        if (!isWindowEvent) return false
        Log.d(SELECTION_TAG, "SystemUI window event type=${event.eventType}")
        notifySystemUiWindowChanged()
        return true
    }

    internal fun collectSelectedText(node: AccessibilityNodeInfo?): String? {
        if (node == null) return null
        val candidates = linkedSetOf<String>()

        // 1. Der Knoten selbst und seine Eltern: dort steht die echte Auswahl.
        //    Guenstig (max. 12 Ebenen) und trifft auch tiefe Browser-Knoten.
        var cursor: AccessibilityNodeInfo? = node
        var level = 0
        while (cursor != null && level < MAX_PARENT_LEVELS) {
            val currentNode = cursor
            try {
                selectedTextFromNode(currentNode)?.let { candidates.add(it) }
                cursor = currentNode.parent
            } catch (e: Exception) {
                Log.w(SELECTION_TAG, "Selection parent walk failed: ${e.message}")
                break
            }
            level++
        }
        if (candidates.isNotEmpty()) {
            return candidates.maxByOrNull { it.length }
                ?.take(MAX_SELECTED_TEXT_LENGTH)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }

        // 2. Notfall: Baum begrenzt durchsuchen. Teile werden NICHT zusammengeklebt –
        //    sonst entstehen doppelte Texte und falsche Zeilenumbrueche.
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(node)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES_TO_SCAN) {
            val current = queue.removeFirst()
            visited++
            try {
                selectedTextFromNode(current)?.let { selected ->
                    if (selected.length <= MAX_SELECTED_TEXT_LENGTH) {
                        candidates.add(selected)
                    }
                }
                for (i in 0 until current.childCount) {
                    current.getChild(i)?.let(queue::addLast)
                }
            } catch (e: Exception) {
                Log.w(SELECTION_TAG, "Selection extraction failed: ${e.message}")
            }
        }
        if (queue.isNotEmpty()) {
            Log.w(SELECTION_TAG, "Selection scan stopped after $MAX_NODES_TO_SCAN nodes")
        }
        Log.w(
            SELECTION_TAG,
            "collect: start=${node.className} visited=$visited candidates=${candidates.size}"
        )
        return candidates.maxByOrNull { it.length }
            ?.take(MAX_SELECTED_TEXT_LENGTH)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun selectedTextFromNode(node: AccessibilityNodeInfo): String? {
        val text = node.text?.toString() ?: return null
        val start = node.textSelectionStart
        val end = node.textSelectionEnd
        if (start < 0 || end < 0 || start >= end) return null
        if (start > text.length || end > text.length) return null
        val safeEnd = end.coerceAtMost(start + MAX_SELECTED_TEXT_LENGTH)
        return text.substring(start, safeEnd).takeIf { it.isNotBlank() }
    }

    private fun extractSelectedTextFromEvent(event: AccessibilityEvent): String? {
        return try {
            val text = event.text
                .mapNotNull { item -> item?.toString()?.takeIf { it.isNotBlank() } }
                .joinToString("\n")
            val from = event.fromIndex
            val to = event.toIndex
            if (text.isBlank() || from < 0 || to <= from || from >= text.length) return null
            val safeTo = to.coerceAtMost(text.length)
            if (safeTo <= from) return null
            text.substring(from, safeTo).takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w(SELECTION_TAG, "extractSelectedTextFromEvent failed: ${e.message}")
            null
        }
    }
}
