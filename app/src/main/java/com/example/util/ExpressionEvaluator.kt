package com.example.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

object ExpressionEvaluator {

  /**
   * Evaluates an arithmetic expression string like "(1250 + 340) × 1.05" or "2(50+25)".
   * Supports implicit multiplication, postfix %, and live partial-expression preview.
   */
  fun evaluate(rawExpression: String, allowTrailingOperator: Boolean = true): Double? {
    if (rawExpression.isBlank()) return null
    var normalized = rawExpression
      .replace("×", "*")
      .replace("÷", "/")
      .replace("−", "-")
      .replace(",", "")
      .replace(" ", "")

    if (allowTrailingOperator) {
      normalized = trimTrailingIncomplete(normalized)
    }
    if (normalized.isEmpty()) return null

    if (allowTrailingOperator) {
      val openCount = normalized.count { it == '(' }
      val closeCount = normalized.count { it == ')' }
      if (openCount > closeCount) {
        normalized += ")".repeat(openCount - closeCount)
      }
    }

    normalized = insertImplicitMultiplication(normalized)

    return try {
      val parser = Parser(normalized)
      val value = parser.parseExpression()
      if (parser.pos < normalized.length) null
      else if (value.isNaN() || value.isInfinite()) null
      else value
    } catch (_: Exception) {
      null
    }
  }

  private fun insertImplicitMultiplication(s: String): String {
    if (s.length < 2) return s
    val out = StringBuilder(s.length + 4)
    for (i in s.indices) {
      val c = s[i]
      if (i > 0) {
        val prev = s[i - 1]
        val prevIsValueEnd = prev.isDigit() || prev == ')' || prev == '%'
        val currIsGroupStart = c == '('
        val prevClosesGroupOrPct = prev == ')' || prev == '%'
        val currIsNumberStart = c.isDigit() || c == '.'
        if ((prevIsValueEnd && currIsGroupStart) || (prevClosesGroupOrPct && currIsNumberStart)) {
          out.append('*')
        }
      }
      out.append(c)
    }
    return out.toString()
  }

  private fun trimTrailingIncomplete(expr: String): String {
    var s = expr
    while (s.isNotEmpty() && (s.last() in charArrayOf('+', '-', '*', '/', '.', '('))) {
      s = s.dropLast(1)
    }
    return s
  }

  private class Parser(private val input: String) {
    var pos: Int = 0

    fun parseExpression(): Double {
      var x = parseTerm()
      while (pos < input.length) {
        when (input[pos]) {
          '+' -> {
            pos++
            x += parseTerm()
          }
          '-' -> {
            pos++
            x -= parseTerm()
          }
          else -> return x
        }
      }
      return x
    }

    private fun parseTerm(): Double {
      var x = parseFactor()
      while (pos < input.length) {
        when (input[pos]) {
          '*' -> {
            pos++
            x *= parseFactor()
          }
          '/' -> {
            pos++
            val denom = parseFactor()
            if (denom == 0.0) throw ArithmeticException("Division by zero")
            x /= denom
          }
          else -> return x
        }
      }
      return x
    }

    private fun parseFactor(): Double {
      if (pos < input.length && input[pos] == '+') {
        pos++
        return parseFactor()
      }
      if (pos < input.length && input[pos] == '-') {
        pos++
        return -parseFactor()
      }

      var x: Double
      val startPos = pos
      if (pos < input.length && input[pos] == '(') {
        pos++
        x = parseExpression()
        if (pos < input.length && input[pos] == ')') {
          pos++
        } else {
          throw IllegalArgumentException("Unclosed parenthesis")
        }
      } else if (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) {
        while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) {
          pos++
        }
        val numStr = input.substring(startPos, pos)
        x = numStr.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid number: $numStr")
      } else {
        throw IllegalArgumentException("Unexpected character at $pos")
      }

      while (pos < input.length && input[pos] == '%') {
        pos++
        x /= 100.0
      }
      return x
    }
  }
}

object NumberFormatter {
  private val symbols = DecimalFormatSymbols(Locale.US)

  fun formatCalcDisplay(value: Double): String {
    if (value.isNaN() || value.isInfinite()) return "Error"
    if (abs(value) < 1e-12) return "0"
    val absVal = abs(value)
    val pattern = when {
      absVal >= 1_000_000_000_000.0 -> "0.####E0"
      absVal >= 1.0 -> "#,##0.######"
      else -> "0.########"
    }
    return DecimalFormat(pattern, symbols).format(value)
  }

  fun formatSignedCurrency(
    symbol: String,
    value: Double,
    calcStyle: Boolean = false,
    maxDecimals: Int = 2,
  ): String {
    if (value.isNaN() || value.isInfinite()) return "${symbol}0"
    if (abs(value) < 1e-12) return if (calcStyle) "${symbol}0" else "${symbol}0.00"
    val negative = value < 0.0
    val formattedAbs = if (calcStyle) {
      formatCalcDisplay(abs(value))
    } else {
      formatAsset(abs(value), maxDecimals)
    }
    return if (negative) "-$symbol$formattedAbs" else "$symbol$formattedAbs"
  }

  fun formatTruncatedInput(value: Double, decimals: Int = 8): String {
    if (!value.isFinite() || value <= 0.0) return "0"
    return try {
      BigDecimal.valueOf(value)
        .setScale(decimals.coerceIn(0, 8), RoundingMode.DOWN)
        .stripTrailingZeros()
        .toPlainString()
    } catch (_: Exception) {
      "0"
    }
  }

  fun formatAsset(amount: Double, maxDecimals: Int): String {
    if (amount.isNaN() || amount.isInfinite()) return "0"
    val absVal = abs(amount)
    val effectiveDecimals = when {
      absVal == 0.0 -> 2
      absVal >= 1000.0 -> minOf(maxDecimals, 2)
      absVal >= 1.0 -> minOf(maxDecimals, 4)
      else -> maxDecimals.coerceAtLeast(4)
    }
    val pattern = buildString {
      append("#,##0")
      if (effectiveDecimals > 0) {
        append(".")
        repeat(minOf(2, effectiveDecimals)) { append("0") }
        repeat((effectiveDecimals - 2).coerceAtLeast(0)) { append("#") }
      }
    }
    return DecimalFormat(pattern, symbols).format(amount)
  }

  fun formatRate(rate: Double): String {
    if (rate.isNaN() || rate.isInfinite()) return "0.00"
    val absVal = abs(rate)
    val pattern = when {
      absVal >= 1000.0 -> "#,##0.00"
      absVal >= 1.0 -> "#,##0.00##"
      absVal >= 0.01 -> "0.0000"
      else -> "0.000000"
    }
    return DecimalFormat(pattern, symbols).format(rate)
  }
}
