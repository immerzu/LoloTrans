package de.lolo.lolotrans

import android.app.Application
import android.content.Intent
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LoloTransApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        AppContext.init(this)
        restoreBubbleIfNeeded()
    }

    private fun restoreBubbleIfNeeded() {
        scope.launch {
            try {
                val repo = SettingsRepository(this@LoloTransApplication)
                val shouldRestore = repo.bubbleEnabled.first()
                if (!shouldRestore) return@launch
                if (!Settings.canDrawOverlays(this@LoloTransApplication)) {
                    Log.w("LoloTransApplication", "Bubble restore skipped: overlay permission missing")
                    repo.setBubbleEnabled(false)
                    return@launch
                }
                if (!AccessibilityStatus.isSelectionServiceEnabled(this@LoloTransApplication)) {
                    Log.w("LoloTransApplication", "Bubble restore skipped: accessibility service disabled")
                    return@launch
                }
                Log.d("LoloTransApplication", "Restoring bubble service after app start")
                startService(Intent(this@LoloTransApplication, FloatingBubbleService::class.java).apply {
                    action = FloatingBubbleService.ACTION_START
                })
            } catch (e: Exception) {
                Log.w("LoloTransApplication", "Bubble restore failed: ${e.message}")
            }
        }
    }

}

