package com.example.data

import com.example.data.local.EncryptedTransactionDao
import com.example.data.local.EncryptedTransactionEntity
import com.example.data.local.OfflineSyncStatus
import com.example.data.local.VaultRecordKind
import com.example.model.WalletTransaction
import com.example.security.PlainOfflineTxPayload
import com.example.security.VaultCryptoEngine
import com.example.util.WalletAddressHelper
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

data class DecryptedVaultTransaction(
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
  val syncStatus: OfflineSyncStatus,
  val kind: VaultRecordKind,
  val createdAtMs: Long,
  val settledAtMs: Long?,
  val lastError: String?,
  val ivPreview: String,
  val ciphertextPreview: String,
  val wrappedDekPreview: String,
  val integrityMacPreview: String,
  val e2eeVoucher: String,
  val isIntegrityVerified: Boolean,
)

data class VaultIntegrityReport(
  val totalRecords: Int,
  val verifiedRecords: Int,
  val tamperedRecords: Int,
  val keyFingerprint: String,
  val chainHeadMac: String,
  val cipherSuite: String,
)

data class OfflineSyncSummary(
  val settledCount: Int,
  val failedCount: Int,
  val remainingQueuedCount: Int,
  val firstError: String?,
)

class OfflineVaultRepository(
  private val dao: EncryptedTransactionDao,
  private val cryptoEngine: VaultCryptoEngine,
  private val vaultRepository: VaultRepository? = null,
) {
  private val writeMutex = Mutex()
  private val syncMutex = Mutex()

  fun observeDecryptedTransactions(userId: String): Flow<List<DecryptedVaultTransaction>> {
    val ownerUidHash = cryptoEngine.hashOwnerUid(userId)
    return dao.observeByOwner(ownerUidHash)
      .map { descList ->
        val asc = descList.asReversed()
        var expectedPrevMac = VaultCryptoEngine.GENESIS_MAC
        val verifiedAsc = asc.map { entity ->
          val decrypted = cryptoEngine.decryptAndVerify(
            id = entity.id,
            ownerUidHash = entity.ownerUidHash,
            txHash = entity.txHash,
            wrappedDekBlob = entity.wrappedDekBlob,
            ivBase64 = entity.ivBase64,
            ciphertextBase64 = entity.ciphertextBase64,
            e2eeVoucher = entity.e2eeVoucher,
            prevMacHex = entity.prevMacHex,
            expectedPrevMacHex = expectedPrevMac,
            integrityMacHex = entity.integrityMacHex,
            createdAtMs = entity.createdAtMs,
          )
          expectedPrevMac = entity.integrityMacHex
          val envelope = decrypted.getOrNull()
          val payload = envelope?.payload
          DecryptedVaultTransaction(
            id = entity.id,
            txType = payload?.txType ?: "ENCRYPTED",
            assetCode = payload?.assetCode ?: "---",
            amount = payload?.amount ?: 0.0,
            priceUsd = payload?.priceUsd ?: 0.0,
            senderTag = payload?.senderTag ?: "",
            recipientInput = payload?.recipientInput ?: "Encrypted Payload",
            counterparty = payload?.counterparty ?: "Verification Failed",
            note = payload?.note,
            txHash = entity.txHash,
            syncStatus = OfflineSyncStatus.safeValueOf(entity.syncStatus),
            kind = VaultRecordKind.safeValueOf(entity.kind),
            createdAtMs = entity.createdAtMs,
            settledAtMs = entity.settledAtMs,
            lastError = entity.lastError,
            ivPreview = entity.ivBase64.take(18),
            ciphertextPreview = entity.ciphertextBase64.take(28),
            wrappedDekPreview = entity.wrappedDekBlob.take(22),
            integrityMacPreview = entity.integrityMacHex.take(16),
            e2eeVoucher = entity.e2eeVoucher,
            isIntegrityVerified = envelope?.isFullyVerified == true,
          )
        }
        verifiedAsc.asReversed()
      }
      .flowOn(Dispatchers.Default)
  }

  suspend fun queueOfflineSend(
    userId: String,
    senderTag: String,
    assetCode: String,
    amount: Double,
    priceUsd: Double,
    recipientInput: String,
    note: String?,
    availableOnlineBalance: Double,
  ): Result<DecryptedVaultTransaction> = withContext(Dispatchers.Default) {
    try {
      require(amount > 0.0 && amount.isFinite()) { "Amount must be greater than zero" }
      val trimmedRecipient = recipientInput.trim()
      require(trimmedRecipient.length >= 3) { "Enter a valid @payTag or wallet address" }

      val cleanSender = senderTag.trim().removePrefix("@").ifBlank { "vault_user" }
      val candidateTag = trimmedRecipient.removePrefix("@").lowercase()
      if (candidateTag == cleanSender.lowercase()) {
        throw IllegalArgumentException("You cannot send an offline transfer to your own @payTag")
      }
      val cleanNote = note?.trim()?.take(140)?.takeIf { it.isNotEmpty() }
      val counterparty = if (trimmedRecipient.startsWith("@") || candidateTag.matches(Regex("^[a-z0-9_]{3,32}$"))) {
        "@$candidateTag"
      } else {
        trimmedRecipient.take(120)
      }

      val ownerUidHash = cryptoEngine.hashOwnerUid(userId)

      val created = writeMutex.withLock {
        val currentAsc = dao.getByOwnerAsc(ownerUidHash)
        var pendingReserved = 0.0
        for (row in currentAsc) {
          val status = OfflineSyncStatus.safeValueOf(row.syncStatus)
          if (status == OfflineSyncStatus.QUEUED_OFFLINE || status == OfflineSyncStatus.SYNCING) {
            val dec = cryptoEngine.decryptAndVerify(
              id = row.id,
              ownerUidHash = row.ownerUidHash,
              txHash = row.txHash,
              wrappedDekBlob = row.wrappedDekBlob,
              ivBase64 = row.ivBase64,
              ciphertextBase64 = row.ciphertextBase64,
              e2eeVoucher = row.e2eeVoucher,
              prevMacHex = row.prevMacHex,
              expectedPrevMacHex = row.prevMacHex,
              integrityMacHex = row.integrityMacHex,
              createdAtMs = row.createdAtMs,
            ).getOrNull()
            if (dec?.payload?.assetCode == assetCode) {
              pendingReserved += dec.payload.amount
            }
          }
        }

        val spendable = (availableOnlineBalance - pendingReserved).coerceAtLeast(0.0)
        if (amount > spendable + 1e-8) {
          throw IllegalArgumentException(
            "Insufficient spendable $assetCode after pending offline sends (avail: $spendable $assetCode)"
          )
        }

        val latest = currentAsc.lastOrNull()
        val prevMac = latest?.integrityMacHex ?: VaultCryptoEngine.GENESIS_MAC
        val nowMs = maxOf(System.currentTimeMillis(), (latest?.createdAtMs ?: 0L) + 1L)
        val id = UUID.randomUUID().toString()
        val txHash = WalletAddressHelper.generateTxHash("$userId:offline:$assetCode:$nowMs")

        val payload = PlainOfflineTxPayload(
          id = id,
          txType = "SEND",
          assetCode = assetCode,
          amount = amount,
          priceUsd = priceUsd.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0,
          senderTag = cleanSender,
          recipientInput = trimmedRecipient,
          counterparty = counterparty,
          note = cleanNote,
          txHash = txHash,
          kind = VaultRecordKind.OFFLINE_SEND.name,
          createdAtMs = nowMs,
        )

        val envelope = cryptoEngine.encryptTransaction(
          payload = payload,
          ownerUidHash = ownerUidHash,
          prevMacHex = prevMac,
        )

        val entity = EncryptedTransactionEntity(
          id = id,
          ownerUidHash = ownerUidHash,
          txHash = txHash,
          wrappedDekBlob = envelope.wrappedDekBlob,
          ivBase64 = envelope.ivBase64,
          ciphertextBase64 = envelope.ciphertextBase64,
          e2eeVoucher = envelope.e2eeVoucher,
          prevMacHex = prevMac,
          integrityMacHex = envelope.integrityMacHex,
          syncStatus = OfflineSyncStatus.QUEUED_OFFLINE.name,
          kind = VaultRecordKind.OFFLINE_SEND.name,
          createdAtMs = nowMs,
          settledAtMs = null,
          lastError = null,
        )
        dao.insert(entity)

        DecryptedVaultTransaction(
          id = entity.id,
          txType = payload.txType,
          assetCode = payload.assetCode,
          amount = payload.amount,
          priceUsd = payload.priceUsd,
          senderTag = payload.senderTag,
          recipientInput = payload.recipientInput,
          counterparty = payload.counterparty,
          note = payload.note,
          txHash = entity.txHash,
          syncStatus = OfflineSyncStatus.QUEUED_OFFLINE,
          kind = VaultRecordKind.OFFLINE_SEND,
          createdAtMs = nowMs,
          settledAtMs = null,
          lastError = null,
          ivPreview = entity.ivBase64.take(18),
          ciphertextPreview = entity.ciphertextBase64.take(28),
          wrappedDekPreview = entity.wrappedDekBlob.take(22),
          integrityMacPreview = entity.integrityMacHex.take(16),
          e2eeVoucher = entity.e2eeVoucher,
          isIntegrityVerified = true,
        )
      }
      Result.success(created)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun archiveSyncedTransactions(
    userId: String,
    transactions: List<WalletTransaction>,
  ) = withContext(Dispatchers.Default) {
    if (transactions.isEmpty()) return@withContext
    val ownerUidHash = cryptoEngine.hashOwnerUid(userId)
    writeMutex.withLock {
      var latest = dao.getLatestByOwner(ownerUidHash)
      for (tx in transactions.sortedBy { it.createdAt?.toDate()?.time ?: 0L }) {
        if (tx.txHash.isBlank()) continue
        if (dao.countByTxHash(ownerUidHash, tx.txHash) > 0) continue

        val prevMac = latest?.integrityMacHex ?: VaultCryptoEngine.GENESIS_MAC
        val rawTime = tx.createdAt?.toDate()?.time ?: System.currentTimeMillis()
        val nowMs = maxOf(rawTime, (latest?.createdAtMs ?: 0L) + 1L)
        val id = if (tx.id.isNotBlank()) "sync_${tx.id}" else UUID.randomUUID().toString()

        val payload = PlainOfflineTxPayload(
          id = id,
          txType = tx.txType,
          assetCode = tx.assetCode,
          amount = tx.amount,
          priceUsd = tx.priceUsd,
          senderTag = "vault_user",
          recipientInput = tx.counterparty,
          counterparty = tx.counterparty,
          note = tx.note,
          txHash = tx.txHash,
          kind = VaultRecordKind.SYNCED_RECORD.name,
          createdAtMs = nowMs,
        )

        val envelope = cryptoEngine.encryptTransaction(
          payload = payload,
          ownerUidHash = ownerUidHash,
          prevMacHex = prevMac,
        )

        val entity = EncryptedTransactionEntity(
          id = id,
          ownerUidHash = ownerUidHash,
          txHash = tx.txHash,
          wrappedDekBlob = envelope.wrappedDekBlob,
          ivBase64 = envelope.ivBase64,
          ciphertextBase64 = envelope.ciphertextBase64,
          e2eeVoucher = envelope.e2eeVoucher,
          prevMacHex = prevMac,
          integrityMacHex = envelope.integrityMacHex,
          syncStatus = OfflineSyncStatus.SETTLED.name,
          kind = VaultRecordKind.SYNCED_RECORD.name,
          createdAtMs = nowMs,
          settledAtMs = nowMs,
          lastError = null,
        )
        dao.insert(entity)
        latest = entity
      }
    }
  }

  suspend fun cancelQueuedOfflineSend(userId: String, txId: String): Result<Unit> =
    withContext(Dispatchers.Default) {
      try {
        val ownerUidHash = cryptoEngine.hashOwnerUid(userId)
        writeMutex.withLock {
          dao.updateSyncState(
            id = txId,
            ownerUidHash = ownerUidHash,
            syncStatus = OfflineSyncStatus.CANCELLED.name,
            settledAtMs = null,
            lastError = "Cancelled while offline",
          )
        }
        Result.success(Unit)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  suspend fun retryFailedTransaction(userId: String, txId: String): Result<Unit> =
    withContext(Dispatchers.Default) {
      try {
        val ownerUidHash = cryptoEngine.hashOwnerUid(userId)
        writeMutex.withLock {
          dao.updateSyncState(
            id = txId,
            ownerUidHash = ownerUidHash,
            syncStatus = OfflineSyncStatus.QUEUED_OFFLINE.name,
            settledAtMs = null,
            lastError = null,
          )
        }
        Result.success(Unit)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  suspend fun syncPendingTransactions(
    userId: String,
    senderTag: String,
    marketSnapshot: MarketSnapshot,
  ): OfflineSyncSummary = withContext(Dispatchers.Default) {
    val repo = vaultRepository ?: return@withContext OfflineSyncSummary(0, 0, 0, "Cloud repository unavailable")
    syncMutex.withLock {
      val ownerUidHash = cryptoEngine.hashOwnerUid(userId)
      val allAsc = dao.getByOwnerAsc(ownerUidHash)
      val pendingRows = allAsc.filter {
        val s = OfflineSyncStatus.safeValueOf(it.syncStatus)
        s == OfflineSyncStatus.QUEUED_OFFLINE || s == OfflineSyncStatus.SYNCING
      }
      if (pendingRows.isEmpty()) {
        return@withLock OfflineSyncSummary(0, 0, 0, null)
      }

      // Build expected prevMac lookup across the full chain
      val expectedPrevById = mutableMapOf<String, String>()
      var prevMac = VaultCryptoEngine.GENESIS_MAC
      for (row in allAsc) {
        expectedPrevById[row.id] = prevMac
        prevMac = row.integrityMacHex
      }

      var settled = 0
      var failed = 0
      var firstError: String? = null

      for (row in pendingRows) {
        val dec = cryptoEngine.decryptAndVerify(
          id = row.id,
          ownerUidHash = ownerUidHash,
          txHash = row.txHash,
          wrappedDekBlob = row.wrappedDekBlob,
          ivBase64 = row.ivBase64,
          ciphertextBase64 = row.ciphertextBase64,
          e2eeVoucher = row.e2eeVoucher,
          prevMacHex = row.prevMacHex,
          expectedPrevMacHex = expectedPrevById[row.id] ?: row.prevMacHex,
          integrityMacHex = row.integrityMacHex,
          createdAtMs = row.createdAtMs,
        ).getOrNull()

        if (dec == null || !dec.isFullyVerified) {
          failed++
          val msg = "Cryptographic integrity check failed — broadcast blocked"
          if (firstError == null) firstError = msg
          dao.updateSyncState(row.id, ownerUidHash, OfflineSyncStatus.FAILED.name, null, msg)
          continue
        }

        dao.updateSyncState(row.id, ownerUidHash, OfflineSyncStatus.SYNCING.name, null, null)
        val p = dec.payload
        val livePriceUsd = marketSnapshot.usdValueOf(p.assetCode).takeIf { it > 0.0 } ?: p.priceUsd

        val sendResult = repo.sendCrypto(
          userId = userId,
          senderTag = senderTag.ifBlank { p.senderTag },
          assetCode = p.assetCode,
          amount = p.amount,
          priceUsd = livePriceUsd,
          recipientInput = p.recipientInput,
          note = p.note ?: "Settled from Encrypted Offline Vault",
          overrideTxHash = row.txHash,
        )

        if (sendResult.isSuccess) {
          settled++
          dao.updateSyncState(
            id = row.id,
            ownerUidHash = ownerUidHash,
            syncStatus = OfflineSyncStatus.SETTLED.name,
            settledAtMs = System.currentTimeMillis(),
            lastError = null,
          )
        } else {
          val err = sendResult.exceptionOrNull()
          if (isTransientNetworkError(err)) {
            dao.updateSyncState(
              id = row.id,
              ownerUidHash = ownerUidHash,
              syncStatus = OfflineSyncStatus.QUEUED_OFFLINE.name,
              settledAtMs = null,
              lastError = "Waiting for stable internet connection",
            )
            val remaining = dao.getPendingOfflineAsc(ownerUidHash).size
            return@withLock OfflineSyncSummary(settled, failed, remaining, firstError)
          } else {
            failed++
            val msg = err?.localizedMessage ?: "Settlement failed"
            if (firstError == null) firstError = msg
            dao.updateSyncState(row.id, ownerUidHash, OfflineSyncStatus.FAILED.name, null, msg)
          }
        }
      }

      val remaining = dao.getPendingOfflineAsc(ownerUidHash).size
      OfflineSyncSummary(settled, failed, remaining, firstError)
    }
  }

  suspend fun verifyFullDatabaseIntegrity(userId: String): VaultIntegrityReport =
    withContext(Dispatchers.Default) {
      val ownerUidHash = cryptoEngine.hashOwnerUid(userId)
      val asc = dao.getByOwnerAsc(ownerUidHash)
      var prevMac = VaultCryptoEngine.GENESIS_MAC
      var verified = 0
      var tampered = 0

      for (row in asc) {
        val result = cryptoEngine.decryptAndVerify(
          id = row.id,
          ownerUidHash = row.ownerUidHash,
          txHash = row.txHash,
          wrappedDekBlob = row.wrappedDekBlob,
          ivBase64 = row.ivBase64,
          ciphertextBase64 = row.ciphertextBase64,
          e2eeVoucher = row.e2eeVoucher,
          prevMacHex = row.prevMacHex,
          expectedPrevMacHex = prevMac,
          integrityMacHex = row.integrityMacHex,
          createdAtMs = row.createdAtMs,
        ).getOrNull()
        prevMac = row.integrityMacHex
        if (result?.isFullyVerified == true) verified++ else tampered++
      }

      VaultIntegrityReport(
        totalRecords = asc.size,
        verifiedRecords = verified,
        tamperedRecords = tampered,
        keyFingerprint = cryptoEngine.publicKeyFingerprint(),
        chainHeadMac = prevMac.take(16),
        cipherSuite = VaultCryptoEngine.CIPHER_SUITE_SUMMARY,
      )
    }

  fun verifyVoucher(voucher: String): Result<PlainOfflineTxPayload> =
    cryptoEngine.decryptE2eeVoucher(voucher)

  private fun isTransientNetworkError(err: Throwable?): Boolean {
    if (err == null) return false
    val firestoreErr = generateSequence(err) { it.cause }
      .filterIsInstance<FirebaseFirestoreException>()
      .firstOrNull()
    if (firestoreErr != null) {
      return firestoreErr.code in setOf(
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
        FirebaseFirestoreException.Code.ABORTED,
      )
    }
    val msg = err.message?.lowercase().orEmpty()
    return "offline" in msg || "unavailable" in msg || "network" in msg || "timeout" in msg
  }
}
