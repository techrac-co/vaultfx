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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.MarketSnapshot
import com.example.model.AssetCatalog
import com.example.model.AssetCategory
import com.example.model.CurrencyAsset
import com.example.model.SavedPair
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

enum class PairFilter(val label: String) {
  ALL("All Markets"),
  FIAT("Fiat & FX"),
  CRYPTO("Spot Crypto"),
  PINNED("Favorites"),
}

@Composable
fun PairsScreen(
  marketSnapshot: MarketSnapshot,
  savedPairs: List<SavedPair>,
  onTogglePin: (baseCode: String, quoteCode: String, currentlyPinned: Boolean) -> Unit,
  onOpenInCalculator: (baseCode: String, quoteCode: String, amount: Double) -> Unit,
) {
  val vaultColors = LocalVaultColors.current
  var spotlightBase by remember { mutableStateOf("USD") }
  var spotlightQuote by remember { mutableStateOf("NGN") }
  var amountText by remember { mutableStateOf("1") }
  var searchQuery by remember { mutableStateOf("") }
  var selectedFilter by remember { mutableStateOf(PairFilter.ALL) }
  var pickingBase by remember { mutableStateOf(false) }
  var pickingQuote by remember { mutableStateOf(false) }
  var swapSpin by remember { mutableFloatStateOf(0f) }
  val swapRotation by animateFloatAsState(
    targetValue = swapSpin,
    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
    label = "pair_swap_spin",
  )

  val amount = amountText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 1.0
  val spotlightBaseAsset = AssetCatalog.find(spotlightBase)
  val spotlightQuoteAsset = AssetCatalog.find(spotlightQuote)
  val spotlightRate = marketSnapshot.rate(spotlightBase, spotlightQuote)
  val spotlightConverted = amount * spotlightRate
  val spotlightChange = marketSnapshot.pairChange24h(spotlightBase, spotlightQuote)
  val spotlightSpark = remember(spotlightBase, spotlightQuote, marketSnapshot) {
    marketSnapshot.sparklineFor(spotlightBase, spotlightQuote, points = 28)
  }

  val pinnedSet = remember(savedPairs) {
    savedPairs.map { "${it.baseCode}_${it.quoteCode}" }.toSet()
  }
  val isSpotlightPinned = "${spotlightBase}_$spotlightQuote" in pinnedSet

  val allPairDefinitions = remember(savedPairs) {
    val combined = linkedSetOf<Pair<String, String>>()
    savedPairs.forEach { combined.add(it.baseCode to it.quoteCode) }
    AssetCatalog.defaultPairs.forEach { combined.add(it) }
    combined.toList()
  }

  val filteredPairs = remember(allPairDefinitions, pinnedSet, searchQuery, selectedFilter) {
    allPairDefinitions.filter { (base, quote) ->
      val baseAsset = AssetCatalog.find(base)
      val quoteAsset = AssetCatalog.find(quote)
      val isPinned = "${base}_$quote" in pinnedSet
      val filterOk = when (selectedFilter) {
        PairFilter.ALL -> true
        PairFilter.FIAT -> baseAsset.category == AssetCategory.FIAT && quoteAsset.category == AssetCategory.FIAT
        PairFilter.CRYPTO -> baseAsset.category == AssetCategory.CRYPTO || quoteAsset.category == AssetCategory.CRYPTO
        PairFilter.PINNED -> isPinned
      }
      filterOk && matchesPairQuery(base, quote, searchQuery)
    }
  }

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
      // Apple iOS 27 Liquid Glass Spotlight Pair Converter Card
      item {
        LiquidGlassCard(
          accentTint = vaultColors.gold,
          shape = RoundedCornerShape(26.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "MARKET PAIR CONVERTER",
                style = MaterialTheme.typography.labelSmall,
                color = vaultColors.gold,
              )
              Row(verticalAlignment = Alignment.CenterVertically) {
                ChangeBadge(changePct = spotlightChange)
                Spacer(Modifier.width(4.dp))
                IconButton(
                  onClick = { onTogglePin(spotlightBase, spotlightQuote, isSpotlightPinned) },
                  modifier = Modifier.testTag("pair_spotlight_pin"),
                ) {
                  Icon(
                    imageVector = if (isSpotlightPinned) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                    contentDescription = if (isSpotlightPinned) "Unpin spotlight pair" else "Pin spotlight pair",
                    tint = if (isSpotlightPinned) vaultColors.gold else MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }

            Spacer(Modifier.height(8.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              CurrencySelectorPill(
                asset = spotlightBaseAsset,
                onClick = { pickingBase = true },
                modifier = Modifier.testTag("pair_spotlight_base"),
              )
              IconButton(
                onClick = {
                  swapSpin += 180f
                  val tmp = spotlightBase
                  spotlightBase = spotlightQuote
                  spotlightQuote = tmp
                },
                modifier = Modifier.testTag("pair_spotlight_swap"),
              ) {
                Icon(
                  imageVector = Icons.Rounded.SwapHoriz,
                  contentDescription = "Swap pair currencies",
                  tint = vaultColors.gold,
                  modifier = Modifier.graphicsLayer { rotationZ = swapRotation },
                )
              }
              CurrencySelectorPill(
                asset = spotlightQuoteAsset,
                onClick = { pickingQuote = true },
                modifier = Modifier.testTag("pair_spotlight_quote"),
              )
            }

            Spacer(Modifier.height(12.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "${NumberFormatter.formatAsset(amount, spotlightBaseAsset.decimals)} $spotlightBase =",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AnimatedContent(
                  targetState = "${spotlightQuoteAsset.symbol}${NumberFormatter.formatAsset(spotlightConverted, spotlightQuoteAsset.decimals)} $spotlightQuote",
                  transitionSpec = { fadeIn() togetherWith fadeOut() },
                  label = "spotlight_val_anim",
                ) { formatted ->
                  Text(
                    text = formatted,
                    style = MaterialTheme.typography.headlineLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("pair_spotlight_value"),
                  )
                }
                Text(
                  text = "1 $spotlightBase = ${NumberFormatter.formatRate(spotlightRate)} $spotlightQuote",
                  style = MaterialTheme.typography.labelSmall,
                  color = vaultColors.gold,
                )
              }

              MarketSparkline(
                points = spotlightSpark,
                isPositive = spotlightChange >= 0.0,
                showGrid = true,
                modifier = Modifier
                  .width(108.dp)
                  .height(54.dp),
              )
            }

            Spacer(Modifier.height(12.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              OutlinedTextField(
                value = amountText,
                onValueChange = { raw ->
                  val clean = raw.filter { it.isDigit() || it == '.' }
                  if (clean.count { it == '.' } <= 1) amountText = clean
                },
                label = { Text("Amount ($spotlightBase)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                  .weight(1f)
                  .testTag("pair_amount_input"),
              )

              listOf("1", "100", "1000").forEach { preset ->
                BouncySurface(
                  onClick = { amountText = preset },
                  modifier = Modifier.testTag("pair_preset_$preset"),
                  color = if (amountText == preset) vaultColors.goldContainer
                  else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                  shape = RoundedCornerShape(50),
                  border = BorderStroke(
                    1.dp,
                    if (amountText == preset) vaultColors.gold else vaultColors.glassBorder,
                  ),
                ) {
                  Text(
                    text = preset,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = if (amountText == preset) vaultColors.gold
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                  )
                }
              }
            }
          }
        }
      }

      // Search & Filter Pills
      item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Check USD to Naira, USD to GBP (GDP), BTC...") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(50),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("pairs_search_input"),
          )

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            PairFilter.entries.forEach { filter ->
              FilterChip(
                selected = selectedFilter == filter,
                onClick = { selectedFilter = filter },
                shape = RoundedCornerShape(50),
                label = { Text(filter.label) },
                modifier = Modifier.testTag("pair_filter_${filter.name.lowercase()}"),
              )
            }
          }
        }
      }

      if (filteredPairs.isEmpty()) {
        item {
          LiquidGlassCard(
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
              )
              Spacer(Modifier.height(8.dp))
              Text("No matching currency pairs", style = MaterialTheme.typography.titleMedium)
              Text(
                "Try searching USD, NGN, GBP, EUR, or BTC.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      } else {
        items(filteredPairs, key = { "${it.first}_${it.second}" }) { (base, quote) ->
          val baseAsset = AssetCatalog.find(base)
          val quoteAsset = AssetCatalog.find(quote)
          val rate = marketSnapshot.rate(base, quote)
          val scaled = amount * rate
          val chg = marketSnapshot.pairChange24h(base, quote)
          val spark = remember(base, quote, marketSnapshot) {
            marketSnapshot.sparklineFor(base, quote, points = 16)
          }
          val isPinned = "${base}_$quote" in pinnedSet
          val isSpotlight = base == spotlightBase && quote == spotlightQuote

          BouncySurface(
            onClick = {
              spotlightBase = base
              spotlightQuote = quote
            },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("pair_row_${base}_$quote"),
            color = vaultColors.glassSurface,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(
              width = if (isSpotlight) 1.3.dp else 1.dp,
              color = if (isSpotlight) vaultColors.gold else vaultColors.glassBorder,
            ),
            pressedScale = 0.98f,
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(modifier = Modifier.width(48.dp)) {
                AssetBadge(asset = baseAsset, size = 32.dp)
                Box(modifier = Modifier.padding(start = 18.dp, top = 8.dp)) {
                  AssetBadge(asset = quoteAsset, size = 24.dp)
                }
              }

              Spacer(Modifier.width(10.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "$base / $quote",
                  style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                  ),
                )
                Text(
                  text = "${baseAsset.name} to ${quoteAsset.name}",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }

              MarketSparkline(
                points = spark,
                isPositive = chg >= 0.0,
                modifier = Modifier
                  .width(48.dp)
                  .height(26.dp),
              )

              Spacer(Modifier.width(10.dp))
              Column(horizontalAlignment = Alignment.End) {
                Text(
                  text = "${quoteAsset.symbol}${NumberFormatter.formatRate(scaled)}",
                  style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                  color = MaterialTheme.colorScheme.onSurface,
                )
                ChangeBadge(changePct = chg)
              }

              IconButton(
                onClick = { onOpenInCalculator(base, quote, amount) },
                modifier = Modifier.testTag("pair_calc_${base}_$quote"),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Calculate,
                  contentDescription = "Open $base/$quote in calculator",
                  tint = vaultColors.gold,
                  modifier = Modifier.size(20.dp),
                )
              }

              IconButton(
                onClick = { onTogglePin(base, quote, isPinned) },
                modifier = Modifier.testTag("pair_pin_${base}_$quote"),
              ) {
                Icon(
                  imageVector = if (isPinned) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                  contentDescription = if (isPinned) "Unpin $base/$quote" else "Pin $base/$quote",
                  tint = if (isPinned) vaultColors.gold else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(20.dp),
                )
              }
            }
          }
        }
      }

      item { Spacer(Modifier.height(16.dp)) }
    }
  }

  if (pickingBase) {
    AssetPickerSheet(
      title = "Select Base Currency",
      selectedCode = spotlightBase,
      onSelect = { spotlightBase = it.code },
      onDismiss = { pickingBase = false },
    )
  }

  if (pickingQuote) {
    AssetPickerSheet(
      title = "Select Quote Currency",
      selectedCode = spotlightQuote,
      onSelect = { spotlightQuote = it.code },
      onDismiss = { pickingQuote = false },
    )
  }
}

