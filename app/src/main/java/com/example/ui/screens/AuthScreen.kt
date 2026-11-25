package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.AuthHelper
import com.example.ui.components.AnimatedAuroraBackground
import com.example.ui.components.GradientBorderSurface
import com.example.ui.theme.LocalVaultColors

@Composable
fun AuthScreen(
  onAuthSuccess: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var entered by remember { mutableStateOf(false) }
  val vaultColors = LocalVaultColors.current

  LaunchedEffect(Unit) { entered = true }

  AnimatedAuroraBackground {
    // Full-bleed atmospheric backdrop
    Image(
      painter = painterResource(id = R.drawable.img_vault_hero),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier
        .fillMaxWidth()
        .height(320.dp),
    )
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(340.dp)
        .background(
          Brush.verticalGradient(
            colors = listOf(
              Color.Transparent,
              MaterialTheme.colorScheme.background.copy(alpha = 0.75f),
              MaterialTheme.colorScheme.background,
            ),
          )
        ),
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .safeDrawingPadding()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      AnimatedVisibility(
        visible = entered,
        enter = fadeIn() + slideInVertically(
          initialOffsetY = { it / 10 },
          animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        ),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp),
        ) {
          Spacer(Modifier.height(16.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(18.dp))
                .border(
                  width = 1.6.dp,
                  brush = Brush.linearGradient(
                    listOf(vaultColors.positive, vaultColors.cyan, vaultColors.violet)
                  ),
                  shape = RoundedCornerShape(18.dp),
                ),
            ) {
              Image(
                painter = painterResource(id = R.drawable.ic_vault_logo),
                contentDescription = "VaultFX app icon",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
              )
            }
            Spacer(Modifier.width(14.dp))
            Column {
              Text(
                text = "VaultFX",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground,
              )
              Text(
                text = "CRYPTO • FX • SMART ESCROW",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }

          Spacer(Modifier.height(24.dp))
          Text(
            text = "Calculate. Trade.\nEscrow & Pay.",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
          )
          Spacer(Modifier.height(10.dp))
          Text(
            text = "Run live arithmetic across BTC, ETH, USD, NGN, GBP and 20+ global pairs while trading and settling peer-to-peer escrow deals.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          Spacer(Modifier.height(24.dp))

          FeatureHighlightCard(
            icon = Icons.Rounded.Calculate,
            accent = MaterialTheme.colorScheme.primary,
            title = "Dual Currency Calculator",
            subtitle = "Evaluate math expressions and watch live conversions across crypto and fiat simultaneously.",
          )
          Spacer(Modifier.height(10.dp))
          FeatureHighlightCard(
            icon = Icons.Rounded.CurrencyExchange,
            accent = vaultColors.gold,
            title = "Live Global Currency Pairs",
            subtitle = "Check real-time USD/NGN, USD/GBP (GDP), USD/USD, BTC/NGN and custom pinned pairs.",
          )
          Spacer(Modifier.height(10.dp))
          FeatureHighlightCard(
            icon = Icons.Rounded.Shield,
            accent = vaultColors.violet,
            title = "Protected Smart Escrow",
            subtitle = "Lock funds with any @payTag peer and release only when delivery is confirmed.",
          )
          Spacer(Modifier.height(10.dp))
          FeatureHighlightCard(
            icon = Icons.Rounded.SwapHoriz,
            accent = vaultColors.cyan,
            title = "Trade, Send & Receive Crypto",
            subtitle = "Instant buy/sell execution, QR wallet addresses, and @payTag transfers.",
          )
        }
      }

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 520.dp)
          .padding(top = 24.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        if (!errorMessage.isNullOrBlank()) {
          Surface(
            color = vaultColors.negativeContainer,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, vaultColors.negative.copy(alpha = 0.45f)),
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 12.dp),
          ) {
            Text(
              text = errorMessage ?: "",
              style = MaterialTheme.typography.bodyMedium,
              color = vaultColors.negative,
              modifier = Modifier.padding(12.dp),
            )
          }
        }

        Button(
          onClick = {
            errorMessage = null
            isLoading = true
            AuthHelper.onGoogleSignInClicked(
              context = context,
              credentialManager = credentialManager,
              onAuthSuccess = {
                isLoading = false
                onAuthSuccess()
              },
              onAuthError = { msg ->
                isLoading = false
                errorMessage = msg
              },
              scope = scope,
              onAuthCancelled = {
                isLoading = false
              },
            )
          },
          enabled = !isLoading,
          shape = RoundedCornerShape(18.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
          modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .testTag("google_sign_in_button"),
        ) {
          if (isLoading) {
            CircularProgressIndicator(
              modifier = Modifier.size(22.dp),
              color = MaterialTheme.colorScheme.onPrimary,
              strokeWidth = 2.5.dp,
            )
            Spacer(Modifier.width(12.dp))
            Text("Connecting Vault...", style = MaterialTheme.typography.labelLarge)
          } else {
            Icon(
              imageVector = Icons.Rounded.VerifiedUser,
              contentDescription = null,
              modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
              text = "Sign in with Google",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
          }
        }

        Spacer(Modifier.height(10.dp))
        Text(
          text = "Your portfolio, pinned pairs, escrows, and wallet ledger sync securely to your Google account.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun FeatureHighlightCard(
  icon: ImageVector,
  accent: Color,
  title: String,
  subtitle: String,
) {
  val vaultColors = LocalVaultColors.current
  GradientBorderSurface(
    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
    shape = RoundedCornerShape(18.dp),
    borderColors = listOf(accent.copy(alpha = 0.55f), vaultColors.cardBorder),
    borderWidth = 1.dp,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        color = accent.copy(alpha = 0.16f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.size(42.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(22.dp),
          )
        }
      }
      Spacer(Modifier.width(14.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(2.dp))
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
