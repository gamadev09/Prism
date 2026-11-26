package com.gamadev.prism.ui.home

import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.gamadev.prism.R

sealed interface HiddenAppAction {
    data class Open(val index: Int) : HiddenAppAction
    data class Hide(val key: String) : HiddenAppAction
    data class Unhide(val key: String) : HiddenAppAction
}

/** Every entry point requires an authenticated device owner; cancellation never unlocks. */
@Composable
internal fun HiddenAppsAuthentication(action: HiddenAppAction, onSuccess: () -> Unit, onFinished: () -> Unit) {
    val context = LocalContext.current
    val success by rememberUpdatedState(onSuccess)
    val finished by rememberUpdatedState(onFinished)
    DisposableEffect(action, context) {
        val cancellation = CancellationSignal()
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val manager = context.getSystemService(BiometricManager::class.java)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        var active = true
        if (keyguard?.isDeviceSecure != true || manager?.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(context, R.string.hidden_auth_unavailable, Toast.LENGTH_LONG).show()
            finished()
        } else {
            val prompt = BiometricPrompt.Builder(context)
                .setTitle(context.getString(R.string.library_hidden))
                .setSubtitle(context.getString(R.string.hidden_auth_subtitle))
                .setAllowedAuthenticators(authenticators)
                .build()
            prompt.authenticate(cancellation, ContextCompat.getMainExecutor(context),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (!active) return
                        active = false
                        success()
                        finished()
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (!active) return
                        active = false
                        finished()
                    }
                })
        }
        onDispose { active = false; cancellation.cancel() }
    }
}
