package com.andloading.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class AssistantAccessibilityService : AccessibilityService() {

    @Volatile
    var foregroundPackage: String? = null
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        restoreResidentChain()
    }

    private fun restoreResidentChain() {
        val app = applicationContext as? com.andloading.assistant.AssistantApp ?: return
        if (!app.settings.keepAliveFloating) return
        if (!android.provider.Settings.canDrawOverlays(this)) return
        if (com.andloading.assistant.service.FloatingWindowService.isRunning) return
        runCatching {
            androidx.core.content.ContextCompat.startForegroundService(
                this,
                android.content.Intent(this, com.andloading.assistant.service.AssistantForegroundService::class.java),
            )
            com.andloading.assistant.service.FloatingWindowService.start(this)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.packageName?.toString()?.let { foregroundPackage = it }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    companion object {
        @Volatile
        var instance: AssistantAccessibilityService? = null
    }
}
