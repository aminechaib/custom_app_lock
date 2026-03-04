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
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.ByteArrayOutputStream

class MainActivity : FlutterFragmentActivity() {
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
    
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
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
                        // Just return success - lock screen handles unlocking
                        result.success(success)
                    }
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
            // Filter to show only user-installed third-party apps (not system apps)
            val isUserApp = (packageInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0
            
            if (isUserApp) {
                try {
                    val appName = pm.getApplicationLabel(packageInfo).toString()
                    val packageName = packageInfo.packageName
                    
                    // Skip our own app
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
        val intent = Intent(this, AppLockerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
    
    private fun stopAppLockerService() {
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
                // Don't call callback here - let user retry
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
    }
}

