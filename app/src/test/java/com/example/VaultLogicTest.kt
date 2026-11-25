package com.example

import com.example.security.InMemoryKeyringProvider
import com.example.security.PinCredentialHelper
import com.example.security.PlainOfflineTxPayload
import com.example.security.PureBase64
import com.example.security.VaultCryptoEngine
import com.example.ui.screens.matchesPairQuery
import com.example.util.ExpressionEvaluator
import com.example.util.NumberFormatter
import com.example.util.QrCodeGenerator
import com.example.util.WalletAddressHelper
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultLogicTest {

  @Test
  fun expressionEvaluator_handlesArithmeticPercentAndImplicitMultiply() {
    assertEquals(1669.5, ExpressionEvaluator.evaluate("(1250 + 340) × 1.05", false)!!, 0.001)
    assertEquals(50.0, ExpressionEvaluator.evaluate("200 ÷ 4", false)!!, 0.001)
    assertEquals(15.0, ExpressionEvaluator.evaluate("100 × 15%", false)!!, 0.001)
    assertEquals(25.0, ExpressionEvaluator.evaluate("100 × 15% + 10", false)!!, 0.001)
    assertEquals(1250.0, ExpressionEvaluator.evaluate("1250 +", true)!!, 0.001)
    assertEquals(16.0, ExpressionEvaluator.evaluate("2(5+3)", false)!!, 0.001)
    assertEquals(20.0, ExpressionEvaluator.evaluate("(4+6)2", false)!!, 0.001)
    assertEquals(16.0, ExpressionEvaluator.evaluate("2(5+3", true)!!, 0.001)
    assertEquals(-200.0, ExpressionEvaluator.evaluate("100×-2", false)!!, 0.001)
    assertEquals(-50.0, ExpressionEvaluator.evaluate("-50", false)!!, 0.001)
  }

  @Test
  fun numberFormatter_formatsSignedCurrencyAndTruncatesMaxWithoutRoundUp() {
    assertEquals("-$50", NumberFormatter.formatSignedCurrency("$", -50.0, calcStyle = true))
    assertEquals("-$50.00", NumberFormatter.formatSignedCurrency("$", -50.0, calcStyle = false, maxDecimals = 2))
    assertEquals("$0.00", NumberFormatter.formatSignedCurrency("$", 0.0, calcStyle = false, maxDecimals = 2))
    assertEquals("0.10599479", NumberFormatter.formatTruncatedInput(0.105994799, decimals = 8))
    assertEquals("0.105994", NumberFormatter.formatTruncatedInput(0.105994799, decimals = 6))
  }

  @Test
  fun matchesPairQuery_handlesNaturalLanguagePairQueries() {
    assertTrue(matchesPairQuery("USD", "NGN", "USD to Naira"))
    assertFalse(matchesPairQuery("USD", "GBP", "USD to Naira"))
    assertTrue(matchesPairQuery("USD", "GBP", "dollar to gdp"))
    assertTrue(matchesPairQuery("USD", "USD", "usd to usd"))
    assertTrue(matchesPairQuery("BTC", "NGN", "bitcoin / ngn"))
  }

  @Test
  fun qrCodeGenerator_buildsValidSquareMatrixWithDarkModule() {
    val matrix = QrCodeGenerator.generateMatrix("bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh")
    val n = matrix.size
    assertTrue(n >= 21)
    assertEquals(n, matrix[0].size)
    // Top-left finder pattern center is dark
    assertTrue(matrix[3][3])
    // ISO 18004 dark module at (x = 8, y = n - 8)
    assertTrue(matrix[n - 8][8])
  }

  @Test
  fun walletAddressHelper_generatesExpectedPrefixes() {
    assertTrue(WalletAddressHelper.deriveAddress("seed_1", "BTC").startsWith("bc1q"))
    assertTrue(WalletAddressHelper.deriveAddress("seed_1", "ETH").startsWith("0x"))
    assertTrue(WalletAddressHelper.deriveAddress("seed_1", "USDT").startsWith("T"))
    assertNotNull(WalletAddressHelper.generateTxHash("tx"))
  }

  @Test
  fun pureBase64_roundTripsAllByteLengths() {
    val rng = java.security.SecureRandom()
    for (len in listOf(0, 1, 2, 3, 4, 12, 32, 127)) {
      val input = ByteArray(len).also { rng.nextBytes(it) }
      val encoded = PureBase64.encode(input)
      val decoded = PureBase64.decode(encoded)
      assertArrayEquals("Mismatch for length $len", input, decoded)
    }
  }

  @Test
  fun vaultCryptoEngine_encryptsDecryptsAndDetectsTampering() {
    val engine = VaultCryptoEngine(InMemoryKeyringProvider())
    val ownerHash = engine.hashOwnerUid("user_uid_123")
    val payload = PlainOfflineTxPayload(
      id = "tx_offline_001",
      txType = "SEND",
      assetCode = "BTC",
      amount = 0.045,
      priceUsd = 96500.0,
      senderTag = "alice",
      recipientInput = "@bob_pay",
      counterparty = "@bob_pay",
      note = "Offline grocery settlement",
      txHash = "0xdeadbeef1234567890",
      kind = "OFFLINE_SEND",
      createdAtMs = 1_760_000_000_000L,
    )

    val env = engine.encryptTransaction(payload, ownerHash, VaultCryptoEngine.GENESIS_MAC)
    assertFalse(env.ciphertextBase64.contains("bob_pay"))
    assertFalse(env.ciphertextBase64.contains("0.045"))
    assertTrue(env.e2eeVoucher.startsWith("VFX1."))

    val decrypted = engine.decryptAndVerify(
      id = payload.id,
      ownerUidHash = ownerHash,
      txHash = payload.txHash,
      wrappedDekBlob = env.wrappedDekBlob,
      ivBase64 = env.ivBase64,
      ciphertextBase64 = env.ciphertextBase64,
      e2eeVoucher = env.e2eeVoucher,
      prevMacHex = VaultCryptoEngine.GENESIS_MAC,
      expectedPrevMacHex = VaultCryptoEngine.GENESIS_MAC,
      integrityMacHex = env.integrityMacHex,
      createdAtMs = payload.createdAtMs,
    ).getOrThrow()

    assertTrue(decrypted.isFullyVerified)
    assertEquals(payload, decrypted.payload)

    // E2EE voucher independent decryption & signature check
    val voucherPayload = engine.decryptE2eeVoucher(env.e2eeVoucher).getOrThrow()
    assertEquals(payload, voucherPayload)

    // Tamper 1: modify expectedPrevMacHex -> HMAC chain breaks
    val brokenChain = engine.decryptAndVerify(
      id = payload.id,
      ownerUidHash = ownerHash,
      txHash = payload.txHash,
      wrappedDekBlob = env.wrappedDekBlob,
      ivBase64 = env.ivBase64,
      ciphertextBase64 = env.ciphertextBase64,
      e2eeVoucher = env.e2eeVoucher,
      prevMacHex = VaultCryptoEngine.GENESIS_MAC,
      expectedPrevMacHex = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
      integrityMacHex = env.integrityMacHex,
      createdAtMs = payload.createdAtMs,
    ).getOrThrow()
    assertFalse(brokenChain.macValid)
    assertFalse(brokenChain.isFullyVerified)

    // Tamper 2: corrupt ciphertext -> AES-GCM auth tag fails
    val rawCipher = PureBase64.decode(env.ciphertextBase64)
    rawCipher[0] = (rawCipher[0].toInt() xor 0xFF).toByte()
    val tamperedCtResult = engine.decryptAndVerify(
      id = payload.id,
      ownerUidHash = ownerHash,
      txHash = payload.txHash,
      wrappedDekBlob = env.wrappedDekBlob,
      ivBase64 = env.ivBase64,
      ciphertextBase64 = PureBase64.encode(rawCipher),
      e2eeVoucher = env.e2eeVoucher,
      prevMacHex = VaultCryptoEngine.GENESIS_MAC,
      expectedPrevMacHex = VaultCryptoEngine.GENESIS_MAC,
      integrityMacHex = env.integrityMacHex,
      createdAtMs = payload.createdAtMs,
    )
    assertTrue(tamperedCtResult.isFailure)

    // Tamper 3: wrong owner AAD -> fails unwrap/GCM
    val wrongOwnerResult = engine.decryptAndVerify(
      id = payload.id,
      ownerUidHash = engine.hashOwnerUid("attacker_uid"),
      txHash = payload.txHash,
      wrappedDekBlob = env.wrappedDekBlob,
      ivBase64 = env.ivBase64,
      ciphertextBase64 = env.ciphertextBase64,
      e2eeVoucher = env.e2eeVoucher,
      prevMacHex = VaultCryptoEngine.GENESIS_MAC,
      expectedPrevMacHex = VaultCryptoEngine.GENESIS_MAC,
      integrityMacHex = env.integrityMacHex,
      createdAtMs = payload.createdAtMs,
    )
    assertTrue(wrongOwnerResult.isFailure)

    // Tamper 4: corrupt E2EE voucher signature -> fails verification
    val voucherParts = env.e2eeVoucher.split('.').toMutableList()
    val sigBytes = PureBase64.decode(voucherParts[4])
    sigBytes[sigBytes.lastIndex] = (sigBytes[sigBytes.lastIndex].toInt() xor 0x01).toByte()
    voucherParts[4] = PureBase64.encode(sigBytes)
    assertTrue(engine.decryptE2eeVoucher(voucherParts.joinToString(".")).isFailure)
  }

  @Test
  fun pinCredentialHelper_hashesWithUniqueSaltAndVerifiesConstantTime() {
    assertTrue(PinCredentialHelper.isValidPinFormat("482910"))
    assertFalse(PinCredentialHelper.isValidPinFormat("123"))
    assertFalse(PinCredentialHelper.isValidPinFormat("12ab56"))

    val hash1 = PinCredentialHelper.hashPin("482910")
    val hash2 = PinCredentialHelper.hashPin("482910")
    assertNotEquals(hash1, hash2)
    assertFalse(hash1.contains("482910"))

    assertTrue(PinCredentialHelper.verifyPin("482910", hash1))
    assertTrue(PinCredentialHelper.verifyPin("482910", hash2))
    assertFalse(PinCredentialHelper.verifyPin("482911", hash1))
    assertFalse(PinCredentialHelper.verifyPin("000000", "malformed:record"))
  }
}
