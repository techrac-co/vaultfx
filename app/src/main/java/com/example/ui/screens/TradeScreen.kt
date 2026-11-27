package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MarketSnapshot
import com.example.model.AssetCatalog
import com.example.model.TransactionType
import com.example.model.WalletHolding
import com.example.model.WalletTransaction
import com.example.ui.TradeUiState
import com.example.ui.components.AssetBadge
import com.example.ui.components.AssetPickerSheet
import com.example.ui.components.BouncySurface
import com.example.ui.components.ChangeBadge
import com.example.ui.components.CurrencySelectorPill
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.MarketSparkline
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalVaultColors
import com.example.util.NumberFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class OrderBookLevel(
  val price: Double,
  val size: Double,
  val fillFraction: Float,
)

@Composable
fun TradeScreen(
  tradeState: TradeUiState,
  marketSnapshot: MarketSnapshot,
  holdings: List<WalletHolding>,
  transactions: List<WalletTransaction>,
  onSetSide: (Boolean) -> Unit,
  onSelectCrypto: (String) -> Unit,
  onSelectFiat: (String) -> Unit,
  onAmountChange: (String) -> Unit,
  onTimeframeChange: (String) -> Unit,
  onApplyPercent: (Double) -> Unit,
  onSubmitTrade: () -> Unit,
  onOpenDepositFiat: () -> Unit,
) {
  val vaultColors = LocalVaultColors.current
  var pickingCrypto by remember { mutableStateOf(false) }
  var pickingFiat by remember { mutableStateOf(false) }

  val cryptoAsset = AssetCatalog.find(tradeState.cryptoCode)
  val fiatAsset = AssetCatalog.find(tradeState.fiatCode)
  val spotPriceInFiat = marketSnapshot.rate(tradeState.cryptoCode, tradeState.fiatCode)
  val changePct = marketSnapshot.pairChange24h(tradeState.cryptoCode, tradeState.fiatCode)

  val rangeFactor = when (tradeState.timeframe) {
    "1H" -> 0.35
    "24H" -> 1.0
    "7D" -> 2.1
    else -> 3.4
  }
  val chartPoints = remember(tradeState.cryptoCode, tradeState.fiatCode, tradeState.timeframe, marketSnapshot) {
    marketSnapshot.sparklineFor(tradeState.cryptoCode, tradeState.fiatCode, points = 34, rangeFactor = rangeFactor)
  }

  val (asks, bids) = remember(spotPriceInFiat, tradeState.cryptoCode) {
    val baseSize = when (tradeState.cryptoCode) {
      "BTC" -> 0.42
      "ETH" -> 3.8
      "SOL" -> 42.0
      else -> 1250.0
    }
    val askRows = (4 downTo 1).map { step ->
      OrderBookLevel(
        price = spotPriceInFiat * (1.0 + step * 0.0007),
        size = baseSize * (0.4 + step * 0.32),
        fillFraction = (0.28f + step * 0.17f).coerceAtMost(0.95f),
      )
    }
    val bidRows = (1..4).map { step ->
      OrderBookLevel(
        price = (spotPriceInFiat * (1.0 - step * 0.0007)).coerceAtLeast(0.0),
        size = baseSize * (0.5 + step * 0.29),
        fillFraction = (0.30f + step * 0.16f).coerceAtMost(0.95f),
      )
    }
    askRows to bidRows
  }

  val cryptoBalance = holdings.firstOrNull { it.assetCode == tradeState.cryptoCode }?.balance ?: 0.0
  val fiatBalance = holdings.firstOrNull { it.assetCode == tradeState.fiatCode }?.balance ?: 0.0

  val parsedAmount = tradeState.amountInput.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
  val grossFiat = parsedAmount * spotPriceInFiat
  val feeFiat = grossFiat * 0.001
  val netFiat = if (tradeState.isBuy) grossFiat + feeFiat else (grossFiat - feeFiat).coerceAtLeast(0.0)

  val tradeHistory = remember(transactions) {
    transactions.filter { it.parsedType == TransactionType.BUY || it.parsedType == TransactionType.SELL }
  }

  val buyContainer by animateColorAsState(
    targetValue = if (tradeState.isBuy) vaultColors.positive else MaterialTheme.colorScheme.surfaceVariant,
    label = "buy_btn_bg",
  )
  val sellContainer by animateColorAsState(
    targetValue = if (!tradeState.isBuy) vaultColors.negative else MaterialTheme.colorScheme.surfaceVariant,
    label = "sell_btn_bg",
  )
  val submitContainer by animateColorAsState(
    targetValue = if (tradeState.isBuy) vaultColors.positive else vaultColors.negative,
    label = "submit_trade_bg",
  )

  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 640.dp),
      contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Bybit Spot Header + Liquid Glass Price Chart Card
      item {
        val chartAccent = if (changePct >= 0.0) vaultColors.positive else vaultColors.negative
        LiquidGlassCard(
          accentTint = chartAccent,
          shape = RoundedCornerShape(26.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CurrencySelectorPill(
                  asset = cryptoAsset,
                  onClick = { pickingCrypto = true },
                  modifier = Modifier.testTag("trade_crypto_selector"),
                )
                Text(
                  text = "/",
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CurrencySelectorPill(
                  asset = fiatAsset,
                  onClick = { pickingFiat = true },
                  modifier = Modifier.testTag("trade_fiat_selector"),
                )
              }

              Surface(
                color = vaultColors.goldContainer,
                shape = RoundedCornerShape(50),
                border = BorderStroke(0.9.dp, vaultColors.gold.copy(alpha = 0.55f)),
              ) {
                Text(
                  text = "SPOT • 0.1%",
                  style = MaterialTheme.typography.labelSmall,
                  color = vaultColors.gold,
                  modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                )
              }
            }

            Spacer(Modifier.height(12.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.Bottom,
            ) {
              Column {
                Text(
                  text = "${cryptoAsset.name} / ${fiatAsset.code}",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AnimatedContent(
                  targetState = "${fiatAsset.symbol}${NumberFormatter.formatRate(spotPriceInFiat)}",
                  transitionSpec = { fadeIn() togetherWith fadeOut() },
                  label = "trade_spot_anim",
                ) { formatted ->
                  Text(
                    text = formatted,
                    style = MaterialTheme.typography.displayMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = chartAccent,
                    modifier = Modifier.testTag("trade_spot_price"),
                  )
                }
              }
              ChangeBadge(changePct = changePct)
            }

            Spacer(Modifier.height(12.dp))
            MarketSparkline(
              points = chartPoints,
              isPositive = changePct >= 0.0,
              showGrid = true,
              modifier = Modifier
                .fillMaxWidth()
                .height(104.dp),
            )

            Spacer(Modifier.height(10.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              listOf("1H", "24H", "7D", "30D").forEach { tf ->
                val selected = tradeState.timeframe == tf
                BouncySurface(
                  onClick = { onTimeframeChange(tf) },
                  modifier = Modifier
                    .weight(1f)
                    .testTag("trade_tf_$tf"),
                  color = if (selected) vaultColors.goldContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                  shape = RoundedCornerShape(50),
                  border = BorderStroke(
                    1.dp,
                    if (selected) vaultColors.gold.copy(alpha = 0.65f) else vaultColors.glassBorder,
                  ),
                ) {
                  Text(
                    text = tf,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) vaultColors.gold else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(vertical = 7.dp),
                    textAlign = TextAlign.Center,
                  )
                }
              }
            }
          }
        }
      }

      // Bybit Split Spot Desk: Left Order Ticket + Right Depth Order Book
      item {
        LiquidGlassCard(
          accentTint = if (tradeState.isBuy) vaultColors.positive else vaultColors.negative,
          shape = RoundedCornerShape(26.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            // LEFT COLUMN: Buy / Sell Order Form
            Column(modifier = Modifier.weight(1.22f)) {
              Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, vaultColors.glassBorder),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(3.dp),
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  BouncySurface(
                    onClick = { onSetSide(true) },
                    modifier = Modifier
                      .weight(1f)
                      .minimumInteractiveComponentSize()
                      .testTag("trade_tab_buy"),
                    color = buyContainer,
                    shape = RoundedCornerShape(50),
                  ) {
                    Text(
                      text = "Buy",
                      style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                      color = if (tradeState.isBuy) Color(0xFF042617) else MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                      textAlign = TextAlign.Center,
                    )
                  }

                  BouncySurface(
                    onClick = { onSetSide(false) },
                    modifier = Modifier
                      .weight(1f)
                      .minimumInteractiveComponentSize()
                      .testTag("trade_tab_sell"),
                    color = sellContainer,
                    shape = RoundedCornerShape(50),
                  ) {
                    Text(
                      text = "Sell",
                      style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                      color = if (!tradeState.isBuy) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                      textAlign = TextAlign.Center,
                    )
                  }
                }
              }

              Spacer(Modifier.height(10.dp))
              Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.8.dp, vaultColors.glassBorder),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = "Market Order",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  Text(
                    text = "Instant",
                    style = MaterialTheme.typography.labelSmall,
                    color = vaultColors.gold,
                  )
                }
              }

              Spacer(Modifier.height(8.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = if (tradeState.isBuy) {
                    "Avail ${fiatAsset.symbol}${NumberFormatter.formatAsset(fiatBalance, 2)}"
                  } else {
                    "Avail ${NumberFormatter.formatAsset(cryptoBalance, cryptoAsset.decimals)}"
                  },
                  style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f),
                )

                if (tradeState.isBuy) {
                  Text(
                    text = "+ ${fiatAsset.code}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = vaultColors.gold,
                    modifier = Modifier
                      .clip(RoundedCornerShape(8.dp))
                      .clickable(onClick = onOpenDepositFiat)
                      .padding(horizontal = 6.dp, vertical = 3.dp)
                      .testTag("trade_add_fiat_button"),
                  )
                }
              }

              Spacer(Modifier.height(6.dp))
              OutlinedTextField(
                value = tradeState.amountInput,
                onValueChange = onAmountChange,
                label = { Text("Qty (${cryptoAsset.code})") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("trade_amount_input"),
              )

              Spacer(Modifier.height(8.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
              ) {
                listOf("25%" to 0.25, "50%" to 0.5, "75%" to 0.75, "MAX" to 1.0).forEach { (label, frac) ->
                  BouncySurface(
                    onClick = { onApplyPercent(frac) },
                    modifier = Modifier
                      .weight(1f)
                      .testTag("trade_pct_$label"),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(0.9.dp, vaultColors.glassBorder),
                  ) {
                    Text(
                      text = label,
                      style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                      color = MaterialTheme.colorScheme.onSurface,
                      maxLines = 1,
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 7.dp),
                      textAlign = TextAlign.Center,
                    )
                  }
                }
              }

              Spacer(Modifier.height(10.dp))
              SummaryRow("Fee (0.1%)", "${fiatAsset.symbol}${NumberFormatter.formatAsset(feeFiat, 2)}")
              SummaryRow(
                label = if (tradeState.isBuy) "Total" else "Receive",
                value = "${fiatAsset.symbol}${NumberFormatter.formatAsset(netFiat, 2)}",
                highlight = true,
              )

              Spacer(Modifier.height(12.dp))
              Button(
                onClick = onSubmitTrade,
                enabled = !tradeState.isSubmitting && parsedAmount > 0.0,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                  containerColor = submitContainer,
                  contentColor = if (tradeState.isBuy) Color(0xFF042617) else Color.White,
                ),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(50.dp)
                  .testTag("trade_submit_button"),
              ) {
                if (tradeState.isSubmitting) {
                  CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                  Text(
                    text = if (tradeState.isBuy) "Buy ${cryptoAsset.code}" else "Sell ${cryptoAsset.code}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                  )
                }
              }
            }

            // RIGHT COLUMN: Live Bybit Spot Order Book Depth
            Column(
              modifier = Modifier.weight(0.88f),
              verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = "Price(${fiatAsset.code})",
                  style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = "Size(${cryptoAsset.code})",
                  style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }

              Spacer(Modifier.height(2.dp))
              asks.forEach { level ->
                OrderBookRow(
                  priceText = NumberFormatter.formatRate(level.price),
                  sizeText = NumberFormatter.formatTruncatedInput(level.size, 3),
                  fillFraction = level.fillFraction,
                  barColor = vaultColors.negative.copy(alpha = 0.22f),
                  textColor = vaultColors.negative,
                )
              }

              // Spread Mid-Price Pill
              Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(0.8.dp, vaultColors.glassBorder),
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 4.dp),
              ) {
                Column(
                  modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                  Text(
                    text = "${fiatAsset.symbol}${NumberFormatter.formatRate(spotPriceInFiat)}",
                    style = MaterialTheme.typography.labelLarge.copy(
                      fontFamily = JetBrainsMonoFontFamily,
                      fontWeight = FontWeight.Bold,
                    ),
                    color = if (changePct >= 0) vaultColors.positive else vaultColors.negative,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    text = "Mark Price",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }

              bids.forEach { level ->
                OrderBookRow(
                  priceText = NumberFormatter.formatRate(level.price),
                  sizeText = NumberFormatter.formatTruncatedInput(level.size, 3),
                  fillFraction = level.fillFraction,
                  barColor = vaultColors.positive.copy(alpha = 0.22f),
                  textColor = vaultColors.positive,
                )
              }
            }
          }
        }
      }

      // Recent Executed Orders Section
      item {
        Text(
          text = "ORDER HISTORY",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 4.dp),
        )
      }

      if (tradeHistory.isEmpty()) {
        item {
          LiquidGlassCard(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = "No spot orders filled yet. Submit your first Buy or Sell market order above.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(18.dp),
            )
          }
        }
      } else {
        items(tradeHistory.take(15), key = { it.id }) { tx ->
          val isBuy = tx.parsedType == TransactionType.BUY
          val asset = AssetCatalog.find(tx.assetCode)
          val quoteAsset = AssetCatalog.find(tx.quoteCode)
          val dateText = remember(tx.createdAt) {
            val date = tx.createdAt?.toDate() ?: Date()
            SimpleDateFormat("MMM d, HH:mm", Locale.US).format(date)
          }
          LiquidGlassCard(
            shape = RoundedCornerShape(18.dp),
            accentTint = if (isBuy) vaultColors.positive else vaultColors.negative,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              AssetBadge(asset = asset, size = 34.dp)
              Spacer(Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = if (isBuy) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                    contentDescription = null,
                    tint = if (isBuy) vaultColors.positive else vaultColors.negative,
                    modifier = Modifier.size(14.dp),
                  )
                  Spacer(Modifier.width(4.dp))
                  Text(
                    text = "${if (isBuy) "Bought" else "Sold"} ${tx.assetCode}",
                    style = MaterialTheme.typography.titleSmall,
                  )
                }
                Text(
                  text = dateText,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Column(horizontalAlignment = Alignment.End) {
                Text(
                  text = "${if (isBuy) "+" else "-"}${NumberFormatter.formatAsset(tx.amount, asset.decimals)} ${tx.assetCode}",
                  style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                  color = if (isBuy) vaultColors.positive else vaultColors.negative,
                )
                Text(
                  text = "${quoteAsset.symbol}${NumberFormatter.formatAsset(tx.quoteAmount, 2)} ${tx.quoteCode}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }

      item { Spacer(Modifier.height(16.dp)) }
    }
  }

  if (pickingCrypto) {
    AssetPickerSheet(
      title = "Select Cryptocurrency to Trade",
      assets = AssetCatalog.cryptoAssets,
      selectedCode = tradeState.cryptoCode,
      onSelect = { onSelectCrypto(it.code) },
      onDismiss = { pickingCrypto = false },
    )
  }

  if (pickingFiat) {
    AssetPickerSheet(
      title = "Select Settlement Currency",
      assets = AssetCatalog.settlementFiats,
      selectedCode = tradeState.fiatCode,
      onSelect = { onSelectFiat(it.code) },
      onDismiss = { pickingFiat = false },
    )
  }
}

@Composable
private fun OrderBookRow(
  priceText: String,
  sizeText: String,
  fillFraction: Float,
  barColor: Color,
  textColor: Color,
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(24.dp)
      .clip(RoundedCornerShape(6.dp)),
  ) {
    Box(
      modifier = Modifier
        .align(Alignment.CenterEnd)
        .fillMaxHeight()
        .fillMaxWidth(fillFraction)
        .background(barColor),
    )
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 5.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = priceText,
        style = MaterialTheme.typography.labelSmall.copy(
          fontFamily = JetBrainsMonoFontFamily,
          fontSize = 10.sp,
          fontWeight = FontWeight.SemiBold,
        ),
        color = textColor,
        maxLines = 1,
      )
      Text(
        text = sizeText,
        style = MaterialTheme.typography.labelSmall.copy(
          fontFamily = JetBrainsMonoFontFamily,
          fontSize = 10.sp,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
      )
    }
  }
}

@Composable
private fun SummaryRow(
  label: String,
  value: String,
  highlight: Boolean = false,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 2.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = label,
      style = if (highlight) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
      color = if (highlight) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = (if (highlight) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium)
        .copy(fontFamily = JetBrainsMonoFontFamily),
      color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
    )
  }
}
