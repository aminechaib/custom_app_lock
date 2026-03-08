package com.example.ac_applock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

class AppLockerService : Service() {
    
    companion object {
        private const val TAG = "AppLockerService"
        private const val CHANNEL_ID = "app_locker_service"
        private const val NOTIFICATION_ID = 1001
        private const val LOCKED_APPS_PREFS = "app_locker_prefs"
        private const val LOCKED_APPS_KEY = "locked_apps_set"
        
        private var lockedApps: MutableSet<String> = mutableSetOf()
        private var temporarilyUnlockedApps: MutableSet<String> = mutableSetOf()
        private var isLockScreenActive = false
        private var protectSettingsEnabled = true
        private var stateLoaded = false
        private var lastObservedForegroundPackage: String? = null
        
        // Debug counters
        private var lockAttemptCount = 0
        private var successfulLockCount = 0
        private var bypassCount = 0
        private var lastBypassReason: String = ""
        
        fun logDebug(msg: String) {
            Log.d(TAG, msg)
        }
        
        fun logError(msg: String) {
            Log.e(TAG, msg)
        }
        
        fun getDebugInfo(): String {
            return """
                Lock Attempts: $lockAttemptCount
                Successful Locks: $successfulLockCount
                Bypassed: $bypassCount
                Last Bypass: $lastBypassReason
                Locked Apps: ${lockedApps.size} -> ${lockedApps.joinToString(", ")}
                Temporarily Unlocked: ${temporarilyUnlockedApps.joinToString(", ")}
                Protect Settings: $protectSettingsEnabled
                Lock Screen Active: $isLockScreenActive
            """.trimIndent()
        }
        
        fun resetDebugCounters() {
            lockAttemptCount = 0
            successfulLockCount = 0
            bypassCount = 0
            lastBypassReason = ""
        }
        
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

        // Settings and uninstall entry points that should always be protected.
        // These take priority over EXCLUDED_PACKAGES
        private val PROTECTED_SYSTEM_PACKAGES = setOf(
            "com.android.settings",
            "com.android.vending",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.miui.securitycenter",
            "com.samsung.android.packageinstaller",
            "com.huawei.systemmanager",
            "com.oneplus.security"
        )
        
        // Track if we recently showed lock screen (to prevent immediate re-show)
        private var lastShownTime: Long = 0
        private const val COOLDOWN_MS = 500L // Reduced from 2000ms for faster re-locking
        
        fun updateLockedApps(apps: Set<String>) {
            logDebug("updateLockedApps: $apps")
            lockedApps.clear()
            lockedApps.addAll(apps)
            temporarilyUnlockedApps.retainAll(lockedApps)
            stateLoaded = true
            logDebug("Locked apps updated: ${lockedApps.size} apps")
        }

        fun setProtectSettingsEnabled(enabled: Boolean) {
            logDebug("setProtectSettingsEnabled: $enabled")
            protectSettingsEnabled = enabled
        }

        fun ensureStateLoaded(context: Context) {
            if (stateLoaded) return
            logDebug("Loading locked apps from prefs...")
            val prefs = context.getSharedPreferences(LOCKED_APPS_PREFS, Context.MODE_PRIVATE)
            val stored = prefs.getStringSet(LOCKED_APPS_KEY, emptySet()) ?: emptySet()
            logDebug("Loaded from prefs: $stored")
            updateLockedApps(stored)
        }
        
        // Called when lock screen is dismissed
        fun onLockScreenDismissed() {
            logDebug("onLockScreenDismissed")
            isLockScreenActive = false
            lastShownTime = System.currentTimeMillis()
        }
        
        // Called when device screen is turned off
        fun onScreenOff() {
            logDebug("onScreenOff - clearing all unlocks")
            // Lock all apps when screen turns off - require re-authentication
            temporarilyUnlockedApps.clear()
            lastShownTime = System.currentTimeMillis()
            isLockScreenActive = false
        }
        
        // Called when device screen is turned on
        fun onScreenOn() {
            logDebug("onScreenOn")
            // Ensure lock screen can be shown again
            isLockScreenActive = false
            // Keep temporarilyUnlockedApps cleared for security
            // User must re-authenticate after screen on
        }
        
        // Called when user navigates away from locked app
        fun onAppBackground(packageName: String) {
            logDebug("onAppBackground: $packageName")
            temporarilyUnlockedApps.remove(packageName)
        }

        fun onAppUnlocked(packageName: String) {
            logDebug("onAppUnlocked: $packageName")
            temporarilyUnlockedApps.add(packageName)
        }
        
        // Check if we should show lock screen for this package
        fun shouldShowLockScreen(packageName: String): Boolean {
            lockAttemptCount++
            
            val isProtectedSystemPackage = protectSettingsEnabled && PROTECTED_SYSTEM_PACKAGES.contains(packageName)
            val isTargetPackage = lockedApps.contains(packageName) || isProtectedSystemPackage
            
            logDebug("shouldShowLockScreen: $packageName -> isTarget=$isTargetPackage, isProtected=$isProtectedSystemPackage")
            
            if (!isTargetPackage) {
                lastBypassReason = "Not a target package"
                bypassCount++
                return false
            }

            // For protected system packages, skip the "already unlocked" check and show lock
            // This ensures Settings/Package Installer always require auth
            if (isProtectedSystemPackage) {
                logDebug("Showing lock for protected system package: $packageName")
                return true
            }

            // App already unlocked in current foreground session
            if (temporarilyUnlockedApps.contains(packageName)) {
                lastBypassReason = "Already unlocked in session"
                bypassCount++
                logDebug("Bypass: Already unlocked: $packageName")
                return false
            }
            
            // Check if this package is in excluded list (only for regular locked apps)
            if (EXCLUDED_PACKAGES.any { packageName.startsWith(it) }) {
                lastBypassReason = "In excluded list"
                bypassCount++
                logDebug("Bypass: Excluded package: $packageName")
                return false
            }

            if (isLockScreenActive) {
                lastBypassReason = "Lock screen already active"
                bypassCount++
                logDebug("Bypass: Lock screen active")
                return false
            }
            
            // Check cooldown to prevent immediate re-show after dismissing
            val currentTime = System.currentTimeMillis()
            val timeSinceLastShown = currentTime - lastShownTime
            if (timeSinceLastShown < COOLDOWN_MS) {
                lastBypassReason = "Cooldown active (${timeSinceLastShown}ms < ${COOLDOWN_MS}ms)"
                bypassCount++
                logDebug("Bypass: Cooldown active: ${timeSinceLastShown}ms")
                return false
            }
            
            logDebug("Showing lock for: $packageName")
            return true
        }
        
        // Mark that we're showing lock screen for this package
        fun setLockScreenShown() {
            logDebug("setLockScreenShown")
            isLockScreenActive = true
            lastShownTime = System.currentTimeMillis()
            successfulLockCount++
        }
        
        fun isPackageLocked(packageName: String): Boolean {
            return lockedApps.contains(packageName)
        }

        fun onForegroundPackageChanged(packageName: String) {
            logDebug("onForegroundPackageChanged: $packageName")
            if (lastObservedForegroundPackage != null && lastObservedForegroundPackage != packageName) {
                onAppBackground(lastObservedForegroundPackage!!)
            }
            lastObservedForegroundPackage = packageName
        }

        fun launchLockScreen(context: Context, packageName: String) {
            logDebug("launchLockScreen: $packageName")
            val intent = Intent(context, LockScreenActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("packageName", packageName)
            }
            context.startActivity(intent)
        }

        fun maybeLockPackage(context: Context, packageName: String): Boolean {
            ensureStateLoaded(context)
            onForegroundPackageChanged(packageName)
            
            logDebug("maybeLockPackage called for: $packageName")
            logDebug("Current state - lockedApps: ${lockedApps.size}, protectSettings: $protectSettingsEnabled")
            
            if (!shouldShowLockScreen(packageName)) {
                logDebug("shouldShowLockScreen returned FALSE for: $packageName, reason: $lastBypassReason")
                return false
            }
            
            setLockScreenShown()
            launchLockScreen(context, packageName)
            logDebug("Lock screen launched for: $packageName")
            return true
        }
    }
    
