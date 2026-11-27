package com.example.util

sealed interface UsernameValidationResult {
  data class Valid(val normalized: String) : UsernameValidationResult
  data class Invalid(val message: String) : UsernameValidationResult
}

object UsernameValidator {
  private val TAG_REGEX = Regex("^[a-z0-9_]{3,32}$")
  private val RESERVED_USERNAMES = setOf(
    "admin",
    "administrator",
    "support",
    "system",
    "vaultfx",
    "bybit",
    "escrow",
    "treasury",
    "official",
    "moderator",
    "vault_user",
    "root",
  )

  fun normalize(raw: String): String =
    raw.trim().removePrefix("@").lowercase()

  fun validate(raw: String): UsernameValidationResult {
    val clean = normalize(raw)
    return when {
      clean.isEmpty() -> UsernameValidationResult.Invalid("Enter a username")
      clean.length < 3 -> UsernameValidationResult.Invalid("Username must be at least 3 characters")
      clean.length > 32 -> UsernameValidationResult.Invalid("Username must be 32 characters or fewer")
      !TAG_REGEX.matches(clean) ->
        UsernameValidationResult.Invalid("Use only lowercase letters, numbers, and underscores (_)")
      clean in RESERVED_USERNAMES ->
        UsernameValidationResult.Invalid("That username is reserved. Choose another.")
      else -> UsernameValidationResult.Valid(clean)
    }
  }

  fun isGeneratedPlaceholder(tag: String?): Boolean {
    if (tag.isNullOrBlank()) return true
    val clean = normalize(tag)
    return clean == "vault_user" ||
      clean.matches(Regex("^trader_[a-z0-9]{6,10}$")) ||
      clean.matches(Regex("^v_[a-z0-9]{10,28}$"))
  }
}
