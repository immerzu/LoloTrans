package de.lolo.lolotrans

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

object AccessibilityStatus {
    fun isSelectionServiceEnabled(context: Context): Boolean {
        return SelectionAccessibilityService.isConnected() ||
            isEnabledInSecureSettings(context) ||
            isEnabledInAccessibilityManager(context)
    }

    private fun isEnabledInSecureSettings(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val expected = ComponentName(context, SelectionAccessibilityService::class.java)
        val expectedFlat = expected.flattenToString()
        val expectedShort = expected.flattenToShortString()
        return enabledServices.split(':').any { raw ->
            val service = raw.trim()
            if (service.equals(expectedFlat, ignoreCase = true) ||
                service.equals(expectedShort, ignoreCase = true)
            ) {
                return@any true
            }
            ComponentName.unflattenFromString(service)?.let { enabled ->
                enabled.packageName == expected.packageName && enabled.className == expected.className
            } == true
        }
    }

    private fun isEnabledInAccessibilityManager(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        val expected = ComponentName(context, SelectionAccessibilityService::class.java)
        return try {
            manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { info ->
                    val serviceInfo = info.resolveInfo?.serviceInfo ?: return@any false
                    serviceInfo.packageName == expected.packageName &&
                        serviceInfo.name == expected.className
                }
        } catch (_: Exception) {
            false
        }
    }
}
