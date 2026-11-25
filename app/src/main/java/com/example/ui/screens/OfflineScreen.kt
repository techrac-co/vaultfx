package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.DecryptedVaultTransaction
import com.example.data.MarketSnapshot
import com.example.data.VaultIntegrityReport
import com.example.data.local.OfflineSyncStatus
import com.example.data.local.VaultRecordKind
import com.example.model.AssetCatalog
import com.example.model.UserProfile
import com.example.ui.components.AssetBadge
import com.example.ui.components.GradientBorderSurface
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalVaultColors
import com.example.util.NumberFormatter
import com.example.util.WalletAddressHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class OfflineVaultFilter(val label: String) {
  ALL("All Encrypted"),
  QUEUED("Queued Offline"),
  SETTLED("Settled"),
  FAILED("Attention"),
}

@Composable
fun OfflineScreen(
  profile: UserProfile?,
  isDeviceOnline: Boolean,
  manualOfflineHold: Boolean,
  isEffectivelyOffline: Boolean,
  isSyncingOffline: Boolean,
  offlineTransactions: List<DecryptedVaultTransaction>,
  pendingOfflineDebits: Map<String, Double>,
  integrityReport: VaultIntegrityReport?,
  marketSnapshot: MarketSnapshot,
  onSetOfflineHold: (Boolean) -> Unit,
  onOpenOfflineSend: () -> Unit,
  onSyncNow: () -> Unit,
  onCancelQueued: (String) -> Unit,
  onRetryFailed: (String) -> Unit,
  onVerifyIntegrity: () -> Unit,
  onInspectVoucher: (DecryptedVaultTransaction) -> Unit,
) {
  val vaultColors = LocalVaultColors.current
  var filter by remember { mutableStateOf(OfflineVaultFilter.ALL) }
  var showCiphertextMode by remember { mutableStateOf(false) }

  val displayCode = profile?.preferredFiat ?: "USD"
  val displayAsset = AssetCatalog.find(displayCode)

  val pendingTotalUsd = remember(pendingOfflineDebits, marketSnapshot) {
    pendingOfflineDebits.entries.sumOf { (code, amt) -> amt * marketSnapshot.usdValueOf(code) }
  }
  val pendingDisplay = marketSnapshot.convert(pendingTotalUsd, "USD", displayCode)
  val queuedCount = offlineTransactions.count {
    it.syncStatus == OfflineSyncStatus.QUEUED_OFFLINE || it.syncStatus == OfflineSyncStatus.SYNCING
  }

  val filteredTransactions = remember(offlineTransactions, filter) {
    offlineTransactions.filter { tx ->
      when (filter) {
        OfflineVaultFilter.ALL -> true
        OfflineVaultFilter.QUEUED -> tx.syncStatus == OfflineSyncStatus.QUEUED_OFFLINE || tx.syncStatus == OfflineSyncStatus.SYNCING
        OfflineVaultFilter.SETTLED -> tx.syncStatus == OfflineSyncStatus.SETTLED
        OfflineVaultFilter.FAILED -> tx.syncStatus == OfflineSyncStatus.FAILED || tx.syncStatus == OfflineSyncStatus.CANCELLED
      }
    }
  }

  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 620.dp),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Hero Offline E2EE Vault Card
      item {
        GradientBorderSurface(
          shape = RoundedCornerShape(26.dp),
          borderColors = if (isEffectivelyOffline) {
            listOf(vaultColors.gold, vaultColors.violet, vaultColors.cyan)
          } else {
            listOf(vaultColors.positive, vaultColors.cyan, vaultColors.violet)
          },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier
              .background(
                Brush.linearGradient(
                  colors = listOf(
                    (if (isEffectivelyOffline) vaultColors.gold else vaultColors.positive)
                      .copy(alpha = if (vaultColors.isDark) 0.14f else 0.08f),
                    vaultColors.violet.copy(alpha = if (vaultColors.isDark) 0.12f else 0.05f),
                    Color.Transparent,
                  )
                )
              )
              .padding(18.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                  color = (if (isEffectivelyOffline) vaultColors.gold else vaultColors.positive).copy(alpha = 0.16f),
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.size(36.dp),
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      imageVector = if (isEffectivelyOffline) Icons.Rounded.CloudOff else Icons.Rounded.CloudDone,
                      contentDescription = null,
                      tint = if (isEffectivelyOffline) vaultColors.gold else vaultColors.positive,
                      modifier = Modifier.size(20.dp),
                    )
                  }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                  Text(
                    text = "E2EE OFFLINE VAULT",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isEffectivelyOffline) vaultColors.gold else vaultColors.positive,
                  )
                  Text(
                    text = if (!isDeviceOnline) {
                      "Network Disconnected"
                    } else if (manualOfflineHold) {
                      "Offline Hold Active"
                    } else {
                      "Online & Auto-Syncing"
                    },
                    style = MaterialTheme.typography.titleMedium,
                  )
                }
              }

              Surface(
                color = (if (isEffectivelyOffline) vaultColors.gold else vaultColors.positive).copy(alpha = 0.16f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(
                  1.dp,
                  (if (isEffectivelyOffline) vaultColors.gold else vaultColors.positive).copy(alpha = 0.5f),
                ),
              ) {
                Text(
                  text = if (isEffectivelyOffline) "OFFLINE" else "ONLINE",
                  style = MaterialTheme.typography.labelSmall,
                  color = if (isEffectivelyOffline) vaultColors.gold else vaultColors.positive,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
              }
            }

            Spacer(Modifier.height(14.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.Bottom,
            ) {
              Column {
                Text(
                  text = "PENDING OFFLINE DEDUCTION",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AnimatedContent(
                  targetState = "${displayAsset.symbol}${NumberFormatter.formatAsset(pendingDisplay, 2)}",
                  transitionSpec = { fadeIn() togetherWith fadeOut() },
                  label = "offline_pending_anim",
                ) { formatted ->
                  Text(
                    text = formatted,
                    style = MaterialTheme.typography.displayMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("offline_pending_total"),
                  )
                }
              }

              Column(horizontalAlignment = Alignment.End) {
                Text(
                  text = "$queuedCount Queued",
                  style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                  color = vaultColors.gold,
                )
                Text(
                  text = "${offlineTransactions.size} Encrypted Rows",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            Spacer(Modifier.height(14.dp))
            // Offline Hold Mode toggle so user can compose offline transactions on demand or automatically when signal drops
            Surface(
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
              shape = RoundedCornerShape(14.dp),
              border = BorderStroke(1.dp, vaultColors.cardBorder),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Hold Transfers Offline",
                    style = MaterialTheme.typography.titleSmall,
                  )
                  Text(
                    text = if (!isDeviceOnline) {
                      "No internet detected — transfers are queued locally until back online."
                    } else {
                      "Encrypt & hold transfers locally, then auto-deduct when switched back online."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                  checked = !isDeviceOnline || manualOfflineHold,
                  enabled = isDeviceOnline,
                  onCheckedChange = onSetOfflineHold,
                  modifier = Modifier.testTag("offline_hold_switch"),
                )
              }
            }

            Spacer(Modifier.height(14.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Button(
                onClick = onOpenOfflineSend,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.primary,
                  contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                  .weight(1f)
                  .height(50.dp)
                  .testTag("offline_send_button"),
              ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Send Offline", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
              }

              OutlinedButton(
                onClick = onSyncNow,
                enabled = !isSyncingOffline && queuedCount > 0 && !isEffectivelyOffline,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                  .height(50.dp)
                  .testTag("offline_sync_now_button"),
              ) {
                if (isSyncingOffline) {
                  CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                  Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text("Sync ($queuedCount)", style = MaterialTheme.typography.labelLarge)
              }
            }
          }
        }
      }

      // Hardware Keystore & Tamper-Proof Chain Card
      item {
        Surface(
          color = MaterialTheme.colorScheme.surface,
          shape = RoundedCornerShape(20.dp),
          border = BorderStroke(
            1.dp,
            if ((integrityReport?.tamperedRecords ?: 0) > 0) vaultColors.negative else vaultColors.cyan.copy(alpha = 0.45f),
          ),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                  imageVector = Icons.Rounded.EnhancedEncryption,
                  contentDescription = null,
                  tint = vaultColors.cyan,
                  modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column {
                  Text(
                    text = "End-to-End & Hardware Keystore Shield",
                    style = MaterialTheme.typography.titleSmall,
                  )
                  Text(
                    text = integrityReport?.cipherSuite ?: "AES-256-GCM + 256b DEK + ECDH P-256 + HMAC-SHA256",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }

              OutlinedButton(
                onClick = onVerifyIntegrity,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.testTag("verify_vault_integrity_button"),
              ) {
                Icon(Icons.Rounded.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Verify", style = MaterialTheme.typography.labelMedium)
              }
            }

            if (integrityReport != null) {
              Spacer(Modifier.height(10.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                CryptoStatPill(
                  label = "KEY ID",
                  value = integrityReport.keyFingerprint,
                  tint = vaultColors.cyan,
                  modifier = Modifier.weight(1f),
                )
                CryptoStatPill(
                  label = "VERIFIED",
                  value = "${integrityReport.verifiedRecords}/${integrityReport.totalRecords} OK",
                  tint = if (integrityReport.tamperedRecords == 0) vaultColors.positive else vaultColors.negative,
                  modifier = Modifier.weight(1f),
                )
                CryptoStatPill(
                  label = "CHAIN MAC",
                  value = integrityReport.chainHeadMac,
                  tint = vaultColors.violet,
                  modifier = Modifier.weight(1f),
                )
              }
            }
          }
        }
      }

      // Filter Chips + Ciphertext Privacy Mode Toggle
      item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OfflineVaultFilter.entries.forEach { itemFilter ->
              FilterChip(
                selected = filter == itemFilter,
                onClick = { filter = itemFilter },
                label = { Text(itemFilter.label) },
                modifier = Modifier.testTag("offline_filter_${itemFilter.name.lowercase()}"),
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "ENCRYPTED LOCAL LEDGER",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
              onClick = { showCiphertextMode = !showCiphertextMode },
              shape = RoundedCornerShape(50),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
              modifier = Modifier.testTag("toggle_ciphertext_view"),
            ) {
              Icon(
                imageVector = if (showCiphertextMode) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(Modifier.width(6.dp))
              Text(
                text = if (showCiphertextMode) "Show Decrypted" else "Inspect Ciphertext",
                style = MaterialTheme.typography.labelMedium,
              )
            }
          }
        }
      }

      if (filteredTransactions.isEmpty()) {
        item {
          Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, vaultColors.cardBorder),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Surface(
                color = vaultColors.cyan.copy(alpha = 0.14f),
                shape = CircleShape,
                modifier = Modifier.size(54.dp),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Rounded.Key,
                    contentDescription = null,
                    tint = vaultColors.cyan,
                    modifier = Modifier.size(26.dp),
                  )
                }
              }
              Spacer(Modifier.height(12.dp))
              Text("Encrypted Vault Ledger Ready", style = MaterialTheme.typography.titleMedium)
              Spacer(Modifier.height(6.dp))
              Text(
                text = "Send funds even with zero internet. Each transaction is sealed with its own 256-bit AES-GCM key wrapped in hardware Keystore, signed with ECDSA, and linked via HMAC-SHA256 until synced online.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      } else {
        items(filteredTransactions, key = { it.id }) { tx ->
          EncryptedLedgerRowCard(
            tx = tx,
            showCiphertextMode = showCiphertextMode,
            onInspectVoucher = { onInspectVoucher(tx) },
            onCancel = { onCancelQueued(tx.id) },
            onRetry = { onRetryFailed(tx.id) },
          )
        }
      }

      item { Spacer(Modifier.height(16.dp)) }
    }
  }
}

