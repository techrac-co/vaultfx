package com.example.data

import android.content.Context
import com.example.R
import com.example.model.EscrowDeal
import com.example.model.EscrowStatus
import com.example.model.PayTagEntry
import com.example.model.PeerTransfer
import com.example.model.SavedPair
import com.example.model.UserProfile
import com.example.model.WalletHolding
import com.example.model.WalletTransaction
import com.example.util.UsernameValidationResult
import com.example.util.UsernameValidator
import com.example.util.WalletAddressHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class VaultRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private fun requireUserId(): String =
    auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

  fun observeProfile(userId: String): Flow<UserProfile?> = flow {
    val uid = requireUserId()
    check(uid == userId) { "Signed-in user mismatch" }
    val path = "users/$uid"
    emitAll(
      db.collection("users").document(uid)
        .snapshots()
        .map { it.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
        .catch { error -> logFlowError(error, OperationType.GET, path, uid); throw error }
    )
  }

  fun observeHoldings(userId: String): Flow<List<WalletHolding>> = flow {
    val uid = requireUserId()
    check(uid == userId) { "Signed-in user mismatch" }
    val path = "users/$uid/holdings"
    emitAll(
      db.collection("users").document(uid).collection("holdings")
        .snapshots()
        .map { snap -> snap.toObjects(WalletHolding::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
        .catch { error -> logFlowError(error, OperationType.LIST, path, uid); throw error }
    )
  }

  fun observeTransactions(userId: String): Flow<List<WalletTransaction>> = flow {
    val uid = requireUserId()
    check(uid == userId) { "Signed-in user mismatch" }
    val path = "users/$uid/transactions"
    emitAll(
      db.collection("users").document(uid).collection("transactions")
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .limit(50)
        .snapshots()
        .map { snap -> snap.toObjects(WalletTransaction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
        .catch { error -> logFlowError(error, OperationType.LIST, path, uid); throw error }
    )
  }

  fun observeSavedPairs(userId: String): Flow<List<SavedPair>> = flow {
    val uid = requireUserId()
    check(uid == userId) { "Signed-in user mismatch" }
    val path = "users/$uid/savedPairs"
    emitAll(
      db.collection("users").document(uid).collection("savedPairs")
        .snapshots()
        .map { snap -> snap.toObjects(SavedPair::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
        .catch { error -> logFlowError(error, OperationType.LIST, path, uid); throw error }
    )
  }

  fun observeIncomingTransfers(userId: String): Flow<List<PeerTransfer>> = flow {
    val uid = requireUserId()
    check(uid == userId) { "Signed-in user mismatch" }
    val path = "transfers"
    emitAll(
      db.collection("transfers")
        .whereEqualTo("recipientUid", uid)
        .snapshots()
        .map { snap -> snap.toObjects(PeerTransfer::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
        .catch { error -> logFlowError(error, OperationType.LIST, path, uid); throw error }
    )
  }

  fun observeEscrows(userId: String): Flow<List<EscrowDeal>> = flow {
    val uid = requireUserId()
    check(uid == userId) { "Signed-in user mismatch" }
    val path = "escrows"
    emitAll(
      db.collection("escrows")
        .whereArrayContains("participantUids", uid)
        .snapshots()
        .map { snap ->
          snap.toObjects(EscrowDeal::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
        }
        .catch { error -> logFlowError(error, OperationType.LIST, path, uid); throw error }
    )
  }

  private fun logFlowError(error: Throwable, op: OperationType, path: String, uid: String) {
    val firestoreError = generateSequence(error) { it.cause }
      .filterIsInstance<FirebaseFirestoreException>()
      .firstOrNull()
    if (firestoreError == null && error is CancellationException) return
    val exceptionToLog = firestoreError ?: (error as? Exception)
    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
      handleFirestoreError(exceptionToLog, op, path)
    }
  }

  suspend fun ensureUserInitialized(userId: String, rawDisplayName: String): Result<Unit> {
    val userPath = "users/$userId"
    return try {
      val uid = requireUserId()
      check(uid == userId)
      val userDoc = db.collection("users").document(uid).get().await()
      if (userDoc.exists()) {
        // Ensure payTag registry doc also exists (self-heal if missing)
        val existingPayTag = userDoc.getString("payTag")
        val existingName = userDoc.getString("displayName") ?: "VaultFX Trader"
        if (!existingPayTag.isNullOrBlank()) {
          val tagRef = db.collection("payTags").document(existingPayTag)
          val tagSnap = runCatching { tagRef.get().await() }.getOrNull()
          if (tagSnap != null && !tagSnap.exists()) {
            tagRef.set(
              mapOf(
                "ownerUid" to uid,
                "payTag" to existingPayTag,
                "displayName" to existingName.take(80),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
              )
            ).await()
          }
        }
        return Result.success(Unit)
      }

      val cleanName = rawDisplayName.trim().ifBlank { "VaultFX Trader" }.take(60)
      val cleanSeed = "vf_${uid.take(24)}"
      val cleanTagBase = cleanName.lowercase().replace(Regex("[^a-z0-9]"), "").take(10).ifBlank { "trader" }
      val cleanUidNorm = uid.lowercase().replace(Regex("[^a-z0-9]"), "")
      val uidSuffix = cleanUidNorm.take(8).ifBlank { "1001" }
      var payTag = "${cleanTagBase}_$uidSuffix".take(30)

      val payTagRef = db.collection("payTags").document(payTag)
      val existingTagSnap = runCatching { payTagRef.get().await() }.getOrNull()
      val shouldWritePayTag = if (existingTagSnap != null && existingTagSnap.exists()) {
        if (existingTagSnap.getString("ownerUid") != uid) {
          payTag = "v_${cleanUidNorm.take(28)}".take(30)
          val fallbackSnap = runCatching { db.collection("payTags").document(payTag).get().await() }.getOrNull()
          fallbackSnap == null || !fallbackSnap.exists()
        } else {
          false
        }
      } else {
        true
      }

      val batch = db.batch()
      batch.set(
        db.collection("users").document(uid),
        mapOf(
          "userId" to uid,
          "displayName" to cleanName,
          "payTag" to payTag,
          "preferredFiat" to "USD",
          "walletSeed" to cleanSeed,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )

      if (shouldWritePayTag) {
        batch.set(
          db.collection("payTags").document(payTag),
          mapOf(
            "ownerUid" to uid,
            "payTag" to payTag,
            "displayName" to cleanName,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }

      val starterHoldings = listOf(
        Triple("USD", 10000.0, 1.0),
        Triple("NGN", 500000.0, 1.0 / 1645.0),
        Triple("GBP", 1200.0, 1.27),
        Triple("BTC", 0.025, 94250.0),
        Triple("ETH", 0.45, 3340.0),
        Triple("SOL", 4.5, 238.5),
        Triple("USDT", 500.0, 1.0),
      )
      for ((code, balance, avgUsd) in starterHoldings) {
        batch.set(
          db.collection("users").document(uid).collection("holdings").document(code),
          mapOf(
            "assetCode" to code,
            "balance" to balance,
            "avgBuyPriceUsd" to avgUsd,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }

      val defaultPairs = listOf(
        "USD" to "NGN",
        "USD" to "GBP",
        "USD" to "EUR",
        "BTC" to "USD",
        "BTC" to "NGN",
        "ETH" to "USD",
      )
      for ((base, quote) in defaultPairs) {
        batch.set(
          db.collection("users").document(uid).collection("savedPairs").document("${base}_$quote"),
          mapOf(
            "baseCode" to base,
            "quoteCode" to quote,
            "createdAt" to FieldValue.serverTimestamp(),
          ),
        )
      }

      val welcomeTxRef = db.collection("users").document(uid).collection("transactions").document()
      batch.set(
        welcomeTxRef,
        mapOf(
          "txType" to "DEPOSIT",
          "assetCode" to "USD",
          "amount" to 10000.0,
          "quoteCode" to "USD",
          "quoteAmount" to 10000.0,
          "priceUsd" to 1.0,
          "feeAmount" to 0.0,
          "counterparty" to "VaultFX Treasury",
          "txHash" to WalletAddressHelper.generateTxHash(uid),
          "status" to "COMPLETED",
          "note" to "Starter multi-currency portfolio",
          "createdAt" to FieldValue.serverTimestamp(),
        ),
      )

      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, userPath)
      Result.failure(e)
    }
  }

  suspend fun updatePreferredFiat(userId: String, fiatCode: String): Result<Unit> {
    val path = "users/$userId"
    return try {
      val uid = requireUserId()
      check(uid == userId)
      require(fiatCode in setOf("USD", "NGN", "GBP", "EUR", "CAD", "GHS", "KES", "ZAR")) {
        "Unsupported display currency"
      }
      db.collection("users").document(uid).update(
        mapOf(
          "preferredFiat" to fiatCode,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun toggleSavedPair(
    userId: String,
    baseCode: String,
    quoteCode: String,
    currentlyPinned: Boolean,
  ): Result<Unit> {
    val pairId = "${baseCode.uppercase()}_${quoteCode.uppercase()}"
    val path = "users/$userId/savedPairs/$pairId"
    return try {
      val uid = requireUserId()
      check(uid == userId)
      val ref = db.collection("users").document(uid).collection("savedPairs").document(pairId)
      if (currentlyPinned) {
        ref.delete().await()
      } else {
        ref.set(
          mapOf(
            "baseCode" to baseCode.uppercase(),
            "quoteCode" to quoteCode.uppercase(),
            "createdAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  suspend fun depositFiat(
    userId: String,
    fiatCode: String,
    amount: Double,
    priceUsd: Double,
  ): Result<Unit> {
    val path = "users/$userId/holdings/$fiatCode"
    return try {
      val uid = requireUserId()
      check(uid == userId)
      require(amount > 0.0 && amount.isFinite()) { "Amount must be greater than zero" }
      val safePriceUsd = priceUsd.takeIf { it.isFinite() && it >= 0.0 } ?: 1.0
      val holdingRef = db.collection("users").document(uid).collection("holdings").document(fiatCode)
      val txRef = db.collection("users").document(uid).collection("transactions").document()
      val txHash = WalletAddressHelper.generateTxHash("$uid:$fiatCode")

      db.runTransaction { tx ->
        val existing = tx.get(holdingRef)
        val currentBal = existing.getDouble("balance") ?: 0.0
        val newBal = currentBal + amount

        if (existing.exists()) {
          tx.update(
            holdingRef,
            mapOf(
              "balance" to newBal,
              "avgBuyPriceUsd" to safePriceUsd,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        } else {
          tx.set(
            holdingRef,
            mapOf(
              "assetCode" to fiatCode,
              "balance" to newBal,
              "avgBuyPriceUsd" to safePriceUsd,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }

        tx.set(
          txRef,
          mapOf(
            "txType" to "DEPOSIT",
            "assetCode" to fiatCode,
            "amount" to amount,
            "quoteCode" to fiatCode,
            "quoteAmount" to amount,
            "priceUsd" to safePriceUsd,
            "feeAmount" to 0.0,
            "counterparty" to "Instant Funding",
            "txHash" to txHash,
            "status" to "COMPLETED",
            "note" to "Added $fiatCode funds",
            "createdAt" to FieldValue.serverTimestamp(),
          ),
        )
      }.await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  suspend fun executeTrade(
    userId: String,
    isBuy: Boolean,
    cryptoCode: String,
    cryptoAmount: Double,
    fiatCode: String,
    fiatTotal: Double,
    feeFiat: Double,
    cryptoPriceUsd: Double,
    fiatPriceUsd: Double,
  ): Result<Unit> {
    val path = "users/$userId/holdings/$cryptoCode"
    return try {
      val uid = requireUserId()
      check(uid == userId)
      require(cryptoAmount > 0.0 && cryptoAmount.isFinite() && fiatTotal > 0.0 && fiatTotal.isFinite()) {
        "Trade amounts must be positive"
      }
      val safeCryptoUsd = cryptoPriceUsd.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
      val safeFiatUsd = fiatPriceUsd.takeIf { it.isFinite() && it >= 0.0 } ?: 1.0

      val cryptoRef = db.collection("users").document(uid).collection("holdings").document(cryptoCode)
      val fiatRef = db.collection("users").document(uid).collection("holdings").document(fiatCode)
      val txRef = db.collection("users").document(uid).collection("transactions").document()
      val txHash = WalletAddressHelper.generateTxHash("$uid:$cryptoCode:$fiatCode")

      db.runTransaction { tx ->
        val cryptoSnap = tx.get(cryptoRef)
        val fiatSnap = tx.get(fiatRef)

        val currentCrypto = cryptoSnap.getDouble("balance") ?: 0.0
        val currentAvgUsd = cryptoSnap.getDouble("avgBuyPriceUsd") ?: safeCryptoUsd
        val currentFiat = fiatSnap.getDouble("balance") ?: 0.0

        val newCrypto: Double
        val newFiat: Double
        val newAvgUsd: Double

        if (isBuy) {
          val totalDebit = fiatTotal + feeFiat
          if (currentFiat + 1e-6 < totalDebit) {
            throw IllegalArgumentException("Insufficient $fiatCode balance. Deposit more $fiatCode or lower order size.")
          }
          newFiat = (currentFiat - totalDebit).coerceAtLeast(0.0)
          newCrypto = currentCrypto + cryptoAmount
          newAvgUsd = if (newCrypto > 0.0) {
            ((currentCrypto * currentAvgUsd) + (cryptoAmount * safeCryptoUsd)) / newCrypto
          } else safeCryptoUsd
        } else {
          if (currentCrypto + 1e-8 < cryptoAmount) {
            throw IllegalArgumentException("Insufficient $cryptoCode balance to sell.")
          }
          val netCredit = (fiatTotal - feeFiat).coerceAtLeast(0.0)
          newCrypto = (currentCrypto - cryptoAmount).coerceAtLeast(0.0)
          newFiat = currentFiat + netCredit
          newAvgUsd = currentAvgUsd
        }

        if (cryptoSnap.exists()) {
          tx.update(
            cryptoRef,
            mapOf(
              "balance" to newCrypto,
              "avgBuyPriceUsd" to newAvgUsd,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        } else {
          tx.set(
            cryptoRef,
            mapOf(
              "assetCode" to cryptoCode,
              "balance" to newCrypto,
              "avgBuyPriceUsd" to newAvgUsd,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }

        if (fiatSnap.exists()) {
          tx.update(
            fiatRef,
            mapOf(
              "balance" to newFiat,
              "avgBuyPriceUsd" to safeFiatUsd,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        } else {
          tx.set(
            fiatRef,
            mapOf(
              "assetCode" to fiatCode,
              "balance" to newFiat,
              "avgBuyPriceUsd" to safeFiatUsd,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }

        tx.set(
          txRef,
          mapOf(
            "txType" to if (isBuy) "BUY" else "SELL",
            "assetCode" to cryptoCode,
            "amount" to cryptoAmount,
            "quoteCode" to fiatCode,
            "quoteAmount" to fiatTotal,
            "priceUsd" to safeCryptoUsd,
            "feeAmount" to feeFiat.coerceAtLeast(0.0),
            "counterparty" to "VaultFX Exchange",
            "txHash" to txHash,
            "status" to "COMPLETED",
            "note" to "${if (isBuy) "Bought" else "Sold"} $cryptoCode with $fiatCode",
            "createdAt" to FieldValue.serverTimestamp(),
          ),
        )
      }.await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  sealed interface UsernameAvailability {
    data class Available(val username: String) : UsernameAvailability
    data class OwnedByCurrentUser(val username: String) : UsernameAvailability
    data class Taken(val username: String) : UsernameAvailability
    data class Invalid(val reason: String) : UsernameAvailability
  }

  suspend fun checkUsernameAvailability(
    rawUsername: String,
    currentUserId: String,
  ): Result<UsernameAvailability> {
    val validation = UsernameValidator.validate(rawUsername)
    if (validation is UsernameValidationResult.Invalid) {
      return Result.success(UsernameAvailability.Invalid(validation.message))
    }
    val cleanTag = (validation as UsernameValidationResult.Valid).normalized
    val path = "payTags/$cleanTag"
    return try {
      val uid = requireUserId()
      check(uid == currentUserId)
      val doc = db.collection("payTags").document(cleanTag).get().await()
      val status = when {
        !doc.exists() -> UsernameAvailability.Available(cleanTag)
        doc.getString("ownerUid") == uid -> UsernameAvailability.OwnedByCurrentUser(cleanTag)
        else -> UsernameAvailability.Taken(cleanTag)
      }
      Result.success(status)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, path)
      Result.failure(e)
    }
  }

  suspend fun claimUsername(
    userId: String,
    rawUsername: String,
    newDisplayName: String? = null,
  ): Result<String> {
    val validation = UsernameValidator.validate(rawUsername)
    if (validation is UsernameValidationResult.Invalid) {
      return Result.failure(IllegalArgumentException(validation.message))
    }
    val newTag = (validation as UsernameValidationResult.Valid).normalized
    val path = "payTags/$newTag"
    return try {
      val uid = requireUserId()
      check(uid == userId)

      val userRef = db.collection("users").document(uid)
      val newTagRef = db.collection("payTags").document(newTag)

      db.runTransaction { tx ->
        val userSnap = tx.get(userRef)
        if (!userSnap.exists()) {
          throw IllegalStateException("Wallet profile is still initializing. Retry in a moment.")
        }
        val oldTag = userSnap.getString("payTag").orEmpty().trim().lowercase()
        val existingName = userSnap.getString("displayName").orEmpty().ifBlank { "VaultFX Trader" }
        val cleanDisplayName = (newDisplayName?.trim()?.takeIf { it.isNotEmpty() } ?: existingName).take(80)

        val newTagSnap = tx.get(newTagRef)
        val oldTagRef = if (oldTag.isNotBlank() && oldTag != newTag && oldTag.matches(Regex("^[a-z0-9_]{3,32}$"))) {
          db.collection("payTags").document(oldTag)
        } else {
          null
        }
        val oldTagSnap = oldTagRef?.let { tx.get(it) }

        if (newTagSnap.exists() && newTagSnap.getString("ownerUid") != uid) {
          throw IllegalArgumentException("@$newTag is already claimed. Choose another username.")
        }

        if (oldTagRef != null && oldTagSnap != null && oldTagSnap.exists() && oldTagSnap.getString("ownerUid") == uid) {
          tx.delete(oldTagRef)
        }

        if (!newTagSnap.exists()) {
          tx.set(
            newTagRef,
            mapOf(
              "ownerUid" to uid,
              "payTag" to newTag,
              "displayName" to cleanDisplayName,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        } else {
          tx.update(
            newTagRef,
            mapOf(
              "displayName" to cleanDisplayName,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }

        tx.update(
          userRef,
          mapOf(
            "payTag" to newTag,
            "displayName" to cleanDisplayName,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }.await()

      Result.success(newTag)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  suspend fun lookupPayTag(rawTag: String): Result<PayTagEntry?> {
    val cleanTag = rawTag.trim().removePrefix("@").lowercase()
    if (!cleanTag.matches(Regex("^[a-z0-9_]{3,32}$"))) {
      return Result.success(null)
    }
    val path = "payTags/$cleanTag"
    return try {
      requireUserId()
      val doc = db.collection("payTags").document(cleanTag).get().await()
      val entry = if (doc.exists()) {
        doc.toObject(PayTagEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      } else null
      Result.success(entry)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, path)
      Result.failure(e)
    }
  }

  suspend fun sendCrypto(
    userId: String,
    senderTag: String,
    assetCode: String,
    amount: Double,
    priceUsd: Double,
    recipientInput: String,
    note: String?,
    overrideTxHash: String? = null,
  ): Result<String> {
    val path = "users/$userId/holdings/$assetCode"
    return try {
      val uid = requireUserId()
      check(uid == userId)
      require(amount > 0.0 && amount.isFinite()) { "Send amount must be greater than zero" }
      val trimmedRecipient = recipientInput.trim()
      require(trimmedRecipient.length >= 3) { "Enter a valid @payTag or wallet address" }

      val cleanNote = note?.trim()?.take(140)?.takeIf { it.isNotEmpty() }
      val txHash = overrideTxHash?.trim()?.take(96)?.takeIf { it.length >= 6 }
        ?: WalletAddressHelper.generateTxHash("$uid:$assetCode:$trimmedRecipient")

      val candidateTag = trimmedRecipient.removePrefix("@").lowercase()
      val peerEntry = if (candidateTag.matches(Regex("^[a-z0-9_]{3,32}$"))) {
        val doc = db.collection("payTags").document(candidateTag).get().await()
        if (doc.exists()) doc.toObject(PayTagEntry::class.java) else null
      } else null

      if (peerEntry != null && peerEntry.ownerUid == uid) {
        throw IllegalArgumentException("You cannot send a peer transfer to your own @payTag")
      }

      val holdingRef = db.collection("users").document(uid).collection("holdings").document(assetCode)
      val userRef = db.collection("users").document(uid)
      val txRef = db.collection("users").document(uid).collection("transactions").document()
      val transferRef = db.collection("transfers").document()

      db.runTransaction { tx ->
        val holdingSnap = tx.get(holdingRef)
        val currentBal = holdingSnap.getDouble("balance") ?: 0.0
        if (currentBal + 1e-8 < amount) {
          throw IllegalArgumentException("Insufficient $assetCode balance to send")
        }
        val resolvedSenderTag = (if (senderTag.isNotBlank() && senderTag != "vault_user") {
          senderTag
        } else {
          tx.get(userRef).getString("payTag") ?: "vault_user"
        }).take(40)

        tx.update(
          holdingRef,
          mapOf(
            "balance" to (currentBal - amount).coerceAtLeast(0.0),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )

        val counterpartyLabel = if (peerEntry != null) "@${peerEntry.payTag}" else trimmedRecipient.take(120)
        val txPayload = buildMap<String, Any> {
          put("txType", "SEND")
          put("assetCode", assetCode)
          put("amount", amount)
          put("quoteCode", "USD")
          put("quoteAmount", amount * priceUsd)
          put("priceUsd", priceUsd)
          put("feeAmount", 0.0)
          put("counterparty", counterpartyLabel)
          put("txHash", txHash)
          put("status", "COMPLETED")
          if (cleanNote != null) put("note", cleanNote)
          put("createdAt", FieldValue.serverTimestamp())
        }
        tx.set(txRef, txPayload)

        if (peerEntry != null) {
          val transferPayload = buildMap<String, Any> {
            put("senderUid", uid)
            put("senderTag", resolvedSenderTag)
            put("recipientUid", peerEntry.ownerUid)
            put("recipientTag", peerEntry.payTag.take(40))
            put("assetCode", assetCode)
            put("amount", amount)
            put("txHash", txHash)
            if (cleanNote != null) put("note", cleanNote)
            put("status", "PENDING")
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
          }
          tx.set(transferRef, transferPayload)
        }
      }.await()

      val statusMsg = if (peerEntry != null) {
        "Sent $amount $assetCode to @${peerEntry.payTag}"
      } else {
        "Broadcast $amount $assetCode to ${WalletAddressHelper.shortenAddress(trimmedRecipient)}"
      }
      Result.success(statusMsg)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  suspend fun claimIncomingTransfer(
    userId: String,
    transfer: PeerTransfer,
    priceUsd: Double,
  ): Result<Unit> {
    val path = "transfers/${transfer.id}"
    return try {
      val uid = requireUserId()
      check(uid == userId)
      require(transfer.id.isNotBlank())

      val transferRef = db.collection("transfers").document(transfer.id)
      val holdingRef = db.collection("users").document(uid).collection("holdings").document(transfer.assetCode)
      val txRef = db.collection("users").document(uid).collection("transactions").document()
      val safeNote = transfer.note?.trim()?.take(140)?.takeIf { it.isNotEmpty() }

      db.runTransaction { tx ->
        val transferSnap = tx.get(transferRef)
        val liveStatus = transferSnap.getString("status") ?: "CLAIMED"
        if (liveStatus != "PENDING") {
          throw IllegalStateException("This transfer has already been claimed")
        }
        val holdingSnap = tx.get(holdingRef)
        val currentBal = holdingSnap.getDouble("balance") ?: 0.0
        val currentAvg = holdingSnap.getDouble("avgBuyPriceUsd") ?: priceUsd

        tx.update(
          transferRef,
          mapOf(
            "status" to "CLAIMED",
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )

        if (holdingSnap.exists()) {
          tx.update(
            holdingRef,
            mapOf(
              "balance" to (currentBal + transfer.amount),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        } else {
          tx.set(
            holdingRef,
            mapOf(
              "assetCode" to transfer.assetCode,
              "balance" to transfer.amount,
              "avgBuyPriceUsd" to currentAvg,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }

        val txPayload = buildMap<String, Any> {
          put("txType", "RECEIVE")
          put("assetCode", transfer.assetCode)
          put("amount", transfer.amount)
          put("quoteCode", "USD")
          put("quoteAmount", transfer.amount * priceUsd)
          put("priceUsd", priceUsd)
          put("feeAmount", 0.0)
          put("counterparty", "@${transfer.senderTag}")
          put("txHash", transfer.txHash)
          put("status", "COMPLETED")
          if (safeNote != null) put("note", safeNote)
          put("createdAt", FieldValue.serverTimestamp())
        }
        tx.set(txRef, txPayload)
      }.await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  suspend fun receiveExternalDeposit(
    userId: String,
    assetCode: String,
    amount: Double,
    priceUsd: Double,
    senderLabel: String = "External Wallet",
  ): Result<Unit> {
    val path = "users/$userId/holdings/$assetCode"
    return try {
      val uid = requireUserId()
      check(uid == userId)
      require(amount > 0.0)

      val holdingRef = db.collection("users").document(uid).collection("holdings").document(assetCode)
      val txRef = db.collection("users").document(uid).collection("transactions").document()
      val txHash = WalletAddressHelper.generateTxHash("$uid:recv:$assetCode")

      db.runTransaction { tx ->
        val snap = tx.get(holdingRef)
        val currentBal = snap.getDouble("balance") ?: 0.0

        if (snap.exists()) {
          tx.update(
            holdingRef,
            mapOf(
              "balance" to (currentBal + amount),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        } else {
          tx.set(
            holdingRef,
            mapOf(
              "assetCode" to assetCode,
              "balance" to amount,
              "avgBuyPriceUsd" to priceUsd,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }

        tx.set(
          txRef,
          mapOf(
            "txType" to "RECEIVE",
            "assetCode" to assetCode,
            "amount" to amount,
            "quoteCode" to "USD",
            "quoteAmount" to (amount * priceUsd),
            "priceUsd" to priceUsd,
            "feeAmount" to 0.0,
            "counterparty" to senderLabel.take(120),
            "txHash" to txHash,
            "status" to "COMPLETED",
            "note" to "On-chain deposit received",
            "createdAt" to FieldValue.serverTimestamp(),
          ),
        )
      }.await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  // --- ESCROW CONTRACT OPERATIONS ---

  suspend fun createEscrow(
    userId: String,
    buyerTag: String,
    sellerTagInput: String,
    assetCode: String,
    amount: Double,
    priceUsd: Double,
    title: String,
    agreedTerms: String?,
  ): Result<String> {
    val path = "escrows"
    return try {
      val uid = requireUserId()
      check(uid == userId)
      require(amount > 0.0 && amount.isFinite()) { "Escrow amount must be positive" }
      val cleanTitle = title.trim().take(80)
      require(cleanTitle.length >= 2) { "Enter a brief escrow deal title (min 2 chars)" }

      val cleanSellerTag = sellerTagInput.trim().removePrefix("@").lowercase()
      require(cleanSellerTag.matches(Regex("^[a-z0-9_]{3,32}$"))) {
        "Enter a valid seller @payTag (letters, digits, underscore)"
      }

      val sellerSnap = db.collection("payTags").document(cleanSellerTag).get().await()
      val sellerEntry = if (sellerSnap.exists()) sellerSnap.toObject(PayTagEntry::class.java) else null
      requireNotNull(sellerEntry) { "No VaultFX user found with @$cleanSellerTag" }
      require(sellerEntry.ownerUid != uid) { "Seller cannot be your own account" }

      val holdingRef = db.collection("users").document(uid).collection("holdings").document(assetCode)
      val userRef = db.collection("users").document(uid)
      val escrowRef = db.collection("escrows").document()
      val txRef = db.collection("users").document(uid).collection("transactions").document()
      val txHash = WalletAddressHelper.generateTxHash("$uid:escrow:$cleanSellerTag:$assetCode")
      val cleanTerms = agreedTerms?.trim()?.take(160)?.takeIf { it.isNotEmpty() }

      db.runTransaction { tx ->
        val holdingSnap = tx.get(holdingRef)
        val currentBal = holdingSnap.getDouble("balance") ?: 0.0
        if (currentBal + 1e-8 < amount) {
          throw IllegalArgumentException("Insufficient $assetCode balance to lock in escrow")
        }
        val resolvedBuyerTag = (if (buyerTag.isNotBlank() && buyerTag != "vault_user") {
          buyerTag
        } else {
          tx.get(userRef).getString("payTag") ?: "vault_user"
        }).take(40)

        tx.update(
          holdingRef,
          mapOf(
            "balance" to (currentBal - amount).coerceAtLeast(0.0),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )

        val escrowPayload = buildMap<String, Any> {
          put("buyerUid", uid)
          put("buyerTag", resolvedBuyerTag)
          put("sellerUid", sellerEntry.ownerUid)
          put("sellerTag", sellerEntry.payTag.take(40))
          put("participantUids", listOf(uid, sellerEntry.ownerUid))
          put("assetCode", assetCode)
          put("amount", amount)
          put("title", cleanTitle)
          if (cleanTerms != null) put("agreedTerms", cleanTerms)
          put("txHash", txHash)
          put("status", "FUNDED")
          put("createdAt", FieldValue.serverTimestamp())
          put("updatedAt", FieldValue.serverTimestamp())
        }
        tx.set(escrowRef, escrowPayload)

        tx.set(
          txRef,
          mapOf(
            "txType" to "ESCROW_LOCK",
            "assetCode" to assetCode,
            "amount" to amount,
            "quoteCode" to "USD",
            "quoteAmount" to (amount * priceUsd),
            "priceUsd" to priceUsd,
            "feeAmount" to 0.0,
            "counterparty" to "Escrow @${sellerEntry.payTag}",
            "txHash" to txHash,
            "status" to "COMPLETED",
            "note" to cleanTitle.take(140),
            "createdAt" to FieldValue.serverTimestamp(),
          ),
        )
      }.await()
      Result.success("Locked $amount $assetCode in Escrow with @${sellerEntry.payTag}")
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
      Result.failure(e)
    }
  }

  suspend fun updateEscrowStatus(
    userId: String,
    escrow: EscrowDeal,
    targetStatus: EscrowStatus,
    disputeReason: String? = null,
  ): Result<Unit> {
    val path = "escrows/${escrow.id}"
    return try {
      val uid = requireUserId()
      check(uid == userId)
      require(escrow.id.isNotBlank())

      val updates = mutableMapOf<String, Any>(
        "status" to targetStatus.name,
        "updatedAt" to FieldValue.serverTimestamp(),
      )
      if (targetStatus == EscrowStatus.DISPUTED) {
        val reason = disputeReason?.trim()?.take(160) ?: ""
        require(reason.length >= 3) { "Provide a dispute reason (min 3 characters)" }
        updates["disputeReason"] = reason
      }

      db.collection("escrows").document(escrow.id).update(updates).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun claimEscrowFunds(
    userId: String,
    escrow: EscrowDeal,
    priceUsd: Double,
  ): Result<Unit> {
    val path = "escrows/${escrow.id}"
    return try {
      val uid = requireUserId()
      check(uid == userId)
      require(escrow.id.isNotBlank())

      val escrowRef = db.collection("escrows").document(escrow.id)
      val holdingRef = db.collection("users").document(uid).collection("holdings").document(escrow.assetCode)
      val txRef = db.collection("users").document(uid).collection("transactions").document()
      val safePriceUsd = priceUsd.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0

      db.runTransaction { tx ->
        val escrowSnap = tx.get(escrowRef)
        val liveStatus = EscrowStatus.safeValueOf(escrowSnap.getString("status") ?: "")
        val buyerUid = escrowSnap.getString("buyerUid") ?: ""
        val sellerUid = escrowSnap.getString("sellerUid") ?: ""
        val isSellerClaim = liveStatus == EscrowStatus.RELEASED && sellerUid == uid
        val isBuyerRefundClaim = liveStatus == EscrowStatus.REFUNDED && buyerUid == uid
        if (!isSellerClaim && !isBuyerRefundClaim) {
          throw IllegalStateException("Escrow funds are not claimable by this account")
        }

        val holdingSnap = tx.get(holdingRef)
        val currentBal = holdingSnap.getDouble("balance") ?: 0.0

        tx.update(
          escrowRef,
          mapOf(
            "status" to "CLAIMED",
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )

        if (holdingSnap.exists()) {
          tx.update(
            holdingRef,
            mapOf(
              "balance" to (currentBal + escrow.amount),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        } else {
          tx.set(
            holdingRef,
            mapOf(
              "assetCode" to escrow.assetCode,
              "balance" to escrow.amount,
              "avgBuyPriceUsd" to safePriceUsd,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }

        val counterpartyTag = if (isSellerClaim) "@${escrow.buyerTag}" else "@${escrow.sellerTag}"
        tx.set(
          txRef,
          mapOf(
            "txType" to "ESCROW_RELEASE",
            "assetCode" to escrow.assetCode,
            "amount" to escrow.amount,
            "quoteCode" to "USD",
            "quoteAmount" to (escrow.amount * safePriceUsd),
            "priceUsd" to safePriceUsd,
            "feeAmount" to 0.0,
            "counterparty" to "Escrow $counterpartyTag",
            "txHash" to escrow.txHash,
            "status" to "COMPLETED",
            "note" to "${if (isSellerClaim) "Escrow released" else "Escrow refunded"}: ${escrow.title}".take(140),
            "createdAt" to FieldValue.serverTimestamp(),
          ),
        )
      }.await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }
}