    private val handler = Handler(Looper.getMainLooper())
    private var isMonitoring = false
    private val monitorIntervalMs = 300L // Reduced from 1000ms for faster detection
    
    // Screen on/off receiver
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                onScreenOff()
            } else if (intent?.action == Intent.ACTION_SCREEN_ON) {
                onScreenOn()
            }
        }
    }
    
    private val monitorRunnable = object : Runnable {
        override fun run() {
            if (isMonitoring) {
                checkForegroundApp()
                handler.postDelayed(this, monitorIntervalMs)
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        logDebug("onCreate - Service starting")
        createNotificationChannel()
        registerScreenReceiver()
    }
    
    private fun registerScreenReceiver() {
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_SCREEN_OFF)
        filter.addAction(Intent.ACTION_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logDebug("onStartCommand")
        startForeground(NOTIFICATION_ID, createNotification())
        loadLockedAppsFromPrefs()
        startMonitoring()
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        logDebug("onDestroy - Service stopping")
        stopMonitoring()
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            // Receiver not registered
        }
    }
    
    private fun startMonitoring() {
        logDebug("startMonitoring")
        isMonitoring = true
        handler.post(monitorRunnable)
    }
    
    private fun stopMonitoring() {
        logDebug("stopMonitoring")
        isMonitoring = false
        handler.removeCallbacks(monitorRunnable)
    }
    
    private fun checkForegroundApp() {
        if (lockedApps.isEmpty() && !protectSettingsEnabled) {
            return
        }

        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return
        
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 1000 // Reduced from 2500ms for faster detection
        
        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        var foregroundPackage: String? = null
        var lastMoveToForegroundTime = 0L
        
        while (usageEvents.hasNextEvent()) {
            val event = UsageEvents.Event()
            usageEvents.getNextEvent(event)
            
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                if (event.timeStamp > lastMoveToForegroundTime) {
                    lastMoveToForegroundTime = event.timeStamp
                    foregroundPackage = event.packageName
                }
            }
        }
        
        if (foregroundPackage != null) {
            logDebug("checkForegroundApp: detected $foregroundPackage")
        }
        
        foregroundPackage?.let { pkg ->
            maybeLockPackage(this, pkg)
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Locker Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitoring app usage to protect locked apps"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun loadLockedAppsFromPrefs() {
        ensureStateLoaded(this)
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("App Locker Active")
            .setContentText("Protecting your apps")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
