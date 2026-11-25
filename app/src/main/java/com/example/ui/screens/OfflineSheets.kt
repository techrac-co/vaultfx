package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.DecryptedVaultTransaction
import com.example.data.MarketSnapshot
import com.example.model.AssetCatalog
import com.example.model.UserProfile
import com.example.model.WalletHolding
import com.example.ui.components.AssetPickerSheet
import com.example.ui.components.CurrencySelectorPill
import com.example.ui.components.QrCodeCanvas
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalVaultColors
import com.example.util.NumberFormatter
import com.example.util.QrCodeGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineSendSheet(
  profile: UserProfile?,
  holdings: List<WalletHolding>,
  pendingOfflineDebits: Map<String, Double>,
  marketSnapshot: MarketSnapshot,
  isQueueing: Boolean,
  onQueueOfflineSend: (assetCode: String, amount: Double, recipient: String, note: String?) -> Unit,
  onDismiss: () -> Unit,
) {
  val vaultColors = LocalVaultColors.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  var assetCode by remember { mutableStateOf("USD") }
  var recipient by remember { mutableStateOf("") }
  var amountText by remember { mutableStateOf("25") }
  var note by remember { mutableStateOf("") }
  var pickingAsset by remember { mutableStateOf(false) }

  val asset = AssetCatalog.find(assetCode)
  val onlineBal = holdings.firstOrNull { it.assetCode == assetCode }?.balance ?: 0.0
  val queuedAlready = pendingOfflineDebits[assetCode] ?: 0.0
  val spendable = (onlineBal - queuedAlready).coerceAtLeast(0.0)
  val amount = amountText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
  val usdEquivalent = amount * marketSnapshot.usdValueOf(assetCode)
  val hasEnough = amount > 0.0 && amount <= spendable + 1e-8
  val cleanTag = recipient.trim().removePrefix("@").lowercase()
  val isSelfTag = profile?.payTag?.isNotBlank() == true && cleanTag == profile.payTag.lowercase()

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp)
        .padding(bottom = 28.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Encrypt & Send Offline", style = MaterialTheme.typography.titleLarge)
        IconButton(onClick = onDismiss) {
          Icon(Icons.Rounded.Close, contentDescription = "Close offline send dialog")
        }
      }

      Surface(
        color = vaultColors.cyan.copy(alpha = 0.12f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, vaultColors.cyan.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.Rounded.EnhancedEncryption, contentDescription = null, tint = vaultColors.cyan, modifier = Modifier.size(20.dp))
          Spacer(Modifier.width(10.dp))
          Text(
            text = "Sealed locally with AES-256-GCM + ECDH P-256 + HMAC chain. Deducted automatically from your account once online.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }

      Spacer(Modifier.height(14.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        CurrencySelectorPill(
          asset = asset,
          onClick = { pickingAsset = true },
          modifier = Modifier.testTag("offline_send_asset_pill"),
        )
        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = "Spendable: ${NumberFormatter.formatAsset(spendable, asset.decimals)} ${asset.code}",
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (queuedAlready > 0.0) {
            Text(
              text = "Reserved offline: ${NumberFormatter.formatAsset(queuedAlready, asset.decimals)}",
              style = MaterialTheme.typography.labelSmall,
              color = vaultColors.gold,
            )
          }
        }
      }

      Spacer(Modifier.height(14.dp))
      OutlinedTextField(
        value = recipient,
        onValueChange = { recipient = it },
        label = { Text("Recipient @payTag or Wallet Address") },
        placeholder = { Text("@friend_1024 or bc1q... / 0x...") },
        supportingText = {
          if (isSelfTag) {
            Text("Cannot send an offline transfer to your own @payTag", color = vaultColors.negative)
          } else {
            Text("Verified online at settlement • Offline recipient check skipped until sync")
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("offline_send_recipient_input"),
      )

      Spacer(Modifier.height(10.dp))
      OutlinedTextField(
        value = amountText,
        onValueChange = { raw ->
          val clean = raw.filter { it.isDigit() || it == '.' }
          if (clean.count { it == '.' } <= 1) amountText = clean
        },
        label = { Text("Offline Amount (${asset.code})") },
        supportingText = {
          Text(
            text = "≈ $${NumberFormatter.formatAsset(usdEquivalent, 2)} USD" +
              if (amount > spendable + 1e-8) " • Exceeds spendable balance" else "",
            color = if (amount > spendable + 1e-8) vaultColors.negative else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("offline_send_amount_input"),
      )

      Spacer(Modifier.height(6.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        listOf("25%" to 0.25, "50%" to 0.5, "MAX" to 1.0).forEach { (label, frac) ->
          Surface(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(10.dp))
              .clickable {
                amountText = NumberFormatter.formatTruncatedInput(spendable * frac, 8)
              }
              .testTag("offline_send_pct_$label"),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(10.dp),
          ) {
            Text(
              text = label,
              style = MaterialTheme.typography.labelSmall,
              modifier = Modifier.padding(vertical = 8.dp),
              textAlign = TextAlign.Center,
            )
          }
        }
      }

      Spacer(Modifier.height(12.dp))
      OutlinedTextField(
        value = note,
        onValueChange = { if (it.length <= 140) note = it },
        label = { Text("Encrypted Memo (optional)") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("offline_send_note_input"),
      )

      Spacer(Modifier.height(18.dp))
      Button(
        onClick = {
          onQueueOfflineSend(assetCode, amount, recipient, note.takeIf { it.isNotBlank() })
        },
        enabled = !isQueueing && hasEnough && !isSelfTag && recipient.trim().length >= 3,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(54.dp)
          .testTag("offline_send_confirm_button"),
      ) {
        if (isQueueing) {
          CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
        } else {
          Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(
            text = "Encrypt & Queue ${asset.code}",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          )
        }
      }
    }
  }

  if (pickingAsset) {
    AssetPickerSheet(
      title = "Select Currency or Crypto",
      assets = AssetCatalog.assets,
      selectedCode = assetCode,
      onSelect = { assetCode = it.code },
      onDismiss = { pickingAsset = false },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun E2eeVoucherSheet(
  tx: DecryptedVaultTransaction,
  onCopyFeedback: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current
  val vaultColors = LocalVaultColors.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val qrMatrix = remember(tx.txHash, tx.integrityMacPreview) {
    QrCodeGenerator.generateMatrix("vfx:${tx.txHash.take(32)}:${tx.integrityMacPreview}")
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp)
        .padding(bottom = 28.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = vaultColors.positive)
          Spacer(Modifier.width(8.dp))
          Text("E2EE Encrypted Voucher", style = MaterialTheme.typography.titleLarge)
        }
        IconButton(onClick = onDismiss) {
          Icon(Icons.Rounded.Close, contentDescription = "Close voucher sheet")
        }
      }

      Spacer(Modifier.height(8.dp))
      QrCodeCanvas(
        matrix = qrMatrix,
        modifier = Modifier.size(188.dp),
      )

      Spacer(Modifier.height(12.dp))
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, vaultColors.cardBorder),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(12.dp)) {
          Text(
            text = "AES-256-GCM + ECDH P-256 + ECDSA SIGNED PACKET",
            style = MaterialTheme.typography.labelSmall,
            color = vaultColors.cyan,
          )
          Spacer(Modifier.height(4.dp))
          Text(
            text = tx.e2eeVoucher,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 4,
          )
          Spacer(Modifier.height(6.dp))
          Text(
            text = "Chain MAC: ${tx.integrityMacPreview}… • Verified: ${tx.isIntegrityVerified}",
            style = MaterialTheme.typography.labelSmall,
            color = if (tx.isIntegrityVerified) vaultColors.positive else vaultColors.negative,
          )
        }
      }

      Spacer(Modifier.height(14.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Button(
          onClick = {
            clipboard.setText(AnnotatedString(tx.e2eeVoucher))
            onCopyFeedback("Copied E2EE encrypted voucher")
          },
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("copy_e2ee_voucher_button"),
        ) {
          Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Copy Packet")
        }

        OutlinedButton(
          onClick = {
            val intent = Intent(Intent.ACTION_SEND).apply {
              type = "text/plain"
              putExtra(Intent.EXTRA_TEXT, tx.e2eeVoucher)
            }
            context.startActivity(Intent.createChooser(intent, "Share Encrypted Voucher"))
          },
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("share_e2ee_voucher_button"),
        ) {
          Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Share")
        }
      }
    }
  }
}
