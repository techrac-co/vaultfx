package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.Brightness7
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AssetCatalog
import com.example.model.AssetCategory
import com.example.model.CurrencyAsset
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalVaultColors
import com.example.util.NumberFormatter

@Composable
fun AnimatedAuroraBackground(
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.() -> Unit,
) {
  val vaultColors = LocalVaultColors.current
  val transition = rememberInfiniteTransition(label = "aurora_bg")
  val drift by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 9000, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "aurora_drift",
  )

  val bgColor = MaterialTheme.colorScheme.background
  val mintAlpha = if (vaultColors.isDark) 0.13f else 0.08f
  val cyanAlpha = if (vaultColors.isDark) 0.10f else 0.06f
  val violetAlpha = if (vaultColors.isDark) 0.11f else 0.05f

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(bgColor),
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val w = size.width
      val h = size.height
      if (w <= 0f || h <= 0f) return@Canvas

      val mintCenter = Offset(w * (0.18f + 0.14f * drift), h * (0.12f + 0.08f * drift))
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(vaultColors.positive.copy(alpha = mintAlpha), Color.Transparent),
          center = mintCenter,
          radius = w * 0.72f,
        ),
        center = mintCenter,
        radius = w * 0.72f,
      )

      val violetCenter = Offset(w * (0.85f - 0.15f * drift), h * (0.28f + 0.12f * drift))
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(vaultColors.violet.copy(alpha = violetAlpha), Color.Transparent),
          center = violetCenter,
          radius = w * 0.65f,
        ),
        center = violetCenter,
        radius = w * 0.65f,
      )

      val cyanCenter = Offset(w * (0.5f + 0.18f * (1f - drift)), h * 0.86f)
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(vaultColors.cyan.copy(alpha = cyanAlpha), Color.Transparent),
          center = cyanCenter,
          radius = w * 0.70f,
        ),
        center = cyanCenter,
        radius = w * 0.70f,
      )
    }

    content()
  }
}

@Composable
fun GradientBorderSurface(
  modifier: Modifier = Modifier,
  shape: Shape = RoundedCornerShape(24.dp),
  containerColor: Color = MaterialTheme.colorScheme.surface,
  borderColors: List<Color>? = null,
  borderWidth: Dp = 1.3.dp,
  content: @Composable BoxScope.() -> Unit,
) {
  val vaultColors = LocalVaultColors.current
  val colors = borderColors ?: listOf(
    vaultColors.positive.copy(alpha = 0.72f),
    vaultColors.cyan.copy(alpha = 0.55f),
    vaultColors.violet.copy(alpha = 0.62f),
  )

  Surface(
    modifier = modifier,
    color = containerColor,
    shape = shape,
    border = BorderStroke(
      width = borderWidth,
      brush = Brush.linearGradient(colors = colors),
    ),
    shadowElevation = if (vaultColors.isDark) 8.dp else 2.dp,
  ) {
    Box(content = content)
  }
}

@Composable
fun BouncySurface(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  shape: Shape = RoundedCornerShape(16.dp),
  color: Color = MaterialTheme.colorScheme.surface,
  border: BorderStroke? = null,
  shadowElevation: Dp = 0.dp,
  enabled: Boolean = true,
  pressedScale: Float = 0.94f,
  content: @Composable BoxScope.() -> Unit,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (isPressed && enabled) pressedScale else 1f,
    animationSpec = spring(
      dampingRatio = Spring.DampingRatioMediumBouncy,
      stiffness = Spring.StiffnessMedium,
    ),
    label = "bouncy_scale",
  )

  Surface(
    modifier = modifier
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
      }
      .clip(shape)
      .clickable(
        interactionSource = interactionSource,
        indication = androidx.compose.material3.ripple(),
        enabled = enabled,
        onClick = onClick,
      ),
    color = color,
    shape = shape,
    border = border,
    shadowElevation = shadowElevation,
  ) {
    Box(content = content)
  }
}

