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
import androidx.compose.material.icons.rounded.AddModerator
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.MarketSnapshot
import com.example.model.AssetCatalog
import com.example.model.EscrowDeal
import com.example.model.EscrowStatus
import com.example.model.UserProfile
import com.example.ui.UiState
import com.example.ui.components.AssetBadge
import com.example.ui.components.BouncySurface
import com.example.ui.components.GradientBorderSurface
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalVaultColors
import com.example.util.NumberFormatter
import com.example.util.WalletAddressHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class EscrowFilter(val label: String) {
  ALL("All Deals"),
  ACTION("Action Needed"),
  BUYER("As Buyer"),
  SELLER("As Seller"),
  COMPLETED("Settled"),
}

@Composable
fun EscrowScreen(
  currentUserId: String,
  profile: UserProfile?,
  escrowsState: UiState<List<EscrowDeal>>,
  marketSnapshot: MarketSnapshot,
  onOpenCreateEscrow: () -> Unit,
  onMarkDelivered: (EscrowDeal) -> Unit,
  onReleaseEscrow: (EscrowDeal) -> Unit,
  onRefundEscrow: (EscrowDeal) -> Unit,
  onDisputeEscrow: (EscrowDeal, String) -> Unit,
  onClaimEscrow: (EscrowDeal) -> Unit,
  onRetryInitialize: () -> Unit,
) {
  val vaultColors = LocalVaultColors.current
  var filter by remember { mutableStateOf(EscrowFilter.ALL) }
  var disputeTarget by remember { mutableStateOf<EscrowDeal?>(null) }
  var disputeReasonText by remember { mutableStateOf("") }

  val escrows = (escrowsState as? UiState.Success)?.data.orEmpty()
  val errorMsg = (escrowsState as? UiState.Error)?.message
  val displayCode = profile?.preferredFiat ?: "USD"
  val displayAsset = AssetCatalog.find(displayCode)

  val activeLockedTotalUsd = remember(escrows, marketSnapshot) {
    escrows
      .filter { it.parsedStatus != EscrowStatus.CLAIMED }
      .sumOf { it.amount * marketSnapshot.usdValueOf(it.assetCode) }
  }
  val activeLockedDisplay = marketSnapshot.convert(activeLockedTotalUsd, "USD", displayCode)
  val activeDealsCount = escrows.count { it.parsedStatus != EscrowStatus.CLAIMED }
  val actionNeededCount = escrows.count { needsUserAction(it, currentUserId) }

  val filteredEscrows = remember(escrows, filter, currentUserId) {
    escrows.filter { deal ->
      when (filter) {
        EscrowFilter.ALL -> true
        EscrowFilter.ACTION -> needsUserAction(deal, currentUserId)
        EscrowFilter.BUYER -> deal.buyerUid == currentUserId
        EscrowFilter.SELLER -> deal.sellerUid == currentUserId
        EscrowFilter.COMPLETED -> deal.parsedStatus == EscrowStatus.CLAIMED
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
      // Hero Vault Escrow Shield Card
      item {
        GradientBorderSurface(
          shape = RoundedCornerShape(26.dp),
          borderColors = listOf(
            vaultColors.cyan,
            vaultColors.violet,
            vaultColors.positive,
          ),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier
              .background(
                Brush.linearGradient(
                  colors = listOf(
                    vaultColors.violet.copy(alpha = if (vaultColors.isDark) 0.16f else 0.08f),
                    vaultColors.cyan.copy(alpha = if (vaultColors.isDark) 0.10f else 0.05f),
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
                  color = vaultColors.cyan.copy(alpha = 0.16f),
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.size(34.dp),
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      imageVector = Icons.Rounded.VerifiedUser,
                      contentDescription = null,
                      tint = vaultColors.cyan,
                      modifier = Modifier.size(20.dp),
                    )
                  }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                  Text(
                    text = "VAULTFX SMART ESCROW",
                    style = MaterialTheme.typography.labelSmall,
                    color = vaultColors.cyan,
                  )
                  Text(
                    text = "Protected P2P Settlement",
                    style = MaterialTheme.typography.titleMedium,
                  )
                }
              }

              if (actionNeededCount > 0) {
                Surface(
                  color = vaultColors.goldContainer,
                  shape = RoundedCornerShape(50),
                  border = BorderStroke(1.dp, vaultColors.gold.copy(alpha = 0.55f)),
                ) {
                  Text(
                    text = "$actionNeededCount ACTION",
                    style = MaterialTheme.typography.labelSmall,
                    color = vaultColors.gold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                  )
                }
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
                  text = "LOCKED IN ESCROW",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AnimatedContent(
                  targetState = "${displayAsset.symbol}${NumberFormatter.formatAsset(activeLockedDisplay, 2)}",
                  transitionSpec = { fadeIn() togetherWith fadeOut() },
                  label = "escrow_total_anim",
                ) { formatted ->
                  Text(
                    text = formatted,
                    style = MaterialTheme.typography.displayMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("escrow_total_locked"),
                  )
                }
              }

              Column(horizontalAlignment = Alignment.End) {
                Text(
                  text = "$activeDealsCount Active",
                  style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                  color = vaultColors.positive,
                )
                Text(
                  text = "${escrows.size} Total Deals",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            Spacer(Modifier.height(16.dp))
            Button(
              onClick = onOpenCreateEscrow,
              shape = RoundedCornerShape(16.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
              ),
              modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("create_escrow_button"),
            ) {
              Icon(Icons.Rounded.AddModerator, contentDescription = null, modifier = Modifier.size(20.dp))
              Spacer(Modifier.width(8.dp))
              Text(
                text = "Create Protected Escrow Deal",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
              )
            }
          }
        }
      }

      // Filter Chips
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          EscrowFilter.entries.forEach { itemFilter ->
            FilterChip(
              selected = filter == itemFilter,
              onClick = { filter = itemFilter },
              label = { Text(itemFilter.label) },
              modifier = Modifier.testTag("escrow_filter_${itemFilter.name.lowercase()}"),
            )
          }
        }
      }

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
                Text("Escrow sync issue", style = MaterialTheme.typography.titleSmall, color = vaultColors.negative)
                Text(errorMsg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
              }
              Button(
                onClick = onRetryInitialize,
                colors = ButtonDefaults.buttonColors(containerColor = vaultColors.negative),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("escrow_retry_button"),
              ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Retry")
              }
            }
          }
        }
      }

      if (filteredEscrows.isEmpty() && errorMsg == null) {
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
                color = vaultColors.violet.copy(alpha = 0.14f),
                shape = CircleShape,
                modifier = Modifier.size(54.dp),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = vaultColors.violet,
                    modifier = Modifier.size(26.dp),
                  )
                }
              }
              Spacer(Modifier.height(12.dp))
              Text(
                text = if (escrows.isEmpty()) "Zero-Trust Peer Escrow" else "No deals in this filter",
                style = MaterialTheme.typography.titleMedium,
              )
              Spacer(Modifier.height(6.dp))
              Text(
                text = "Lock crypto with any @payTag peer. Funds stay safely held until the seller marks delivery and the buyer releases—or can be refunded or disputed transparently.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      } else {
        items(filteredEscrows, key = { it.id }) { deal ->
          EscrowDealCard(
            deal = deal,
            currentUserId = currentUserId,
            marketSnapshot = marketSnapshot,
            onMarkDelivered = { onMarkDelivered(deal) },
            onRelease = { onReleaseEscrow(deal) },
            onRefund = { onRefundEscrow(deal) },
            onOpenDispute = {
              disputeReasonText = ""
              disputeTarget = deal
            },
            onClaim = { onClaimEscrow(deal) },
          )
        }
      }

      item { Spacer(Modifier.height(16.dp)) }
    }
  }

  val target = disputeTarget
  if (target != null) {
    AlertDialog(
      onDismissRequest = { disputeTarget = null },
      title = { Text("Open Escrow Dispute", style = MaterialTheme.typography.titleLarge) },
      text = {
        Column {
          Text(
            text = "Flag \"${target.title}\" (${target.amount} ${target.assetCode}) for review. The buyer can still release or the seller can still refund at any time.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(12.dp))
          OutlinedTextField(
            value = disputeReasonText,
            onValueChange = { if (it.length <= 160) disputeReasonText = it },
            label = { Text("Dispute Reason") },
            placeholder = { Text("Describe the issue (min 3 chars)") },
            singleLine = false,
            maxLines = 3,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("dispute_reason_input"),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onDisputeEscrow(target, disputeReasonText)
            disputeTarget = null
          },
          enabled = disputeReasonText.trim().length >= 3,
          colors = ButtonDefaults.buttonColors(containerColor = vaultColors.negative),
          modifier = Modifier.testTag("confirm_dispute_button"),
        ) {
          Text("Flag Dispute")
        }
      },
      dismissButton = {
        TextButton(onClick = { disputeTarget = null }) {
          Text("Cancel")
        }
      },
    )
  }
}

