package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CandlestickChart
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.AuthHelper
import com.example.data.MarketRatesRepository
import com.example.data.VaultRepository
import com.example.ui.MainTab
import com.example.ui.UiState
import com.example.ui.VaultViewModel
import com.example.ui.components.AnimatedAuroraBackground
import com.example.ui.components.VaultTopBar
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CalcHistorySheet
import com.example.ui.screens.CalculatorScreen
import com.example.ui.screens.CreateEscrowSheet
import com.example.ui.screens.DepositFiatSheet
import com.example.ui.screens.EscrowScreen
import com.example.ui.screens.PairsScreen
import com.example.ui.screens.ProfileSheet
import com.example.ui.screens.ReceiveCryptoSheet
import com.example.ui.screens.SendCryptoSheet
import com.example.ui.screens.TradeScreen
import com.example.ui.screens.WalletScreen
import com.example.ui.screens.needsUserAction
import com.example.ui.theme.VaultFxTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      AppContent()
    }
  }
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
  val viewModel: VaultViewModel = viewModel(
    key = currentUserId,
    factory = viewModelFactory {
      initializer {
        val app = checkNotNull(this[APPLICATION_KEY])
        val databaseId = app.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        VaultViewModel(
          repository = VaultRepository(db),
          marketRepository = MarketRatesRepository(),
          currentUserId = currentUserId,
          initialDisplayName = userDisplayName,
        )
      }
    },
  )

  val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
  val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
  val calcState by viewModel.calcState.collectAsStateWithLifecycle()
  val tradeState by viewModel.tradeState.collectAsStateWithLifecycle()
  val marketSnapshot by viewModel.marketSnapshot.collectAsStateWithLifecycle()
  val isSendingCrypto by viewModel.isSendingCrypto.collectAsStateWithLifecycle()
  val isSubmittingEscrow by viewModel.isSubmittingEscrow.collectAsStateWithLifecycle()

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

  var showSendSheet by remember { mutableStateOf(false) }
  var sendSheetAsset by remember { mutableStateOf("BTC") }
  var sendSheetAmount by remember { mutableStateOf<Double?>(null) }

  var showEscrowSheet by remember { mutableStateOf(false) }
  var escrowSheetAsset by remember { mutableStateOf("USDT") }
  var escrowSheetAmount by remember { mutableStateOf<Double?>(null) }

  var showReceiveSheet by remember { mutableStateOf(false) }
  var receiveSheetAsset by remember { mutableStateOf("BTC") }

  var showDepositSheet by remember { mutableStateOf(false) }
  var showCalcHistorySheet by remember { mutableStateOf(false) }
  var showProfileSheet by remember { mutableStateOf(false) }

  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  LaunchedEffect(viewModel) {
    viewModel.events.collect { msg ->
      snackbarHostState.showSnackbar(msg)
    }
  }

  BackHandler(enabled = currentTab != MainTab.CALCULATOR) {
    viewModel.selectTab(MainTab.CALCULATOR)
  }

  val navItems = listOf(
    Triple(MainTab.CALCULATOR, Icons.Rounded.Calculate, Icons.Outlined.Calculate),
    Triple(MainTab.PAIRS, Icons.Rounded.CurrencyExchange, Icons.Outlined.CurrencyExchange),
    Triple(MainTab.TRADE, Icons.Rounded.CandlestickChart, Icons.Outlined.CandlestickChart),
    Triple(MainTab.ESCROW, Icons.Rounded.Shield, Icons.Outlined.Shield),
    Triple(MainTab.WALLET, Icons.Rounded.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
  )

  VaultFxTheme(darkTheme = isDarkTheme) {
    AnimatedAuroraBackground {
      BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
          modifier = Modifier.fillMaxSize(),
          containerColor = Color.Transparent,
          contentWindowInsets = WindowInsets.safeDrawing,
          snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
          bottomBar = {
            if (!useNavRail) {
              NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 0.dp,
              ) {
                navItems.forEach { (tab, filledIcon, outlinedIcon) ->
                  val selected = currentTab == tab
                  val badgeCount = when (tab) {
                    MainTab.ESCROW -> actionableEscrowCount
                    MainTab.WALLET -> incomingTransfers.size
                    else -> 0
                  }
                  NavigationBarItem(
                    selected = selected,
                    onClick = { viewModel.selectTab(tab) },
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
                    label = {
                      Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    },
                    modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}"),
                  )
                }
              }
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
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                modifier = Modifier.fillMaxHeight(),
              ) {
                navItems.forEach { (tab, filledIcon, outlinedIcon) ->
                  val selected = currentTab == tab
                  val badgeCount = when (tab) {
                    MainTab.ESCROW -> actionableEscrowCount
                    MainTab.WALLET -> incomingTransfers.size
                    else -> 0
                  }
                  NavigationRailItem(
                    selected = selected,
                    onClick = { viewModel.selectTab(tab) },
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
                  MainTab.PAIRS -> "Currency Pairs"
                  MainTab.TRADE -> "Trade Desk"
                  MainTab.ESCROW -> "Smart Escrow"
                  MainTab.WALLET -> "Crypto Wallet"
                },
                isLive = marketSnapshot.isLive,
                isRefreshing = marketSnapshot.isRefreshing,
                isDarkTheme = isDarkTheme,
                payTag = profile?.payTag,
                onRefreshClick = { viewModel.refreshMarketRates() },
                onToggleTheme = { viewModel.toggleTheme() },
                onProfileClick = { showProfileSheet = true },
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
                      onSubmitTrade = viewModel::submitTrade,
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
                      onReleaseEscrow = viewModel::releaseEscrow,
                      onRefundEscrow = viewModel::refundEscrow,
                      onDisputeEscrow = viewModel::disputeEscrow,
                      onClaimEscrow = viewModel::claimEscrowFunds,
                      onRetryInitialize = viewModel::retryInitialize,
                    )

                    MainTab.WALLET -> WalletScreen(
                      currentUserId = currentUserId,
                      profileState = profileState,
                      holdingsState = holdingsState,
                      transactions = transactions,
                      incomingTransfers = incomingTransfers,
                      escrows = escrows,
                      marketSnapshot = marketSnapshot,
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
                      onChangePreferredFiat = viewModel::updatePreferredFiat,
                      onClaimTransfer = viewModel::claimTransfer,
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
            isSending = isSendingCrypto,
            onLookupPayTag = viewModel::lookupPayTag,
            onSend = { assetCode, amt, recipient, note ->
              viewModel.sendCrypto(
                senderTag = profile?.payTag ?: "vault_user",
                assetCode = assetCode,
                amount = amt,
                recipientInput = recipient,
                note = note,
                onSuccess = { showSendSheet = false },
              )
            },
            onDismiss = { showSendSheet = false },
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
            },
            onDismiss = { showEscrowSheet = false },
          )
        }

        if (showDepositSheet) {
          DepositFiatSheet(
            onDeposit = viewModel::depositFiat,
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
            onSignOut = onSignOut,
            onDismiss = { showProfileSheet = false },
          )
        }
      }
    }
  }
}