@Composable
fun VaultTopBar(
  title: String,
  isLive: Boolean,
  isRefreshing: Boolean,
  isDarkTheme: Boolean,
  payTag: String?,
  showLockButton: Boolean = false,
  onLockClick: () -> Unit = {},
  onRefreshClick: () -> Unit,
  onToggleTheme: () -> Unit,
  onProfileClick: () -> Unit,
) {
  val vaultColors = LocalVaultColors.current
  val pulseTransition = rememberInfiniteTransition(label = "live_pulse")
  val pulseAlpha by pulseTransition.animateFloat(
    initialValue = 0.35f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
    label = "pulse_alpha",
  )

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .weight(1f)
        .padding(end = 6.dp),
    ) {
      Box(
        modifier = Modifier
          .size(38.dp)
          .clip(RoundedCornerShape(12.dp))
          .border(
            width = 1.4.dp,
            brush = Brush.linearGradient(
              listOf(vaultColors.positive, vaultColors.cyan, vaultColors.violet)
            ),
            shape = RoundedCornerShape(12.dp),
          ),
      ) {
        Image(
          painter = painterResource(id = R.drawable.ic_vault_logo),
          contentDescription = "VaultFX logo",
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Crop,
        )
      }
      Spacer(Modifier.width(10.dp))
      Column {
        Text(
          text = title,
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onBackground,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (!payTag.isNullOrBlank()) {
          Text(
            text = "@$payTag",
            style = MaterialTheme.typography.labelSmall,
            color = vaultColors.gold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }

    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      BouncySurface(
        onClick = onRefreshClick,
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("refresh_rates_pill"),
        color = if (isLive) vaultColors.positiveContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(50),
        border = BorderStroke(
          1.dp,
          if (isLive) vaultColors.positive.copy(alpha = 0.5f) else vaultColors.cardBorder,
        ),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier = Modifier
              .size(7.dp)
              .clip(CircleShape)
              .background(
                if (isLive) vaultColors.positive.copy(alpha = pulseAlpha)
                else vaultColors.gold
              ),
          )
          Spacer(Modifier.width(6.dp))
          Text(
            text = if (isRefreshing) "SYNC" else if (isLive) "LIVE" else "RATE",
            style = MaterialTheme.typography.labelSmall,
            color = if (isLive) vaultColors.positive else MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.width(4.dp))
          Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = "Refresh live market rates",
            tint = if (isLive) vaultColors.positive else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
          )
        }
      }

      IconButton(
        onClick = onToggleTheme,
        modifier = Modifier.testTag("theme_toggle_button"),
      ) {
        Icon(
          imageVector = if (isDarkTheme) Icons.Rounded.Brightness7 else Icons.Rounded.Brightness4,
          contentDescription = "Toggle light or dark theme",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      if (showLockButton) {
        IconButton(
          onClick = onLockClick,
          modifier = Modifier.testTag("top_bar_lock_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = "Lock VaultFX now",
            tint = vaultColors.positive,
          )
        }
      }

      BouncySurface(
        onClick = onProfileClick,
        modifier = Modifier
          .size(40.dp)
          .minimumInteractiveComponentSize()
          .testTag("profile_button"),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
          1.2.dp,
          Brush.linearGradient(listOf(vaultColors.positive.copy(alpha = 0.65f), vaultColors.cyan.copy(alpha = 0.65f))),
        ),
      ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Rounded.Person,
            contentDescription = "Open wallet profile and sign out",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
          )
        }
      }
    }
  }
}

@Composable
fun AssetBadge(
  asset: CurrencyAsset,
  size: Dp = 36.dp,
) {
  val accent = Color(asset.badgeHex)
  Box(
    modifier = Modifier
      .size(size)
      .clip(CircleShape)
      .background(
        Brush.radialGradient(
          colors = listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0.10f))
        )
      )
      .border(1.1.dp, accent.copy(alpha = 0.65f), CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = if (asset.symbol.length <= 2) asset.symbol else asset.code.take(3),
      style = MaterialTheme.typography.labelMedium.copy(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = if (size >= 40.dp) 15.sp else 12.sp,
      ),
      color = accent,
    )
  }
}

@Composable
fun CurrencySelectorPill(
  asset: CurrencyAsset,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val vaultColors = LocalVaultColors.current
  BouncySurface(
    onClick = onClick,
    modifier = modifier.minimumInteractiveComponentSize(),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    shape = RoundedCornerShape(14.dp),
    border = BorderStroke(1.dp, vaultColors.cardBorder),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      AssetBadge(asset = asset, size = 24.dp)
      Spacer(Modifier.width(8.dp))
      Text(
        text = asset.code,
        style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(Modifier.width(2.dp))
      Icon(
        imageVector = Icons.Rounded.KeyboardArrowDown,
        contentDescription = "Select currency",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp),
      )
    }
  }
}

