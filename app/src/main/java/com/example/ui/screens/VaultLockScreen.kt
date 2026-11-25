package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.security.BiometricHardwareStatus
import com.example.security.PinCheckResult
import com.example.security.VaultBiometricManager
import com.example.ui.components.AnimatedAuroraBackground
import com.example.ui.components.BouncySurface
import com.example.ui.components.GradientBorderSurface
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalVaultColors
import kotlinx.coroutines.delay

@Composable
fun VaultLockScreen(
  userDisplayName: String,
  userEmail: String?,
  hardwareStatus: BiometricHardwareStatus,
  hasVaultPin: Boolean,
  statusBanner: String?,
  onTriggerBiometricUnlock: () -> Unit,
  onOpenPinPrompt: () -> Unit,
  onOpenPinSetup: () -> Unit,
  onOpenBiometricSettings: () -> Unit,
  onContinueWithoutCredentials: () -> Unit,
  onSignOut: () -> Unit,
) {
  val vaultColors = LocalVaultColors.current
  val canUseOsAuth = hardwareStatus == BiometricHardwareStatus.BIOMETRIC_READY ||
    hardwareStatus == BiometricHardwareStatus.DEVICE_CREDENTIAL_ONLY
  val hasAnyCredential = canUseOsAuth || hasVaultPin

  val pulse = rememberInfiniteTransition(label = "lock_pulse")
  val ringScale by pulse.animateFloat(
    initialValue = 0.94f,
    targetValue = 1.08f,
    animationSpec = infiniteRepeatable(
      animation = tween(1500, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "lock_ring_scale",
  )

  // Automatically prompt once on entry if OS biometric/credential is configured.
  LaunchedEffect(canUseOsAuth) {
    if (canUseOsAuth) {
      delay(220)
      onTriggerBiometricUnlock()
    }
  }

  AnimatedAuroraBackground {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(20.dp),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 460.dp)
          .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        GradientBorderSurface(
          shape = RoundedCornerShape(30.dp),
          borderColors = listOf(vaultColors.positive, vaultColors.cyan, vaultColors.violet),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier
              .background(
                Brush.verticalGradient(
                  colors = listOf(
                    vaultColors.cyan.copy(alpha = if (vaultColors.isDark) 0.12f else 0.06f),
                    vaultColors.violet.copy(alpha = if (vaultColors.isDark) 0.08f else 0.04f),
                    Color.Transparent,
                  )
                )
              )
              .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            // App emblem + account chip
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center,
            ) {
              Box(
                modifier = Modifier
                  .size(34.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .border(
                    1.2.dp,
                    Brush.linearGradient(listOf(vaultColors.positive, vaultColors.cyan)),
                    RoundedCornerShape(10.dp),
                  ),
              ) {
                Image(
                  painter = painterResource(id = R.drawable.ic_vault_logo),
                  contentDescription = "VaultFX logo",
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize(),
                )
              }
              Spacer(Modifier.width(10.dp))
              Text(
                text = "VAULTFX SECURITY SHIELD",
                style = MaterialTheme.typography.labelSmall,
                color = vaultColors.cyan,
              )
            }

            Spacer(Modifier.height(22.dp))

            // Animated Concentric Biometric Emblem
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier.size(124.dp),
            ) {
              Box(
                modifier = Modifier
                  .size(116.dp)
                  .scale(ringScale)
                  .clip(CircleShape)
                  .background(
                    Brush.radialGradient(
                      colors = listOf(
                        vaultColors.positive.copy(alpha = 0.24f),
                        vaultColors.cyan.copy(alpha = 0.08f),
                        Color.Transparent,
                      )
                    )
                  ),
              )
              Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape,
                border = BorderStroke(
                  1.5.dp,
                  Brush.linearGradient(listOf(vaultColors.positive, vaultColors.cyan)),
                ),
                modifier = Modifier.size(86.dp),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = when (hardwareStatus) {
                      BiometricHardwareStatus.BIOMETRIC_READY -> Icons.Rounded.Fingerprint
                      BiometricHardwareStatus.DEVICE_CREDENTIAL_ONLY -> Icons.Rounded.Lock
                      else -> if (hasVaultPin) Icons.Rounded.Key else Icons.Rounded.Fingerprint
                    },
                    contentDescription = "Biometric vault lock",
                    tint = vaultColors.positive,
                    modifier = Modifier.size(44.dp),
                  )
                }
              }
            }

            Spacer(Modifier.height(16.dp))
            Text(
              text = "VaultFX Locked",
              style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
              text = "Signed in as $userDisplayName" + (userEmail?.let { "\n$it" } ?: ""),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(14.dp))
            Surface(
              color = if (hasAnyCredential) vaultColors.positiveContainer else vaultColors.goldContainer,
              shape = RoundedCornerShape(50),
              border = BorderStroke(
                1.dp,
                (if (hasAnyCredential) vaultColors.positive else vaultColors.gold).copy(alpha = 0.5f),
              ),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = Icons.Rounded.Face,
                  contentDescription = null,
                  tint = if (hasAnyCredential) vaultColors.positive else vaultColors.gold,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                  text = VaultBiometricManager.statusDisplayLabel(hardwareStatus, hasVaultPin),
                  style = MaterialTheme.typography.labelSmall,
                  color = if (hasAnyCredential) vaultColors.positive else vaultColors.gold,
                )
              }
            }

            AnimatedVisibility(visible = !statusBanner.isNullOrBlank()) {
              Column {
                Spacer(Modifier.height(12.dp))
                Surface(
                  color = vaultColors.negativeContainer,
                  shape = RoundedCornerShape(12.dp),
                  border = BorderStroke(1.dp, vaultColors.negative.copy(alpha = 0.45f)),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Text(
                    text = statusBanner.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = vaultColors.negative,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                  )
                }
              }
            }

            Spacer(Modifier.height(22.dp))

            if (canUseOsAuth) {
              Button(
                onClick = onTriggerBiometricUnlock,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.primary,
                  contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(54.dp)
                  .testTag("biometric_unlock_button"),
              ) {
                Icon(Icons.Rounded.Fingerprint, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                  text = if (hardwareStatus == BiometricHardwareStatus.BIOMETRIC_READY) {
                    "Unlock with Fingerprint / Face"
                  } else {
                    "Unlock with Device Credential"
                  },
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
              }
            }

            if (hasVaultPin) {
              if (canUseOsAuth) Spacer(Modifier.height(10.dp))
              val pinAction: () -> Unit = onOpenPinPrompt
              if (canUseOsAuth) {
                OutlinedButton(
                  onClick = pinAction,
                  shape = RoundedCornerShape(16.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("vault_pin_unlock_button"),
                ) {
                  Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(8.dp))
                  Text("Use 6-Digit Vault PIN", style = MaterialTheme.typography.titleSmall)
                }
              } else {
                Button(
                  onClick = pinAction,
                  shape = RoundedCornerShape(16.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("vault_pin_unlock_button"),
                ) {
                  Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(20.dp))
                  Spacer(Modifier.width(8.dp))
                  Text(
                    text = "Enter 6-Digit Vault PIN",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                  )
                }
              }
            }

            if (!canUseOsAuth) {
              if (hasVaultPin) Spacer(Modifier.height(10.dp))
              OutlinedButton(
                onClick = onOpenBiometricSettings,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(48.dp)
                  .testTag("enroll_biometrics_button"),
              ) {
                Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Enroll Fingerprint / Face Unlock", style = MaterialTheme.typography.labelLarge)
              }

              if (!hasVaultPin) {
                Spacer(Modifier.height(10.dp))
                Button(
                  onClick = onOpenPinSetup,
                  shape = RoundedCornerShape(16.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("setup_pin_button"),
                ) {
                  Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(8.dp))
                  Text(
                    text = "Set 6-Digit Vault Backup PIN",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                  )
                }

                Spacer(Modifier.height(8.dp))
                TextButton(
                  onClick = onContinueWithoutCredentials,
                  modifier = Modifier.testTag("continue_without_biometrics_button"),
                ) {
                  Text(
                    text = "Continue without device lock (setup later)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }

            Spacer(Modifier.height(12.dp))
            TextButton(
              onClick = onSignOut,
              modifier = Modifier.testTag("lock_sign_out_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.Logout,
                contentDescription = null,
                tint = vaultColors.negative,
                modifier = Modifier.size(16.dp),
              )
              Spacer(Modifier.width(6.dp))
              Text(
                text = "Sign Out",
                style = MaterialTheme.typography.labelLarge,
                color = vaultColors.negative,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun PinVerifyDialog(
  title: String,
  subtitle: String,
  onVerifyPin: (String) -> PinCheckResult,
  onVerified: () -> Unit,
  onDismiss: () -> Unit,
) {
  val vaultColors = LocalVaultColors.current
  var enteredPin by remember { mutableStateOf("") }
  var errorText by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Key, contentDescription = null, tint = vaultColors.cyan)
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
      }
    },
    text = {
      Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        PinDotsIndicator(filledCount = enteredPin.length, total = 6, isError = errorText != null)
        if (errorText != null) {
          Spacer(Modifier.height(8.dp))
          Text(
            text = errorText.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = vaultColors.negative,
            textAlign = TextAlign.Center,
          )
        }
        Spacer(Modifier.height(16.dp))
        NumericPinKeypad(
          onDigit = { digit ->
            if (enteredPin.length < 6) {
              val next = enteredPin + digit
              enteredPin = next
              errorText = null
              if (next.length == 6) {
                when (val check = onVerifyPin(next)) {
                  is PinCheckResult.Verified -> onVerified()
                  is PinCheckResult.Invalid -> {
                    enteredPin = ""
                    errorText = "Incorrect Vault PIN (${check.remainingAttempts} attempt${if (check.remainingAttempts == 1) "" else "s"} left)"
                  }
                  is PinCheckResult.LockedOut -> {
                    enteredPin = ""
                    errorText = "Too many attempts. Wait ${check.remainingSeconds}s before retrying."
                  }
                  PinCheckResult.NotConfigured -> {
                    enteredPin = ""
                    errorText = "No Vault PIN configured."
                  }
                }
              }
            }
          },
          onBackspace = {
            if (enteredPin.isNotEmpty()) {
              enteredPin = enteredPin.dropLast(1)
              errorText = null
            }
          },
        )
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
fun PinSetupDialog(
  onSavePin: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val vaultColors = LocalVaultColors.current
  var firstPin by remember { mutableStateOf("") }
  var confirmPin by remember { mutableStateOf("") }
  var confirming by remember { mutableStateOf(false) }
  var errorText by remember { mutableStateOf<String?>(null) }

  val activePin = if (confirming) confirmPin else firstPin

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
    title = {
      Text(
        text = if (confirming) "Confirm 6-Digit Vault PIN" else "Create 6-Digit Vault PIN",
        style = MaterialTheme.typography.titleLarge,
      )
    },
    text = {
      Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
          text = if (confirming) {
            "Re-enter your 6-digit PIN to seal it inside Hardware Keystore."
          } else {
            "Choose a 6-digit backup PIN to unlock VaultFX and confirm transactions."
          },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        PinDotsIndicator(filledCount = activePin.length, total = 6, isError = errorText != null)
        if (errorText != null) {
          Spacer(Modifier.height(8.dp))
          Text(
            text = errorText.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = vaultColors.negative,
          )
        }
        Spacer(Modifier.height(16.dp))
        NumericPinKeypad(
          onDigit = { digit ->
            if (!confirming) {
              if (firstPin.length < 6) {
                firstPin += digit
                errorText = null
                if (firstPin.length == 6) confirming = true
              }
            } else {
              if (confirmPin.length < 6) {
                val next = confirmPin + digit
                confirmPin = next
                errorText = null
                if (next.length == 6) {
                  if (next == firstPin) {
                    onSavePin(next)
                  } else {
                    confirmPin = ""
                    firstPin = ""
                    confirming = false
                    errorText = "PINs did not match. Try again."
                  }
                }
              }
            }
          },
          onBackspace = {
            if (confirming) {
              if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
              else confirming = false
            } else if (firstPin.isNotEmpty()) {
              firstPin = firstPin.dropLast(1)
            }
            errorText = null
          },
        )
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
fun BiometricRecommendTxDialog(
  title: String,
  subtitle: String,
  onConfirmAnyway: () -> Unit,
  onSetVaultPin: () -> Unit,
  onDismiss: () -> Unit,
) {
  val vaultColors = LocalVaultColors.current
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = vaultColors.gold)
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = "No fingerprint, face unlock, or Vault PIN is enrolled on this device yet. Set a 6-digit Vault PIN for transaction sign-off, or confirm to execute now.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    confirmButton = {
      Button(
        onClick = onConfirmAnyway,
        modifier = Modifier.testTag("tx_dialog_confirm_anyway"),
      ) {
        Text("Confirm Transaction")
      }
    },
    dismissButton = {
      Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = onSetVaultPin) { Text("Set PIN") }
        TextButton(onClick = onDismiss) { Text("Cancel") }
      }
    },
  )
}

@Composable
private fun PinDotsIndicator(
  filledCount: Int,
  total: Int,
  isError: Boolean,
) {
  val vaultColors = LocalVaultColors.current
  Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    for (i in 0 until total) {
      val filled = i < filledCount
      Box(
        modifier = Modifier
          .size(16.dp)
          .clip(CircleShape)
          .background(
            when {
              isError -> vaultColors.negative.copy(alpha = 0.25f)
              filled -> vaultColors.positive
              else -> MaterialTheme.colorScheme.surfaceVariant
            }
          )
          .border(
            1.2.dp,
            when {
              isError -> vaultColors.negative
              filled -> vaultColors.positive
              else -> vaultColors.cardBorder
            },
            CircleShape,
          ),
      )
    }
  }
}

@Composable
private fun NumericPinKeypad(
  onDigit: (String) -> Unit,
  onBackspace: () -> Unit,
) {
  val rows = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf("", "0", "⌫"),
  )
  Column(
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    rows.forEach { row ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        row.forEach { key ->
          if (key.isEmpty()) {
            Spacer(Modifier.weight(1f))
          } else {
            BouncySurface(
              onClick = { if (key == "⌫") onBackspace() else onDigit(key) },
              color = MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .weight(1f)
                .height(50.dp)
                .testTag(if (key == "⌫") "pin_key_back" else "pin_key_$key"),
            ) {
              Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                if (key == "⌫") {
                  Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Backspace,
                    contentDescription = "Delete digit",
                    modifier = Modifier.size(20.dp),
                  )
                } else {
                  Text(
                    text = key,
                    style = MaterialTheme.typography.titleMedium.copy(
                      fontFamily = JetBrainsMonoFontFamily,
                      fontWeight = FontWeight.Bold,
                    ),
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
