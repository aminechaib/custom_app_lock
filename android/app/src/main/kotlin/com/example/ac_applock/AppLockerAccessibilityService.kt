package com.example.ac_applock

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class AppLockerAccessibilityService : AccessibilityService() {
    
    companion object {
        var isServiceRunning = false
        
        // Package names that should not trigger lock (system UI, launcher, our app)
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
            "com.miui.securitycenter",
            "com.coloros.launcher",
            "com.coloros.safecenter",
            "com.oppo.launcher",
            "com.vivo.launcher",
            "com.huawei.android.launcher",
            "com.huawei.systemmanager",
            "com.oneplus.launcher",
            "com.oneplus.security",
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
            // Xiaomi specific
            "com.miui.powerkeeper",
            "com.miui.securitycenter.permission",
            "com.miui.systemui",
            // Media/notification related
            "com.android.providers.downloads",
            "com.android.providers.media",
            // Permission manager
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            // Package installer
            "com.android.packageinstaller",
            "com.google.android.packageinstaller"
        )
        
        fun shouldIgnorePackage(packageName: String): Boolean {
            return EXCLUDED_PACKAGES.any { packageName.startsWith(it) }
        }
    }

    private var lastHandledPackage: String? = null
    private var lastHandledAt: Long = 0L
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        AppLockerService.ensureStateLoaded(this)
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return

        // Check if package is in excluded list first
        if (shouldIgnorePackage(pkg)) {
            return
        }
        
        // Coalesce duplicate window events from the same package.
        val now = System.currentTimeMillis()
        if (pkg == lastHandledPackage && (now - lastHandledAt) < 150) return
        lastHandledPackage = pkg
        lastHandledAt = now

        AppLockerService.maybeLockPackage(this, pkg)
    }
    
    override fun onInterrupt() {
        // Not used
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
    }
}

