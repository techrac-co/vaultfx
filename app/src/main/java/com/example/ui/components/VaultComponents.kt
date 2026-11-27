package com.example.ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.Brightness7
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.ui.MainTab
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalVaultColors
import com.example.util.NumberFormatter
import com.example.util.UsernameValidator

@Composable
fun AnimatedAuroraBackground(
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.() -> Unit,
) {
  val vaultColors = LocalVaultColors.current
  val transition = rememberInfiniteTransition(label = "liquid_glass_bg")
  val drift by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 10000, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "aurora_drift",
  )

  val bgColor = MaterialTheme.colorScheme.background
  val goldAlpha = if (vaultColors.isDark) 0.11f else 0.08f
  val cyanAlpha = if (vaultColors.isDark) 0.10f else 0.08f
  val mintAlpha = if (vaultColors.isDark) 0.08f else 0.06f

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(bgColor),
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val w = size.width
      val h = size.height
      if (w <= 0f || h <= 0f) return@Canvas

      val goldCenter = Offset(w * (0.20f + 0.12f * drift), h * (0.10f + 0.07f * drift))
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(vaultColors.gold.copy(alpha = goldAlpha), Color.Transparent),
          center = goldCenter,
          radius = w * 0.72f,
        ),
        center = goldCenter,
        radius = w * 0.72f,
      )

      val cyanCenter = Offset(w * (0.86f - 0.14f * drift), h * (0.30f + 0.10f * drift))
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(vaultColors.cyan.copy(alpha = cyanAlpha), Color.Transparent),
          center = cyanCenter,
          radius = w * 0.68f,
        ),
        center = cyanCenter,
        radius = w * 0.68f,
      )

      val mintCenter = Offset(w * (0.48f + 0.16f * (1f - drift)), h * 0.88f)
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(vaultColors.positive.copy(alpha = mintAlpha), Color.Transparent),
          center = mintCenter,
          radius = w * 0.70f,
        ),
        center = mintCenter,
        radius = w * 0.70f,
      )
    }

    content()
  }
}

@Composable
fun LiquidGlassCard(
  modifier: Modifier = Modifier,
  shape: Shape = RoundedCornerShape(26.dp),
  containerColor: Color = LocalVaultColors.current.glassSurface,
  accentTint: Color? = null,
  borderWidth: Dp = 1.15.dp,
  content: @Composable BoxScope.() -> Unit,
) {
  val vaultColors = LocalVaultColors.current
  val rimColors = if (accentTint != null) {
    listOf(
      vaultColors.glassHighlight,
      accentTint.copy(alpha = if (vaultColors.isDark) 0.52f else 0.45f),
      vaultColors.glassBorder,
    )
  } else {
    listOf(
      vaultColors.glassHighlight,
      vaultColors.cyan.copy(alpha = if (vaultColors.isDark) 0.22f else 0.18f),
      vaultColors.glassBorder,
    )
  }

  Surface(
    modifier = modifier,
    color = containerColor,
    shape = shape,
    border = BorderStroke(
      width = borderWidth,
      brush = Brush.linearGradient(colors = rimColors),
    ),
    shadowElevation = if (vaultColors.isDark) 10.dp else 4.dp,
  ) {
    Box(
      modifier = Modifier.background(
        Brush.verticalGradient(
          colors = listOf(
            Color.White.copy(alpha = if (vaultColors.isDark) 0.055f else 0.35f),
            Color.Transparent,
            Color.Black.copy(alpha = if (vaultColors.isDark) 0.12f else 0.02f),
          )
        )
      ),
      content = content,
    )
  }
}

@Composable
fun GradientBorderSurface(
  modifier: Modifier = Modifier,
  shape: Shape = RoundedCornerShape(26.dp),
  containerColor: Color = LocalVaultColors.current.glassSurface,
  borderColors: List<Color>? = null,
  borderWidth: Dp = 1.25.dp,
  content: @Composable BoxScope.() -> Unit,
) {
  val vaultColors = LocalVaultColors.current
  val colors = borderColors?.let {
    listOf(vaultColors.glassHighlight) + it
  } ?: listOf(
    vaultColors.glassHighlight,
    vaultColors.gold.copy(alpha = 0.55f),
    vaultColors.cyan.copy(alpha = 0.45f),
    vaultColors.glassBorder,
  )

  Surface(
    modifier = modifier,
    color = containerColor,
    shape = shape,
    border = BorderStroke(
      width = borderWidth,
      brush = Brush.linearGradient(colors = colors),
    ),
    shadowElevation = if (vaultColors.isDark) 10.dp else 3.dp,
  ) {
    Box(
      modifier = Modifier.background(
        Brush.verticalGradient(
          colors = listOf(
            Color.White.copy(alpha = if (vaultColors.isDark) 0.05f else 0.32f),
            Color.Transparent,
          )
        )
      ),
      content = content,
    )
  }
}

