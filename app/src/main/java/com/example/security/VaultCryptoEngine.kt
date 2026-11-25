package com.example.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@JsonClass(generateAdapter = true)
data class PlainOfflineTxPayload(
  val id: String,
  val txType: String,
  val assetCode: String,
  val amount: Double,
  val priceUsd: Double,
  val senderTag: String,
  val recipientInput: String,
  val counterparty: String,
  val note: String?,
  val txHash: String,
  val kind: String,
  val createdAtMs: Long,
)

data class EncryptedEnvelopeResult(
  val wrappedDekBlob: String,
  val ivBase64: String,
  val ciphertextBase64: String,
  val e2eeVoucher: String,
  val integrityMacHex: String,
)

data class DecryptedEnvelopeResult(
  val payload: PlainOfflineTxPayload,
  val macValid: Boolean,
  val e2eeSignatureValid: Boolean,
  val gcmValid: Boolean,
) {
  val isFullyVerified: Boolean
    get() = macValid && e2eeSignatureValid && gcmValid
}

interface VaultKeyringProvider {
  fun getOrCreateMasterAesKey(): SecretKey
  fun getOrCreateHmacChainKey(masterKey: SecretKey): SecretKey
  fun getOrCreateSigningKeyPair(masterKey: SecretKey): KeyPair
}

class AndroidKeystoreKeyringProvider(context: Context) : VaultKeyringProvider {
  private val appContext = context.applicationContext
  private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  private val secureRandom = SecureRandom()

  @Synchronized
  override fun getOrCreateMasterAesKey(): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    val existing = keyStore.getKey(MASTER_AES_ALIAS, null) as? SecretKey
    if (existing != null) return existing

    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    val spec = KeyGenParameterSpec.Builder(
      MASTER_AES_ALIAS,
      KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
    )
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
      .setKeySize(256)
      .setRandomizedEncryptionRequired(true)
      .build()
    generator.init(spec)
    return generator.generateKey()
  }

  @Synchronized
  override fun getOrCreateHmacChainKey(masterKey: SecretKey): SecretKey {
    val wrapped = prefs.getString(KEY_WRAPPED_HMAC, null)
    if (wrapped != null) {
      val unwrapped = runCatching { unwrapBytes(masterKey, wrapped, HMAC_AAD) }.getOrNull()
      if (unwrapped != null && unwrapped.size == 32) {
        return SecretKeySpec(unwrapped, "HmacSHA256")
      }
    }
    val rawKey = ByteArray(32).also { secureRandom.nextBytes(it) }
    val newWrapped = wrapBytes(masterKey, rawKey, HMAC_AAD)
    prefs.edit().putString(KEY_WRAPPED_HMAC, newWrapped).apply()
    return SecretKeySpec(rawKey, "HmacSHA256")
  }

  @Synchronized
  override fun getOrCreateSigningKeyPair(masterKey: SecretKey): KeyPair {
    val wrappedPriv = prefs.getString(KEY_WRAPPED_EC_PRIV, null)
    val pubB64 = prefs.getString(KEY_EC_PUB, null)
    if (wrappedPriv != null && pubB64 != null) {
      val restored = runCatching {
        val privBytes = unwrapBytes(masterKey, wrappedPriv, EC_AAD)
        val pubBytes = PureBase64.decode(pubB64)
        val kf = KeyFactory.getInstance("EC")
        val priv: PrivateKey = kf.generatePrivate(PKCS8EncodedKeySpec(privBytes))
        val pub: PublicKey = kf.generatePublic(X509EncodedKeySpec(pubBytes))
        KeyPair(pub, priv)
      }.getOrNull()
      if (restored != null) return restored
    }

    val kpg = KeyPairGenerator.getInstance("EC")
    kpg.initialize(ECGenParameterSpec("secp256r1"), secureRandom)
    val pair = kpg.generateKeyPair()
    val encodedPriv = wrapBytes(masterKey, pair.private.encoded, EC_AAD)
    val encodedPub = PureBase64.encode(pair.public.encoded)
    prefs.edit()
      .putString(KEY_WRAPPED_EC_PRIV, encodedPriv)
      .putString(KEY_EC_PUB, encodedPub)
      .apply()
    return pair
  }

  private fun wrapBytes(masterKey: SecretKey, plainBytes: ByteArray, aad: String): String {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, masterKey)
    cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
    val iv = cipher.iv
    val ct = cipher.doFinal(plainBytes)
    return "${PureBase64.encode(iv)}.${PureBase64.encode(ct)}"
  }

  private fun unwrapBytes(masterKey: SecretKey, blob: String, aad: String): ByteArray {
    val parts = blob.split('.')
    require(parts.size == 2) { "Malformed wrapped key blob" }
    val iv = PureBase64.decode(parts[0])
    val ct = PureBase64.decode(parts[1])
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(128, iv))
    cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
    return cipher.doFinal(ct)
  }

  private companion object {
    const val ANDROID_KEYSTORE = "AndroidKeyStore"
    const val PREFS_NAME = "vaultfx_secure_keyring"
    const val MASTER_AES_ALIAS = "vaultfx_keystore_master_aes_v1"
    const val KEY_WRAPPED_HMAC = "wrapped_hmac_v1"
    const val KEY_WRAPPED_EC_PRIV = "wrapped_ec_priv_v1"
    const val KEY_EC_PUB = "ec_pub_v1"
    const val HMAC_AAD = "VAULTFX|HMAC_KEY|V1"
    const val EC_AAD = "VAULTFX|EC_PRIV|V1"
  }
}

