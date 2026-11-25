package com.example

import com.example.ui.screens.matchesPairQuery
import com.example.util.ExpressionEvaluator
import com.example.util.NumberFormatter
import com.example.util.QrCodeGenerator
import com.example.util.WalletAddressHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
