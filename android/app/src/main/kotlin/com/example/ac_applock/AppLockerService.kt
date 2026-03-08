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
import androidx.core.app.NotificationCompat

class AppLockerService : Service() {
    
    companion object {
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

        // Settings and uninstall entry points that should always be protected.
        private val PROTECTED_SYSTEM_PACKAGES = setOf(
            "com.android.settings",
            "com.android.vending",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.miui.securitycenter",
            "com.samsung.android.packageinstaller"
        )
        
        // Track if we recently showed lock screen (to prevent immediate re-show)
        private var lastShownTime: Long = 0
        private const val COOLDOWN_MS = 2000L // 2 second cooldown
        
        fun updateLockedApps(apps: Set<String>) {
            lockedApps.clear()
            lockedApps.addAll(apps)
            temporarilyUnlockedApps.retainAll(lockedApps)
            stateLoaded = true
        }

        fun setProtectSettingsEnabled(enabled: Boolean) {
            protectSettingsEnabled = enabled
        }

        fun ensureStateLoaded(context: Context) {
            if (stateLoaded) return
            val prefs = context.getSharedPreferences(LOCKED_APPS_PREFS, Context.MODE_PRIVATE)
            val stored = prefs.getStringSet(LOCKED_APPS_KEY, emptySet()) ?: emptySet()
            updateLockedApps(stored)
        }
        
        // Called when lock screen is dismissed
        fun onLockScreenDismissed() {
            isLockScreenActive = false
            lastShownTime = System.currentTimeMillis()
        }
        
        // Called when device screen is turned off
        fun onScreenOff() {
            // Lock all apps when screen turns off - require re-authentication
            temporarilyUnlockedApps.clear()
            lastShownTime = System.currentTimeMillis()
            isLockScreenActive = false
        }
        
        // Called when device screen is turned on
        fun onScreenOn() {
            // Ensure lock screen can be shown again
            isLockScreenActive = false
            // Keep temporarilyUnlockedApps cleared for security
            // User must re-authenticate after screen on
        }
        
        // Called when user navigates away from locked app
        fun onAppBackground(packageName: String) {
            temporarilyUnlockedApps.remove(packageName)
        }

        fun onAppUnlocked(packageName: String) {
            temporarilyUnlockedApps.add(packageName)
        }
        
        // Check if we should show lock screen for this package
        fun shouldShowLockScreen(packageName: String): Boolean {
            val isTargetPackage = lockedApps.contains(packageName) ||
                (protectSettingsEnabled && PROTECTED_SYSTEM_PACKAGES.contains(packageName))
            if (!isTargetPackage) {
                return false
            }

            // App already unlocked in current foreground session
            if (temporarilyUnlockedApps.contains(packageName)) {
                return false
            }
            
            // Check if this package is in excluded list
            if (EXCLUDED_PACKAGES.any { packageName.startsWith(it) }) {
                return false
            }

            if (isLockScreenActive) {
                return false
            }
            
            // Check cooldown to prevent immediate re-show after dismissing
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastShownTime < COOLDOWN_MS) {
                return false
            }
            
            return true
        }
        
        // Mark that we're showing lock screen for this package
        fun setLockScreenShown() {
            isLockScreenActive = true
            lastShownTime = System.currentTimeMillis()
        }
        
        fun isPackageLocked(packageName: String): Boolean {
            return lockedApps.contains(packageName)
        }

        fun onForegroundPackageChanged(packageName: String) {
            if (lastObservedForegroundPackage != null && lastObservedForegroundPackage != packageName) {
                onAppBackground(lastObservedForegroundPackage!!)
            }
            lastObservedForegroundPackage = packageName
        }

        fun launchLockScreen(context: Context, packageName: String) {
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
            if (!shouldShowLockScreen(packageName)) {
                return false
            }
            setLockScreenShown()
            launchLockScreen(context, packageName)
            return true
        }
    }
    
    private val handler = Handler(Looper.getMainLooper())
    private var isMonitoring = false
    private val monitorIntervalMs = 1000L
    
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
        stopMonitoring()
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            // Receiver not registered
        }
    }
    
    private fun startMonitoring() {
        isMonitoring = true
        handler.post(monitorRunnable)
    }
    
    private fun stopMonitoring() {
        isMonitoring = false
        handler.removeCallbacks(monitorRunnable)
    }
    
    private fun checkForegroundApp() {
        if (lockedApps.isEmpty() && !protectSettingsEnabled) {
            return
        }

        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return
        
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 2500 // Last 2.5 seconds
        
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

