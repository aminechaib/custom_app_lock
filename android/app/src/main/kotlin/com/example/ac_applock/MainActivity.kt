package com.example.ac_applock

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.provider.Settings
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.ByteArrayOutputStream

class MainActivity : FlutterFragmentActivity() {
    private val TAG = "MainActivity"
    private val CHANNEL = "com.example.ac_applock/app_locker"
    private val LOCKED_APPS_PREFS = "app_locker_prefs"
    private val LOCKED_APPS_KEY = "locked_apps_set"
    
    // Shared locked apps list
    private var lockedApps: MutableSet<String> = mutableSetOf()
    
    // Currently locked package (for biometric authentication)
    private var currentLockedPackage: String? = null
    
    // Biometric prompt
    private var biometricPrompt: BiometricPrompt? = null
    private var promptInfo: BiometricPrompt.PromptInfo? = null
    
    // App access state
    private var isAppUnlocked = false
    private var lastUnlockTime: Long = 0
    private val UNLOCK_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes
    
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        Log.d(TAG, "configureFlutterEngine called")
        
        // Setup method channel - authentication will be checked in onResume
        setupMethodChannel(flutterEngine)
        loadLockedAppsFromPrefs()
        needsAuthOnResume = !isAppAccessAllowed()
        
        Log.d(TAG, "Locked apps loaded: ${lockedApps.size}")
        Log.d(TAG, "Locked apps: ${lockedApps.joinToString(", ")}")
    }
    
    private var needsAuthOnResume = false
    
    private fun loadLockedAppsFromPrefs() {
        val prefs = getSharedPreferences(LOCKED_APPS_PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getStringSet(LOCKED_APPS_KEY, emptySet()) ?: emptySet()
        lockedApps.clear()
        lockedApps.addAll(stored)
        
        // Also update the service
        AppLockerService.updateLockedApps(lockedApps)
        
        Log.d(TAG, "Loaded ${lockedApps.size} locked apps from prefs")
    }
    
    private fun isAppAccessAllowed(): Boolean {
        val currentTime = System.currentTimeMillis()
        val allowed = isAppUnlocked && (currentTime - lastUnlockTime < UNLOCK_TIMEOUT_MS)
        Log.d(TAG, "isAppAccessAllowed: $allowed (isAppUnlocked=$isAppUnlocked, timeDiff=${currentTime - lastUnlockTime})")
        return allowed
    }
    
    private fun showBiometricAuth() {
        Log.d(TAG, "showBiometricAuth called")
        val executor = ContextCompat.getMainExecutor(this)
        
        biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Log.d(TAG, "Biometric auth error: $errorCode - $errString")
                finish()
            }
            
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                Log.d(TAG, "Biometric auth succeeded")
                isAppUnlocked = true
                lastUnlockTime = System.currentTimeMillis()
                needsAuthOnResume = false
            }
            
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Log.d(TAG, "Biometric auth failed")
            }
        })
        
        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("App Locker")
            .setSubtitle("Authenticate to access app settings")
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        
        biometricPrompt?.authenticate(promptInfo!!)
    }
    
    private fun setupMethodChannel(flutterEngine: FlutterEngine) {
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            Log.d(TAG, "Method call: ${call.method}")
            
            when (call.method) {
                "getInstalledApps" -> {
                    result.success(getInstalledApps())
                }
                "getAppIcon" -> {
                    val packageName = call.argument<String>("packageName")
                    if (packageName != null) {
                        result.success(getAppIcon(packageName))
                    } else {
                        result.error("INVALID_ARGUMENT", "Package name is required", null)
                    }
                }
                "hasUsageStatsPermission" -> {
                    result.success(hasUsageStatsPermission())
                }
                "requestUsageStatsPermission" -> {
                    requestUsageStatsPermission()
                    result.success(true)
                }
                "hasOverlayPermission" -> {
                    result.success(hasOverlayPermission())
                }
                "requestOverlayPermission" -> {
                    requestOverlayPermission()
                    result.success(true)
                }
                "isAccessibilityServiceEnabled" -> {
                    result.success(isAccessibilityServiceEnabled())
                }
                "requestAccessibilityService" -> {
                    requestAccessibilityService()
                    result.success(true)
                }
                "startAppLockerService" -> {
                    startAppLockerService()
                    result.success(true)
                }
                "stopAppLockerService" -> {
                    stopAppLockerService()
                    result.success(true)
                }
                "updateLockedApps" -> {
                    val packageNames = call.argument<List<String>>("packageNames")
                    if (packageNames != null) {
                        lockedApps.clear()
                        lockedApps.addAll(packageNames)
                        saveLockedAppsToPrefs(lockedApps)
                        AppLockerService.updateLockedApps(lockedApps)
                        Log.d(TAG, "updateLockedApps: ${packageNames.size} apps locked")
                        result.success(true)
                    } else {
                        result.error("INVALID_ARGUMENT", "Package names list is required", null)
                    }
                }
                "isBiometricAvailable" -> {
                    result.success(isBiometricAvailable())
                }
                "authenticateWithBiometric" -> {
                    val packageName = call.argument<String>("packageName")
                    currentLockedPackage = packageName
                    authenticateWithBiometric { success ->
                        result.success(success)
                    }
                }
                "lockApp" -> {
                    // Lock the app immediately (require re-authentication)
                    isAppUnlocked = false
                    lastUnlockTime = 0
                    Log.d(TAG, "lockApp called - app locked")
                    result.success(true)
                }
                "getDebugInfo" -> {
                    result.success(AppLockerService.getDebugInfo())
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
    }
    
    private fun getInstalledApps(): List<Map<String, Any?>> {
        val pm = packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val apps = mutableListOf<Map<String, Any?>>()
        
        for (packageInfo in packages) {
            val isUserApp = (packageInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0
            
            if (isUserApp) {
                try {
                    val appName = pm.getApplicationLabel(packageInfo).toString()
                    val packageName = packageInfo.packageName
                    
                    if (packageName != this.packageName) {
                        apps.add(mapOf(
                            "packageName" to packageName,
                            "appName" to appName,
                            "icon" to null,
                            "isLocked" to lockedApps.contains(packageName)
                        ))
                    }
                } catch (e: Exception) {
                    // Skip apps that can't be queried
                }
            }
        }
        
        return apps.sortedBy { it["appName"] as? String }
    }
    
    private fun getAppIcon(packageName: String): ByteArray? {
        return try {
            val pm = packageManager
            val drawable = pm.getApplicationIcon(packageName)
            val bitmap = drawableToBitmap(drawable)
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        } catch (e: Exception) {
            null
        }
    }
    
    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }
        
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 48
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 48
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
    
    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
    
    private fun requestUsageStatsPermission() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }
    
    private fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(this)
    }
    
    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:$packageName")
        )
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServicesRaw = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val component = ComponentName(this, AppLockerAccessibilityService::class.java)
        val expected = component.flattenToString()
        val expectedShort = component.flattenToShortString()
        val enabledServices = enabledServicesRaw.split(':').map { it.trim() }

        return enabledServices.any { it.equals(expected, ignoreCase = true) } ||
            enabledServices.any { it.equals(expectedShort, ignoreCase = true) }
    }
    
    private fun requestAccessibilityService() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }
    
    private fun startAppLockerService() {
        Log.d(TAG, "startAppLockerService called")
        val intent = Intent(this, AppLockerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
    
    private fun stopAppLockerService() {
        Log.d(TAG, "stopAppLockerService called")
        val intent = Intent(this, AppLockerService::class.java)
        stopService(intent)
    }
    
    private fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(this)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }
    
    private fun authenticateWithBiometric(callback: (Boolean) -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        
        biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                callback(false)
            }
            
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                callback(true)
            }
            
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
            }
        })
        
        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("App Locked")
            .setSubtitle("Authenticate to unlock")
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        
        biometricPrompt?.authenticate(promptInfo!!)
    }

    private fun saveLockedAppsToPrefs(packageNames: Set<String>) {
        val prefs = getSharedPreferences(LOCKED_APPS_PREFS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(LOCKED_APPS_KEY, packageNames).apply()
        Log.d(TAG, "Saved ${packageNames.size} locked apps to prefs")
    }
    
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called, needsAuthOnResume=$needsAuthOnResume")
        
        // Use handler to delay auth check to avoid fragment transaction conflicts
        if (!isAppAccessAllowed() && needsAuthOnResume) {
            Handler(mainLooper).postDelayed({
                if (!isFinishing && !isAppAccessAllowed()) {
                    showBiometricAuth()
                }
            }, 500)
        }
    }
    
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause called")
        needsAuthOnResume = true
    }
}