@Composable
private fun EscrowDealCard(
  deal: EscrowDeal,
  currentUserId: String,
  marketSnapshot: MarketSnapshot,
  onMarkDelivered: () -> Unit,
  onRelease: () -> Unit,
  onRefund: () -> Unit,
  onOpenDispute: () -> Unit,
  onClaim: () -> Unit,
) {
  val vaultColors = LocalVaultColors.current
  val status = deal.parsedStatus
  val isBuyer = deal.buyerUid == currentUserId
  val isSeller = deal.sellerUid == currentUserId
  val asset = AssetCatalog.find(deal.assetCode)
  val usdValue = deal.amount * marketSnapshot.usdValueOf(deal.assetCode)

  val (statusColor, statusBg) = when (status) {
    EscrowStatus.FUNDED -> vaultColors.cyan to vaultColors.cyan.copy(alpha = 0.14f)
    EscrowStatus.DELIVERED -> vaultColors.gold to vaultColors.goldContainer
    EscrowStatus.DISPUTED -> vaultColors.negative to vaultColors.negativeContainer
    EscrowStatus.RELEASED -> vaultColors.positive to vaultColors.positiveContainer
    EscrowStatus.REFUNDED -> vaultColors.violet to vaultColors.violet.copy(alpha = 0.16f)
    EscrowStatus.CLAIMED -> MaterialTheme.colorScheme.onSurfaceVariant to MaterialTheme.colorScheme.surfaceVariant
  }

  val dateText = remember(deal.createdAt) {
    SimpleDateFormat("MMM d, HH:mm", Locale.US).format(deal.createdAt?.toDate() ?: Date())
  }

  Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(22.dp),
    border = BorderStroke(
      width = if (needsUserAction(deal, currentUserId)) 1.3.dp else 1.dp,
      color = if (needsUserAction(deal, currentUserId)) statusColor.copy(alpha = 0.65f) else vaultColors.cardBorder,
    ),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("escrow_card_${deal.id}"),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Surface(
            color = if (isBuyer) vaultColors.cyan.copy(alpha = 0.14f) else vaultColors.violet.copy(alpha = 0.16f),
            shape = RoundedCornerShape(8.dp),
          ) {
            Text(
              text = if (isBuyer) "BUYER" else "SELLER",
              style = MaterialTheme.typography.labelSmall,
              color = if (isBuyer) vaultColors.cyan else vaultColors.violet,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
          }
          Spacer(Modifier.width(8.dp))
          Text(
            text = dateText,
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
            text = status.name,
            style = MaterialTheme.typography.labelSmall,
            color = statusColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }
      }

      Spacer(Modifier.height(12.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        AssetBadge(asset = asset, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = deal.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "@${deal.buyerTag}  →  @${deal.sellerTag}",
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = "${NumberFormatter.formatAsset(deal.amount, asset.decimals)} ${asset.code}",
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = "≈ $${NumberFormatter.formatAsset(usdValue, 2)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      if (!deal.agreedTerms.isNullOrBlank()) {
        Spacer(Modifier.height(10.dp))
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "Terms: ${deal.agreedTerms}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          )
        }
      }

      if (status == EscrowStatus.DISPUTED && !deal.disputeReason.isNullOrBlank()) {
        Spacer(Modifier.height(8.dp))
        Surface(
          color = vaultColors.negativeContainer,
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, vaultColors.negative.copy(alpha = 0.45f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "Dispute: ${deal.disputeReason}",
            style = MaterialTheme.typography.bodyMedium,
            color = vaultColors.negative,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          )
        }
      }

      Spacer(Modifier.height(12.dp))
      EscrowStepTimeline(status = status)

      Spacer(Modifier.height(6.dp))
      Text(
        text = "Contract: ${WalletAddressHelper.shortenAddress(deal.txHash)}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
      )

      // Role-Based Action Row
      val showClaim = (status == EscrowStatus.RELEASED && isSeller) || (status == EscrowStatus.REFUNDED && isBuyer)
      val showSellerDelivered = status == EscrowStatus.FUNDED && isSeller
      val showBuyerRelease = isBuyer && status in setOf(EscrowStatus.FUNDED, EscrowStatus.DELIVERED, EscrowStatus.DISPUTED)
      val showSellerRefund = isSeller && status in setOf(EscrowStatus.FUNDED, EscrowStatus.DELIVERED, EscrowStatus.DISPUTED)
      val showDispute = status in setOf(EscrowStatus.FUNDED, EscrowStatus.DELIVERED)

      if (showClaim || showSellerDelivered || showBuyerRelease || showSellerRefund || showDispute) {
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = vaultColors.cardBorder.copy(alpha = 0.5f))
        Spacer(Modifier.height(10.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (showClaim) {
            Button(
              onClick = onClaim,
              colors = ButtonDefaults.buttonColors(
                containerColor = vaultColors.positive,
                contentColor = Color(0xFF032315),
              ),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("claim_escrow_${deal.id}"),
            ) {
              Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text(
                text = if (status == EscrowStatus.REFUNDED) "Claim Refund" else "Claim ${asset.code}",
                style = MaterialTheme.typography.labelLarge,
              )
            }
          }

          if (showSellerDelivered) {
            Button(
              onClick = onMarkDelivered,
              colors = ButtonDefaults.buttonColors(
                containerColor = vaultColors.gold,
                contentColor = Color(0xFF2B1D00),
              ),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("mark_delivered_${deal.id}"),
            ) {
              Icon(Icons.Rounded.LocalShipping, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Delivered", style = MaterialTheme.typography.labelLarge)
            }
          }

          if (showBuyerRelease) {
            Button(
              onClick = onRelease,
              colors = ButtonDefaults.buttonColors(
                containerColor = vaultColors.positive,
                contentColor = Color(0xFF032315),
              ),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("release_escrow_${deal.id}"),
            ) {
              Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Release", style = MaterialTheme.typography.labelLarge)
            }
          }

          if (showSellerRefund) {
            OutlinedButton(
              onClick = onRefund,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("refund_escrow_${deal.id}"),
            ) {
              Icon(Icons.Rounded.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("Refund", style = MaterialTheme.typography.labelLarge)
            }
          }

          if (showDispute) {
            BouncySurface(
              onClick = onOpenDispute,
              color = vaultColors.negativeContainer,
              shape = RoundedCornerShape(14.dp),
              border = BorderStroke(1.dp, vaultColors.negative.copy(alpha = 0.4f)),
              modifier = Modifier
                .height(48.dp)
                .widthIn(min = 48.dp)
                .testTag("dispute_escrow_${deal.id}"),
            ) {
              Box(
                modifier = Modifier
                  .height(48.dp)
                  .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Rounded.Gavel,
                  contentDescription = "Dispute escrow",
                  tint = vaultColors.negative,
                  modifier = Modifier.size(18.dp),
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun EscrowStepTimeline(status: EscrowStatus) {
  val vaultColors = LocalVaultColors.current
  val currentStep = when (status) {
    EscrowStatus.FUNDED -> 0
    EscrowStatus.DELIVERED -> 1
    EscrowStatus.DISPUTED -> 1
    EscrowStatus.RELEASED, EscrowStatus.REFUNDED -> 2
    EscrowStatus.CLAIMED -> 3
  }
  val stepLabels = listOf(
    "Funded",
    if (status == EscrowStatus.DISPUTED) "Disputed" else "Delivered",
    if (status == EscrowStatus.REFUNDED) "Refunded" else "Released",
    "Claimed",
  )

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    stepLabels.forEachIndexed { idx, label ->
      val reached = idx <= currentStep
      val barColor = when {
        status == EscrowStatus.DISPUTED && idx == 1 -> vaultColors.negative
        reached -> vaultColors.positive
        else -> vaultColors.cardBorder
      }
      Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(50))
            .background(barColor),
        )
        Spacer(Modifier.height(4.dp))
        Text(
          text = label,
          style = MaterialTheme.typography.labelSmall,
          color = if (reached) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

fun needsUserAction(deal: EscrowDeal, currentUserId: String): Boolean {
  val status = deal.parsedStatus
  val isBuyer = deal.buyerUid == currentUserId
  val isSeller = deal.sellerUid == currentUserId
  return (status == EscrowStatus.FUNDED && isSeller) ||
    (status == EscrowStatus.DELIVERED && isBuyer) ||
    (status == EscrowStatus.RELEASED && isSeller) ||
    (status == EscrowStatus.REFUNDED && isBuyer)
}
