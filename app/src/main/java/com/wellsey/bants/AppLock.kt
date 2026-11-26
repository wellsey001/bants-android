package com.wellsey.bants

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.wellsey.bants.data.Bants
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Optional lock: Bants asks for your fingerprint, face or phone PIN when you open it,
 * or come back after a while. Calls can still be answered while it's locked.
 */
object AppLock {
    val locked = MutableStateFlow(false)
    private var leftAt = 0L
    // The PIN screen briefly sends Bants to the background; don't count that as leaving.
    @Volatile private var authenticating = false

    val enabled: Boolean get() = Bants.session.appLock

    /** Fingerprint/face/PIN, whichever the phone has. API 30+ can mix them in one prompt. */
    private val authenticators = if (Build.VERSION.SDK_INT >= 30) BIOMETRIC_WEAK or DEVICE_CREDENTIAL else BIOMETRIC_WEAK

    fun canUse(activity: android.content.Context): Boolean =
        BiometricManager.from(activity).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    fun onAppStart() {
        if (enabled) locked.value = true
    }

    fun onBackground() {
        if (!authenticating) leftAt = System.currentTimeMillis()
    }

    fun onForeground() {
        if (authenticating || !enabled || locked.value || leftAt == 0L) return
        if (System.currentTimeMillis() - leftAt >= Bants.session.appLockAfterSeconds * 1000L) locked.value = true
    }

    fun prompt(activity: FragmentActivity, onDone: (Boolean) -> Unit = {}) {
        if (authenticating) return
        authenticating = true
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Bants")
            .setSubtitle("Your chats are locked")
            .setAllowedAuthenticators(authenticators)
            .apply { if (Build.VERSION.SDK_INT < 30) setNegativeButtonText("Cancel") }
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                authenticating = false
                leftAt = 0L
                locked.value = false
                onDone(true)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                authenticating = false
                onDone(false)
            }
        }).authenticate(info)
    }
}
