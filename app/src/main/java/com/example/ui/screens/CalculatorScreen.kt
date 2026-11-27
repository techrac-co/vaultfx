package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CandlestickChart
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MarketSnapshot
import com.example.model.AssetCatalog
import com.example.ui.CalculatorUiState
import com.example.ui.components.AssetBadge
import com.example.ui.components.AssetPickerSheet
import com.example.ui.components.BouncySurface
import com.example.ui.components.CurrencySelectorPill
import com.example.ui.components.LiquidGlassCard
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalVaultColors
import com.example.util.NumberFormatter

@Composable
fun CalculatorScreen(
  calcState: CalculatorUiState,
  marketSnapshot: MarketSnapshot,
  onKey: (String) -> Unit,
  onSwapCurrencies: () -> Unit,
  onSelectCurrencies: (String, String) -> Unit,
  onSendToTrade: () -> Unit,
  onOpenSendSheet: (assetCode: String, amount: Double) -> Unit,
  onOpenEscrowSheet: (assetCode: String, amount: Double) -> Unit,
  onOpenHistory: () -> Unit,
) {
  val vaultColors = LocalVaultColors.current
  val haptics = LocalHapticFeedback.current
  var pickingBase by remember { mutableStateOf(false) }
  var pickingTarget by remember { mutableStateOf(false) }
  var swapRotationTarget by remember { mutableFloatStateOf(0f) }
  val swapRotation by animateFloatAsState(
    targetValue = swapRotationTarget,
    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
    label = "swap_spin",
  )

  val baseAsset = remember(calcState.baseCode) { AssetCatalog.find(calcState.baseCode) }
  val targetAsset = remember(calcState.targetCode) { AssetCatalog.find(calcState.targetCode) }
  val convertedValue = remember(calcState.evaluatedValue, calcState.baseCode, calcState.targetCode, marketSnapshot) {
    marketSnapshot.convert(calcState.evaluatedValue, calcState.baseCode, calcState.targetCode)
  }
  val unitRate = remember(calcState.baseCode, calcState.targetCode, marketSnapshot) {
    marketSnapshot.rate(calcState.baseCode, calcState.targetCode)
  }

  // Live Bybit Ticker Ribbon pairs
  val tickerPairs = remember {
    listOf("BTC" to "USD", "ETH" to "USD", "USD" to "NGN", "USD" to "GBP")
  }

  // Simultaneous multi-currency strip excluding base & target
  val multiConvertAssets = remember(calcState.baseCode, calcState.targetCode) {
    listOf("USD", "NGN", "GBP", "EUR", "BTC", "ETH", "SOL", "USDT")
      .filter { it != calcState.baseCode && it != calcState.targetCode }
      .take(4)
      .map { AssetCatalog.find(it) }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 14.dp, vertical = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 600.dp),
    ) {
      // Compact Bybit Live Market Ticker Pills
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
      ) {
        tickerPairs.forEach { (b, q) ->
          val rate = marketSnapshot.rate(b, q)
          val chg = marketSnapshot.pairChange24h(b, q)
          val quoteSym = AssetCatalog.find(q).symbol
          BouncySurface(
            onClick = { onSelectCurrencies(b, q) },
            color = vaultColors.glassSurface,
            shape = RoundedCornerShape(50),
            border = BorderStroke(0.9.dp, vaultColors.glassBorder),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "$b/$q",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
              )
              Spacer(Modifier.width(5.dp))
              Text(
                text = "$quoteSym${NumberFormatter.formatRate(rate)}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                color = vaultColors.gold,
              )
              Spacer(Modifier.width(4.dp))
              Text(
                text = "${if (chg >= 0) "+" else ""}${"%.1f".format(java.util.Locale.US, chg)}%",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = if (chg >= 0) vaultColors.positive else vaultColors.negative,
              )
            }
          }
        }
      }

      // Apple iOS 27 Liquid Glass Dual Display Deck
      Box(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          LiquidGlassCard(
            accentTint = vaultColors.gold,
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                CurrencySelectorPill(
                  asset = baseAsset,
                  onClick = { pickingBase = true },
                  modifier = Modifier.testTag("calc_base_currency_pill"),
                )
                Text(
                  text = calcState.expression,
                  style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                  ),
                  color = if (calcState.hasSyntaxError) vaultColors.negative
                  else MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  textAlign = TextAlign.End,
                  modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
                    .testTag("calc_expression_text"),
                )
              }

              Spacer(Modifier.height(10.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
              ) {
                Text(
                  text = if (calcState.hasSyntaxError) "Invalid expression" else baseAsset.name,
                  style = MaterialTheme.typography.labelMedium,
                  color = if (calcState.hasSyntaxError) vaultColors.negative else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AnimatedContent(
                  targetState = NumberFormatter.formatSignedCurrency(
                    symbol = baseAsset.symbol,
                    value = calcState.evaluatedValue,
                    calcStyle = true,
                  ),
                  transitionSpec = { fadeIn() togetherWith fadeOut() },
                  label = "calc_base_num",
                ) { formatted ->
                  Text(
                    text = formatted,
                    style = MaterialTheme.typography.displayMedium.copy(
                      fontFamily = JetBrainsMonoFontFamily,
                      fontSize = 30.sp,
                      fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("calc_base_result"),
                  )
                }
              }
            }
          }

          LiquidGlassCard(
            accentTint = vaultColors.cyan,
            containerColor = vaultColors.glassElevated,
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                CurrencySelectorPill(
                  asset = targetAsset,
                  onClick = { pickingTarget = true },
                  modifier = Modifier.testTag("calc_target_currency_pill"),
                )
                Text(
                  text = "1 ${baseAsset.code} = ${NumberFormatter.formatRate(unitRate)} ${targetAsset.code}",
                  style = MaterialTheme.typography.labelSmall,
                  color = vaultColors.gold,
                )
              }

              Spacer(Modifier.height(10.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
              ) {
                Text(
                  text = targetAsset.name,
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AnimatedContent(
                  targetState = NumberFormatter.formatSignedCurrency(
                    symbol = targetAsset.symbol,
                    value = convertedValue,
                    calcStyle = false,
                    maxDecimals = targetAsset.decimals,
                  ),
                  transitionSpec = { fadeIn() togetherWith fadeOut() },
                  label = "calc_target_num",
                ) { formatted ->
                  Text(
                    text = formatted,
                    style = MaterialTheme.typography.displayMedium.copy(
                      fontFamily = JetBrainsMonoFontFamily,
                      fontSize = 28.sp,
                      fontWeight = FontWeight.Bold,
                    ),
                    color = vaultColors.positive,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("calc_target_result"),
                  )
                }
              }
            }
          }
        }

        // Overlapping Liquid Glass Swap Orb
        BouncySurface(
          onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            swapRotationTarget += 180f
            onSwapCurrencies()
          },
          modifier = Modifier
            .align(Alignment.Center)
            .size(44.dp)
            .minimumInteractiveComponentSize()
            .testTag("calc_swap_button"),
          shape = CircleShape,
          color = vaultColors.gold,
          border = BorderStroke(2.dp, MaterialTheme.colorScheme.background),
          shadowElevation = 10.dp,
        ) {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Rounded.SwapVert,
              contentDescription = "Swap base and target currencies",
              tint = Color(0xFF1A1100),
              modifier = Modifier
                .size(22.dp)
                .graphicsLayer { rotationZ = swapRotation },
            )
          }
        }
      }

      Spacer(Modifier.height(10.dp))

      // Simultaneous Multi-Currency Live Strip
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        multiConvertAssets.forEach { asset ->
          val valConverted = marketSnapshot.convert(calcState.evaluatedValue, calcState.baseCode, asset.code)
          BouncySurface(
            onClick = { onSelectCurrencies(calcState.baseCode, asset.code) },
            modifier = Modifier.testTag("multi_strip_${asset.code}"),
            color = vaultColors.glassSurface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, vaultColors.glassBorder),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              AssetBadge(asset = asset, size = 22.dp)
              Spacer(Modifier.width(7.dp))
              Column {
                Text(
                  text = asset.code,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = NumberFormatter.formatSignedCurrency(
                    symbol = asset.symbol,
                    value = valConverted,
                    calcStyle = false,
                    maxDecimals = asset.decimals,
                  ),
                  style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                  color = MaterialTheme.colorScheme.onSurface,
                )
              }
            }
          }
        }
      }

      Spacer(Modifier.height(10.dp))

      // Quick Actions Row (Trade, Send, Escrow, History) in iOS 27 Glass Capsules
      val preferredCryptoCode = if (baseAsset.category.name == "CRYPTO") baseAsset.code
      else if (targetAsset.category.name == "CRYPTO") targetAsset.code
      else "USDT"
      val preferredCryptoAmt = marketSnapshot.convert(calcState.evaluatedValue.coerceAtLeast(0.0), calcState.baseCode, preferredCryptoCode)

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        QuickCalcActionPill(
          icon = Icons.Rounded.CandlestickChart,
          label = "Trade",
          accent = vaultColors.gold,
          onClick = onSendToTrade,
          modifier = Modifier
            .weight(1f)
            .testTag("calc_to_trade_button"),
        )
        QuickCalcActionPill(
          icon = Icons.AutoMirrored.Rounded.Send,
          label = "Send",
          accent = vaultColors.positive,
          onClick = { onOpenSendSheet(preferredCryptoCode, preferredCryptoAmt) },
          modifier = Modifier
            .weight(1f)
            .testTag("calc_to_send_button"),
        )
        QuickCalcActionPill(
          icon = Icons.Rounded.Shield,
          label = "Escrow",
          accent = vaultColors.violet,
          onClick = { onOpenEscrowSheet(preferredCryptoCode, preferredCryptoAmt) },
          modifier = Modifier
            .weight(1f)
            .testTag("calc_to_escrow_button"),
        )
        QuickCalcActionPill(
          icon = Icons.Rounded.History,
          label = "${calcState.history.size}",
          accent = vaultColors.cyan,
          onClick = onOpenHistory,
          modifier = Modifier
            .weight(0.75f)
            .testTag("calc_history_button"),
        )
      }

      Spacer(Modifier.height(10.dp))
      HorizontalDivider(color = vaultColors.glassBorder)
      Spacer(Modifier.height(10.dp))

      // Precision Apple iOS 27 Frosted Glass Keypad
      val smartParen = remember(calcState.expression) {
        val openCount = calcState.expression.count { it == '(' }
        val closeCount = calcState.expression.count { it == ')' }
        if (openCount > closeCount && calcState.expression.lastOrNull()?.let { it.isDigit() || it == ')' || it == '%' } == true) ")" else "("
      }

      val rows = listOf(
        listOf("AC", smartParen, "%", "÷"),
        listOf("7", "8", "9", "×"),
        listOf("4", "5", "6", "-"),
        listOf("1", "2", "3", "+"),
        listOf("0", ".", "⌫", "="),
      )

      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { rowKeys ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            rowKeys.forEach { keyLabel ->
              val isEquals = keyLabel == "="
              val isClear = keyLabel == "AC"
              val isOperator = keyLabel in setOf("÷", "×", "-", "+", "%", "(", ")")
              val isBackspace = keyLabel == "⌫"

              val containerColor = when {
                isEquals -> vaultColors.gold
                isClear -> vaultColors.negativeContainer
                isOperator -> vaultColors.glassElevated
                else -> vaultColors.glassSurface
              }
              val contentColor = when {
                isEquals -> Color(0xFF1A1100)
                isClear -> vaultColors.negative
                isOperator -> vaultColors.gold
                else -> MaterialTheme.colorScheme.onSurface
              }

              BouncySurface(
                onClick = {
                  haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  onKey(keyLabel)
                },
                modifier = Modifier
                  .weight(1f)
                  .height(55.dp)
                  .minimumInteractiveComponentSize()
                  .testTag("calc_key_$keyLabel"),
                color = containerColor,
                shape = RoundedCornerShape(20.dp),
                border = if (isEquals) null else BorderStroke(
                  1.dp,
                  Brush.verticalGradient(
                    colors = listOf(
                      vaultColors.glassHighlight.copy(alpha = if (vaultColors.isDark) 0.20f else 0.85f),
                      if (isOperator) vaultColors.gold.copy(alpha = 0.25f) else vaultColors.glassBorder,
                    )
                  ),
                ),
                shadowElevation = if (isEquals) 8.dp else 2.dp,
                pressedScale = 0.91f,
              ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                  if (isBackspace) {
                    Icon(
                      imageVector = Icons.AutoMirrored.Rounded.Backspace,
                      contentDescription = "Backspace",
                      tint = contentColor,
                      modifier = Modifier.size(22.dp),
                    )
                  } else {
                    Text(
                      text = keyLabel,
                      style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = if (isOperator || isEquals || isClear) MaterialTheme.typography.titleLarge.fontFamily else JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 21.sp,
                      ),
                      color = contentColor,
                    )
                  }
                }
              }
            }
          }
        }
      }

      Spacer(Modifier.height(16.dp))
    }
  }

  if (pickingBase) {
    AssetPickerSheet(
      title = "Select Base Currency",
      selectedCode = calcState.baseCode,
      onSelect = { onSelectCurrencies(it.code, calcState.targetCode) },
      onDismiss = { pickingBase = false },
    )
  }

  if (pickingTarget) {
    AssetPickerSheet(
      title = "Select Target Currency",
      selectedCode = calcState.targetCode,
      onSelect = { onSelectCurrencies(calcState.baseCode, it.code) },
      onDismiss = { pickingTarget = false },
    )
  }
}

@Composable
private fun QuickCalcActionPill(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  accent: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val vaultColors = LocalVaultColors.current
  BouncySurface(
    onClick = onClick,
    modifier = modifier.minimumInteractiveComponentSize(),
    color = vaultColors.glassSurface,
    shape = RoundedCornerShape(50),
    border = BorderStroke(1.dp, vaultColors.glassBorder),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = accent,
        modifier = Modifier.size(16.dp),
      )
      Spacer(Modifier.width(5.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
