package com.example.ac_applock

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class AppLockerAccessibilityService : AccessibilityService() {
    
    companion object {
        var isServiceRunning = false
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

