package de.lolo.lolotrans

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArraySet

private const val SELECTION_TAG = "SelectionAccessibility"

class SelectionAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val MAX_SELECTED_TEXT_LENGTH = 10_000
        private const val DEFAULT_MAX_AGE_MS = 30_000L
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

        fun isConnected(): Boolean = connected

        fun getCurrentSelectedText(): String? {
            val service = activeService
            if (service == null) {
                Log.w(SELECTION_TAG, "getCurrentSelectedText: activeService is NULL")
                return null
            }
            val root = service.rootInActiveWindow
            val rootPkg = try { root?.packageName?.toString() } catch (e: Exception) { "error" }
            val result = service.collectSelectedTextFromAllWindows()
                ?: service.collectSelectedText(root)
            if (result != null) {
                Log.d(SELECTION_TAG, "getCurrentSelectedText: OK package=$rootPkg length=${result.length}")
            } else {
                Log.w(SELECTION_TAG, "getCurrentSelectedText: NULL (package=$rootPkg, no selected text found)")
            }
            return result
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

        /** Markiert den aktuellen Cache als verbraucht (nach Bubble-Tap). */
        fun consumeSelection() {
            synchronized(lock) {
                lastSelectionConsumed = true
                Log.d(SELECTION_TAG, "consumeSelection: cache consumed")
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

        /** Prüft, ob [text] bereits als verbraucht markiert ist (Clipboard-Wiederverwendung verhindern). */
        fun isTextConsumed(text: String): Boolean {
            val ct = consumedText ?: return false
            return text == ct
        }

        /** Markiert [text] als verbraucht, nachdem er erfolgreich übersetzt wurde. */
        fun setConsumedText(text: String?) {
            consumedText = text
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
                val existing = lastSelectedText?.trim().orEmpty()
                val samePackage = sourcePackage == null || lastSelectedPackage == null ||
                    sourcePackage == lastSelectedPackage
                val existingIsFresh = System.currentTimeMillis() - lastSelectedAtMs <= DEFAULT_MAX_AGE_MS
                if (!lastSelectionConsumed && samePackage && existingIsFresh && existing.length > normalized.length) {
                    val existingNorm = normalizeForCompare(existing)
                    val normalizedNorm = normalizeForCompare(normalized)
                    if (existingNorm.contains(normalizedNorm)) {
                        lastSelectedAtMs = System.currentTimeMillis()
                        Log.d(SELECTION_TAG, "storeSelectedText: keep longer cached selection existing=${existing.length} new=${normalized.length} pkg=$sourcePackage")
                        return
                    }
                }
                lastSelectedText = normalized
                lastSelectedAtMs = System.currentTimeMillis()
                lastSelectedPackage = sourcePackage
                lastSelectionConsumed = false
                consumedText = null // neue Auswahl → alter verbrauchter Clipboard-Text ist ungültig
                Log.d(SELECTION_TAG, "storeSelectedText: length=${normalized.length} pkg=$sourcePackage consumed=reset")
            }
        }

        private fun normalizeForCompare(text: String): String =
            text.trim().lowercase().replace(Regex("\\s+"), " ")
    }

    override fun onCreate() {
        super.onCreate()
        activeService = this
        connected = true
        persistAccessibilityApprovedOnce()
        Log.d(SELECTION_TAG, "Accessibility service created")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = this
        connected = true
        persistAccessibilityApprovedOnce()
        Log.d(SELECTION_TAG, "Accessibility service connected")
    }

    override fun onDestroy() {
        if (activeService === this) {
            activeService = null
        }
        connected = false
        serviceScope.cancel()
        super.onDestroy()
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
        Log.d(SELECTION_TAG, "onAccessibilityEvent type=$type pkg=$pkg isSelection=$isSelection")
        if (notifySystemUiNavigationIfNeeded(event)) return

        val fromSource = collectSelectedText(event.source)
        val fromRoot = collectSelectedText(rootInActiveWindow)

        val selected = when {
            fromSource != null && fromRoot != null ->
                if (fromRoot.length >= fromSource.length) fromRoot else fromSource
            fromSource != null -> fromSource
            fromRoot != null -> fromRoot
            else -> extractSelectedTextFromEvent(event)
        }

        if (selected != null) {
            Log.d(SELECTION_TAG, "onAccessibilityEvent: selectedText length=${selected.length} pkg=$pkg")
        } else {
            Log.d(SELECTION_TAG, "onAccessibilityEvent: no selection found pkg=$pkg")
        }
        storeSelectedText(selected, pkg)
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
        val values = linkedSetOf<String>()
        collectSelectedText(node, values)
        return values.joinToString("\n").takeIf { it.isNotBlank() }
    }

    private fun collectSelectedTextFromAllWindows(): String? {
        val values = linkedSetOf<String>()
        collectWindowRoots().forEach { root -> collectSelectedText(root, values) }
        return values.joinToString("\n").takeIf { it.isNotBlank() }
    }

    private fun collectSelectedText(node: AccessibilityNodeInfo?, values: MutableSet<String>) {
        if (node == null) return
        try {
            val direct = selectedTextFromNode(node)
            if (!direct.isNullOrBlank()) {
                values.add(direct)
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                collectSelectedText(child, values)
            }
        } catch (e: Exception) {
            Log.w(SELECTION_TAG, "Selection extraction failed: ${e.message}")
        }
    }

    private fun selectedTextFromNode(node: AccessibilityNodeInfo): String? {
        val text = node.text?.toString() ?: return null
        val start = node.textSelectionStart
        val end = node.textSelectionEnd
        if (start < 0 || end < 0 || start >= end) return null
        if (start > text.length || end > text.length) return null
        val safeEnd = end.coerceAtMost(start + MAX_SELECTED_TEXT_LENGTH)
        return text.substring(start, safeEnd).trim().takeIf { it.isNotBlank() }
    }

    private fun collectWindowRoots(): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        try {
            windows?.mapNotNullTo(roots) { it.root }
        } catch (e: Exception) {
            Log.w(SELECTION_TAG, "windows read failed: ${e.message}")
        }
        rootInActiveWindow?.let { root ->
            if (roots.none { it === root }) roots.add(root)
        }
        return roots
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
