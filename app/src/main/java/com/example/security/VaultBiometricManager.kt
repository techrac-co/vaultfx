package com.example.security

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

enum class BiometricHardwareStatus {
  BIOMETRIC_READY,
  DEVICE_CREDENTIAL_ONLY,
  NOT_ENROLLED,
  UNAVAILABLE,
}

data class BiometricPromptRequest(
  val title: String,
  val subtitle: String,
  val description: String,
  val allowDeviceCredential: Boolean = true,
  val requireConfirmation: Boolean = true,
)

sealed interface BiometricAuthResult {
  data class Success(val usedBiometric: Boolean) : BiometricAuthResult
  data object Cancelled : BiometricAuthResult
  data object FallbackToPin : BiometricAuthResult
  data class NeedsLegacyDeviceCredential(val title: String, val subtitle: String) : BiometricAuthResult
  data class Error(val message: String, val canRetry: Boolean) : BiometricAuthResult
}

object VaultBiometricManager {

  fun checkHardwareStatus(context: Context): BiometricHardwareStatus {
    val manager = BiometricManager.from(context)
    val biometricResult = manager.canAuthenticate(BIOMETRIC_STRONG or BIOMETRIC_WEAK)
    if (biometricResult == BiometricManager.BIOMETRIC_SUCCESS) {
      return BiometricHardwareStatus.BIOMETRIC_READY
    }

    val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    val isDeviceSecure = keyguard?.isDeviceSecure == true

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      if (manager.canAuthenticate(DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS || isDeviceSecure) {
        return BiometricHardwareStatus.DEVICE_CREDENTIAL_ONLY
      }
    } else if (isDeviceSecure) {
      return BiometricHardwareStatus.DEVICE_CREDENTIAL_ONLY
    }

    return if (biometricResult == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
      BiometricHardwareStatus.NOT_ENROLLED
    } else {
      BiometricHardwareStatus.UNAVAILABLE
    }
  }

  fun statusDisplayLabel(status: BiometricHardwareStatus, hasVaultPin: Boolean): String = when (status) {
    BiometricHardwareStatus.BIOMETRIC_READY -> "Fingerprint / Face Ready"
    BiometricHardwareStatus.DEVICE_CREDENTIAL_ONLY -> "Device PIN / Pattern Ready"
    BiometricHardwareStatus.NOT_ENROLLED -> if (hasVaultPin) "Vault Backup PIN Active" else "Biometrics Not Enrolled"
    BiometricHardwareStatus.UNAVAILABLE -> if (hasVaultPin) "Vault Backup PIN Active" else "Hardware Unavailable"
  }

  fun createBiometricEnrollIntent(): Intent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
        putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, BIOMETRIC_STRONG or BIOMETRIC_WEAK)
      }
    } else {
      Intent(Settings.ACTION_SECURITY_SETTINGS)
    }
  }

  fun createLegacyDeviceCredentialIntent(
    context: Context,
    title: String,
    description: String,
  ): Intent? {
    val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return null
    @Suppress("DEPRECATION")
    return if (keyguard.isDeviceSecure) {
      keyguard.createConfirmDeviceCredentialIntent(title, description)
    } else {
      null
    }
  }

  fun authenticate(
    activity: FragmentActivity,
    request: BiometricPromptRequest,
    hasVaultPin: Boolean,
    onResult: (BiometricAuthResult) -> Unit,
  ) {
    val status = checkHardwareStatus(activity)
    when (status) {
      BiometricHardwareStatus.NOT_ENROLLED,
      BiometricHardwareStatus.UNAVAILABLE -> {
        if (hasVaultPin) {
          onResult(BiometricAuthResult.FallbackToPin)
        } else {
          onResult(
            BiometricAuthResult.Error(
              message = "No fingerprint, face unlock, or screen lock is enrolled on this device.",
              canRetry = false,
            )
          )
        }
        return
      }
      BiometricHardwareStatus.DEVICE_CREDENTIAL_ONLY -> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
          if (request.allowDeviceCredential) {
            onResult(BiometricAuthResult.NeedsLegacyDeviceCredential(request.title, request.subtitle))
          } else if (hasVaultPin) {
            onResult(BiometricAuthResult.FallbackToPin)
          } else {
            onResult(BiometricAuthResult.Error("Enroll fingerprint or face unlock to continue.", canRetry = false))
          }
          return
        }
      }
      BiometricHardwareStatus.BIOMETRIC_READY -> Unit
    }

    val executor = ContextCompat.getMainExecutor(activity)
    var settled = false
    val prompt = BiometricPrompt(
      activity,
      executor,
      object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
          if (settled) return
          settled = true
          val usedBio = result.authenticationType != BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL
          onResult(BiometricAuthResult.Success(usedBiometric = usedBio))
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
          if (settled) return
          settled = true
          when (errorCode) {
            BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
              if (hasVaultPin) onResult(BiometricAuthResult.FallbackToPin)
              else onResult(BiometricAuthResult.Cancelled)
            }
            BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_CANCELED -> {
              onResult(BiometricAuthResult.Cancelled)
            }
            BiometricPrompt.ERROR_NO_BIOMETRICS,
            BiometricPrompt.ERROR_HW_NOT_PRESENT,
            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> {
              if (hasVaultPin) onResult(BiometricAuthResult.FallbackToPin)
              else onResult(BiometricAuthResult.Error(errString.toString(), canRetry = false))
            }
            BiometricPrompt.ERROR_LOCKOUT,
            BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
              if (hasVaultPin) onResult(BiometricAuthResult.FallbackToPin)
              else onResult(BiometricAuthResult.Error(errString.toString(), canRetry = true))
            }
            else -> {
              onResult(BiometricAuthResult.Error(errString.toString(), canRetry = true))
            }
          }
        }

        override fun onAuthenticationFailed() {
          // System sheet remains open and displays shake/retry guidance automatically.
        }
      },
    )

    val builder = BiometricPrompt.PromptInfo.Builder()
      .setTitle(request.title)
      .setSubtitle(request.subtitle)
      .setDescription(request.description)
      .setConfirmationRequired(request.requireConfirmation)

    if (status == BiometricHardwareStatus.DEVICE_CREDENTIAL_ONLY) {
      // API 30+ guaranteed here because API < 30 returns above via NeedsLegacyDeviceCredential.
      builder.setAllowedAuthenticators(DEVICE_CREDENTIAL)
    } else if (hasVaultPin) {
      builder
        .setAllowedAuthenticators(BIOMETRIC_STRONG or BIOMETRIC_WEAK)
        .setNegativeButtonText("Use Vault PIN")
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && request.allowDeviceCredential) {
      builder.setAllowedAuthenticators(BIOMETRIC_STRONG or BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
    } else {
      builder
        .setAllowedAuthenticators(BIOMETRIC_STRONG or BIOMETRIC_WEAK)
        .setNegativeButtonText("Cancel")
    }

    runCatching {
      prompt.authenticate(builder.build())
    }.onFailure { err ->
      if (!settled) {
        settled = true
        if (hasVaultPin) {
          onResult(BiometricAuthResult.FallbackToPin)
        } else {
          onResult(BiometricAuthResult.Error(err.localizedMessage ?: "Biometric prompt failed", canRetry = true))
        }
      }
    }
  }
}
