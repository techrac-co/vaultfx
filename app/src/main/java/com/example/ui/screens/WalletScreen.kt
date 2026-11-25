package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.MarketSnapshot
import com.example.model.AssetCatalog
import com.example.model.EscrowDeal
import com.example.model.PeerTransfer
import com.example.model.TransactionType
import com.example.model.UserProfile
import com.example.model.WalletHolding
import com.example.model.WalletTransaction
import com.example.ui.UiState
import com.example.ui.components.AssetBadge
import com.example.ui.components.AssetPickerSheet
import com.example.ui.components.BouncySurface
import com.example.ui.components.ChangeBadge
import com.example.ui.components.GradientBorderSurface
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalVaultColors
import com.example.util.NumberFormatter
import com.example.util.WalletAddressHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WalletScreen(
  currentUserId: String,
  profileState: UiState<UserProfile?>,
  holdingsState: UiState<List<WalletHolding>>,
  transactions: List<WalletTransaction>,
  incomingTransfers: List<PeerTransfer>,
  escrows: List<EscrowDeal>,
  marketSnapshot: MarketSnapshot,
  onOpenSend: (String?) -> Unit,
  onOpenReceive: (String?) -> Unit,
  onOpenDeposit: () -> Unit,
  onOpenEscrowTab: () -> Unit,
  onChangePreferredFiat: (String) -> Unit,
  onClaimTransfer: (PeerTransfer) -> Unit,
  onRetryInitialize: () -> Unit,
  onCopyTagMessage: (String) -> Unit,
) {
  val vaultColors = LocalVaultColors.current
  val clipboard = LocalClipboardManager.current
  var pickingDisplayFiat by remember { mutableStateOf(false) }

  val profile = (profileState as? UiState.Success)?.data
  val holdings = (holdingsState as? UiState.Success)?.data.orEmpty()
  val errorMsg = (holdingsState as? UiState.Error)?.message ?: (profileState as? UiState.Error)?.message

  val displayCode = profile?.preferredFiat ?: "USD"
  val displayAsset = AssetCatalog.find(displayCode)

  val totalUsd = remember(holdings, marketSnapshot) {
    holdings.sumOf { it.balance * marketSnapshot.usdValueOf(it.assetCode) }
  }
  val totalDisplayValue = remember(totalUsd, displayCode, marketSnapshot) {
    marketSnapshot.convert(totalUsd, "USD", displayCode)
  }

  val actionableEscrowsCount = remember(escrows, currentUserId) {
    escrows.count { needsUserAction(it, currentUserId) }
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
      // Hero Vault Portfolio Card
      item {
        GradientBorderSurface(
          shape = RoundedCornerShape(26.dp),
          borderColors = listOf(vaultColors.positive, vaultColors.cyan, vaultColors.violet),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Box(modifier = Modifier.fillMaxWidth()) {
            Image(
              painter = painterResource(id = R.drawable.img_vault_hero),
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier.matchParentSize(),
            )
            Box(
              modifier = Modifier
                .matchParentSize()
                .background(
                  Brush.verticalGradient(
                    colors = listOf(
                      Color(0xC806080E),
                      Color(0xEE0B1018),
                    ),
                  )
                ),
            )

            Column(modifier = Modifier.padding(20.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "TOTAL VAULT BALANCE",
                  style = MaterialTheme.typography.labelSmall,
                  color = Color(0xFF94A3B8),
                )
                BouncySurface(
                  onClick = { pickingDisplayFiat = true },
                  modifier = Modifier.testTag("wallet_display_fiat_pill"),
                  color = Color(0xFF1F2A3B),
                  shape = RoundedCornerShape(50),
                  border = BorderStroke(1.dp, Color(0xFF334155)),
                ) {
                  Text(
                    text = "${displayAsset.code} ▾",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF2EFFA8),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                  )
                }
              }

              Spacer(Modifier.height(6.dp))
              AnimatedContent(
                targetState = "${displayAsset.symbol}${NumberFormatter.formatAsset(totalDisplayValue, 2)}",
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "wallet_bal_anim",
              ) { formatted ->
                Text(
                  text = formatted,
                  style = MaterialTheme.typography.displayLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                  color = Color.White,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.testTag("wallet_total_balance"),
                )
              }

              if (profile != null && profile.payTag.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                BouncySurface(
                  onClick = {
                    clipboard.setText(AnnotatedString("@${profile.payTag}"))
                    onCopyTagMessage("Copied PayTag @${profile.payTag}")
                  },
                  modifier = Modifier.testTag("wallet_copy_paytag"),
                  color = Color(0x33FFCA4B),
                  shape = RoundedCornerShape(10.dp),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Text(
                      text = "PayTag: @${profile.payTag}",
                      style = MaterialTheme.typography.labelSmall,
                      color = Color(0xFFFFCA4B),
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                      imageVector = Icons.Rounded.ContentCopy,
                      contentDescription = "Copy PayTag",
                      tint = Color(0xFFFFCA4B),
                      modifier = Modifier.size(13.dp),
                    )
                  }
                }
              }

              Spacer(Modifier.height(18.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                WalletActionPill(
                  icon = Icons.AutoMirrored.Rounded.CallMade,
                  label = "Send",
                  containerColor = Color(0xFF2EFFA8),
                  contentColor = Color(0xFF032315),
                  onClick = { onOpenSend(null) },
                  modifier = Modifier
                    .weight(1f)
                    .testTag("wallet_send_button"),
                )
                WalletActionPill(
                  icon = Icons.Rounded.QrCode2,
                  label = "Receive",
                  containerColor = Color(0xFF1F2A3B),
                  contentColor = Color.White,
                  onClick = { onOpenReceive(null) },
                  modifier = Modifier
                    .weight(1f)
                    .testTag("wallet_receive_button"),
                )
                WalletActionPill(
                  icon = Icons.Rounded.Add,
                  label = "Deposit",
                  containerColor = Color(0xFF1F2A3B),
                  contentColor = Color(0xFFFFCA4B),
                  onClick = onOpenDeposit,
                  modifier = Modifier
                    .weight(1f)
                    .testTag("wallet_deposit_button"),
                )
              }
            }
          }
        }
      }

      // Error State with Retry
      if (errorMsg != null) {
        item {
          Surface(
            color = vaultColors.negativeContainer,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, vaultColors.negative.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(Icons.Rounded.Warning, contentDescription = null, tint = vaultColors.negative)
              Spacer(Modifier.width(10.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text("Wallet sync issue", style = MaterialTheme.typography.titleSmall, color = vaultColors.negative)
                Text(errorMsg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
              }
              Button(
                onClick = onRetryInitialize,
                colors = ButtonDefaults.buttonColors(containerColor = vaultColors.negative),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("wallet_retry_button"),
              ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Retry")
              }
            }
          }
        }
      }

      // Actionable Escrow Alert Banner
      if (actionableEscrowsCount > 0) {
        item {
          BouncySurface(
            onClick = onOpenEscrowTab,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("wallet_escrow_alert"),
            color = vaultColors.violet.copy(alpha = 0.16f),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.2.dp, vaultColors.violet.copy(alpha = 0.7f)),
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(Icons.Rounded.Shield, contentDescription = null, tint = vaultColors.cyan, modifier = Modifier.size(26.dp))
              Spacer(Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "$actionableEscrowsCount Escrow Deal${if (actionableEscrowsCount > 1) "s" else ""} Ready",
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text = "Tap to mark delivered, release, or claim escrow funds.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Text(
                text = "Open →",
                style = MaterialTheme.typography.labelLarge,
                color = vaultColors.cyan,
              )
            }
          }
        }
      }

      // Incoming Peer Transfers Banner
      if (incomingTransfers.isNotEmpty()) {
        items(incomingTransfers, key = { "incoming_${it.id}" }) { transfer ->
          val asset = AssetCatalog.find(transfer.assetCode)
          Surface(
            color = vaultColors.positiveContainer,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, vaultColors.positive),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              AssetBadge(asset = asset, size = 38.dp)
              Spacer(Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Incoming ${transfer.assetCode} Transfer",
                  style = MaterialTheme.typography.titleSmall,
                  color = vaultColors.positive,
                )
                Text(
                  text = "+${NumberFormatter.formatAsset(transfer.amount, asset.decimals)} ${transfer.assetCode} from @${transfer.senderTag}",
                  style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                  color = MaterialTheme.colorScheme.onSurface,
                )
                if (!transfer.note.isNullOrBlank()) {
                  Text(
                    text = "\"${transfer.note}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              Button(
                onClick = { onClaimTransfer(transfer) },
                colors = ButtonDefaults.buttonColors(
                  containerColor = vaultColors.positive,
                  contentColor = Color(0xFF042617),
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("claim_transfer_${transfer.id}"),
              ) {
                Text("Claim", style = MaterialTheme.typography.labelLarge)
              }
            }
          }
        }
      }

      // Holdings List
      item {
        Text(
          text = "PORTFOLIO ASSETS",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      if (holdings.isEmpty() && errorMsg == null) {
        item {
          Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, vaultColors.cardBorder),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(20.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Icon(
                Icons.Rounded.AccountBalanceWallet,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp),
              )
              Spacer(Modifier.height(8.dp))
              Text("Initializing your multi-currency wallet...", style = MaterialTheme.typography.bodyMedium)
            }
          }
        }
      } else if (holdings.isNotEmpty()) {
        val nonZeroHoldings = holdings.filter { it.balance > 0.0 }
        if (nonZeroHoldings.isEmpty()) {
          item {
            Surface(
              color = MaterialTheme.colorScheme.surface,
              shape = RoundedCornerShape(18.dp),
              border = BorderStroke(1.dp, vaultColors.cardBorder),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Icon(
                  Icons.Rounded.AccountBalanceWallet,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(30.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text("No funded balances yet", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                  text = "Tap Deposit or Receive above to fund your multi-currency wallet.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        } else {
          items(nonZeroHoldings, key = { "hold_${it.assetCode}" }) { holding ->
          val asset = AssetCatalog.find(holding.assetCode)
          val valueInPreferred = marketSnapshot.convert(holding.balance, holding.assetCode, displayCode)
          val chg = marketSnapshot.change24hPct[holding.assetCode] ?: 0.0
          val isCrypto = asset.category.name == "CRYPTO"

          BouncySurface(
            onClick = {
              if (isCrypto) onOpenReceive(holding.assetCode) else onOpenDeposit()
            },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("holding_row_${holding.assetCode}"),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, vaultColors.cardBorder),
            pressedScale = 0.98f,
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              AssetBadge(asset = asset, size = 38.dp)
              Spacer(Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = asset.code,
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                  )
                  Spacer(Modifier.width(8.dp))
                  ChangeBadge(changePct = chg)
                }
                Text(
                  text = asset.name,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }

              Column(horizontalAlignment = Alignment.End) {
                Text(
                  text = "${NumberFormatter.formatAsset(holding.balance, asset.decimals)} ${asset.code}",
                  style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                )
                Text(
                  text = "${displayAsset.symbol}${NumberFormatter.formatAsset(valueInPreferred, 2)}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
          }
        }
      }

      // Transaction Activity Ledger
      item {
        Text(
          text = "TRANSACTION ACTIVITY",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 6.dp),
        )
      }

      if (transactions.isEmpty()) {
        item {
          Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, vaultColors.cardBorder),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = "No transfers or trades yet. Use Send, Receive, Escrow, or Trade to see activity here.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(18.dp),
            )
          }
        }
      } else {
        items(transactions, key = { "tx_${it.id}" }) { tx ->
          val asset = AssetCatalog.find(tx.assetCode)
          val type = tx.parsedType
          val (icon, tint, sign) = when (type) {
            TransactionType.SEND -> Triple(Icons.AutoMirrored.Rounded.CallMade, vaultColors.negative, "-")
            TransactionType.RECEIVE -> Triple(Icons.AutoMirrored.Rounded.CallReceived, vaultColors.positive, "+")
            TransactionType.BUY -> Triple(Icons.Rounded.SwapHoriz, vaultColors.positive, "+")
            TransactionType.SELL -> Triple(Icons.Rounded.SwapHoriz, vaultColors.gold, "-")
            TransactionType.DEPOSIT -> Triple(Icons.Rounded.Add, vaultColors.cyan, "+")
            TransactionType.ESCROW_LOCK -> Triple(Icons.Rounded.Lock, vaultColors.violet, "-")
            TransactionType.ESCROW_RELEASE -> Triple(Icons.Rounded.VerifiedUser, vaultColors.positive, "+")
          }
          val label = when (type) {
            TransactionType.ESCROW_LOCK -> "ESCROW LOCK ${tx.assetCode}"
            TransactionType.ESCROW_RELEASE -> "ESCROW CLAIM ${tx.assetCode}"
            else -> "${type.name} ${tx.assetCode}"
          }
          val dateStr = remember(tx.createdAt) {
            SimpleDateFormat("MMM d, HH:mm", Locale.US).format(tx.createdAt?.toDate() ?: Date())
          }

          Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, vaultColors.cardBorder),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Surface(
                color = tint.copy(alpha = 0.14f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(38.dp),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                }
              }
              Spacer(Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = label,
                  style = MaterialTheme.typography.titleSmall,
                )
                Text(
                  text = "${tx.counterparty} • $dateStr",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = "Hash: ${WalletAddressHelper.shortenAddress(tx.txHash)}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
              }

              Column(horizontalAlignment = Alignment.End) {
                Text(
                  text = "$sign${NumberFormatter.formatAsset(tx.amount, asset.decimals)} ${tx.assetCode}",
                  style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                  color = tint,
                )
                val statusColor = when (tx.status.uppercase(Locale.US)) {
                  "PENDING" -> vaultColors.gold
                  "FAILED", "CANCELLED" -> vaultColors.negative
                  else -> vaultColors.positive
                }
                Text(
                  text = tx.status,
                  style = MaterialTheme.typography.labelSmall,
                  color = statusColor,
                )
              }
            }
          }
        }
      }

      item { Spacer(Modifier.height(16.dp)) }
    }
  }

  if (pickingDisplayFiat) {
    AssetPickerSheet(
      title = "Select Portfolio Display Currency",
      assets = AssetCatalog.settlementFiats,
      selectedCode = displayCode,
      onSelect = { onChangePreferredFiat(it.code) },
      onDismiss = { pickingDisplayFiat = false },
    )
  }
}

@Composable
private fun WalletActionPill(
  icon: ImageVector,
  label: String,
  containerColor: Color,
  contentColor: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BouncySurface(
    onClick = onClick,
    modifier = modifier.minimumInteractiveComponentSize(),
    color = containerColor,
    shape = RoundedCornerShape(14.dp),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(6.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = contentColor,
        maxLines = 1,
      )
    }
  }
}