@Composable
fun BouncySurface(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  shape: Shape = RoundedCornerShape(18.dp),
  color: Color = LocalVaultColors.current.glassSurface,
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
  onCreateUsernameClick: () -> Unit = onProfileClick,
) {
  val vaultColors = LocalVaultColors.current
  val pulseTransition = rememberInfiniteTransition(label = "live_pulse")
  val pulseAlpha by pulseTransition.animateFloat(
    initialValue = 0.35f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
    label = "pulse_alpha",
  )
  val isPlaceholder = remember(payTag) { UsernameValidator.isGeneratedPlaceholder(payTag) }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp, vertical = 8.dp),
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
          .clip(RoundedCornerShape(13.dp))
          .border(
            width = 1.3.dp,
            brush = Brush.linearGradient(
              listOf(vaultColors.glassHighlight, vaultColors.gold, vaultColors.cyan)
            ),
            shape = RoundedCornerShape(13.dp),
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
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onBackground,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        BouncySurface(
          onClick = onCreateUsernameClick,
          color = if (isPlaceholder) vaultColors.goldContainer.copy(alpha = 0.9f) else vaultColors.glassSurface,
          shape = RoundedCornerShape(50),
          border = BorderStroke(
            0.9.dp,
            if (isPlaceholder) vaultColors.gold.copy(alpha = 0.65f) else vaultColors.glassHighlight,
          ),
          modifier = Modifier.testTag("top_bar_username_pill"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = if (isPlaceholder) Icons.Rounded.AlternateEmail else Icons.Rounded.Edit,
              contentDescription = "Create or edit username",
              tint = vaultColors.gold,
              modifier = Modifier.size(11.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
              text = when {
                payTag.isNullOrBlank() -> "Create Username"
                isPlaceholder -> "Set @username"
                else -> "@$payTag"
              },
              style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
              ),
              color = vaultColors.gold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }

    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      BouncySurface(
        onClick = onRefreshClick,
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("refresh_rates_pill"),
        color = if (isLive) vaultColors.positiveContainer.copy(alpha = 0.85f) else vaultColors.glassSurface,
        shape = RoundedCornerShape(50),
        border = BorderStroke(
          1.dp,
          if (isLive) vaultColors.positive.copy(alpha = 0.55f) else vaultColors.glassBorder,
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
          Spacer(Modifier.width(5.dp))
          Text(
            text = if (isRefreshing) "SYNC" else if (isLive) "LIVE" else "FX",
            style = MaterialTheme.typography.labelSmall,
            color = if (isLive) vaultColors.positive else MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.width(4.dp))
          Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = "Refresh live market rates",
            tint = if (isLive) vaultColors.positive else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(13.dp),
          )
        }
      }

      BouncySurface(
        onClick = onToggleTheme,
        shape = CircleShape,
        color = vaultColors.glassSurface,
        border = BorderStroke(1.dp, vaultColors.glassBorder),
        modifier = Modifier
          .size(38.dp)
          .minimumInteractiveComponentSize()
          .testTag("theme_toggle_button"),
      ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
          Icon(
            imageVector = if (isDarkTheme) Icons.Rounded.Brightness7 else Icons.Rounded.Brightness4,
            contentDescription = "Toggle light or dark theme",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
          )
        }
      }

      if (showLockButton) {
        BouncySurface(
          onClick = onLockClick,
          shape = CircleShape,
          color = vaultColors.glassSurface,
          border = BorderStroke(1.dp, vaultColors.positive.copy(alpha = 0.45f)),
          modifier = Modifier
            .size(38.dp)
            .minimumInteractiveComponentSize()
            .testTag("top_bar_lock_button"),
        ) {
          Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
              imageVector = Icons.Rounded.Lock,
              contentDescription = "Lock VaultFX now",
              tint = vaultColors.positive,
              modifier = Modifier.size(17.dp),
            )
          }
        }
      }

      BouncySurface(
        onClick = onProfileClick,
        modifier = Modifier
          .size(38.dp)
          .minimumInteractiveComponentSize()
          .testTag("profile_button"),
        shape = CircleShape,
        color = vaultColors.glassElevated,
        border = BorderStroke(
          1.2.dp,
          Brush.linearGradient(listOf(vaultColors.glassHighlight, vaultColors.gold.copy(alpha = 0.7f))),
        ),
      ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Rounded.Person,
            contentDescription = "Open account, username and security settings",
            tint = vaultColors.gold,
            modifier = Modifier.size(19.dp),
          )
        }
      }
    }
  }
}

