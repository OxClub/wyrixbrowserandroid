package com.wyrix.browser.security

import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal

/**
 * Thin wrapper over the platform BiometricPrompt (API 28+; no extra dependency). On Android 11+ the prompt accepts
 * biometrics OR the device PIN/pattern/password; on 9-10 it is biometrics only with a Cancel button.
 * The app never sees biometric data; it only receives success/failure.
 */
object BiometricGate {
    enum class Availability { AVAILABLE, NOT_ENROLLED, UNAVAILABLE }

    private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun availability(context: Context): Availability {
        if (Build.VERSION.SDK_INT < 28) return Availability.UNAVAILABLE
        if (Build.VERSION.SDK_INT < 29) return Availability.AVAILABLE // no query API on 28; the prompt itself reports problems
        val bm = context.getSystemService(BiometricManager::class.java) ?: return Availability.UNAVAILABLE
        val code = if (Build.VERSION.SDK_INT >= 30) bm.canAuthenticate(AUTHENTICATORS) else bm.canAuthenticate()
        return when (code) {
            BiometricManager.BIOMETRIC_SUCCESS -> Availability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Availability.NOT_ENROLLED
            else -> Availability.UNAVAILABLE
        }
    }

    /** Shows the system prompt; [onResult] is called exactly once on the main thread (true only on success). */
    fun authenticate(activity: Activity, title: String, subtitle: String, onResult: (Boolean) -> Unit) {
        var done = false
        fun finish(ok: Boolean) { if (!done) { done = true; onResult(ok) } }
        try {
            val builder = BiometricPrompt.Builder(activity).setTitle(title).setSubtitle(subtitle)
            if (Build.VERSION.SDK_INT >= 30) builder.setAllowedAuthenticators(AUTHENTICATORS)
            else builder.setNegativeButton("Cancel", activity.mainExecutor) { _, _ -> finish(false) }
            builder.build().authenticate(CancellationSignal(), activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) { finish(true) }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) { finish(false) }
                // onAuthenticationFailed = a rejected attempt; the system keeps the prompt open for retries.
            })
        } catch (e: Exception) {
            finish(false)
        }
    }
}
