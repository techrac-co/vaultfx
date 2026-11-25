package com.example.util

/**
 * Pure-Kotlin ISO/IEC 18004 QR Code matrix generator (Byte mode, ECC-L, Versions 1..4).
 * Supports payloads up to 78 UTF-8 bytes (sufficient for wallet addresses & PayTags).
 */
object QrCodeGenerator {

  private data class VersionSpec(
    val version: Int,
    val size: Int,
    val dataCodewords: Int,
    val ecCodewords: Int,
    val alignCenter: Int?,
  )

  private val specs = listOf(
    VersionSpec(1, 21, 19, 7, null),
    VersionSpec(2, 25, 34, 10, 18),
    VersionSpec(3, 29, 55, 15, 22),
    VersionSpec(4, 33, 80, 20, 26),
  )

  fun generateMatrix(content: String): Array<BooleanArray> {
    val bytes = content.toByteArray(Charsets.UTF_8)
    val spec = specs.firstOrNull { bytes.size + 2 <= it.dataCodewords } ?: specs.last()
    val maxBytes = (spec.dataCodewords - 2).coerceAtLeast(1)
    val payload = if (bytes.size > maxBytes) bytes.copyOf(maxBytes) else bytes

    val bitStream = encodeDataBits(payload, spec.dataCodewords)
    val ecBytes = computeReedSolomon(bitStream, spec.ecCodewords)
    val allBits = buildList {
      for (b in bitStream) {
        for (i in 7 downTo 0) add(((b.toInt() ushr i) and 1) == 1)
      }
      for (b in ecBytes) {
        for (i in 7 downTo 0) add(((b.toInt() ushr i) and 1) == 1)
      }
      repeat(7) { add(false) }
    }

    val n = spec.size
    val modules = Array(n) { BooleanArray(n) }
    val isFunction = Array(n) { BooleanArray(n) }

    drawFinderPattern(modules, isFunction, 0, 0)
    drawFinderPattern(modules, isFunction, n - 7, 0)
    drawFinderPattern(modules, isFunction, 0, n - 7)

    // Timing patterns
    for (i in 8 until n - 8) {
      val dark = i % 2 == 0
      setFunctionModule(modules, isFunction, i, 6, dark)
      setFunctionModule(modules, isFunction, 6, i, dark)
    }

    // Alignment pattern (Version >= 2)
    spec.alignCenter?.let { c ->
      drawAlignmentPattern(modules, isFunction, c, c)
    }

    // Reserve format info areas before placing data bits
    for (i in 0..8) {
      if (i != 6) {
        isFunction[8][i] = true
        isFunction[i][8] = true
      }
    }
    for (i in n - 8 until n) {
      isFunction[8][i] = true
      isFunction[i][8] = true
    }

    // Dark module at (x = 8, y = 4 * version + 9) = (8, n - 8)
    setFunctionModule(modules, isFunction, 8, 4 * spec.version + 9, true)

    // Place data bits in zig-zag columns
    var bitIdx = 0
    var upward = true
    var right = n - 1
    while (right >= 1) {
      if (right == 6) right--
      for (vert in 0 until n) {
        val y = if (upward) n - 1 - vert else vert
        for (j in 0..1) {
          val x = right - j
          if (!isFunction[y][x]) {
            val bit = if (bitIdx < allBits.size) allBits[bitIdx++] else false
            val masked = if ((x + y) % 2 == 0) !bit else bit
            modules[y][x] = masked
          }
        }
      }
      upward = !upward
      right -= 2
    }

    // Format info for ECC-L (01) + Mask 0 (000) -> BCH = 111011111000100 (MSB=bit14 .. LSB=bit0)
    val formatBits = 0b111011111000100
    // Copy 1 (around top-left finder) & Copy 2 (bottom-left and top-right) per ISO 18004
    for (i in 0..5) {
      val bit = ((formatBits ushr i) and 1) == 1
      modules[i][8] = bit // (x=8, y=0..5)
      modules[8][n - 1 - i] = bit // (x=n-1..n-6, y=8)
    }
    modules[7][8] = ((formatBits ushr 6) and 1) == 1
    modules[8][n - 7] = ((formatBits ushr 6) and 1) == 1

    modules[8][8] = ((formatBits ushr 7) and 1) == 1
    modules[8][n - 8] = ((formatBits ushr 7) and 1) == 1

    modules[8][7] = ((formatBits ushr 8) and 1) == 1
    modules[n - 7][8] = ((formatBits ushr 8) and 1) == 1

    for (i in 9..14) {
      val bit = ((formatBits ushr i) and 1) == 1
      modules[8][14 - i] = bit // (x=5..0, y=8)
      modules[n - 15 + i][8] = bit // (x=8, y=n-6..n-1)
    }

    return modules
  }

