package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.VaultRepository.UsernameAvailability
import com.example.model.UserProfile
import com.example.ui.components.BouncySurface
import com.example.ui.components.LiquidGlassCard
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalVaultColors
import com.example.util.UsernameValidationResult
import com.example.util.UsernameValidator
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateUsernameSheet(
  profile: UserProfile?,
  isUpdating: Boolean,
  onCheckAvailability: suspend (String) -> Result<UsernameAvailability>,
  onSaveUsername: (username: String, displayName: String?) -> Unit,
  onDismiss: () -> Unit,
) {
  val vaultColors = LocalVaultColors.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  val initialTag = profile?.payTag.orEmpty()
  val isCurrentlyPlaceholder = remember(initialTag) { UsernameValidator.isGeneratedPlaceholder(initialTag) }
  var usernameInput by remember(initialTag) {
    mutableStateOf(if (isCurrentlyPlaceholder) "" else initialTag)
  }
  var displayNameInput by remember(profile?.displayName) {
    mutableStateOf(profile?.displayName ?: "VaultFX Trader")
  }
  var isChecking by remember { mutableStateOf(false) }
  var availability by remember { mutableStateOf<UsernameAvailability?>(null) }

  val normalizedInput = remember(usernameInput) { UsernameValidator.normalize(usernameInput) }
  val validation = remember(usernameInput) { UsernameValidator.validate(usernameInput) }

  val suggestions = remember(displayNameInput, profile?.walletSeed) {
    val base = displayNameInput.lowercase().replace(Regex("[^a-z0-9]"), "").take(10).ifBlank { "trader" }
    val seedSuffix = (profile?.walletSeed ?: "vf99").replace(Regex("[^a-z0-9]"), "").takeLast(4).ifBlank { "2026" }
    listOf(
      base.takeIf { it.length >= 3 } ?: "${base}_fx",
      "${base}_fx",
      "${base}_$seedSuffix",
    ).distinct().filter { UsernameValidator.validate(it) is UsernameValidationResult.Valid }
  }

  LaunchedEffect(usernameInput) {
    if (normalizedInput.isBlank()) {
      isChecking = false
      availability = null
      return@LaunchedEffect
    }
    if (validation is UsernameValidationResult.Invalid) {
      isChecking = false
      availability = UsernameAvailability.Invalid(validation.message)
      return@LaunchedEffect
    }
    isChecking = true
    delay(320)
    val result = onCheckAvailability(normalizedInput)
    availability = result.getOrElse {
      UsernameAvailability.Invalid(it.localizedMessage ?: "Unable to verify availability")
    }
    isChecking = false
  }

  val canSubmit = !isUpdating && !isChecking && validation is UsernameValidationResult.Valid && when (availability) {
    is UsernameAvailability.Available -> true
    is UsernameAvailability.OwnedByCurrentUser -> displayNameInput.trim().isNotEmpty() &&
      displayNameInput.trim() != (profile?.displayName ?: "")
    else -> false
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
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(
            text = if (isCurrentlyPlaceholder) "Create Username" else "Edit Username",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
          )
          Text(
            text = "Claim your unique @handle for P2P, Escrow & Offline transfers",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(onClick = onDismiss) {
          Icon(Icons.Rounded.Close, contentDescription = "Close username setup")
        }
      }

      Spacer(Modifier.height(14.dp))

      // Live Liquid Glass Identity Card Preview
      LiquidGlassCard(
        accentTint = vaultColors.gold,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier = Modifier
              .size(48.dp)
              .clip(CircleShape)
              .background(
                Brush.linearGradient(
                  listOf(vaultColors.gold, vaultColors.cyan)
                )
              ),
            contentAlignment = Alignment.Center,
          ) {
            val initials = displayNameInput.trim()
              .split(" ")
              .filter { it.isNotBlank() }
              .take(2)
              .joinToString("") { it.first().uppercase() }
              .ifBlank { "VF" }
            Text(
              text = initials,
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = Color(0xFF0B0E11),
            )
          }
          Spacer(Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = displayNameInput.trim().ifBlank { "VaultFX Trader" },
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = if (normalizedInput.isNotBlank()) "@$normalizedInput" else "@your_username",
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                color = vaultColors.gold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              if (availability is UsernameAvailability.Available || availability is UsernameAvailability.OwnedByCurrentUser) {
                Spacer(Modifier.width(6.dp))
                Icon(
                  imageVector = Icons.Rounded.VerifiedUser,
                  contentDescription = null,
                  tint = vaultColors.positive,
                  modifier = Modifier.size(15.dp),
                )
              }
            }
          }
        }
      }

      Spacer(Modifier.height(14.dp))
      OutlinedTextField(
        value = displayNameInput,
        onValueChange = { if (it.length <= 60) displayNameInput = it },
        label = { Text("Display Name") },
        placeholder = { Text("Your trader name") },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("username_display_name_input"),
      )

      Spacer(Modifier.height(12.dp))
      OutlinedTextField(
        value = usernameInput,
        onValueChange = { raw ->
          val sanitized = raw.lowercase().replace(" ", "_").filter { it.isLetterOrDigit() || it == '_' }
          if (sanitized.length <= 32) usernameInput = sanitized
        },
        label = { Text("Username (@handle)") },
        placeholder = { Text("satoshi_fx") },
        leadingIcon = {
          Icon(
            imageVector = Icons.Rounded.AlternateEmail,
            contentDescription = null,
            tint = vaultColors.gold,
          )
        },
        trailingIcon = {
          when {
            isChecking -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            availability is UsernameAvailability.Available || availability is UsernameAvailability.OwnedByCurrentUser ->
              Icon(Icons.Rounded.CheckCircle, contentDescription = "Available", tint = vaultColors.positive)
            availability is UsernameAvailability.Taken || availability is UsernameAvailability.Invalid ->
              Icon(Icons.Rounded.ErrorOutline, contentDescription = "Unavailable", tint = vaultColors.negative)
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("create_username_input"),
      )

      Spacer(Modifier.height(6.dp))
      val statusText = when {
        normalizedInput.isBlank() -> "Choose 3–32 characters using a-z, 0-9, or underscores (_)."
        isChecking -> "Checking @$normalizedInput availability…"
        else -> when (val status = availability) {
          is UsernameAvailability.Available -> "@${status.username} is available to claim!"
          is UsernameAvailability.OwnedByCurrentUser -> "@${status.username} is your current active username."
          is UsernameAvailability.Taken -> "@${status.username} is already taken. Try another."
          is UsernameAvailability.Invalid -> status.reason
          null -> ""
        }
      }
      val statusColor = when {
        normalizedInput.isBlank() || isChecking -> MaterialTheme.colorScheme.onSurfaceVariant
        availability is UsernameAvailability.Available -> vaultColors.positive
        availability is UsernameAvailability.OwnedByCurrentUser -> vaultColors.cyan
        else -> vaultColors.negative
      }
      Text(
        text = statusText,
        style = MaterialTheme.typography.labelMedium,
        color = statusColor,
        modifier = Modifier.testTag("username_availability_status"),
      )

      if (suggestions.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text(
          text = "SUGGESTED USERNAMES",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          suggestions.forEach { suggestion ->
            BouncySurface(
              onClick = { usernameInput = suggestion },
              color = if (normalizedInput == suggestion) vaultColors.goldContainer else MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(50),
              border = BorderStroke(
                1.dp,
                if (normalizedInput == suggestion) vaultColors.gold else vaultColors.glassBorder,
              ),
              modifier = Modifier.testTag("username_suggestion_$suggestion"),
            ) {
              Text(
                text = "@$suggestion",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                color = if (normalizedInput == suggestion) vaultColors.gold else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
              )
            }
          }
        }
      }

      Spacer(Modifier.height(18.dp))
      Button(
        onClick = {
          onSaveUsername(
            normalizedInput,
            displayNameInput.trim().takeIf { it.isNotEmpty() },
          )
        },
        enabled = canSubmit,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = Modifier
          .fillMaxWidth()
          .height(54.dp)
          .testTag("claim_username_confirm_button"),
      ) {
        if (isUpdating) {
          CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            strokeWidth = 2.dp,
          )
        } else {
          Icon(Icons.Rounded.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(
            text = when {
              normalizedInput.isNotBlank() && availability is UsernameAvailability.OwnedByCurrentUser -> "Save Profile Changes"
              normalizedInput.isNotBlank() -> "Claim @$normalizedInput"
              else -> "Create Username"
            },
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          )
        }
      }
    }
  }
}