class InMemoryKeyringProvider : VaultKeyringProvider {
  private val secureRandom = SecureRandom()
  private val masterKey: SecretKey by lazy {
    val raw = ByteArray(32).also { secureRandom.nextBytes(it) }
    SecretKeySpec(raw, "AES")
  }
  private val hmacKey: SecretKey by lazy {
    val raw = ByteArray(32).also { secureRandom.nextBytes(it) }
    SecretKeySpec(raw, "HmacSHA256")
  }
  private val ecPair: KeyPair by lazy {
    val kpg = KeyPairGenerator.getInstance("EC")
    kpg.initialize(ECGenParameterSpec("secp256r1"), secureRandom)
    kpg.generateKeyPair()
  }

  override fun getOrCreateMasterAesKey(): SecretKey = masterKey
  override fun getOrCreateHmacChainKey(masterKey: SecretKey): SecretKey = hmacKey
  override fun getOrCreateSigningKeyPair(masterKey: SecretKey): KeyPair = ecPair
}

class VaultCryptoEngine(
  private val keyringProvider: VaultKeyringProvider,
) {
  private val secureRandom = SecureRandom()
  private val moshi: Moshi = Moshi.Builder().build()
  private val payloadAdapter = moshi.adapter(PlainOfflineTxPayload::class.java)

  fun hashOwnerUid(userId: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
      .digest("VAULTFX_UID:$userId".toByteArray(Charsets.UTF_8))
    return digest.toHex()
  }

  fun publicKeyFingerprint(): String {
    val master = keyringProvider.getOrCreateMasterAesKey()
    val ecPair = keyringProvider.getOrCreateSigningKeyPair(master)
    val hash = MessageDigest.getInstance("SHA-256").digest(ecPair.public.encoded)
    return hash.take(8).toByteArray().toHex().chunked(4).joinToString("-")
  }

  fun encryptTransaction(
    payload: PlainOfflineTxPayload,
    ownerUidHash: String,
    prevMacHex: String,
  ): EncryptedEnvelopeResult {
    val masterKey = keyringProvider.getOrCreateMasterAesKey()
    val hmacKey = keyringProvider.getOrCreateHmacChainKey(masterKey)
    val ecPair = keyringProvider.getOrCreateSigningKeyPair(masterKey)

    val jsonBytes = payloadAdapter.toJson(payload).toByteArray(Charsets.UTF_8)

    // Layer 1 & 2: Generate unique ephemeral 256-bit DEK for this single record
    val dekBytes = ByteArray(32).also { secureRandom.nextBytes(it) }
    val dekKey = SecretKeySpec(dekBytes, "AES")

    val payloadIv = ByteArray(GCM_IV_BYTES).also { secureRandom.nextBytes(it) }
    val payloadAad = buildPayloadAad(payload.id, ownerUidHash, payload.txHash)
    val payloadCipher = Cipher.getInstance("AES/GCM/NoPadding")
    payloadCipher.init(Cipher.ENCRYPT_MODE, dekKey, GCMParameterSpec(128, payloadIv))
    payloadCipher.updateAAD(payloadAad)
    val ciphertext = payloadCipher.doFinal(jsonBytes)

    // Wrap the 256-bit DEK using the hardware-backed Master Key
    val wrapAad = buildDekAad(payload.id, ownerUidHash)
    val wrapCipher = Cipher.getInstance("AES/GCM/NoPadding")
    wrapCipher.init(Cipher.ENCRYPT_MODE, masterKey)
    wrapCipher.updateAAD(wrapAad)
    val wrapIv = wrapCipher.iv
    val wrappedDek = wrapCipher.doFinal(dekBytes)
    val wrappedDekBlob = "${PureBase64.encode(wrapIv)}.${PureBase64.encode(wrappedDek)}"

    // Layer 3: End-to-End Encrypted Voucher (ECDH P-256 + HKDF-SHA256 + AES-256-GCM + ECDSA-SHA256)
    val e2eeVoucher = buildE2eeVoucher(
      jsonBytes = jsonBytes,
      recipientPubKey = ecPair.public,
      signingPrivKey = ecPair.private,
    )

    val ivBase64 = PureBase64.encode(payloadIv)
    val ciphertextBase64 = PureBase64.encode(ciphertext)

    // Layer 4: Tamper-evident HMAC-SHA256 chain link
    val integrityMacHex = computeChainMacHex(
      hmacKey = hmacKey,
      prevMacHex = prevMacHex,
      id = payload.id,
      ownerUidHash = ownerUidHash,
      txHash = payload.txHash,
      ivBase64 = ivBase64,
      ciphertextBase64 = ciphertextBase64,
      wrappedDekBlob = wrappedDekBlob,
      createdAtMs = payload.createdAtMs,
    )

    return EncryptedEnvelopeResult(
      wrappedDekBlob = wrappedDekBlob,
      ivBase64 = ivBase64,
      ciphertextBase64 = ciphertextBase64,
      e2eeVoucher = e2eeVoucher,
      integrityMacHex = integrityMacHex,
    )
  }

  fun decryptAndVerify(
    id: String,
    ownerUidHash: String,
    txHash: String,
    wrappedDekBlob: String,
    ivBase64: String,
    ciphertextBase64: String,
    e2eeVoucher: String,
    prevMacHex: String,
    expectedPrevMacHex: String,
    integrityMacHex: String,
    createdAtMs: Long,
  ): Result<DecryptedEnvelopeResult> {
    return runCatching {
      val masterKey = keyringProvider.getOrCreateMasterAesKey()
      val hmacKey = keyringProvider.getOrCreateHmacChainKey(masterKey)
      val ecPair = keyringProvider.getOrCreateSigningKeyPair(masterKey)

      // 1. Verify HMAC chain
      val computedMac = computeChainMacHex(
        hmacKey = hmacKey,
        prevMacHex = prevMacHex,
        id = id,
        ownerUidHash = ownerUidHash,
        txHash = txHash,
        ivBase64 = ivBase64,
        ciphertextBase64 = ciphertextBase64,
        wrappedDekBlob = wrappedDekBlob,
        createdAtMs = createdAtMs,
      )
      val macValid = prevMacHex == expectedPrevMacHex &&
        MessageDigest.isEqual(
          computedMac.toByteArray(Charsets.UTF_8),
          integrityMacHex.toByteArray(Charsets.UTF_8),
        )

      // 2. Unwrap per-record 256-bit DEK with hardware Master Key
      val wrapParts = wrappedDekBlob.split('.')
      require(wrapParts.size == 2) { "Corrupted wrapped DEK" }
      val wrapIv = PureBase64.decode(wrapParts[0])
      val wrappedDek = PureBase64.decode(wrapParts[1])
      val unwrapCipher = Cipher.getInstance("AES/GCM/NoPadding")
      unwrapCipher.init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(128, wrapIv))
      unwrapCipher.updateAAD(buildDekAad(id, ownerUidHash))
      val dekBytes = unwrapCipher.doFinal(wrappedDek)
      val dekKey = SecretKeySpec(dekBytes, "AES")

      // 3. Decrypt payload with AES-256-GCM + AAD verification
      val payloadIv = PureBase64.decode(ivBase64)
      val ciphertext = PureBase64.decode(ciphertextBase64)
      val payloadCipher = Cipher.getInstance("AES/GCM/NoPadding")
      payloadCipher.init(Cipher.DECRYPT_MODE, dekKey, GCMParameterSpec(128, payloadIv))
      payloadCipher.updateAAD(buildPayloadAad(id, ownerUidHash, txHash))
      val plainBytes = payloadCipher.doFinal(ciphertext)

      val payload = payloadAdapter.fromJson(String(plainBytes, Charsets.UTF_8))
        ?: error("Invalid decrypted transaction payload")

      // 4. Verify E2EE ECDSA P-256 voucher signature
      val sigValid = verifyE2eeVoucherSignature(e2eeVoucher, ecPair.public)

      DecryptedEnvelopeResult(
        payload = payload,
        macValid = macValid,
        e2eeSignatureValid = sigValid,
        gcmValid = true,
      )
    }
  }

  fun decryptE2eeVoucher(voucher: String): Result<PlainOfflineTxPayload> = runCatching {
    val parts = voucher.split('.')
    require(parts.size == 5 && parts[0] == VOUCHER_PREFIX) { "Invalid VaultFX E2EE voucher format" }
    val masterKey = keyringProvider.getOrCreateMasterAesKey()
    val ecPair = keyringProvider.getOrCreateSigningKeyPair(masterKey)

    require(verifyE2eeVoucherSignature(voucher, ecPair.public)) {
      "E2EE voucher ECDSA signature verification failed"
    }

    val ephPubBytes = PureBase64.decode(parts[1])
    val iv = PureBase64.decode(parts[2])
    val cipherBytes = PureBase64.decode(parts[3])

    val ephPubKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(ephPubBytes))
    val agreement = KeyAgreement.getInstance("ECDH")
    agreement.init(ecPair.private)
    agreement.doPhase(ephPubKey, true)
    val sharedSecret = agreement.generateSecret()

    val aesKeyBytes = hkdfSha256(
      ikm = sharedSecret,
      salt = ephPubBytes,
      info = E2EE_HKDF_INFO,
      outputLength = 32,
    )
    val aesKey = SecretKeySpec(aesKeyBytes, "AES")
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
    val plainBytes = cipher.doFinal(cipherBytes)
    payloadAdapter.fromJson(String(plainBytes, Charsets.UTF_8))
      ?: error("Malformed E2EE payload")
  }

  private fun buildE2eeVoucher(
    jsonBytes: ByteArray,
    recipientPubKey: PublicKey,
    signingPrivKey: PrivateKey,
  ): String {
    val kpg = KeyPairGenerator.getInstance("EC")
    kpg.initialize(ECGenParameterSpec("secp256r1"), secureRandom)
    val ephPair = kpg.generateKeyPair()
    val ephPubBytes = ephPair.public.encoded

    val agreement = KeyAgreement.getInstance("ECDH")
    agreement.init(ephPair.private)
    agreement.doPhase(recipientPubKey, true)
    val sharedSecret = agreement.generateSecret()

    val sessionAesBytes = hkdfSha256(
      ikm = sharedSecret,
      salt = ephPubBytes,
      info = E2EE_HKDF_INFO,
      outputLength = 32,
    )
    val sessionKey = SecretKeySpec(sessionAesBytes, "AES")
    val iv = ByteArray(GCM_IV_BYTES).also { secureRandom.nextBytes(it) }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, sessionKey, GCMParameterSpec(128, iv))
    val encrypted = cipher.doFinal(jsonBytes)

    val signedBlob = buildVoucherSigningInput(ephPubBytes, iv, encrypted)
    val signature = Signature.getInstance("SHA256withECDSA").apply {
      initSign(signingPrivKey, secureRandom)
      update(signedBlob)
    }.sign()

    return listOf(
      VOUCHER_PREFIX,
      PureBase64.encode(ephPubBytes),
      PureBase64.encode(iv),
      PureBase64.encode(encrypted),
      PureBase64.encode(signature),
    ).joinToString(".")
  }

  private fun verifyE2eeVoucherSignature(voucher: String, signerPubKey: PublicKey): Boolean {
    return runCatching {
      val parts = voucher.split('.')
      if (parts.size != 5 || parts[0] != VOUCHER_PREFIX) return false
      val ephPubBytes = PureBase64.decode(parts[1])
      val iv = PureBase64.decode(parts[2])
      val cipherBytes = PureBase64.decode(parts[3])
      val sigBytes = PureBase64.decode(parts[4])

      val signedPrefix = buildVoucherSigningInput(ephPubBytes, iv, cipherBytes)
      Signature.getInstance("SHA256withECDSA").run {
        initVerify(signerPubKey)
        update(signedPrefix)
        verify(sigBytes)
      }
    }.getOrDefault(false)
  }

  private fun buildVoucherSigningInput(
    ephPub: ByteArray,
    iv: ByteArray,
    ciphertext: ByteArray,
  ): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(VOUCHER_PREFIX.toByteArray(Charsets.UTF_8))
    digest.update(ephPub)
    digest.update(iv)
    digest.update(ciphertext)
    return digest.digest()
  }

  private fun computeChainMacHex(
    hmacKey: SecretKey,
    prevMacHex: String,
    id: String,
    ownerUidHash: String,
    txHash: String,
    ivBase64: String,
    ciphertextBase64: String,
    wrappedDekBlob: String,
    createdAtMs: Long,
  ): String {
    val input = "$prevMacHex|$id|$ownerUidHash|$txHash|$ivBase64|$ciphertextBase64|$wrappedDekBlob|$createdAtMs"
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(hmacKey)
    return mac.doFinal(input.toByteArray(Charsets.UTF_8)).toHex()
  }

  private fun hkdfSha256(
    ikm: ByteArray,
    salt: ByteArray,
    info: ByteArray,
    outputLength: Int,
  ): ByteArray {
    val extractMac = Mac.getInstance("HmacSHA256")
    extractMac.init(SecretKeySpec(salt, "HmacSHA256"))
    val prk = extractMac.doFinal(ikm)

    val expandMac = Mac.getInstance("HmacSHA256")
    expandMac.init(SecretKeySpec(prk, "HmacSHA256"))
    val okm = ByteArray(outputLength)
    var t = ByteArray(0)
    var offset = 0
    var counter = 1
    while (offset < outputLength) {
      expandMac.reset()
      expandMac.update(t)
      expandMac.update(info)
      expandMac.update(counter.toByte())
      t = expandMac.doFinal()
      val copyLen = minOf(t.size, outputLength - offset)
      System.arraycopy(t, 0, okm, offset, copyLen)
      offset += copyLen
      counter++
    }
    return okm
  }

  private fun buildPayloadAad(id: String, ownerUidHash: String, txHash: String): ByteArray =
    "$id|$ownerUidHash|$txHash|PAYLOAD_V1".toByteArray(Charsets.UTF_8)

  private fun buildDekAad(id: String, ownerUidHash: String): ByteArray =
    "DEK|$id|$ownerUidHash|V1".toByteArray(Charsets.UTF_8)

  private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

  companion object {
    const val GENESIS_MAC = "0000000000000000000000000000000000000000000000000000000000000000"
    const val CIPHER_SUITE_SUMMARY = "AES-256-GCM + 256b DEK + ECDH P-256 + ECDSA + HMAC-SHA256"
    private const val GCM_IV_BYTES = 12
    private const val VOUCHER_PREFIX = "VFX1"
    private val E2EE_HKDF_INFO = "VaultFX-Offline-E2EE-v1".toByteArray(Charsets.UTF_8)
  }
}