@Composable
fun MarketSparkline(
  points: List<Double>,
  isPositive: Boolean,
  modifier: Modifier = Modifier,
  showGrid: Boolean = false,
) {
  val vaultColors = LocalVaultColors.current
  val strokeColor = if (isPositive) vaultColors.positive else vaultColors.negative
  val gridColor = vaultColors.cardBorder.copy(alpha = 0.45f)

  Canvas(modifier = modifier) {
    if (points.size < 2 || size.width <= 0f || size.height <= 0f) return@Canvas
    val minVal = points.minOrNull() ?: 0.0
    val maxVal = points.maxOrNull() ?: 1.0
    val rawRange = maxVal - minVal
    val isFlat = rawRange < 1e-12
    val padY = size.height * 0.14f
    val usableHeight = (size.height - padY * 2).coerceAtLeast(1f)

    if (showGrid) {
      val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
      listOf(0.25f, 0.5f, 0.75f).forEach { frac ->
        val y = size.height * frac
        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = dash)
      }
    }

    val linePath = Path()
    val fillPath = Path()
    var lastOffset = Offset.Zero

    points.forEachIndexed { idx, value ->
      val x = (idx.toFloat() / (points.size - 1)) * size.width
      val normY = if (isFlat) 0.5f else ((value - minVal) / rawRange).toFloat()
      val y = size.height - padY - (normY * usableHeight)
      lastOffset = Offset(x, y)
      if (idx == 0) {
        linePath.moveTo(x, y)
        fillPath.moveTo(x, size.height)
        fillPath.lineTo(x, y)
      } else {
        linePath.lineTo(x, y)
        fillPath.lineTo(x, y)
      }
    }
    fillPath.lineTo(size.width, size.height)
    fillPath.close()

    drawPath(
      path = fillPath,
      brush = Brush.verticalGradient(
        colors = listOf(strokeColor.copy(alpha = 0.30f), Color.Transparent),
      ),
    )
    drawPath(
      path = linePath,
      color = strokeColor,
      style = Stroke(width = 2.3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    if (showGrid) {
      drawCircle(
        color = strokeColor.copy(alpha = 0.25f),
        radius = 6.dp.toPx(),
        center = lastOffset,
      )
      drawCircle(
        color = strokeColor,
        radius = 3.dp.toPx(),
        center = lastOffset,
      )
    }
  }
}

@Composable
fun ChangeBadge(changePct: Double) {
  val vaultColors = LocalVaultColors.current
  val positive = changePct >= 0.0
  val bg = if (positive) vaultColors.positiveContainer else vaultColors.negativeContainer
  val fg = if (positive) vaultColors.positive else vaultColors.negative
  val prefix = if (positive) "+" else ""

  Surface(
    color = bg,
    shape = RoundedCornerShape(8.dp),
    border = BorderStroke(0.75.dp, fg.copy(alpha = 0.3f)),
  ) {
    Text(
      text = "$prefix${"%.2f".format(java.util.Locale.US, changePct)}%",
      style = MaterialTheme.typography.labelSmall,
      color = fg,
      modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetPickerSheet(
  title: String,
  assets: List<CurrencyAsset> = AssetCatalog.assets,
  selectedCode: String,
  onSelect: (CurrencyAsset) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var query by remember { mutableStateOf("") }
  var filterCategory by remember { mutableStateOf<AssetCategory?>(null) }

  val filtered = remember(query, filterCategory, assets) {
    val q = query.trim().lowercase()
    assets.filter { asset ->
      val catMatch = filterCategory == null || asset.category == filterCategory
      val qMatch = q.isEmpty() ||
        asset.code.lowercase().contains(q) ||
        asset.name.lowercase().contains(q) ||
        asset.searchAliases.any { it.contains(q) }
      catMatch && qMatch
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(bottom = 24.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        IconButton(onClick = onDismiss) {
          Icon(Icons.Rounded.Close, contentDescription = "Close currency selector")
        }
      }

      Spacer(Modifier.height(10.dp))
      OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        placeholder = { Text("Search BTC, USD, Naira, GBP / GDP...") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("asset_picker_search"),
      )

      Spacer(Modifier.height(10.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
          selected = filterCategory == null,
          onClick = { filterCategory = null },
          label = { Text("All") },
        )
        FilterChip(
          selected = filterCategory == AssetCategory.CRYPTO,
          onClick = { filterCategory = AssetCategory.CRYPTO },
          label = { Text("Digital Crypto") },
        )
        FilterChip(
          selected = filterCategory == AssetCategory.FIAT,
          onClick = { filterCategory = AssetCategory.FIAT },
          label = { Text("Fiat FX") },
        )
      }

      Spacer(Modifier.height(10.dp))
      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .height(360.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(filtered, key = { it.code }) { asset ->
          val isSelected = asset.code == selectedCode
          Surface(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(16.dp))
              .clickable {
                onSelect(asset)
                onDismiss()
              }
              .testTag("pick_asset_${asset.code}"),
            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp),
            border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              AssetBadge(asset = asset, size = 38.dp)
              Spacer(Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = asset.code,
                  style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                )
                Text(
                  text = "${asset.name} • ${asset.networkName}",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              Text(
                text = "$${NumberFormatter.formatRate(asset.defaultUsdPrice)}",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun QrCodeCanvas(
  matrix: Array<BooleanArray>,
  modifier: Modifier = Modifier,
) {
  Canvas(
    modifier = modifier
      .clip(RoundedCornerShape(16.dp))
      .background(Color.White)
      .padding(12.dp),
  ) {
    val n = matrix.size
    if (n == 0) return@Canvas
    val cellSize = size.minDimension / n
    val offsetX = (size.width - cellSize * n) / 2f
    val offsetY = (size.height - cellSize * n) / 2f
    for (y in 0 until n) {
      for (x in 0 until n) {
        if (matrix[y][x]) {
          drawRect(
            color = Color(0xFF06080E),
            topLeft = Offset(offsetX + x * cellSize, offsetY + y * cellSize),
            size = androidx.compose.ui.geometry.Size(cellSize + 0.5f, cellSize + 0.5f),
          )
        }
      }
    }
  }
}
