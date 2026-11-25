package com.example.util

import java.security.MessageDigest
import kotlin.math.abs

object WalletAddressHelper {

  fun deriveAddress(walletSeed: String, assetCode: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
      .digest("$walletSeed:$assetCode".toByteArray(Charsets.UTF_8))
    val hex = digest.joinToString("") { "%02x".format(it) }
    val base58Chars = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    val base58 = buildString {
      for (b in digest.take(30)) {
        append(base58Chars[(b.toInt() and 0xFF) % base58Chars.length])
      }
    }
    return when (assetCode.uppercase()) {
      "BTC" -> "bc1q${hex.take(36)}"
      "ETH", "USDC", "LINK", "AVAX", "BNB" -> "0x${hex.take(40)}"
      "USDT" -> "T${base58.take(33)}"
      "SOL" -> base58.take(42)
      "XRP" -> "r${base58.take(32)}"
      "TON" -> "UQ${base58.take(38)}"
      "DOGE" -> "D${base58.take(33)}"
      "ADA" -> "addr1q${hex.take(38)}"
      else -> "0x${hex.take(40)}"
    }
  }

  fun generateTxHash(seed: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
      .digest("$seed:${System.nanoTime()}".toByteArray(Charsets.UTF_8))
    return "0x" + digest.take(16).joinToString("") { "%02x".format(it) }
  }

  fun deriveDefaultPayTag(displayName: String, uid: String): String {
    val cleanName = displayName
      .lowercase()
      .replace(Regex("[^a-z0-9]"), "")
      .take(12)
      .ifBlank { "vault" }
    val suffix = abs(uid.hashCode()).toString().take(4)
    return "${cleanName}_$suffix".take(30)
  }

  fun shortenAddress(address: String): String {
    if (address.length <= 16) return address
    return "${address.take(8)}...${address.takeLast(6)}"
  }
}
