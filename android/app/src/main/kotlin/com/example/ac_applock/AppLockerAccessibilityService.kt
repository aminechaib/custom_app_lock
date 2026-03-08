package com.example.ac_applock

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class AppLockerAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "AppLockerAccessService"
        var isServiceRunning = false
        
        // Package names that should not trigger lock (system UI, launcher, our app)
        // NOTE: Package installer and permission controller are NOT here - they need to be protected!
        private val EXCLUDED_PACKAGES = setOf(
            "com.example.ac_applock",
            "android",
            "com.android.systemui",
            "com.android.launcher",
            "com.android.launcher3",
            "com.android.launcher2",
            "com.sec.android.app.launcher",
            "com.samsung.android.app.launcher",
            "com.samsung.android.launcher",
            "com.miui.home",
            "com.coloros.launcher",
            "com.coloros.safecenter",
            "com.oppo.launcher",
            "com.vivo.launcher",
            "com.huawei.android.launcher",
            "com.oneplus.launcher",
            // Notification panel and quick settings
            "com.android.systemui.recents",
            "com.android.systemui.statusbar",
            "com.android.systemui.navbar",
            "com.android.systemui.volume",
            "com.android.systemui.editorservices",
            "com.android.systemui.gesture",
            // Quick settings and panel
            "com.android.quicksearchbox",
            "com.android.quicksettings",
            "com.android.panel",
            // Samsung specific system UI
            "com.samsung.android.app.cocktailbarservice",
            "com.samsung.android.app.taskbar",
            "com.samsung.android.smartswitchassistant",
            // Xiaomi specific (but NOT security center - that's protected!)
            "com.miui.powerkeeper",
            "com.miui.systemui",
            // Media/notification related
            "com.android.providers.downloads",
            "com.android.providers.media"
        )
        
        fun shouldIgnorePackage(packageName: String): Boolean {
            return EXCLUDED_PACKAGES.any { packageName.startsWith(it) }
        }
        
        fun logDebug(msg: String) {
            Log.d(TAG, msg)
        }
    }

    private var lastHandledPackage: String? = null
    private var lastHandledAt: Long = 0L
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        logDebug("onServiceConnected - Accessibility service started")
        AppLockerService.ensureStateLoaded(this)
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        
        logDebug("onAccessibilityEvent: $pkg")

        // Check if package is in excluded list first
        if (shouldIgnorePackage(pkg)) {
            logDebug("Ignoring excluded package: $pkg")
            return
        }
        
        // Coalesce duplicate window events from the same package.
        val now = System.currentTimeMillis()
        if (pkg == lastHandledPackage && (now - lastHandledAt) < 50) {
            logDebug("Debounce: skipping $pkg")
            return
        }
        lastHandledPackage = pkg
        lastHandledAt = now

        logDebug("Calling maybeLockPackage for: $pkg")
        AppLockerService.maybeLockPackage(this, pkg)
    }
    
    override fun onInterrupt() {
        // Not used
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        logDebug("onDestroy - Accessibility service stopped")
    }
}