/**
 * Pure Kotlin URL-safe unpadded Base64 codec so cryptography works identically on Android API 24+
 * and local JVM unit tests without Android framework mocks or API 26 dependencies.
 */
object PureBase64 {
  private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
  private val DECODE_TABLE = IntArray(128) { -1 }.apply {
    ALPHABET.forEachIndexed { idx, c -> this[c.code] = idx }
    this['+'.code] = 62
    this['/'.code] = 63
  }

  fun encode(bytes: ByteArray): String {
    if (bytes.isEmpty()) return ""
    val out = StringBuilder((bytes.size * 4 + 2) / 3)
    var i = 0
    while (i < bytes.size) {
      val b0 = bytes[i].toInt() and 0xFF
      val hasB1 = i + 1 < bytes.size
      val hasB2 = i + 2 < bytes.size
      val b1 = if (hasB1) bytes[i + 1].toInt() and 0xFF else 0
      val b2 = if (hasB2) bytes[i + 2].toInt() and 0xFF else 0
      val triple = (b0 shl 16) or (b1 shl 8) or b2
      out.append(ALPHABET[(triple ushr 18) and 0x3F])
      out.append(ALPHABET[(triple ushr 12) and 0x3F])
      if (hasB1) out.append(ALPHABET[(triple ushr 6) and 0x3F])
      if (hasB2) out.append(ALPHABET[triple and 0x3F])
      i += 3
    }
    return out.toString()
  }

  fun decode(text: String): ByteArray {
    val clean = text.trimEnd('=')
    if (clean.isEmpty()) return ByteArray(0)
    val out = ByteArray((clean.length * 3) / 4)
    var outIdx = 0
    var buffer = 0
    var bitsLeft = 0
    for (ch in clean) {
      val code = ch.code
      val value = if (code in 0..127) DECODE_TABLE[code] else -1
      require(value >= 0) { "Invalid Base64 character: $ch" }
      buffer = (buffer shl 6) or value
      bitsLeft += 6
      if (bitsLeft >= 8) {
        bitsLeft -= 8
        out[outIdx++] = ((buffer shr bitsLeft) and 0xFF).toByte()
      }
    }
    return out.copyOf(outIdx)
  }
}
