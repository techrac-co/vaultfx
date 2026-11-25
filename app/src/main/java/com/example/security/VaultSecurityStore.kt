package com.example.security

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

data class VaultSecuritySettings(
  val appLockEnabled: Boolean = true,
  val txConfirmationEnabled: Boolean = true,
  val hasVaultPin: Boolean = false,
  val failedPinAttempts: Int = 0,
  val pinLockoutRemainingMs: Long = 0L,
)

sealed interface PinCheckResult {
  data object Verified : PinCheckResult
  data class Invalid(val remainingAttempts: Int) : PinCheckResult
  data class LockedOut(val remainingSeconds: Int) : PinCheckResult
  data object NotConfigured : PinCheckResult
}

/**
 * Pure-JVM / API 24+ salted PBKDF2 PIN credential helper with constant-time verification.
 */
object PinCredentialHelper {
  private const val ITERATIONS = 60_000
  private const val KEY_LENGTH_BITS = 256
  private val secureRandom = SecureRandom()

  fun isValidPinFormat(pin: String): Boolean =
    pin.length in 4..8 && pin.all { it.isDigit() }

  fun hashPin(pin: String, salt: ByteArray = ByteArray(16).also { secureRandom.nextBytes(it) }): String {
    require(isValidPinFormat(pin)) { "Vault PIN must be 4 to 8 digits" }
    val (algoTag, factory) = resolveKeyFactory()
    val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
    val derived = try {
      factory.generateSecret(spec).encoded
    } finally {
      spec.clearPassword()
    }
    return "$algoTag:$ITERATIONS:${PureBase64.encode(salt)}:${PureBase64.encode(derived)}"
  }

  fun verifyPin(pin: String, storedRecord: String): Boolean {
    if (!isValidPinFormat(pin)) return false
    val parts = storedRecord.split(':')
    if (parts.size != 4) return false
    val algoTag = parts[0]
    val iterations = parts[1].toIntOrNull()?.takeIf { it in 10_000..500_000 } ?: return false
    val salt = runCatching { PureBase64.decode(parts[2]) }.getOrNull() ?: return false
    val expected = runCatching { PureBase64.decode(parts[3]) }.getOrNull() ?: return false

    val algoName = when (algoTag) {
      "pbkdf2_sha256" -> "PBKDF2WithHmacSHA256"
      "pbkdf2_sha1" -> "PBKDF2WithHmacSHA1"
      else -> return false
    }
    val factory = runCatching { SecretKeyFactory.getInstance(algoName) }.getOrNull() ?: return false
    val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, expected.size * 8)
    val actual = try {
      factory.generateSecret(spec).encoded
    } finally {
      spec.clearPassword()
    }
    return MessageDigest.isEqual(expected, actual)
  }

  private fun resolveKeyFactory(): Pair<String, SecretKeyFactory> {
    return runCatching {
      "pbkdf2_sha256" to SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    }.getOrElse {
      "pbkdf2_sha1" to SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
    }
  }
}

/**
 * Hardware-backed security preference and backup PIN store sealed inside vaultfx_secure_keyring
 * (excluded from cloud backups and device transfers via backup_rules.xml / data_extraction_rules.xml).
 */
