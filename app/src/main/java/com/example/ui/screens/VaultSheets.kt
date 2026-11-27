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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddModerator
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import com.example.security.BiometricHardwareStatus
import com.example.security.VaultBiometricManager
import com.example.security.VaultSecuritySettings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import com.example.data.MarketSnapshot
import com.example.model.AssetCatalog
import com.example.model.CalcHistoryEntry
import com.example.model.PayTagEntry
import com.example.model.UserProfile
import com.example.model.WalletHolding
import com.example.ui.components.AssetPickerSheet
import com.example.ui.components.CurrencySelectorPill
import com.example.ui.components.QrCodeCanvas
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalVaultColors
import com.example.util.NumberFormatter
import com.example.util.QrCodeGenerator
import com.example.util.WalletAddressHelper
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveCryptoSheet(
  profile: UserProfile?,
  initialAssetCode: String,
  onReceiveSampleDeposit: (String, Double) -> Unit,
  onCopyFeedback: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current
  val vaultColors = LocalVaultColors.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  var assetCode by remember(initialAssetCode) {
    mutableStateOf(
      if (AssetCatalog.find(initialAssetCode).category.name == "CRYPTO") initialAssetCode else "BTC"
    )
  }
  var pickingCrypto by remember { mutableStateOf(false) }

  val asset = AssetCatalog.find(assetCode)
  val walletSeed = profile?.walletSeed?.ifBlank { "vf_default" } ?: "vf_default"
  val payTag = profile?.payTag?.takeIf { it.isNotBlank() }
  val address = remember(walletSeed, assetCode) {
    WalletAddressHelper.deriveAddress(walletSeed, assetCode)
  }
  val qrMatrix = remember(address) {
    QrCodeGenerator.generateMatrix(address)
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
        Text("Receive Cryptocurrency", style = MaterialTheme.typography.titleLarge)
        IconButton(onClick = onDismiss) {
          Icon(Icons.Rounded.Close, contentDescription = "Close receive dialog")
        }
      }

      Spacer(Modifier.height(10.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        CurrencySelectorPill(
          asset = asset,
          onClick = { pickingCrypto = true },
          modifier = Modifier.testTag("receive_asset_pill"),
        )
        Text(
          text = asset.networkName,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
        )
      }

      Spacer(Modifier.height(18.dp))
      QrCodeCanvas(
        matrix = qrMatrix,
        modifier = Modifier.size(196.dp),
      )

      Spacer(Modifier.height(14.dp))
      Text(
        text = "Your ${asset.code} Receive Address",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(6.dp))
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, vaultColors.cardBorder),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = address,
          style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier
            .padding(14.dp)
            .testTag("receive_address_text"),
        )
      }

      if (payTag != null) {
        Spacer(Modifier.height(8.dp))
        Text(
          text = "Or receive instantly from VaultFX peers via @$payTag",
          style = MaterialTheme.typography.labelMedium,
          color = vaultColors.gold,
        )
      }

      Spacer(Modifier.height(16.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Button(
          onClick = {
            clipboard.setText(AnnotatedString(address))
            onCopyFeedback("Copied ${asset.code} address")
          },
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .weight(1f)
            .height(50.dp)
            .testTag("receive_copy_button"),
        ) {
          Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text("Copy Address")
        }

        OutlinedButton(
          onClick = {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
              type = "text/plain"
              putExtra(
                Intent.EXTRA_TEXT,
                "Send ${asset.code} (${asset.networkName}) to my VaultFX address:\n$address" +
                  (payTag?.let { "\nOr VaultFX PayTag: @$it" } ?: ""),
              )
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share ${asset.code} Address"))
          },
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .weight(1f)
            .height(50.dp)
            .testTag("receive_share_button"),
        ) {
          Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text("Share")
        }
      }

      Spacer(Modifier.height(12.dp))
      val sampleAmount = when (assetCode) {
        "BTC" -> 0.005
        "ETH" -> 0.05
        "SOL" -> 1.0
        else -> 100.0
      }
      OutlinedButton(
        onClick = {
          onReceiveSampleDeposit(assetCode, sampleAmount)
          onDismiss()
        },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("receive_test_deposit_button"),
      ) {
        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Simulate Incoming +$sampleAmount $assetCode")
      }
    }
  }

  if (pickingCrypto) {
    AssetPickerSheet(
      title = "Select Crypto to Receive",
      assets = AssetCatalog.cryptoAssets,
      selectedCode = assetCode,
      onSelect = { assetCode = it.code },
      onDismiss = { pickingCrypto = false },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendCryptoSheet(
  profile: UserProfile?,
  holdings: List<WalletHolding>,
  marketSnapshot: MarketSnapshot,
  initialAssetCode: String,
  initialAmount: Double?,
  isSending: Boolean,
  isEffectivelyOffline: Boolean = false,
  pendingOfflineDebits: Map<String, Double> = emptyMap(),
  onLookupPayTag: suspend (String) -> Result<PayTagEntry?>,
  onSend: (assetCode: String, amount: Double, recipient: String, note: String?) -> Unit,
  onDismiss: () -> Unit,
) {
  val vaultColors = LocalVaultColors.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  var assetCode by remember(initialAssetCode) {
    mutableStateOf(
      if (AssetCatalog.find(initialAssetCode).category.name == "CRYPTO") initialAssetCode else "BTC"
    )
  }
  var recipient by remember { mutableStateOf("") }
  var amountText by remember(initialAmount) {
    mutableStateOf(
      if (initialAmount != null && initialAmount > 0.0) {
        NumberFormatter.formatTruncatedInput(initialAmount, 8).takeIf { it != "0" } ?: "0.01"
      } else "0.01"
    )
  }
  var note by remember { mutableStateOf("") }
  var matchedPeer by remember { mutableStateOf<PayTagEntry?>(null) }
  var pickingCrypto by remember { mutableStateOf(false) }

  LaunchedEffect(recipient, isEffectivelyOffline) {
    val clean = recipient.trim().removePrefix("@").lowercase()
    if (!isEffectivelyOffline && clean.matches(Regex("^[a-z0-9_]{3,32}$"))) {
      delay(300)
      matchedPeer = onLookupPayTag(clean).getOrNull()
    } else {
      matchedPeer = null
    }
  }

  val asset = AssetCatalog.find(assetCode)
  val onlineBalance = holdings.firstOrNull { it.assetCode == assetCode }?.balance ?: 0.0
  val reservedOffline = pendingOfflineDebits[assetCode] ?: 0.0
  val available = (onlineBalance - reservedOffline).coerceAtLeast(0.0)
  val amount = amountText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
  val usdEquivalent = amount * marketSnapshot.usdValueOf(assetCode)
  val hasEnough = amount > 0.0 && amount <= available + 1e-10
  val peer = matchedPeer
  val cleanRecipientTag = recipient.trim().removePrefix("@").lowercase()
  val isSelfTag = (peer != null && peer.ownerUid == profile?.userId) ||
    (profile?.payTag?.isNotBlank() == true && cleanRecipientTag == profile.payTag.lowercase())

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
        Text("Send Cryptocurrency", style = MaterialTheme.typography.titleLarge)
        IconButton(onClick = onDismiss) {
          Icon(Icons.Rounded.Close, contentDescription = "Close send dialog")
        }
      }

      Spacer(Modifier.height(10.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        CurrencySelectorPill(
          asset = asset,
          onClick = { pickingCrypto = true },
          modifier = Modifier.testTag("send_asset_pill"),
        )
        Text(
          text = "Avail: ${NumberFormatter.formatAsset(available, asset.decimals)} ${asset.code}",
          style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Spacer(Modifier.height(14.dp))
      OutlinedTextField(
        value = recipient,
        onValueChange = { recipient = it },
        label = { Text("Recipient @payTag or Wallet Address") },
        placeholder = { Text("@friend_1024 or bc1q... / 0x...") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("send_recipient_input"),
      )

      if (peer != null) {
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            if (isSelfTag) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = if (isSelfTag) vaultColors.negative else vaultColors.positive,
            modifier = Modifier.size(16.dp),
          )
          Spacer(Modifier.width(6.dp))
          Text(
            text = if (isSelfTag) "Cannot send a peer transfer to yourself"
            else "Verified VaultFX Peer: ${peer.displayName} (@${peer.payTag})",
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelfTag) vaultColors.negative else vaultColors.positive,
          )
        }
      }

      Spacer(Modifier.height(12.dp))
      OutlinedTextField(
        value = amountText,
        onValueChange = { raw ->
          val clean = raw.filter { it.isDigit() || it == '.' }
          if (clean.count { it == '.' } <= 1) amountText = clean
        },
        label = { Text("Amount (${asset.code})") },
        supportingText = {
          Text(
            text = "≈ $${NumberFormatter.formatAsset(usdEquivalent, 2)} USD" +
              if (amount > available + 1e-10) " • Exceeds balance" else "",
            color = if (amount > available + 1e-10) vaultColors.negative
            else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("send_amount_input"),
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
                val valAmt = available * frac
                amountText = NumberFormatter.formatTruncatedInput(valAmt, 8)
              }
              .testTag("send_pct_$label"),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(10.dp),
          ) {
            Text(
              text = label,
              style = MaterialTheme.typography.labelSmall,
              modifier = Modifier.padding(vertical = 8.dp),
              textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
          }
        }
      }

      Spacer(Modifier.height(12.dp))
      OutlinedTextField(
        value = note,
        onValueChange = { if (it.length <= 140) note = it },
        label = { Text("Note (optional)") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("send_note_input"),
      )

      if (isEffectivelyOffline) {
        Spacer(Modifier.height(10.dp))
        Surface(
          color = vaultColors.goldContainer,
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, vaultColors.gold.copy(alpha = 0.5f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = vaultColors.gold, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
              text = "Offline mode: will encrypt in Room DB and deduct automatically when online.",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }

      Spacer(Modifier.height(18.dp))
      Button(
        onClick = { onSend(assetCode, amount, recipient, note.takeIf { it.isNotBlank() }) },
        enabled = !isSending && hasEnough && !isSelfTag && recipient.trim().length >= 3,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(54.dp)
          .testTag("send_confirm_button"),
      ) {
        if (isSending) {
          CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
        } else {
          Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(
            text = if (isEffectivelyOffline) "Encrypt & Queue ${asset.code} Offline" else "Send ${asset.code} Now",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          )
        }
      }
    }
  }

  if (pickingCrypto) {
    AssetPickerSheet(
      title = "Select Crypto to Send",
      assets = AssetCatalog.cryptoAssets,
      selectedCode = assetCode,
      onSelect = { assetCode = it.code },
      onDismiss = { pickingCrypto = false },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEscrowSheet(
  profile: UserProfile?,
  holdings: List<WalletHolding>,
  marketSnapshot: MarketSnapshot,
  initialAssetCode: String,
  initialAmount: Double?,
  isSubmitting: Boolean,
  onLookupPayTag: suspend (String) -> Result<PayTagEntry?>,
  onCreateEscrow: (sellerTag: String, assetCode: String, amount: Double, title: String, terms: String?) -> Unit,
  onDismiss: () -> Unit,
) {
  val vaultColors = LocalVaultColors.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  var assetCode by remember(initialAssetCode) {
    mutableStateOf(
      if (AssetCatalog.find(initialAssetCode).category.name == "CRYPTO") initialAssetCode else "USDT"
    )
  }
  var sellerTagInput by remember { mutableStateOf("") }
  var amountText by remember(initialAmount) {
    mutableStateOf(
      if (initialAmount != null && initialAmount > 0.0) {
        NumberFormatter.formatTruncatedInput(initialAmount, 8).takeIf { it != "0" } ?: "50"
      } else "50"
    )
  }
  var title by remember { mutableStateOf("") }
  var agreedTerms by remember { mutableStateOf("") }
  var matchedSeller by remember { mutableStateOf<PayTagEntry?>(null) }
  var hasCheckedSeller by remember { mutableStateOf(false) }
  var pickingCrypto by remember { mutableStateOf(false) }

  LaunchedEffect(sellerTagInput) {
    val clean = sellerTagInput.trim().removePrefix("@").lowercase()
    if (clean.matches(Regex("^[a-z0-9_]{3,32}$"))) {
      delay(320)
      matchedSeller = onLookupPayTag(clean).getOrNull()
      hasCheckedSeller = true
    } else {
      matchedSeller = null
      hasCheckedSeller = false
    }
  }

  val asset = AssetCatalog.find(assetCode)
  val available = holdings.firstOrNull { it.assetCode == assetCode }?.balance ?: 0.0
  val amount = amountText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
  val usdEquivalent = amount * marketSnapshot.usdValueOf(assetCode)
  val hasEnough = amount > 0.0 && amount <= available + 1e-10
  val seller = matchedSeller
  val isSelfTag = seller != null && seller.ownerUid == profile?.userId
  val sellerValid = seller != null && !isSelfTag

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
        Text("Create Smart Escrow", style = MaterialTheme.typography.titleLarge)
        IconButton(onClick = onDismiss) {
          Icon(Icons.Rounded.Close, contentDescription = "Close escrow sheet")
        }
      }

      Spacer(Modifier.height(10.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        CurrencySelectorPill(
          asset = asset,
          onClick = { pickingCrypto = true },
          modifier = Modifier.testTag("escrow_asset_pill"),
        )
        Text(
          text = "Avail: ${NumberFormatter.formatAsset(available, asset.decimals)} ${asset.code}",
          style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Spacer(Modifier.height(14.dp))
      OutlinedTextField(
        value = sellerTagInput,
        onValueChange = { sellerTagInput = it },
        label = { Text("Seller @payTag") },
        placeholder = { Text("@seller_tag") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("escrow_seller_input"),
      )

      if (seller != null) {
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            if (isSelfTag) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = if (isSelfTag) vaultColors.negative else vaultColors.positive,
            modifier = Modifier.size(16.dp),
          )
          Spacer(Modifier.width(6.dp))
          Text(
            text = if (isSelfTag) "Seller cannot be your own @payTag"
            else "Verified Seller: ${seller.displayName} (@${seller.payTag})",
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelfTag) vaultColors.negative else vaultColors.positive,
          )
        }
      } else if (hasCheckedSeller && sellerTagInput.trim().removePrefix("@").length >= 3) {
        Spacer(Modifier.height(6.dp))
        Text(
          text = "No VaultFX user found with that @payTag yet.",
          style = MaterialTheme.typography.labelMedium,
          color = vaultColors.gold,
        )
      }

      Spacer(Modifier.height(12.dp))
      OutlinedTextField(
        value = amountText,
        onValueChange = { raw ->
          val clean = raw.filter { it.isDigit() || it == '.' }
          if (clean.count { it == '.' } <= 1) amountText = clean
        },
        label = { Text("Escrow Amount (${asset.code})") },
        supportingText = {
          Text(
            text = "≈ $${NumberFormatter.formatAsset(usdEquivalent, 2)} USD" +
              if (amount > available + 1e-10) " • Exceeds balance" else "",
            color = if (amount > available + 1e-10) vaultColors.negative
            else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("escrow_amount_input"),
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
                val valAmt = available * frac
                amountText = NumberFormatter.formatTruncatedInput(valAmt, 8)
              }
              .testTag("escrow_pct_$label"),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(10.dp),
          ) {
            Text(
              text = label,
              style = MaterialTheme.typography.labelSmall,
              modifier = Modifier.padding(vertical = 8.dp),
              textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
          }
        }
      }

      Spacer(Modifier.height(12.dp))
      OutlinedTextField(
        value = title,
        onValueChange = { if (it.length <= 80) title = it },
        label = { Text("Deal Title") },
        placeholder = { Text("e.g. OTC NGN Settlement / Design Milestone") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("escrow_title_input"),
      )

      Spacer(Modifier.height(12.dp))
      OutlinedTextField(
        value = agreedTerms,
        onValueChange = { if (it.length <= 160) agreedTerms = it },
        label = { Text("Escrow Conditions (optional)") },
        placeholder = { Text("Release once bank transfer or asset is confirmed") },
        singleLine = false,
        maxLines = 2,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("escrow_terms_input"),
      )

      Spacer(Modifier.height(18.dp))
      Button(
        onClick = {
          onCreateEscrow(
            sellerTagInput,
            assetCode,
            amount,
            title,
            agreedTerms.takeIf { it.isNotBlank() },
          )
        },
        enabled = !isSubmitting && hasEnough && sellerValid && title.trim().length >= 2,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(54.dp)
          .testTag("escrow_confirm_button"),
      ) {
        if (isSubmitting) {
          CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
        } else {
          Icon(Icons.Rounded.AddModerator, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(
            text = "Lock ${asset.code} in Escrow",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          )
        }
      }
    }
  }

  if (pickingCrypto) {
    AssetPickerSheet(
      title = "Select Crypto for Escrow",
      assets = AssetCatalog.cryptoAssets,
      selectedCode = assetCode,
      onSelect = { assetCode = it.code },
      onDismiss = { pickingCrypto = false },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepositFiatSheet(
  onDeposit: (fiatCode: String, amount: Double) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var fiatCode by remember { mutableStateOf("USD") }
  var amountText by remember { mutableStateOf("1000") }
  var pickingFiat by remember { mutableStateOf(false) }

  val asset = AssetCatalog.find(fiatCode)
  val amount = amountText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(bottom = 28.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Add Funds to Wallet", style = MaterialTheme.typography.titleLarge)
        IconButton(onClick = onDismiss) {
          Icon(Icons.Rounded.Close, contentDescription = "Close deposit dialog")
        }
      }

      Spacer(Modifier.height(10.dp))
      CurrencySelectorPill(
        asset = asset,
        onClick = { pickingFiat = true },
        modifier = Modifier.testTag("deposit_fiat_pill"),
      )

      Spacer(Modifier.height(14.dp))
      OutlinedTextField(
        value = amountText,
        onValueChange = { raw ->
          val clean = raw.filter { it.isDigit() || it == '.' }
          if (clean.count { it == '.' } <= 1) amountText = clean
        },
        label = { Text("Deposit Amount (${asset.code})") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("deposit_amount_input"),
      )

      Spacer(Modifier.height(10.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        val presets = if (fiatCode == "NGN") listOf("50000", "250000", "1000000") else listOf("500", "1000", "5000")
        presets.forEach { preset ->
          Surface(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(10.dp))
              .clickable { amountText = preset },
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(10.dp),
          ) {
            Text(
              text = "${asset.symbol}$preset",
              style = MaterialTheme.typography.labelSmall,
              modifier = Modifier.padding(vertical = 10.dp),
              textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
          }
        }
      }

      Spacer(Modifier.height(18.dp))
      Button(
        onClick = {
          if (amount > 0.0) {
            onDeposit(fiatCode, amount)
            onDismiss()
          }
        },
        enabled = amount > 0.0,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(54.dp)
          .testTag("deposit_confirm_button"),
      ) {
        Text(
          text = "Deposit ${asset.symbol}$amountText ${asset.code}",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        )
      }
    }
  }

  if (pickingFiat) {
    AssetPickerSheet(
      title = "Select Currency to Deposit",
      assets = AssetCatalog.settlementFiats,
      selectedCode = fiatCode,
      onSelect = { fiatCode = it.code },
      onDismiss = { pickingFiat = false },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalcHistorySheet(
  history: List<CalcHistoryEntry>,
  onSelectEntry: (CalcHistoryEntry) -> Unit,
  onDismiss: () -> Unit,
) {
  val vaultColors = LocalVaultColors.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(bottom = 28.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Calculation History", style = MaterialTheme.typography.titleLarge)
        IconButton(onClick = onDismiss) {
          Icon(Icons.Rounded.Close, contentDescription = "Close calculation history")
        }
      }

      Spacer(Modifier.height(8.dp))
      if (history.isEmpty()) {
        Text(
          text = "Press '=' on the calculator to save expressions and live currency conversions here.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = 24.dp),
        )
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .height(320.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(history, key = { it.id }) { entry ->
            val baseAsset = AssetCatalog.find(entry.baseCode)
            val targetAsset = AssetCatalog.find(entry.targetCode)
            Surface(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable {
                  onSelectEntry(entry)
                  onDismiss()
                },
              color = MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(14.dp),
              border = BorderStroke(1.dp, vaultColors.cardBorder),
            ) {
              Column(modifier = Modifier.padding(14.dp)) {
                Text(
                  text = "${entry.expression} = ${baseAsset.symbol}${NumberFormatter.formatCalcDisplay(entry.result)} ${entry.baseCode}",
                  style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                  text = "→ ${targetAsset.symbol}${NumberFormatter.formatAsset(entry.convertedResult, targetAsset.decimals)} ${entry.targetCode}",
                  style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(
  profile: UserProfile?,
  userEmail: String?,
  securitySettings: VaultSecuritySettings = VaultSecuritySettings(),
  hardwareStatus: BiometricHardwareStatus = BiometricHardwareStatus.UNAVAILABLE,
  onOpenCreateUsername: () -> Unit = {},
  onToggleAppLock: (Boolean) -> Unit = {},
  onToggleTxConfirmation: (Boolean) -> Unit = {},
  onOpenSetupPin: () -> Unit = {},
  onRemoveVaultPin: () -> Unit = {},
  onOpenBiometricEnroll: () -> Unit = {},
  onLockNow: () -> Unit = {},
  onSignOut: () -> Unit,
  onDismiss: () -> Unit,
) {
  val vaultColors = LocalVaultColors.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val payTag = profile?.payTag?.takeIf { it.isNotBlank() }
  val isPlaceholderTag = remember(payTag) { com.example.util.UsernameValidator.isGeneratedPlaceholder(payTag) }
  val canUseBiometrics = hardwareStatus == BiometricHardwareStatus.BIOMETRIC_READY ||
    hardwareStatus == BiometricHardwareStatus.DEVICE_CREDENTIAL_ONLY

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
        Text("Account & Security", style = MaterialTheme.typography.titleLarge)
        IconButton(onClick = onDismiss) {
          Icon(Icons.Rounded.Close, contentDescription = "Close profile sheet")
        }
      }

      Spacer(Modifier.height(10.dp))
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, vaultColors.glassHighlight.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            text = profile?.displayName ?: "VaultFX Trader",
            style = MaterialTheme.typography.titleMedium,
          )
          if (!userEmail.isNullOrBlank()) {
            Text(
              text = userEmail,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Spacer(Modifier.height(12.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
              Text(
                text = "USERNAME HANDLE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = if (payTag != null) "@$payTag" else "Not set yet",
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                color = vaultColors.gold,
              )
            }
            Button(
              onClick = {
                onDismiss()
                onOpenCreateUsername()
              },
              shape = RoundedCornerShape(50),
              colors = ButtonDefaults.buttonColors(
                containerColor = if (isPlaceholderTag) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (isPlaceholderTag) MaterialTheme.colorScheme.onPrimary else vaultColors.gold,
              ),
              modifier = Modifier.testTag("profile_create_username_button"),
            ) {
              Text(
                text = if (isPlaceholderTag) "Create Username" else "Edit",
                style = MaterialTheme.typography.labelLarge,
              )
            }
          }
        }
      }

      Spacer(Modifier.height(14.dp))
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
          1.dp,
          if (canUseBiometrics || securitySettings.hasVaultPin) vaultColors.positive.copy(alpha = 0.45f) else vaultColors.gold.copy(alpha = 0.55f),
        ),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Rounded.Fingerprint,
                contentDescription = null,
                tint = if (canUseBiometrics) vaultColors.positive else vaultColors.gold,
                modifier = Modifier.size(22.dp),
              )
              Spacer(Modifier.width(8.dp))
              Column {
                Text("Biometric Security", style = MaterialTheme.typography.titleSmall)
                Text(
                  text = VaultBiometricManager.statusDisplayLabel(hardwareStatus, securitySettings.hasVaultPin),
                  style = MaterialTheme.typography.labelSmall,
                  color = if (canUseBiometrics || securitySettings.hasVaultPin) vaultColors.positive else vaultColors.gold,
                )
              }
            }
            Icon(
              imageVector = Icons.Rounded.VerifiedUser,
              contentDescription = null,
              tint = vaultColors.cyan,
              modifier = Modifier.size(18.dp),
            )
          }

          HorizontalDivider(color = vaultColors.cardBorder.copy(alpha = 0.5f))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
              Text("App Entry Lock", style = MaterialTheme.typography.titleSmall)
              Text(
                text = "Require Fingerprint, Face, or Vault PIN whenever opening VaultFX",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = securitySettings.appLockEnabled,
              onCheckedChange = onToggleAppLock,
              modifier = Modifier.testTag("toggle_app_biometric_lock"),
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
              Text("Transaction Confirmation", style = MaterialTheme.typography.titleSmall)
              Text(
                text = "Verify identity before sends, trades, offline queue & escrow actions",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = securitySettings.txConfirmationEnabled,
              onCheckedChange = onToggleTxConfirmation,
              modifier = Modifier.testTag("toggle_tx_biometric_confirm"),
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedButton(
              onClick = onOpenSetupPin,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .weight(1f)
                .height(46.dp)
                .testTag("profile_setup_pin_button"),
            ) {
              Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text(if (securitySettings.hasVaultPin) "Change PIN" else "Set Vault PIN")
            }

            if (securitySettings.hasVaultPin) {
              OutlinedButton(
                onClick = onRemoveVaultPin,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = vaultColors.negative),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                  .height(46.dp)
                  .testTag("profile_remove_pin_button"),
              ) {
                Text("Clear PIN")
              }
            }
          }

          if (hardwareStatus != BiometricHardwareStatus.BIOMETRIC_READY) {
            OutlinedButton(
              onClick = onOpenBiometricEnroll,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .testTag("profile_enroll_biometric_button"),
            ) {
              Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text("Enroll Device Fingerprint / Face")
            }
          }

          if (securitySettings.appLockEnabled && (canUseBiometrics || securitySettings.hasVaultPin)) {
            Button(
              onClick = {
                onDismiss()
                onLockNow()
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
              ),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("profile_lock_now_button"),
            ) {
              Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text("Lock VaultFX Now", style = MaterialTheme.typography.labelLarge)
            }
          }
        }
      }

      Spacer(Modifier.height(16.dp))
      HorizontalDivider(color = vaultColors.cardBorder.copy(alpha = 0.5f))
      Spacer(Modifier.height(16.dp))

      Button(
        onClick = {
          onDismiss()
          onSignOut()
        },
        colors = ButtonDefaults.buttonColors(
          containerColor = vaultColors.negativeContainer,
          contentColor = vaultColors.negative,
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .testTag("sign_out_button"),
      ) {
        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Sign Out", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
      }
    }
  }
}