private fun assetMatchesToken(asset: CurrencyAsset, token: String): Boolean {
  return asset.code.lowercase().contains(token) ||
    asset.name.lowercase().contains(token) ||
    asset.searchAliases.any { it.contains(token) }
}

fun matchesPairQuery(baseCode: String, quoteCode: String, rawQuery: String): Boolean {
  val q = rawQuery.trim().lowercase()
  if (q.isEmpty()) return true
  val baseAsset = AssetCatalog.find(baseCode)
  val quoteAsset = AssetCatalog.find(quoteCode)
  if ("$baseCode/$quoteCode".lowercase().contains(q) || "$baseCode $quoteCode".lowercase().contains(q)) {
    return true
  }
  val stopWords = setOf("to", "in", "into", "for", "vs", "and", "per", "=", "rate", "price", "check", "how", "much", "is")
  val tokens = q.split(Regex("[\\s/\\->→]+"))
    .map { it.trim() }
    .filter { it.isNotEmpty() && it !in stopWords }
  if (tokens.isEmpty()) return true
  if (tokens.size == 1) {
    val t = tokens[0]
    return assetMatchesToken(baseAsset, t) || assetMatchesToken(quoteAsset, t)
  }
  val first = tokens.first()
  val last = tokens.last()
  val forwardMatch = assetMatchesToken(baseAsset, first) && assetMatchesToken(quoteAsset, last)
  val reverseMatch = assetMatchesToken(baseAsset, last) && assetMatchesToken(quoteAsset, first)
  return forwardMatch || reverseMatch || tokens.all { t ->
    assetMatchesToken(baseAsset, t) || assetMatchesToken(quoteAsset, t)
  }
}