@Composable
private fun CryptoStatPill(
  label: String,
  value: String,
  tint: Color,
  modifier: Modifier = Modifier,
) {
  val vaultColors = LocalVaultColors.current
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant,
    shape = RoundedCornerShape(12.dp),
    border = BorderStroke(1.dp, vaultColors.cardBorder),
    modifier = modifier,
  ) {
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
      )
      Text(
        text = value,
        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
        color = tint,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun EncryptedLedgerRowCard(
  tx: DecryptedVaultTransaction,
  showCiphertextMode: Boolean,
  onInspectVoucher: () -> Unit,
  onCancel: () -> Unit,
  onRetry: () -> Unit,
) {
  val vaultColors = LocalVaultColors.current
  val asset = AssetCatalog.find(tx.assetCode)
  val (statusColor, statusBg) = when (tx.syncStatus) {
    OfflineSyncStatus.QUEUED_OFFLINE -> vaultColors.gold to vaultColors.goldContainer
    OfflineSyncStatus.SYNCING -> vaultColors.cyan to vaultColors.cyan.copy(alpha = 0.14f)
    OfflineSyncStatus.SETTLED -> vaultColors.positive to vaultColors.positiveContainer
    OfflineSyncStatus.FAILED -> vaultColors.negative to vaultColors.negativeContainer
    OfflineSyncStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant to MaterialTheme.colorScheme.surfaceVariant
  }
  val dateStr = remember(tx.createdAtMs) {
    SimpleDateFormat("MMM d, HH:mm:ss", Locale.US).format(Date(tx.createdAtMs))
  }

  Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(20.dp),
    border = BorderStroke(
      1.dp,
      if (!tx.isIntegrityVerified) vaultColors.negative else vaultColors.cardBorder,
    ),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("offline_tx_card_${tx.id}"),
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = if (tx.isIntegrityVerified) Icons.Rounded.Shield else Icons.Rounded.Warning,
            contentDescription = if (tx.isIntegrityVerified) "Integrity verified" else "Integrity warning",
            tint = if (tx.isIntegrityVerified) vaultColors.positive else vaultColors.negative,
            modifier = Modifier.size(16.dp),
          )
          Spacer(Modifier.width(6.dp))
          Text(
            text = if (tx.kind == VaultRecordKind.OFFLINE_SEND) "OFFLINE SEND • $dateStr" else "${tx.txType} ARCHIVE • $dateStr",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Surface(
          color = statusBg,
          shape = RoundedCornerShape(50),
          border = BorderStroke(0.8.dp, statusColor.copy(alpha = 0.45f)),
        ) {
          Text(
            text = tx.syncStatus.name.replace('_', ' '),
            style = MaterialTheme.typography.labelSmall,
            color = statusColor,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
          )
        }
      }

      Spacer(Modifier.height(10.dp))
      if (showCiphertextMode) {
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(10.dp)) {
            Text(
              text = "AES-256-GCM CT: ${tx.ciphertextPreview}…",
              style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
              color = vaultColors.cyan,
            )
            Text(
              text = "DEK WRAP: ${tx.wrappedDekPreview}…  |  IV: ${tx.ivPreview}…",
              style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = "HMAC-SHA256: ${tx.integrityMacPreview}…",
              style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
              color = vaultColors.violet,
            )
          }
        }
      } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
          AssetBadge(asset = asset, size = 36.dp)
          Spacer(Modifier.width(10.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = tx.counterparty,
              style = MaterialTheme.typography.titleSmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            if (!tx.note.isNullOrBlank()) {
              Text(
                text = tx.note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            Text(
              text = "Tx: ${WalletAddressHelper.shortenAddress(tx.txHash)} • MAC ${tx.integrityMacPreview}…",
              style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            )
          }

          Column(horizontalAlignment = Alignment.End) {
            Text(
              text = "${NumberFormatter.formatAsset(tx.amount, asset.decimals)} ${asset.code}",
              style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = "≈ $${NumberFormatter.formatAsset(tx.amount * tx.priceUsd, 2)}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      if (!tx.lastError.isNullOrBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(
          text = tx.lastError,
          style = MaterialTheme.typography.labelMedium,
          color = if (tx.syncStatus == OfflineSyncStatus.FAILED) vaultColors.negative else vaultColors.gold,
        )
      }

      Spacer(Modifier.height(10.dp))
      HorizontalDivider(color = vaultColors.cardBorder.copy(alpha = 0.5f))
      Spacer(Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(
          onClick = onInspectVoucher,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .weight(1f)
            .height(44.dp)
            .testTag("offline_voucher_${tx.id}"),
        ) {
          Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("E2EE Receipt", style = MaterialTheme.typography.labelMedium)
        }

        if (tx.syncStatus == OfflineSyncStatus.FAILED) {
          Button(
            onClick = onRetry,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .height(44.dp)
              .testTag("offline_retry_${tx.id}"),
          ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Retry", style = MaterialTheme.typography.labelMedium)
          }
        }

        if (tx.syncStatus == OfflineSyncStatus.QUEUED_OFFLINE || tx.syncStatus == OfflineSyncStatus.FAILED) {
          OutlinedButton(
            onClick = onCancel,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = vaultColors.negative),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .height(44.dp)
              .testTag("offline_cancel_${tx.id}"),
          ) {
            Icon(Icons.Rounded.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Cancel", style = MaterialTheme.typography.labelMedium)
          }
        }
      }
    }
  }
}
