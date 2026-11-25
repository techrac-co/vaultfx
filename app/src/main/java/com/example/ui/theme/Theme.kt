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
  val isDark: Boolean,
)

val LocalVaultColors = staticCompositionLocalOf {
  VaultExtendedColors(
    positive = ElectricMint,
    positiveContainer = MintContainerDark,
    negative = CoralRose,
    negativeContainer = CoralContainerDark,
    gold = ChampagneGold,
    goldContainer = GoldContainerDark,
    cyan = SkyCyan,
    violet = NeonViolet,
    cardBorder = ObsidianBorder,
    elevatedSurface = ObsidianSurfaceHigh,
    isDark = true,
  )
}

private val VaultDarkColorScheme = darkColorScheme(
  primary = ElectricMint,
  onPrimary = OnElectricMint,
  primaryContainer = MintContainerDark,
  onPrimaryContainer = ElectricMint,
  secondary = ChampagneGold,
  onSecondary = OnChampagneGold,
  secondaryContainer = GoldContainerDark,
  onSecondaryContainer = ChampagneGold,
  tertiary = SkyCyan,
  onTertiary = ObsidianBg,
  background = ObsidianBg,
  onBackground = TextPrimaryDark,
  surface = ObsidianSurface,
  onSurface = TextPrimaryDark,
  surfaceVariant = ObsidianSurfaceElevated,
  onSurfaceVariant = TextSecondaryDark,
  surfaceContainerLow = ObsidianSurface,
  surfaceContainer = ObsidianSurfaceElevated,
  surfaceContainerHigh = ObsidianSurfaceHigh,
  outline = ObsidianBorder,
  outlineVariant = ObsidianBorder.copy(alpha = 0.6f),
  error = CoralRose,
  onError = Color.White,
  errorContainer = CoralContainerDark,
  onErrorContainer = CoralRose,
)

private val VaultLightColorScheme = lightColorScheme(
  primary = EmeraldPrimaryLight,
  onPrimary = Color.White,
  primaryContainer = MintContainerLight,
  onPrimaryContainer = OnElectricMint,
  secondary = GoldAccentLight,
  onSecondary = Color.White,
  secondaryContainer = GoldContainerLight,
  onSecondaryContainer = OnChampagneGold,
  tertiary = Color(0xFF0284C7),
  onTertiary = Color.White,
  background = AlabasterBg,
  onBackground = TextPrimaryLight,
  surface = AlabasterSurface,
  onSurface = TextPrimaryLight,
  surfaceVariant = AlabasterSurfaceElevated,
  onSurfaceVariant = TextSecondaryLight,
  surfaceContainerLow = AlabasterSurface,
  surfaceContainer = AlabasterSurfaceElevated,
  surfaceContainerHigh = Color(0xFFE0E8F2),
  outline = AlabasterBorder,
  outlineVariant = AlabasterBorder.copy(alpha = 0.7f),
  error = CoralLight,
  onError = Color.White,
  errorContainer = CoralContainerLight,
  onErrorContainer = CoralLight,
)

private val VaultShapes = Shapes(
  small = RoundedCornerShape(12.dp),
  medium = RoundedCornerShape(18.dp),
  large = RoundedCornerShape(24.dp),
  extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun VaultFxTheme(
  darkTheme: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) VaultDarkColorScheme else VaultLightColorScheme
  val extendedColors = if (darkTheme) {
    VaultExtendedColors(
      positive = ElectricMint,
      positiveContainer = MintContainerDark,
      negative = CoralRose,
      negativeContainer = CoralContainerDark,
      gold = ChampagneGold,
      goldContainer = GoldContainerDark,
      cyan = SkyCyan,
      violet = NeonViolet,
      cardBorder = ObsidianBorder,
      elevatedSurface = ObsidianSurfaceHigh,
      isDark = true,
    )
  } else {
    VaultExtendedColors(
      positive = EmeraldPrimaryLight,
      positiveContainer = MintContainerLight,
      negative = CoralLight,
      negativeContainer = CoralContainerLight,
      gold = GoldAccentLight,
      goldContainer = GoldContainerLight,
      cyan = Color(0xFF0284C7),
      violet = VioletLight,
      cardBorder = AlabasterBorder,
      elevatedSurface = Color(0xFFE0E8F2),
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
