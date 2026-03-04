package com.example.ac_applock

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

class LockScreenActivity : AppCompatActivity() {
    
    private var packageName: String? = null
    private var biometricPrompt: BiometricPrompt? = null
    private var isAuthenticated = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Keep screen on and show over lock screen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        
        packageName = intent.getStringExtra("packageName")
        
        if (packageName == null) {
            finish()
            return
        }
        
        setupUI()
        authenticateWithBiometric()
    }
    
    private fun setupUI() {
        setContentView(R.layout.activity_lock_screen)
        
        val appIconView = findViewById<ImageView>(R.id.appIcon)
        val appNameView = findViewById<TextView>(R.id.appName)
        val unlockButton = findViewById<View>(R.id.unlockButton)
        
        // Set app info
        try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName!!, 0)
            val appName = pm.getApplicationLabel(appInfo).toString()
            val appIcon = pm.getApplicationIcon(packageName!!)
            
            appNameView.text = appName
            appIconView.setImageDrawable(appIcon)
        } catch (e: PackageManager.NameNotFoundException) {
            appNameView.text = packageName
        }
        
        // Unlock button
        unlockButton.setOnClickListener {
            authenticateWithBiometric()
        }
    }
    
    private fun authenticateWithBiometric() {
        if (isAuthenticated) return
        
        val executor = ContextCompat.getMainExecutor(this)
        
        biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                
                // Handle cancel - just dismiss
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                    // User cancelled - go back to home
                    goHome()
                    return
                }
                
                // For other errors, show message
                Toast.makeText(
                    this@LockScreenActivity,
                    "Authentication error: $errString",
                    Toast.LENGTH_SHORT
                ).show()
                
                // Re-prompt after error
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isAuthenticated && !isFinishing) {
                        authenticateWithBiometric()
                    }
                }, 1000)
            }
            
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onAuthenticationSuccess()
            }
            
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // Don't finish - let user retry
                Toast.makeText(
                    this@LockScreenActivity,
                    "Authentication failed. Try again.",
                    Toast.LENGTH_SHORT
                ).show()
                
                // Re-prompt after failed attempt
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isAuthenticated && !isFinishing) {
                        authenticateWithBiometric()
                    }
                }, 500)
            }
        })
        
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("App Locked")
            .setSubtitle("Authenticate to unlock ${getAppName()}")
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        
        biometricPrompt?.authenticate(promptInfo)
    }
    
    private fun onAuthenticationSuccess() {
        isAuthenticated = true
        packageName?.let { AppLockerService.onAppUnlocked(it) }
        
        // Close lock screen
        finish()
    }
    
    private fun getAppName(): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName!!, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName ?: "App"
        }
    }
    
    private fun goHome() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }
    
    override fun onBackPressed() {
        // Go to home screen when back is pressed
        goHome()
    }
    
    override fun onPause() {
        super.onPause()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        AppLockerService.onLockScreenDismissed()
    }
}