  private fun setFunctionModule(
    modules: Array<BooleanArray>,
    isFunction: Array<BooleanArray>,
    x: Int,
    y: Int,
    dark: Boolean,
  ) {
    modules[y][x] = dark
    isFunction[y][x] = true
  }

  private fun drawFinderPattern(
    modules: Array<BooleanArray>,
    isFunction: Array<BooleanArray>,
    startX: Int,
    startY: Int,
  ) {
    val n = modules.size
    for (dy in -1..7) {
      for (dx in -1..7) {
        val x = startX + dx
        val y = startY + dy
        if (x in 0 until n && y in 0 until n) {
          val inOuter = dx in 0..6 && dy in 0..6
          val isBorder = dx == 0 || dx == 6 || dy == 0 || dy == 6
          val isInner = dx in 2..4 && dy in 2..4
          setFunctionModule(modules, isFunction, x, y, inOuter && (isBorder || isInner))
        }
      }
    }
  }

  private fun drawAlignmentPattern(
    modules: Array<BooleanArray>,
    isFunction: Array<BooleanArray>,
    cx: Int,
    cy: Int,
  ) {
    for (dy in -2..2) {
      for (dx in -2..2) {
        val dist = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
        setFunctionModule(modules, isFunction, cx + dx, cy + dy, dist != 1)
      }
    }
  }

  private fun encodeDataBits(payload: ByteArray, totalDataCodewords: Int): ByteArray {
    val bits = ArrayList<Boolean>()
    fun appendBits(value: Int, count: Int) {
      for (i in count - 1 downTo 0) {
        bits.add(((value ushr i) and 1) == 1)
      }
    }
    appendBits(0b0100, 4)
    appendBits(payload.size, 8)
    for (b in payload) {
      appendBits(b.toInt() and 0xFF, 8)
    }
    val targetBits = totalDataCodewords * 8
    val terminator = minOf(4, targetBits - bits.size)
    repeat(terminator) { bits.add(false) }
    while (bits.size % 8 != 0) bits.add(false)

    val out = ByteArray(totalDataCodewords)
    var idx = 0
    for (i in 0 until bits.size step 8) {
      var v = 0
      for (j in 0..7) {
        v = (v shl 1) or (if (bits[i + j]) 1 else 0)
      }
      out[idx++] = v.toByte()
    }
    var padToggle = true
    while (idx < totalDataCodewords) {
      out[idx++] = if (padToggle) 0xEC.toByte() else 0x11.toByte()
      padToggle = !padToggle
    }
    return out
  }

  private fun computeReedSolomon(data: ByteArray, ecCount: Int): ByteArray {
    val gen = IntArray(ecCount)
    gen[ecCount - 1] = 1
    var root = 1
    for (i in 0 until ecCount) {
      for (j in 0 until ecCount) {
        gen[j] = gfMultiply(gen[j], root)
        if (j + 1 < ecCount) gen[j] = gen[j] xor gen[j + 1]
      }
      root = gfMultiply(root, 0x02)
    }

    val result = IntArray(ecCount)
    for (b in data) {
      val factor = (b.toInt() and 0xFF) xor result[0]
      System.arraycopy(result, 1, result, 0, ecCount - 1)
      result[ecCount - 1] = 0
      for (j in 0 until ecCount) {
        result[j] = result[j] xor gfMultiply(gen[j], factor)
      }
    }
    return ByteArray(ecCount) { result[it].toByte() }
  }

  private fun gfMultiply(x: Int, y: Int): Int {
    var z = 0
    for (i in 7 downTo 0) {
      z = (z shl 1) xor ((z ushr 7) * 0x11D)
      z = z xor (((y ushr i) and 1) * x)
    }
    return z
  }
}
