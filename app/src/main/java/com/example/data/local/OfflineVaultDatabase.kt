package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

enum class OfflineSyncStatus {
  QUEUED_OFFLINE,
  SYNCING,
  SETTLED,
  FAILED,
  CANCELLED;

  companion object {
    fun safeValueOf(raw: String): OfflineSyncStatus =
      entries.firstOrNull { it.name == raw.uppercase() } ?: QUEUED_OFFLINE
  }
}

enum class VaultRecordKind {
  OFFLINE_SEND,
  SYNCED_RECORD;

  companion object {
    fun safeValueOf(raw: String): VaultRecordKind =
      entries.firstOrNull { it.name == raw.uppercase() } ?: OFFLINE_SEND
  }
}

@Entity(
  tableName = "encrypted_transactions",
  indices = [
    Index(value = ["ownerUidHash", "createdAtMs"]),
    Index(value = ["ownerUidHash", "txHash"], unique = true),
  ],
)
data class EncryptedTransactionEntity(
  @androidx.room.PrimaryKey val id: String,
  val ownerUidHash: String,
  val txHash: String,
  val wrappedDekBlob: String,
  val ivBase64: String,
  val ciphertextBase64: String,
  val e2eeVoucher: String,
  val prevMacHex: String,
  val integrityMacHex: String,
  val syncStatus: String,
  val kind: String,
  val createdAtMs: Long,
  val settledAtMs: Long? = null,
  val lastError: String? = null,
)

@Dao
interface EncryptedTransactionDao {
  @Query("SELECT * FROM encrypted_transactions WHERE ownerUidHash = :ownerUidHash ORDER BY createdAtMs DESC")
  fun observeByOwner(ownerUidHash: String): Flow<List<EncryptedTransactionEntity>>

  @Query("SELECT * FROM encrypted_transactions WHERE ownerUidHash = :ownerUidHash ORDER BY createdAtMs ASC")
  suspend fun getByOwnerAsc(ownerUidHash: String): List<EncryptedTransactionEntity>

  @Query("SELECT * FROM encrypted_transactions WHERE ownerUidHash = :ownerUidHash ORDER BY createdAtMs DESC LIMIT 1")
  suspend fun getLatestByOwner(ownerUidHash: String): EncryptedTransactionEntity?

  @Query("SELECT * FROM encrypted_transactions WHERE ownerUidHash = :ownerUidHash AND syncStatus IN ('QUEUED_OFFLINE', 'SYNCING') ORDER BY createdAtMs ASC")
  suspend fun getPendingOfflineAsc(ownerUidHash: String): List<EncryptedTransactionEntity>

  @Query("SELECT COUNT(*) FROM encrypted_transactions WHERE ownerUidHash = :ownerUidHash AND txHash = :txHash")
  suspend fun countByTxHash(ownerUidHash: String, txHash: String): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(entity: EncryptedTransactionEntity)

  @Query("UPDATE encrypted_transactions SET syncStatus = :syncStatus, settledAtMs = :settledAtMs, lastError = :lastError WHERE id = :id AND ownerUidHash = :ownerUidHash")
  suspend fun updateSyncState(
    id: String,
    ownerUidHash: String,
    syncStatus: String,
    settledAtMs: Long?,
    lastError: String?,
  )
}

@Database(
  entities = [EncryptedTransactionEntity::class],
  version = 1,
  exportSchema = false,
)
abstract class OfflineVaultDatabase : RoomDatabase() {
  abstract fun encryptedTransactionDao(): EncryptedTransactionDao

  companion object {
    @Volatile
    private var INSTANCE: OfflineVaultDatabase? = null

    fun getInstance(context: Context): OfflineVaultDatabase {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: Room.databaseBuilder(
          context.applicationContext,
          OfflineVaultDatabase::class.java,
          "vaultfx_encrypted_ledger.db",
        ).build().also { INSTANCE = it }
      }
    }
  }
}
