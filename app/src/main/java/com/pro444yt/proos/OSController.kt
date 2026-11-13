package com.pro444yt.proos

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.MessageDigest

object OSController {
    val lockedPackages = LinkedHashSet<String>()
    val hiddenPackages = LinkedHashSet<String>()

    private const val PREFS = "proos_vault_sec_v2"
    private const val KEY_PIN_HASH = "vault_pin_hash"
    private const val KEY_FACE_ENABLED = "vault_face_enabled"

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        lockedPackages.clear()
        lockedPackages.addAll(prefs.getStringSet("locked_pkgs", emptySet()) ?: emptySet())
        hiddenPackages.clear()
        hiddenPackages.addAll(prefs.getStringSet("hidden_pkgs", emptySet()) ?: emptySet())
    }

    fun save(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet("locked_pkgs", lockedPackages.toSet())
            .putStringSet("hidden_pkgs", hiddenPackages.toSet())
            .apply()
    }

    fun hasCustomPin(context: Context): Boolean {
        return !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PIN_HASH, null).isNullOrEmpty()
    }

    fun setCustomPin(context: Context, pin: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_PIN_HASH, hashPin(pin))
            .apply()
    }

    fun verifyCustomPin(context: Context, pin: String): Boolean {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PIN_HASH, null) ?: return false
        return saved == hashPin(pin)
    }

    fun isFaceUnlockEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_FACE_ENABLED, false)
    }

    fun setFaceUnlockEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_FACE_ENABLED, enabled)
            .apply()
    }

    private fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(("ProOS_Salt_#444_" + pin).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun toggleLock(context: Context, pkg: String): Boolean {
        val added = if (lockedPackages.contains(pkg)) {
            lockedPackages.remove(pkg)
            false
        } else {
            lockedPackages.add(pkg)
            true
        }
        save(context)
        return added
    }

    fun isDeviceOwner(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    fun setAppSystemHidden(context: Context, targetPackage: String, hide: Boolean): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, AdminReceiver::class.java)
        val deepOk = if (dpm.isDeviceOwnerApp(context.packageName)) {
            dpm.setApplicationHidden(admin, targetPackage, hide)
        } else {
            false
        }
        if (hide) {
            hiddenPackages.add(targetPackage)
        } else {
            hiddenPackages.remove(targetPackage)
        }
        save(context)
        return deepOk
    }

    /** Biometric-only Face/Fingerprint prompt (never falls back to tablet's system lock PIN). */
    fun triggerBiometricOnly(
        activity: FragmentActivity,
        title: String = "PRO OS",
        subtitle: String = "Verify Face ID",
        onSuccess: () -> Unit,
        onFallbackPin: () -> Unit
    ) {
        val bm = BiometricManager.from(activity)
        if (bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) != BiometricManager.BIOMETRIC_SUCCESS) {
            onFallbackPin()
            return
        }
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onFallbackPin()
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Use Vault PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()

        prompt.authenticate(info)
    }
}
