package com.gamadev.prism.data

import android.app.Activity
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Bundle
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import com.gamadev.prism.R

/** Transparent host activity so BiometricPrompt runs on a real Activity context when unlocking from AppLockOverlay. */
class AppLockAuthActivity : Activity() {
    private var cancel: CancellationSignal? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        window.decorView.setBackgroundColor(if (isDark) android.graphics.Color.BLACK else android.graphics.Color.rgb(242, 242, 247))
        AppLockOverlay.hideForAuth()
        val pkg = intent.getStringExtra(EXTRA_PKG)
        if (pkg.isNullOrBlank()) {
            finish()
            return
        }
        val auth = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val bm = getSystemService(BiometricManager::class.java)
        val km = getSystemService(KeyguardManager::class.java)
        if (km?.isDeviceSecure != true || bm?.canAuthenticate(auth) != BiometricManager.BIOMETRIC_SUCCESS) {
            AppLockOverlay.allowFromLauncher(pkg)
            finish()
            return
        }
        val sig = CancellationSignal()
        cancel = sig
        val prompt = BiometricPrompt.Builder(this)
            .setTitle(getString(R.string.app_lock_title))
            .setSubtitle(getString(R.string.app_lock_subtitle))
            .setAllowedAuthenticators(auth)
            .build()
        prompt.authenticate(sig, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                AppLockOverlay.allowFromLauncher(pkg)
                finish()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                AppLockOverlay.onAuthCancel()
                finish()
            }
        })
    }

    override fun onDestroy() {
        cancel?.cancel()
        cancel = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PKG = "pkg"
    }
}