class VaultSecurityStore(
  context: Context,
  private val keyringProvider: VaultKeyringProvider,
) {
  private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  fun ownerHashFor(userId: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
      .digest("VAULTFX_UID:$userId".toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
  }

  @Synchronized
  fun getSettings(userId: String, nowMs: Long = System.currentTimeMillis()): VaultSecuritySettings {
    val uidHash = ownerHashFor(userId)
    val appLock = readEncryptedFlag(uidHash, FIELD_APP_LOCK, defaultValue = true)
    val txConfirm = readEncryptedFlag(uidHash, FIELD_TX_AUTH, defaultValue = true)
    val hasPin = prefs.contains(keyFor(uidHash, FIELD_PIN_HASH))
    val attempts = prefs.getInt(keyFor(uidHash, FIELD_PIN_ATTEMPTS), 0)
    val lockoutUntil = prefs.getLong(keyFor(uidHash, FIELD_PIN_LOCKOUT_UNTIL), 0L)
    val remainingLockout = (lockoutUntil - nowMs).coerceAtLeast(0L)
    return VaultSecuritySettings(
      appLockEnabled = appLock,
      txConfirmationEnabled = txConfirm,
      hasVaultPin = hasPin,
      failedPinAttempts = attempts,
      pinLockoutRemainingMs = remainingLockout,
    )
  }

  @Synchronized
  fun setAppLockEnabled(userId: String, enabled: Boolean) {
    val uidHash = ownerHashFor(userId)
    writeEncryptedString(uidHash, FIELD_APP_LOCK, if (enabled) "1" else "0")
  }

  @Synchronized
  fun setTxConfirmationEnabled(userId: String, enabled: Boolean) {
    val uidHash = ownerHashFor(userId)
    writeEncryptedString(uidHash, FIELD_TX_AUTH, if (enabled) "1" else "0")
  }

  @Synchronized
  fun saveVaultPin(userId: String, pin: String) {
    val uidHash = ownerHashFor(userId)
    val saltedRecord = PinCredentialHelper.hashPin(pin)
    writeEncryptedString(uidHash, FIELD_PIN_HASH, saltedRecord)
    prefs.edit()
      .remove(keyFor(uidHash, FIELD_PIN_ATTEMPTS))
      .remove(keyFor(uidHash, FIELD_PIN_LOCKOUT_UNTIL))
      .apply()
  }

  @Synchronized
  fun clearVaultPin(userId: String) {
    val uidHash = ownerHashFor(userId)
    prefs.edit()
      .remove(keyFor(uidHash, FIELD_PIN_HASH))
      .remove(keyFor(uidHash, FIELD_PIN_ATTEMPTS))
      .remove(keyFor(uidHash, FIELD_PIN_LOCKOUT_UNTIL))
      .apply()
  }

  @Synchronized
  fun verifyVaultPin(userId: String, pin: String, nowMs: Long = System.currentTimeMillis()): PinCheckResult {
    val uidHash = ownerHashFor(userId)
    val lockoutUntil = prefs.getLong(keyFor(uidHash, FIELD_PIN_LOCKOUT_UNTIL), 0L)
    if (nowMs < lockoutUntil) {
      val secs = ((lockoutUntil - nowMs + 999L) / 1000L).toInt().coerceAtLeast(1)
      return PinCheckResult.LockedOut(secs)
    }

    val storedRecord = readEncryptedString(uidHash, FIELD_PIN_HASH)
      ?: return PinCheckResult.NotConfigured

    return if (PinCredentialHelper.verifyPin(pin, storedRecord)) {
      prefs.edit()
        .putInt(keyFor(uidHash, FIELD_PIN_ATTEMPTS), 0)
        .putLong(keyFor(uidHash, FIELD_PIN_LOCKOUT_UNTIL), 0L)
        .apply()
      PinCheckResult.Verified
    } else {
      val nextAttempts = prefs.getInt(keyFor(uidHash, FIELD_PIN_ATTEMPTS), 0) + 1
      if (nextAttempts >= MAX_PIN_ATTEMPTS) {
        prefs.edit()
          .putInt(keyFor(uidHash, FIELD_PIN_ATTEMPTS), 0)
          .putLong(keyFor(uidHash, FIELD_PIN_LOCKOUT_UNTIL), nowMs + LOCKOUT_DURATION_MS)
          .apply()
        PinCheckResult.LockedOut((LOCKOUT_DURATION_MS / 1000L).toInt())
      } else {
        prefs.edit().putInt(keyFor(uidHash, FIELD_PIN_ATTEMPTS), nextAttempts).apply()
        PinCheckResult.Invalid(MAX_PIN_ATTEMPTS - nextAttempts)
      }
    }
  }

  private fun readEncryptedFlag(uidHash: String, field: String, defaultValue: Boolean): Boolean {
    val key = keyFor(uidHash, field)
    if (!prefs.contains(key)) return defaultValue
    val decrypted = readEncryptedString(uidHash, field) ?: return defaultValue
    return decrypted == "1"
  }

  private fun writeEncryptedString(uidHash: String, field: String, value: String) {
    val masterKey: SecretKey = keyringProvider.getOrCreateMasterAesKey()
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, masterKey)
    cipher.updateAAD(aadFor(uidHash, field))
    val iv = cipher.iv
    val ct = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
    val blob = "${PureBase64.encode(iv)}.${PureBase64.encode(ct)}"
    prefs.edit().putString(keyFor(uidHash, field), blob).apply()
  }

  private fun readEncryptedString(uidHash: String, field: String): String? {
    val blob = prefs.getString(keyFor(uidHash, field), null) ?: return null
    return runCatching {
      val parts = blob.split('.')
      if (parts.size != 2) return null
      val iv = PureBase64.decode(parts[0])
      val ct = PureBase64.decode(parts[1])
      val masterKey = keyringProvider.getOrCreateMasterAesKey()
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(128, iv))
      cipher.updateAAD(aadFor(uidHash, field))
      String(cipher.doFinal(ct), Charsets.UTF_8)
    }.getOrNull()
  }

  private fun keyFor(uidHash: String, field: String): String = "sec_${uidHash.take(24)}_$field"
  private fun aadFor(uidHash: String, field: String): ByteArray = "VAULTFX_SEC|$uidHash|$field|V1".toByteArray(Charsets.UTF_8)

  private companion object {
    const val PREFS_NAME = "vaultfx_secure_keyring"
    const val FIELD_APP_LOCK = "app_lock_v1"
    const val FIELD_TX_AUTH = "tx_auth_v1"
    const val FIELD_PIN_HASH = "vault_pin_v1"
    const val FIELD_PIN_ATTEMPTS = "pin_attempts_v1"
    const val FIELD_PIN_LOCKOUT_UNTIL = "pin_lockout_until_v1"
    const val MAX_PIN_ATTEMPTS = 5
    const val LOCKOUT_DURATION_MS = 30_000L
  }
}