@Composable
fun FloatingLiquidGlassNavBar(
  items: List<Triple<MainTab, ImageVector, ImageVector>>,
  currentTab: MainTab,
  badgeFor: (MainTab) -> Int,
  onSelectTab: (MainTab) -> Unit,
  modifier: Modifier = Modifier,
) {
  val vaultColors = LocalVaultColors.current
  Box(
    modifier = modifier
      .fillMaxWidth()
      .windowInsetsPadding(WindowInsets.navigationBars)
      .padding(horizontal = 10.dp, vertical = 6.dp),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      shape = RoundedCornerShape(32.dp),
      color = vaultColors.glassElevated,
      border = BorderStroke(
        width = 1.2.dp,
        brush = Brush.verticalGradient(
          colors = listOf(
            vaultColors.glassHighlight,
            vaultColors.glassBorder,
          )
        ),
      ),
      shadowElevation = if (vaultColors.isDark) 16.dp else 10.dp,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(
            Brush.verticalGradient(
              colors = listOf(
                Color.White.copy(alpha = if (vaultColors.isDark) 0.08f else 0.55f),
                Color.Transparent,
              )
            )
          )
          .padding(horizontal = 5.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        items.forEach { (tab, filledIcon, outlinedIcon) ->
          val selected = currentTab == tab
          val badgeCount = badgeFor(tab)
          val pillBg by animateColorAsState(
            targetValue = if (selected) {
              vaultColors.gold.copy(alpha = if (vaultColors.isDark) 0.22f else 0.20f)
            } else {
              Color.Transparent
            },
            label = "nav_pill_bg_${tab.name}",
          )
          val contentTint by animateColorAsState(
            targetValue = if (selected) vaultColors.gold else MaterialTheme.colorScheme.onSurfaceVariant,
            label = "nav_tint_${tab.name}",
          )

          Box(
            modifier = Modifier
              .weight(1f)
              .minimumInteractiveComponentSize()
              .clip(RoundedCornerShape(26.dp))
              .background(pillBg)
              .then(
                if (selected) {
                  Modifier.border(
                    width = 0.9.dp,
                    brush = Brush.verticalGradient(
                      colors = listOf(
                        vaultColors.glassHighlight,
                        vaultColors.gold.copy(alpha = 0.45f),
                      )
                    ),
                    shape = RoundedCornerShape(26.dp),
                  )
                } else {
                  Modifier
                }
              )
              .clickable { onSelectTab(tab) }
              .padding(vertical = 7.dp, horizontal = 2.dp)
              .testTag("nav_tab_${tab.name.lowercase()}"),
            contentAlignment = Alignment.Center,
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
            ) {
              Box(contentAlignment = Alignment.TopEnd) {
                Icon(
                  imageVector = if (selected) filledIcon else outlinedIcon,
                  contentDescription = tab.label,
                  tint = contentTint,
                  modifier = Modifier.size(20.dp),
                )
                if (badgeCount > 0) {
                  Surface(
                    color = vaultColors.negative,
                    shape = CircleShape,
                    modifier = Modifier
                      .offset(x = 6.dp, y = (-4).dp)
                      .size(15.dp),
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Text(
                        text = if (badgeCount > 9) "9+" else badgeCount.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(
                          fontSize = 8.sp,
                          fontWeight = FontWeight.Bold,
                        ),
                        color = Color.White,
                      )
                    }
                  }
                }
              }
              Spacer(Modifier.height(2.dp))
              Text(
                text = tab.label,
                style = MaterialTheme.typography.labelSmall.copy(
                  fontSize = 10.sp,
                  fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                ),
                color = contentTint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
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
          colors = listOf(accent.copy(alpha = 0.30f), accent.copy(alpha = 0.12f))
        )
      )
      .border(1.1.dp, accent.copy(alpha = 0.72f), CircleShape),
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
    color = vaultColors.glassElevated,
    shape = RoundedCornerShape(50),
    border = BorderStroke(
      1.dp,
      Brush.linearGradient(listOf(vaultColors.glassHighlight, vaultColors.glassBorder)),
    ),
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
        colors = listOf(strokeColor.copy(alpha = 0.28f), Color.Transparent),
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
    border = BorderStroke(0.75.dp, fg.copy(alpha = 0.35f)),
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
        shape = RoundedCornerShape(16.dp),
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
              .clip(RoundedCornerShape(18.dp))
              .clickable {
                onSelect(asset)
                onDismiss()
              }
              .testTag("pick_asset_${asset.code}"),
            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(18.dp),
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
      .clip(RoundedCornerShape(18.dp))
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
            color = Color(0xFF0B0E11),
            topLeft = Offset(offsetX + x * cellSize, offsetY + y * cellSize),
            size = androidx.compose.ui.geometry.Size(cellSize + 0.5f, cellSize + 0.5f),
          )
        }
      }
    }
  }
}
