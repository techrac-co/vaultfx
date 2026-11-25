package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class TransactionType {
  BUY,
  SELL,
  SEND,
  RECEIVE,
  DEPOSIT,
  ESCROW_LOCK,
  ESCROW_RELEASE;

  companion object {
    fun safeValueOf(raw: String): TransactionType =
      entries.firstOrNull { it.name == raw.uppercase() } ?: DEPOSIT
  }
}

enum class TransferStatus {
  PENDING,
  CLAIMED;

  companion object {
    fun safeValueOf(raw: String): TransferStatus =
      entries.firstOrNull { it.name == raw.uppercase() } ?: PENDING
  }
}

enum class EscrowStatus {
  FUNDED,
  DELIVERED,
  DISPUTED,
  RELEASED,
  REFUNDED,
  CLAIMED;

  companion object {
    fun safeValueOf(raw: String): EscrowStatus =
      entries.firstOrNull { it.name == raw.uppercase() } ?: FUNDED
  }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val displayName: String = "",
  val payTag: String = "",
  val preferredFiat: String = "USD",
  val walletSeed: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class PayTagEntry(
  @DocumentId val id: String = "",
  val ownerUid: String = "",
  val payTag: String = "",
  val displayName: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class WalletHolding(
  @DocumentId val id: String = "",
  val assetCode: String = "",
  val balance: Double = 0.0,
  val avgBuyPriceUsd: Double = 0.0,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class WalletTransaction(
  @DocumentId val id: String = "",
  val txType: String = "BUY",
  val assetCode: String = "",
  val amount: Double = 0.0,
  val quoteCode: String = "USD",
  val quoteAmount: Double = 0.0,
  val priceUsd: Double = 0.0,
  val feeAmount: Double = 0.0,
  val counterparty: String = "",
  val txHash: String = "",
  val status: String = "COMPLETED",
  val note: String? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
) {
  val parsedType: TransactionType
    get() = TransactionType.safeValueOf(txType)
}

@Keep
@IgnoreExtraProperties
data class SavedPair(
  @DocumentId val id: String = "",
  val baseCode: String = "",
  val quoteCode: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class PeerTransfer(
  @DocumentId val id: String = "",
  val senderUid: String = "",
  val senderTag: String = "",
  val recipientUid: String = "",
  val recipientTag: String = "",
  val assetCode: String = "",
  val amount: Double = 0.0,
  val txHash: String = "",
  val note: String? = null,
  val status: String = "PENDING",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val parsedStatus: TransferStatus
    get() = TransferStatus.safeValueOf(status)
}

@Keep
@IgnoreExtraProperties
data class EscrowDeal(
  @DocumentId val id: String = "",
  val buyerUid: String = "",
  val buyerTag: String = "",
  val sellerUid: String = "",
  val sellerTag: String = "",
  val participantUids: List<String> = emptyList(),
  val assetCode: String = "",
  val amount: Double = 0.0,
  val title: String = "",
  val agreedTerms: String? = null,
  val txHash: String = "",
  val status: String = "FUNDED",
  val disputeReason: String? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val parsedStatus: EscrowStatus
    get() = EscrowStatus.safeValueOf(status)
}
