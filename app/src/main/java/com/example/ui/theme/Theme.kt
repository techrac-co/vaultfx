package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class VaultExtendedColors(
  val positive: Color,
  val positiveContainer: Color,
  val negative: Color,
  val negativeContainer: Color,
  val gold: Color,
  val goldContainer: Color,
  val cyan: Color,
  val violet: Color,
  val cardBorder: Color,
  val elevatedSurface: Color,
  val glassSurface: Color,
  val glassElevated: Color,
  val glassHighlight: Color,
  val glassBorder: Color,
  val isDark: Boolean,
)

val LocalVaultColors = staticCompositionLocalOf {
  VaultExtendedColors(
    positive = BybitEmeraldGreen,
    positiveContainer = BybitEmeraldContainerDark,
    negative = BybitRubyRed,
    negativeContainer = BybitRubyContainerDark,
    gold = BybitAmberGold,
    goldContainer = BybitAmberContainerDark,
    cyan = LiquidIceCyan,
    violet = LiquidPrismViolet,
    cardBorder = BybitHairlineBorder,
    elevatedSurface = BybitGlassSurfaceHigh,
    glassSurface = Color(0xCC151A22),
    glassElevated = Color(0xE61E2531),
    glassHighlight = Color.White.copy(alpha = 0.28f),
    glassBorder = Color.White.copy(alpha = 0.11f),
    isDark = true,
  )
}

private val VaultDarkColorScheme = darkColorScheme(
  primary = BybitAmberGold,
  onPrimary = OnBybitAmber,
  primaryContainer = BybitAmberContainerDark,
  onPrimaryContainer = BybitAmberGold,
  secondary = BybitEmeraldGreen,
  onSecondary = OnBybitEmerald,
  secondaryContainer = BybitEmeraldContainerDark,
  onSecondaryContainer = BybitEmeraldGreen,
  tertiary = LiquidIceCyan,
  onTertiary = BybitObsidianBg,
  background = BybitObsidianBg,
  onBackground = TextPrimaryDark,
  surface = BybitGlassSurface,
  onSurface = TextPrimaryDark,
  surfaceVariant = BybitGlassSurfaceElevated,
  onSurfaceVariant = TextSecondaryDark,
  surfaceContainerLow = BybitGlassSurface,
  surfaceContainer = BybitGlassSurfaceElevated,
  surfaceContainerHigh = BybitGlassSurfaceHigh,
  outline = BybitHairlineBorder,
  outlineVariant = BybitHairlineBorder.copy(alpha = 0.65f),
  error = BybitRubyRed,
  onError = Color.White,
  errorContainer = BybitRubyContainerDark,
  onErrorContainer = BybitRubyRed,
)

private val VaultLightColorScheme = lightColorScheme(
  primary = BybitAmberLight,
  onPrimary = Color.White,
  primaryContainer = BybitAmberContainerLight,
  onPrimaryContainer = OnBybitAmber,
  secondary = BybitEmeraldLight,
  onSecondary = Color.White,
  secondaryContainer = BybitEmeraldContainerLight,
  onSecondaryContainer = OnBybitEmerald,
  tertiary = LiquidCyanLight,
  onTertiary = Color.White,
  background = AppleFrostBg,
  onBackground = TextPrimaryLight,
  surface = AppleFrostSurface,
  onSurface = TextPrimaryLight,
  surfaceVariant = AppleFrostSurfaceElevated,
  onSurfaceVariant = TextSecondaryLight,
  surfaceContainerLow = AppleFrostSurface,
  surfaceContainer = AppleFrostSurfaceElevated,
  surfaceContainerHigh = Color(0xFFE0E7F1),
  outline = AppleFrostBorder,
  outlineVariant = AppleFrostBorder.copy(alpha = 0.75f),
  error = BybitRubyLight,
  onError = Color.White,
  errorContainer = BybitRubyContainerLight,
  onErrorContainer = BybitRubyLight,
)

private val VaultShapes = Shapes(
  small = RoundedCornerShape(14.dp),
  medium = RoundedCornerShape(20.dp),
  large = RoundedCornerShape(26.dp),
  extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun VaultFxTheme(
  darkTheme: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) VaultDarkColorScheme else VaultLightColorScheme
  val extendedColors = if (darkTheme) {
    VaultExtendedColors(
      positive = BybitEmeraldGreen,
      positiveContainer = BybitEmeraldContainerDark,
      negative = BybitRubyRed,
      negativeContainer = BybitRubyContainerDark,
      gold = BybitAmberGold,
      goldContainer = BybitAmberContainerDark,
      cyan = LiquidIceCyan,
      violet = LiquidPrismViolet,
      cardBorder = BybitHairlineBorder,
      elevatedSurface = BybitGlassSurfaceHigh,
      glassSurface = Color(0xCC151A22),
      glassElevated = Color(0xE61E2531),
      glassHighlight = Color.White.copy(alpha = 0.28f),
      glassBorder = Color.White.copy(alpha = 0.11f),
      isDark = true,
    )
  } else {
    VaultExtendedColors(
      positive = BybitEmeraldLight,
      positiveContainer = BybitEmeraldContainerLight,
      negative = BybitRubyLight,
      negativeContainer = BybitRubyContainerLight,
      gold = BybitAmberLight,
      goldContainer = BybitAmberContainerLight,
      cyan = LiquidCyanLight,
      violet = LiquidVioletLight,
      cardBorder = AppleFrostBorder,
      elevatedSurface = Color(0xFFE0E7F1),
      glassSurface = Color(0xDDFDFEFF),
      glassElevated = Color(0xEEFFFFFF),
      glassHighlight = Color.White.copy(alpha = 0.94f),
      glassBorder = Color(0xFFCBD5E1).copy(alpha = 0.72f),
      isDark = false,
    )
  }

  CompositionLocalProvider(LocalVaultColors provides extendedColors) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      shapes = VaultShapes,
      content = content,
    )
  }
}
