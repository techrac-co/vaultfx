package com.example

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CandlestickChart
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CandlestickChart
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.AuthHelper
import com.example.data.DecryptedVaultTransaction
import com.example.data.MarketRatesRepository
import com.example.data.NetworkMonitor
import com.example.data.OfflineVaultRepository
import com.example.data.VaultRepository
import com.example.data.local.OfflineSyncStatus
import com.example.data.local.OfflineVaultDatabase
import com.example.security.AndroidKeystoreKeyringProvider
import com.example.security.BiometricAuthResult
import com.example.security.BiometricHardwareStatus
import com.example.security.BiometricPromptRequest
import com.example.security.VaultBiometricManager
import com.example.security.VaultCryptoEngine
import com.example.security.VaultSecurityStore
import com.example.ui.MainTab
import com.example.ui.UiState
import com.example.ui.VaultViewModel
import com.example.ui.components.AnimatedAuroraBackground
import com.example.ui.components.FloatingLiquidGlassNavBar
import com.example.ui.components.VaultTopBar
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BiometricRecommendTxDialog
import com.example.ui.screens.CalcHistorySheet
import com.example.ui.screens.CalculatorScreen
import com.example.ui.screens.CreateEscrowSheet
import com.example.ui.screens.CreateUsernameSheet
import com.example.ui.screens.DepositFiatSheet
import com.example.ui.screens.E2eeVoucherSheet
import com.example.ui.screens.EscrowScreen
import com.example.ui.screens.OfflineScreen
import com.example.ui.screens.OfflineSendSheet
import com.example.ui.screens.PairsScreen
import com.example.ui.screens.PinSetupDialog
import com.example.ui.screens.PinVerifyDialog
import com.example.ui.screens.ProfileSheet
import com.example.ui.screens.ReceiveCryptoSheet
import com.example.ui.screens.SendCryptoSheet
import com.example.ui.screens.TradeScreen
import com.example.ui.screens.VaultLockScreen
import com.example.ui.screens.WalletScreen
import com.example.ui.screens.needsUserAction
import com.example.ui.theme.LocalVaultColors
import com.example.ui.theme.VaultFxTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      AppContent()
    }
  }
}

private data class PendingAuthChallenge(
  val title: String,
  val subtitle: String,
  val description: String,
  val requireConfirmation: Boolean,
  val onAuthorized: () -> Unit,
)

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
  is FragmentActivity -> this
  is ContextWrapper -> baseContext.findFragmentActivity()
  else -> null
}

@Composable
fun AppContent() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

  DisposableEffect(Unit) {
    val listener = FirebaseAuth.AuthStateListener { auth ->
      currentUser = auth.currentUser
    }
    Firebase.auth.addAuthStateListener(listener)
    onDispose { Firebase.auth.removeAuthStateListener(listener) }
  }

  LaunchedEffect(Unit) {
    if (Firebase.auth.currentUser == null) {
      AuthHelper.attemptAutoSignIn(
        context = context,
        credentialManager = credentialManager,
        onAuthSuccess = { currentUser = Firebase.auth.currentUser },
        onUnauthenticated = {},
        scope = scope,
      )
    }
  }

  val user = currentUser
  if (user == null) {
    VaultFxTheme(darkTheme = true) {
      AuthScreen(onAuthSuccess = { currentUser = Firebase.auth.currentUser })
    }
  } else {
    val uid = user.uid
    key(uid) {
      VaultAppRoot(
        currentUserId = uid,
        userDisplayName = user.displayName?.takeIf { it.isNotBlank() } ?: "VaultFX Trader",
        userEmail = user.email,
        onSignOut = {
          AuthHelper.signOut(credentialManager, scope) {
            currentUser = null
          }
        },
      )
    }
  }
}

@Composable
fun VaultAppRoot(
  currentUserId: String,
  userDisplayName: String,
  userEmail: String?,
  onSignOut: () -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val viewModel: VaultViewModel = viewModel(
    key = currentUserId,
    factory = viewModelFactory {
      initializer {
        val app = checkNotNull(this[APPLICATION_KEY])
        val databaseId = app.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        val vaultRepo = VaultRepository(db)
        val cryptoEngine = VaultCryptoEngine(AndroidKeystoreKeyringProvider(app))
        val localDao = OfflineVaultDatabase.getInstance(app).encryptedTransactionDao()
        val offlineRepo = OfflineVaultRepository(
          dao = localDao,
          cryptoEngine = cryptoEngine,
          vaultRepository = vaultRepo,
        )
        val networkMonitor = NetworkMonitor(app)
        VaultViewModel(
          repository = vaultRepo,
          marketRepository = MarketRatesRepository(),
          currentUserId = currentUserId,
          initialDisplayName = userDisplayName,
          offlineRepository = offlineRepo,
          networkMonitor = networkMonitor,
        )
      }
    },
  )

  val securityStore = remember(context) {
    VaultSecurityStore(context.applicationContext, AndroidKeystoreKeyringProvider(context.applicationContext))
  }
  var securitySettings by remember(currentUserId) {
    mutableStateOf(securityStore.getSettings(currentUserId))
  }
  var hardwareStatus by remember {
    mutableStateOf(VaultBiometricManager.checkHardwareStatus(context))
  }
  var isVaultUnlocked by remember(currentUserId) {
    mutableStateOf(!securitySettings.appLockEnabled)
  }
  var lockBannerError by remember(currentUserId) { mutableStateOf<String?>(null) }
  var isBiometricPromptActive by remember { mutableStateOf(false) }
  var suppressNextBackgroundLock by remember { mutableStateOf(false) }

  var pinVerifyChallenge by remember { mutableStateOf<PendingAuthChallenge?>(null) }
  var showPinSetupDialog by remember { mutableStateOf(false) }
  var recommendTxChallenge by remember { mutableStateOf<PendingAuthChallenge?>(null) }
  var legacyCredentialChallenge by remember { mutableStateOf<PendingAuthChallenge?>(null) }

  val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  val canEnforceAuth = hardwareStatus == BiometricHardwareStatus.BIOMETRIC_READY ||
    hardwareStatus == BiometricHardwareStatus.DEVICE_CREDENTIAL_ONLY ||
    securitySettings.hasVaultPin

  val legacyCredentialLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult()
  ) { result ->
    val pending = legacyCredentialChallenge
    legacyCredentialChallenge = null
    if (result.resultCode == Activity.RESULT_OK && pending != null) {
      lockBannerError = null
      pending.onAuthorized()
    } else if (pending != null) {
      if (!isVaultUnlocked) {
        lockBannerError = "Device verification cancelled."
      } else {
        scope.launch { snackbarHostState.showSnackbar("Transaction cancelled — verification required") }
      }
    }
  }

  val enrollBiometricLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult()
  ) {
    hardwareStatus = VaultBiometricManager.checkHardwareStatus(context)
    securitySettings = securityStore.getSettings(currentUserId)
  }

  fun launchBiometricEnrollment() {
    suppressNextBackgroundLock = true
    runCatching {
      enrollBiometricLauncher.launch(VaultBiometricManager.createBiometricEnrollIntent())
    }.onFailure {
      suppressNextBackgroundLock = false
      scope.launch { snackbarHostState.showSnackbar("Open Android Settings → Security to enroll biometrics") }
    }
  }

  fun runAuthChallenge(challenge: PendingAuthChallenge, isAppUnlock: Boolean) {
    val latestStatus = VaultBiometricManager.checkHardwareStatus(context)
    val latestSettings = securityStore.getSettings(currentUserId)
    hardwareStatus = latestStatus
    securitySettings = latestSettings

    val canUseOs = latestStatus == BiometricHardwareStatus.BIOMETRIC_READY ||
      latestStatus == BiometricHardwareStatus.DEVICE_CREDENTIAL_ONLY
    val activity = context.findFragmentActivity()

    when {
      canUseOs && activity != null -> {
        isBiometricPromptActive = true
        VaultBiometricManager.authenticate(
          activity = activity,
          request = BiometricPromptRequest(
            title = challenge.title,
            subtitle = challenge.subtitle,
            description = challenge.description,
            allowDeviceCredential = true,
            requireConfirmation = challenge.requireConfirmation,
          ),
          hasVaultPin = latestSettings.hasVaultPin,
        ) { result ->
          isBiometricPromptActive = false
          when (result) {
            is BiometricAuthResult.Success -> {
              lockBannerError = null
              challenge.onAuthorized()
            }
            BiometricAuthResult.FallbackToPin -> {
              pinVerifyChallenge = challenge
            }
            is BiometricAuthResult.NeedsLegacyDeviceCredential -> {
              val intent = VaultBiometricManager.createLegacyDeviceCredentialIntent(
                context = context,
                title = result.title,
                description = result.subtitle,
              )
              if (intent != null) {
                suppressNextBackgroundLock = true
                legacyCredentialChallenge = challenge
                legacyCredentialLauncher.launch(intent)
              } else if (latestSettings.hasVaultPin) {
                pinVerifyChallenge = challenge
              } else if (isAppUnlock) {
                lockBannerError = "Device lock unavailable."
              } else {
                scope.launch { snackbarHostState.showSnackbar("Device lock verification unavailable") }
              }
            }
            BiometricAuthResult.Cancelled -> {
              if (isAppUnlock) {
                lockBannerError = "Authentication cancelled. Tap below to unlock VaultFX."
              } else {
                scope.launch { snackbarHostState.showSnackbar("Cancelled — biometric confirmation required") }
              }
            }
            is BiometricAuthResult.Error -> {
              if (isAppUnlock) {
                lockBannerError = result.message
              } else {
                scope.launch { snackbarHostState.showSnackbar(result.message) }
              }
            }
          }
        }
      }
      latestSettings.hasVaultPin -> {
        pinVerifyChallenge = challenge
      }
      isAppUnlock -> {
        lockBannerError = "Enroll device biometrics or create a 6-digit Vault PIN, or continue below."
      }
      else -> {
        recommendTxChallenge = challenge
      }
    }
  }

  fun triggerAppEntryUnlock() {
    runAuthChallenge(
      challenge = PendingAuthChallenge(
        title = "Unlock VaultFX",
        subtitle = "Biometric verification for $userDisplayName",
        description = "Confirm with Fingerprint, Face Unlock, or device credential to access your vault.",
        requireConfirmation = false,
        onAuthorized = { isVaultUnlocked = true },
      ),
      isAppUnlock = true,
    )
  }

  fun authorizeTransaction(
    title: String,
    subtitle: String,
    onAuthorized: () -> Unit,
  ) {
    val latestSettings = securityStore.getSettings(currentUserId)
    securitySettings = latestSettings
    if (!latestSettings.txConfirmationEnabled) {
      onAuthorized()
      return
    }
    runAuthChallenge(
      challenge = PendingAuthChallenge(
        title = title,
        subtitle = subtitle,
        description = "Verify your identity to sign & execute this VaultFX action.",
        requireConfirmation = true,
        onAuthorized = onAuthorized,
      ),
      isAppUnlock = false,
    )
  }

  // Re-lock app entry whenever backgrounded while App Entry Lock & credentials are active.
  DisposableEffect(lifecycleOwner, currentUserId) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> {
          hardwareStatus = VaultBiometricManager.checkHardwareStatus(context)
          securitySettings = securityStore.getSettings(currentUserId)
          suppressNextBackgroundLock = false
        }
        Lifecycle.Event.ON_STOP -> {
          val latestStatus = VaultBiometricManager.checkHardwareStatus(context)
          val latestSettings = securityStore.getSettings(currentUserId)
          val hasProtection = latestStatus == BiometricHardwareStatus.BIOMETRIC_READY ||
            latestStatus == BiometricHardwareStatus.DEVICE_CREDENTIAL_ONLY ||
            latestSettings.hasVaultPin
          if (latestSettings.appLockEnabled && hasProtection && !isBiometricPromptActive && !suppressNextBackgroundLock) {
            isVaultUnlocked = false
            lockBannerError = null
          }
        }
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
  val calcState by viewModel.calcState.collectAsStateWithLifecycle()
  val tradeState by viewModel.tradeState.collectAsStateWithLifecycle()
  val marketSnapshot by viewModel.marketSnapshot.collectAsStateWithLifecycle()
  val isSendingCrypto by viewModel.isSendingCrypto.collectAsStateWithLifecycle()
  val isSubmittingEscrow by viewModel.isSubmittingEscrow.collectAsStateWithLifecycle()
  val isUpdatingUsername by viewModel.isUpdatingUsername.collectAsStateWithLifecycle()

  val isDeviceOnline by viewModel.isDeviceOnline.collectAsStateWithLifecycle()
  val manualOfflineHold by viewModel.manualOfflineHold.collectAsStateWithLifecycle()
  val isEffectivelyOffline by viewModel.isEffectivelyOffline.collectAsStateWithLifecycle()
  val isSyncingOffline by viewModel.isSyncingOffline.collectAsStateWithLifecycle()
  val isQueueingOffline by viewModel.isQueueingOffline.collectAsStateWithLifecycle()
  val offlineTransactions by viewModel.offlineTransactions.collectAsStateWithLifecycle()
  val pendingOfflineDebits by viewModel.pendingOfflineDebits.collectAsStateWithLifecycle()
  val integrityReport by viewModel.integrityReport.collectAsStateWithLifecycle()

  val profileState by viewModel.profileState.collectAsStateWithLifecycle()
  val holdingsState by viewModel.holdingsState.collectAsStateWithLifecycle()
  val transactionsState by viewModel.transactionsState.collectAsStateWithLifecycle()
  val savedPairsState by viewModel.savedPairsState.collectAsStateWithLifecycle()
  val incomingTransfersState by viewModel.incomingTransfersState.collectAsStateWithLifecycle()
  val escrowsState by viewModel.escrowsState.collectAsStateWithLifecycle()

  val profile = (profileState as? UiState.Success)?.data
  val holdings = (holdingsState as? UiState.Success)?.data.orEmpty()
  val transactions = (transactionsState as? UiState.Success)?.data.orEmpty()
  val savedPairs = (savedPairsState as? UiState.Success)?.data.orEmpty()
  val incomingTransfers = (incomingTransfersState as? UiState.Success)?.data.orEmpty()
  val escrows = (escrowsState as? UiState.Success)?.data.orEmpty()

  val actionableEscrowCount = remember(escrows, currentUserId) {
    escrows.count { needsUserAction(it, currentUserId) }
  }
  val pendingOfflineCount = remember(offlineTransactions) {
    offlineTransactions.count {
      it.syncStatus == OfflineSyncStatus.QUEUED_OFFLINE || it.syncStatus == OfflineSyncStatus.SYNCING
    }
  }

  var showSendSheet by remember { mutableStateOf(false) }
  var sendSheetAsset by remember { mutableStateOf("BTC") }
  var sendSheetAmount by remember { mutableStateOf<Double?>(null) }

  var showEscrowSheet by remember { mutableStateOf(false) }
  var escrowSheetAsset by remember { mutableStateOf("USDT") }
  var escrowSheetAmount by remember { mutableStateOf<Double?>(null) }

  var showOfflineSendSheet by remember { mutableStateOf(false) }
  var voucherToInspect by remember { mutableStateOf<DecryptedVaultTransaction?>(null) }

  var showReceiveSheet by remember { mutableStateOf(false) }
  var receiveSheetAsset by remember { mutableStateOf("BTC") }

  var showDepositSheet by remember { mutableStateOf(false) }
  var showCalcHistorySheet by remember { mutableStateOf(false) }
  var showProfileSheet by remember { mutableStateOf(false) }
  var showCreateUsernameSheet by remember { mutableStateOf(false) }

  LaunchedEffect(viewModel) {
    viewModel.events.collect { msg ->
      snackbarHostState.showSnackbar(msg)
    }
  }

  BackHandler(enabled = isVaultUnlocked && currentTab != MainTab.CALCULATOR) {
    viewModel.selectTab(MainTab.CALCULATOR)
  }

  val navItems = listOf(
    Triple(MainTab.CALCULATOR, Icons.Rounded.Calculate, Icons.Outlined.Calculate),
    Triple(MainTab.PAIRS, Icons.Rounded.CurrencyExchange, Icons.Outlined.CurrencyExchange),
    Triple(MainTab.TRADE, Icons.Rounded.CandlestickChart, Icons.Outlined.CandlestickChart),
    Triple(MainTab.ESCROW, Icons.Rounded.Shield, Icons.Outlined.Shield),
    Triple(MainTab.OFFLINE, Icons.Rounded.CloudOff, Icons.Outlined.CloudOff),
    Triple(MainTab.WALLET, Icons.Rounded.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
  )

  VaultFxTheme(darkTheme = isDarkTheme) {
    AnimatedContent(
      targetState = isVaultUnlocked,
      transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(220)) },
      label = "vault_entry_lock_gate",
    ) { unlocked ->
      if (!unlocked) {
        VaultLockScreen(
          userDisplayName = profile?.displayName ?: userDisplayName,
          userEmail = userEmail,
          hardwareStatus = hardwareStatus,
          hasVaultPin = securitySettings.hasVaultPin,
          statusBanner = lockBannerError,
          onTriggerBiometricUnlock = { triggerAppEntryUnlock() },
          onOpenPinPrompt = {
            pinVerifyChallenge = PendingAuthChallenge(
              title = "Enter Vault PIN",
              subtitle = "Unlock VaultFX session",
              description = "Verify your 6-digit backup PIN.",
              requireConfirmation = false,
              onAuthorized = {
                lockBannerError = null
                isVaultUnlocked = true
              },
            )
          },
          onOpenPinSetup = { showPinSetupDialog = true },
          onOpenBiometricSettings = { launchBiometricEnrollment() },
          onContinueWithoutCredentials = {
            if (!canEnforceAuth) {
              isVaultUnlocked = true
              lockBannerError = null
            }
          },
          onSignOut = onSignOut,
        )
      } else {
        AnimatedAuroraBackground {
          val vaultColors = LocalVaultColors.current
          BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavRail = maxWidth >= 600.dp

            Scaffold(
              modifier = Modifier.fillMaxSize(),
              containerColor = Color.Transparent,
              contentWindowInsets = WindowInsets.safeDrawing,
              snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
              bottomBar = {
                if (!useNavRail) {
                  FloatingLiquidGlassNavBar(
                    items = navItems,
                    currentTab = currentTab,
                    badgeFor = { tab ->
                      when (tab) {
                        MainTab.ESCROW -> actionableEscrowCount
                        MainTab.OFFLINE -> pendingOfflineCount
                        MainTab.WALLET -> incomingTransfers.size
                        else -> 0
                      }
                    },
                    onSelectTab = viewModel::selectTab,
                  )
                }
              },
            ) { innerPadding ->
              Row(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(innerPadding),
              ) {
                if (useNavRail) {
                  NavigationRail(
                    containerColor = vaultColors.glassElevated,
                    modifier = Modifier.fillMaxHeight(),
                  ) {
                    navItems.forEach { (tab, filledIcon, outlinedIcon) ->
                      val selected = currentTab == tab
                      val badgeCount = when (tab) {
                        MainTab.ESCROW -> actionableEscrowCount
                        MainTab.OFFLINE -> pendingOfflineCount
                        MainTab.WALLET -> incomingTransfers.size
                        else -> 0
                      }
                      NavigationRailItem(
                        selected = selected,
                        onClick = { viewModel.selectTab(tab) },
                        colors = NavigationRailItemDefaults.colors(
                          selectedIconColor = vaultColors.gold,
                          selectedTextColor = vaultColors.gold,
                          indicatorColor = vaultColors.goldContainer,
                        ),
                        icon = {
                          if (badgeCount > 0) {
                            BadgedBox(badge = { Badge { Text(badgeCount.toString()) } }) {
                              Icon(
                                imageVector = if (selected) filledIcon else outlinedIcon,
                                contentDescription = tab.label,
                              )
                            }
                          } else {
                            Icon(
                              imageVector = if (selected) filledIcon else outlinedIcon,
                              contentDescription = tab.label,
                            )
                          }
                        },
                        label = { Text(tab.label, maxLines = 1) },
                        modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}"),
                      )
                    }
                  }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                  VaultTopBar(
                    title = when (currentTab) {
                      MainTab.CALCULATOR -> "VaultFX"
                      MainTab.PAIRS -> "Markets"
                      MainTab.TRADE -> "Spot Trade"
                      MainTab.ESCROW -> "P2P Escrow"
                      MainTab.OFFLINE -> "Offline Vault"
                      MainTab.WALLET -> "Assets"
                    },
                    isLive = marketSnapshot.isLive && !isEffectivelyOffline,
                    isRefreshing = marketSnapshot.isRefreshing || isSyncingOffline,
                    isDarkTheme = isDarkTheme,
                    payTag = profile?.payTag,
                    showLockButton = securitySettings.appLockEnabled && canEnforceAuth,
                    onLockClick = {
                      isVaultUnlocked = false
                      lockBannerError = null
                    },
                    onRefreshClick = {
                      if (currentTab == MainTab.OFFLINE) {
                        authorizeTransaction(
                          title = "Authorize Offline Settlement",
                          subtitle = "Sync & deduct $pendingOfflineCount queued offline transfer(s)",
                        ) {
                          viewModel.syncOfflineQueue(manual = true)
                        }
                      } else {
                        viewModel.refreshMarketRates()
                      }
                    },
                    onToggleTheme = { viewModel.toggleTheme() },
                    onProfileClick = { showProfileSheet = true },
                    onCreateUsernameClick = { showCreateUsernameSheet = true },
                  )

                  Box(modifier = Modifier.weight(1f)) {
                    AnimatedContent(
                      targetState = currentTab,
                      transitionSpec = {
                        val forward = targetState.ordinal >= initialState.ordinal
                        val enterSlide = slideInHorizontally(tween(240)) { w -> if (forward) w / 6 else -w / 6 }
                        val exitSlide = slideOutHorizontally(tween(200)) { w -> if (forward) -w / 8 else w / 8 }
                        (enterSlide + fadeIn(tween(240))) togetherWith (exitSlide + fadeOut(tween(180)))
                      },
                      label = "main_tab_transition",
                    ) { tab ->
                      when (tab) {
                        MainTab.CALCULATOR -> CalculatorScreen(
                          calcState = calcState,
                          marketSnapshot = marketSnapshot,
                          onKey = viewModel::onCalcKey,
                          onSwapCurrencies = viewModel::swapCalcCurrencies,
                          onSelectCurrencies = viewModel::setCalcCurrencies,
                          onSendToTrade = viewModel::sendCalcAmountToTrade,
                          onOpenSendSheet = { assetCode, amt ->
                            sendSheetAsset = assetCode
                            sendSheetAmount = amt
                            showSendSheet = true
                          },
                          onOpenEscrowSheet = { assetCode, amt ->
                            escrowSheetAsset = assetCode
                            escrowSheetAmount = amt
                            showEscrowSheet = true
                          },
                          onOpenHistory = { showCalcHistorySheet = true },
                        )

                        MainTab.PAIRS -> PairsScreen(
                          marketSnapshot = marketSnapshot,
                          savedPairs = savedPairs,
                          onTogglePin = viewModel::togglePinPair,
                          onOpenInCalculator = viewModel::loadPairInCalculator,
                        )

                        MainTab.TRADE -> TradeScreen(
                          tradeState = tradeState,
                          marketSnapshot = marketSnapshot,
                          holdings = holdings,
                          transactions = transactions,
                          onSetSide = viewModel::setTradeSide,
                          onSelectCrypto = viewModel::setTradeCrypto,
                          onSelectFiat = viewModel::setTradeFiat,
                          onAmountChange = viewModel::setTradeAmountInput,
                          onTimeframeChange = viewModel::setTradeTimeframe,
                          onApplyPercent = { frac -> viewModel.applyTradePercent(frac, holdings) },
                          onSubmitTrade = {
                            val sideLabel = if (tradeState.isBuy) "Buy" else "Sell"
                            authorizeTransaction(
                              title = "Authorize $sideLabel Order",
                              subtitle = "$sideLabel ${tradeState.amountInput} ${tradeState.cryptoCode} (${tradeState.fiatCode})",
                            ) {
                              viewModel.submitTrade()
                            }
                          },
                          onOpenDepositFiat = { showDepositSheet = true },
                        )

                        MainTab.ESCROW -> EscrowScreen(
                          currentUserId = currentUserId,
                          profile = profile,
                          escrowsState = escrowsState,
                          marketSnapshot = marketSnapshot,
                          onOpenCreateEscrow = {
                            escrowSheetAsset = "USDT"
                            escrowSheetAmount = null
                            showEscrowSheet = true
                          },
                          onMarkDelivered = viewModel::markEscrowDelivered,
                          onReleaseEscrow = { deal ->
                            authorizeTransaction(
                              title = "Authorize Escrow Release",
                              subtitle = "Release ${deal.amount} ${deal.assetCode} to @${deal.sellerTag}",
                            ) {
                              viewModel.releaseEscrow(deal)
                            }
                          },
                          onRefundEscrow = { deal ->
                            authorizeTransaction(
                              title = "Authorize Escrow Refund",
                              subtitle = "Refund ${deal.amount} ${deal.assetCode} to @${deal.buyerTag}",
                            ) {
                              viewModel.refundEscrow(deal)
                            }
                          },
                          onDisputeEscrow = { deal, reason ->
                            authorizeTransaction(
                              title = "Authorize Escrow Dispute",
                              subtitle = "Flag \"${deal.title}\" as disputed",
                            ) {
                              viewModel.disputeEscrow(deal, reason)
                            }
                          },
                          onClaimEscrow = { deal ->
                            authorizeTransaction(
                              title = "Authorize Escrow Claim",
                              subtitle = "Claim ${deal.amount} ${deal.assetCode} into your wallet",
                            ) {
                              viewModel.claimEscrowFunds(deal)
                            }
                          },
                          onRetryInitialize = viewModel::retryInitialize,
                        )

                        MainTab.OFFLINE -> OfflineScreen(
                          profile = profile,
                          isDeviceOnline = isDeviceOnline,
                          manualOfflineHold = manualOfflineHold,
                          isEffectivelyOffline = isEffectivelyOffline,
                          isSyncingOffline = isSyncingOffline,
                          offlineTransactions = offlineTransactions,
                          pendingOfflineDebits = pendingOfflineDebits,
                          integrityReport = integrityReport,
                          marketSnapshot = marketSnapshot,
                          onSetOfflineHold = viewModel::setOfflineHold,
                          onOpenOfflineSend = { showOfflineSendSheet = true },
                          onSyncNow = {
                            authorizeTransaction(
                              title = "Authorize Offline Settlement",
                              subtitle = "Broadcast & deduct $pendingOfflineCount queued offline transfer(s)",
                            ) {
                              viewModel.syncOfflineQueue(manual = true)
                            }
                          },
                          onCancelQueued = viewModel::cancelOfflineTransaction,
                          onRetryFailed = { txId ->
                            authorizeTransaction(
                              title = "Authorize Retry Transfer",
                              subtitle = "Re-queue & settle offline transfer",
                            ) {
                              viewModel.retryFailedOfflineTransaction(txId)
                            }
                          },
                          onVerifyIntegrity = viewModel::verifyOfflineVaultIntegrity,
                          onInspectVoucher = { tx -> voucherToInspect = tx },
                        )

                        MainTab.WALLET -> WalletScreen(
                          currentUserId = currentUserId,
                          profileState = profileState,
                          holdingsState = holdingsState,
                          transactions = transactions,
                          incomingTransfers = incomingTransfers,
                          escrows = escrows,
                          marketSnapshot = marketSnapshot,
                          pendingOfflineDebits = pendingOfflineDebits,
                          isBiometricProtected = (securitySettings.appLockEnabled || securitySettings.txConfirmationEnabled) && canEnforceAuth,
                          onOpenSecuritySettings = { showProfileSheet = true },
                          onOpenCreateUsername = { showCreateUsernameSheet = true },
                          onOpenSend = { asset ->
                            sendSheetAsset = asset ?: "BTC"
                            sendSheetAmount = null
                            showSendSheet = true
                          },
                          onOpenReceive = { asset ->
                            receiveSheetAsset = asset ?: "BTC"
                            showReceiveSheet = true
                          },
                          onOpenDeposit = { showDepositSheet = true },
                          onOpenEscrowTab = { viewModel.selectTab(MainTab.ESCROW) },
                          onOpenOfflineTab = { viewModel.selectTab(MainTab.OFFLINE) },
                          onChangePreferredFiat = viewModel::updatePreferredFiat,
                          onClaimTransfer = { transfer ->
                            authorizeTransaction(
                              title = "Authorize Claim Transfer",
                              subtitle = "Claim ${transfer.amount} ${transfer.assetCode} from @${transfer.senderTag}",
                            ) {
                              viewModel.claimTransfer(transfer)
                            }
                          },
                          onRetryInitialize = viewModel::retryInitialize,
                          onCopyTagMessage = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                          },
                        )
                      }
                    }
                  }
                }
              }
            }

            if (showReceiveSheet) {
              ReceiveCryptoSheet(
                profile = profile,
                initialAssetCode = receiveSheetAsset,
                onReceiveSampleDeposit = viewModel::receiveSampleDeposit,
                onCopyFeedback = { msg ->
                  scope.launch { snackbarHostState.showSnackbar(msg) }
                },
                onDismiss = { showReceiveSheet = false },
              )
            }

            if (showSendSheet) {
              SendCryptoSheet(
                profile = profile,
                holdings = holdings,
                marketSnapshot = marketSnapshot,
                initialAssetCode = sendSheetAsset,
                initialAmount = sendSheetAmount,
                isSending = isSendingCrypto || isQueueingOffline,
                isEffectivelyOffline = isEffectivelyOffline,
                pendingOfflineDebits = pendingOfflineDebits,
                onLookupPayTag = viewModel::lookupPayTag,
                onSend = { assetCode, amt, recipient, note ->
                  val actionTitle = if (isEffectivelyOffline) "Authorize Offline Send" else "Authorize Send Transfer"
                  val actionSubtitle = if (isEffectivelyOffline) {
                    "Encrypt & queue $amt $assetCode to $recipient"
                  } else {
                    "Send $amt $assetCode to $recipient"
                  }
                  authorizeTransaction(
                    title = actionTitle,
                    subtitle = actionSubtitle,
                  ) {
                    if (isEffectivelyOffline) {
                      viewModel.queueOfflineSend(
                        senderTag = profile?.payTag ?: "vault_user",
                        assetCode = assetCode,
                        amount = amt,
                        recipientInput = recipient,
                        note = note,
                        holdings = holdings,
                        onSuccess = { showSendSheet = false },
                      )
                    } else {
                      viewModel.sendCrypto(
                        senderTag = profile?.payTag ?: "vault_user",
                        assetCode = assetCode,
                        amount = amt,
                        recipientInput = recipient,
                        note = note,
                        onSuccess = { showSendSheet = false },
                      )
                    }
                  }
                },
                onDismiss = { showSendSheet = false },
              )
            }

            if (showOfflineSendSheet) {
              OfflineSendSheet(
                profile = profile,
                holdings = holdings,
                pendingOfflineDebits = pendingOfflineDebits,
                marketSnapshot = marketSnapshot,
                isQueueing = isQueueingOffline,
                onQueueOfflineSend = { assetCode, amt, recipient, note ->
                  authorizeTransaction(
                    title = "Authorize Offline Send",
                    subtitle = "Seal & queue $amt $assetCode to $recipient",
                  ) {
                    viewModel.queueOfflineSend(
                      senderTag = profile?.payTag ?: "vault_user",
                      assetCode = assetCode,
                      amount = amt,
                      recipientInput = recipient,
                      note = note,
                      holdings = holdings,
                      onSuccess = { showOfflineSendSheet = false },
                    )
                  }
                },
                onDismiss = { showOfflineSendSheet = false },
              )
            }

            val inspectTarget = voucherToInspect
            if (inspectTarget != null) {
              E2eeVoucherSheet(
                tx = inspectTarget,
                onCopyFeedback = { msg ->
                  scope.launch { snackbarHostState.showSnackbar(msg) }
                },
                onDismiss = { voucherToInspect = null },
              )
            }

            if (showEscrowSheet) {
              CreateEscrowSheet(
                profile = profile,
                holdings = holdings,
                marketSnapshot = marketSnapshot,
                initialAssetCode = escrowSheetAsset,
                initialAmount = escrowSheetAmount,
                isSubmitting = isSubmittingEscrow,
                onLookupPayTag = viewModel::lookupPayTag,
                onCreateEscrow = { sellerTag, assetCode, amount, title, terms ->
                  authorizeTransaction(
                    title = "Authorize Smart Escrow",
                    subtitle = "Lock $amount $assetCode in escrow for @${sellerTag.trim().removePrefix("@")}",
                  ) {
                    viewModel.createEscrow(
                      buyerTag = profile?.payTag ?: "vault_user",
                      sellerTagInput = sellerTag,
                      assetCode = assetCode,
                      amount = amount,
                      title = title,
                      agreedTerms = terms,
                      onSuccess = {
                        showEscrowSheet = false
                        viewModel.selectTab(MainTab.ESCROW)
                      },
                    )
                  }
                },
                onDismiss = { showEscrowSheet = false },
              )
            }

            if (showDepositSheet) {
              DepositFiatSheet(
                onDeposit = { fiatCode, amount ->
                  authorizeTransaction(
                    title = "Authorize Wallet Deposit",
                    subtitle = "Deposit $amount $fiatCode to your VaultFX wallet",
                  ) {
                    viewModel.depositFiat(fiatCode, amount)
                  }
                },
                onDismiss = { showDepositSheet = false },
              )
            }

            if (showCalcHistorySheet) {
              CalcHistorySheet(
                history = calcState.history,
                onSelectEntry = viewModel::restoreHistoryEntry,
                onDismiss = { showCalcHistorySheet = false },
              )
            }

            if (showProfileSheet) {
              ProfileSheet(
                profile = profile,
                userEmail = userEmail,
                securitySettings = securitySettings,
                hardwareStatus = hardwareStatus,
                onOpenCreateUsername = {
                  showProfileSheet = false
                  showCreateUsernameSheet = true
                },
                onToggleAppLock = { enabled ->
                  if (enabled) {
                    securityStore.setAppLockEnabled(currentUserId, true)
                    securitySettings = securityStore.getSettings(currentUserId)
                    scope.launch { snackbarHostState.showSnackbar("App entry biometric lock enabled") }
                  } else {
                    authorizeTransaction(
                      title = "Verify Identity",
                      subtitle = "Authenticate to disable App Entry Lock",
                    ) {
                      securityStore.setAppLockEnabled(currentUserId, false)
                      securitySettings = securityStore.getSettings(currentUserId)
                      scope.launch { snackbarHostState.showSnackbar("App entry lock disabled") }
                    }
                  }
                },
                onToggleTxConfirmation = { enabled ->
                  if (enabled) {
                    securityStore.setTxConfirmationEnabled(currentUserId, true)
                    securitySettings = securityStore.getSettings(currentUserId)
                    scope.launch { snackbarHostState.showSnackbar("Biometric transaction confirmation enabled") }
                  } else {
                    authorizeTransaction(
                      title = "Verify Identity",
                      subtitle = "Authenticate to disable transaction confirmation",
                    ) {
                      securityStore.setTxConfirmationEnabled(currentUserId, false)
                      securitySettings = securityStore.getSettings(currentUserId)
                      scope.launch { snackbarHostState.showSnackbar("Transaction confirmation disabled") }
                    }
                  }
                },
                onOpenSetupPin = {
                  if (securitySettings.hasVaultPin) {
                    authorizeTransaction(
                      title = "Verify Identity",
                      subtitle = "Authenticate to update your 6-digit Vault PIN",
                    ) {
                      showPinSetupDialog = true
                    }
                  } else {
                    showPinSetupDialog = true
                  }
                },
                onRemoveVaultPin = {
                  authorizeTransaction(
                    title = "Verify Identity",
                    subtitle = "Authenticate to remove your 6-digit Vault PIN",
                  ) {
                    securityStore.clearVaultPin(currentUserId)
                    securitySettings = securityStore.getSettings(currentUserId)
                    scope.launch { snackbarHostState.showSnackbar("Vault backup PIN cleared") }
                  }
                },
                onOpenBiometricEnroll = { launchBiometricEnrollment() },
                onLockNow = {
                  isVaultUnlocked = false
                  lockBannerError = null
                },
                onSignOut = onSignOut,
                onDismiss = { showProfileSheet = false },
              )
            }

            if (showCreateUsernameSheet) {
              CreateUsernameSheet(
                profile = profile,
                isUpdating = isUpdatingUsername,
                onCheckAvailability = viewModel::checkUsernameAvailability,
                onSaveUsername = { newUsername, newDisplayName ->
                  authorizeTransaction(
                    title = "Authorize Username Update",
                    subtitle = "Claim @$newUsername as your VaultFX @handle",
                  ) {
                    viewModel.claimUsername(
                      rawUsername = newUsername,
                      newDisplayName = newDisplayName,
                      onSuccess = { showCreateUsernameSheet = false },
                    )
                  }
                },
                onDismiss = { showCreateUsernameSheet = false },
              )
            }
          }
        }
      }
    }

    val activePinChallenge = pinVerifyChallenge
    if (activePinChallenge != null) {
      PinVerifyDialog(
        title = activePinChallenge.title,
        subtitle = activePinChallenge.subtitle,
        onVerifyPin = { pin -> securityStore.verifyVaultPin(currentUserId, pin) },
        onVerified = {
          pinVerifyChallenge = null
          activePinChallenge.onAuthorized()
        },
        onDismiss = {
          pinVerifyChallenge = null
          if (!isVaultUnlocked) {
            lockBannerError = "PIN entry cancelled."
          }
        },
      )
    }

    if (showPinSetupDialog) {
      PinSetupDialog(
        onSavePin = { pin ->
          securityStore.saveVaultPin(currentUserId, pin)
          securitySettings = securityStore.getSettings(currentUserId)
          showPinSetupDialog = false
          lockBannerError = null
          scope.launch { snackbarHostState.showSnackbar("6-digit Vault PIN sealed in Hardware Keystore") }
        },
        onDismiss = { showPinSetupDialog = false },
      )
    }

    val activeRecommendChallenge = recommendTxChallenge
    if (activeRecommendChallenge != null) {
      BiometricRecommendTxDialog(
        title = activeRecommendChallenge.title,
        subtitle = activeRecommendChallenge.subtitle,
        onConfirmAnyway = {
          recommendTxChallenge = null
          activeRecommendChallenge.onAuthorized()
        },
        onSetVaultPin = {
          recommendTxChallenge = null
          showPinSetupDialog = true
        },
        onDismiss = { recommendTxChallenge = null },
      )
    }
  }
}
