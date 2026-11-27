package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DecryptedVaultTransaction
import com.example.data.MarketRatesRepository
import com.example.data.MarketSnapshot
import com.example.data.NetworkMonitor
import com.example.data.OfflineVaultRepository
import com.example.data.VaultIntegrityReport
import com.example.data.VaultRepository
import com.example.data.local.OfflineSyncStatus
import com.example.model.AssetCatalog
import com.example.model.CalcHistoryEntry
import com.example.model.EscrowDeal
import com.example.model.EscrowStatus
import com.example.model.PayTagEntry
import com.example.model.PeerTransfer
import com.example.model.SavedPair
import com.example.model.TransferStatus
import com.example.model.UserProfile
import com.example.model.WalletHolding
import com.example.model.WalletTransaction
import com.example.util.ExpressionEvaluator
import com.example.util.NumberFormatter
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

enum class MainTab(val label: String) {
  CALCULATOR("Calc"),
  PAIRS("Markets"),
  TRADE("Trade"),
  ESCROW("Escrow"),
  OFFLINE("Offline"),
  WALLET("Assets"),
}

data class CalculatorUiState(
  val expression: String = "100",
  val evaluatedValue: Double = 100.0,
  val hasSyntaxError: Boolean = false,
  val justEvaluated: Boolean = false,
  val baseCode: String = "USD",
  val targetCode: String = "NGN",
  val pinnedCodes: List<String> = listOf("BTC", "ETH", "GBP", "EUR"),
  val history: List<CalcHistoryEntry> = emptyList(),
)

data class TradeUiState(
  val isBuy: Boolean = true,
  val cryptoCode: String = "BTC",
  val fiatCode: String = "USD",
  val amountInput: String = "0.01",
  val timeframe: String = "24H",
  val isSubmitting: Boolean = false,
)

class VaultViewModel(
  private val repository: VaultRepository,
  private val marketRepository: MarketRatesRepository,
  private val currentUserId: String,
  private val initialDisplayName: String,
  private val offlineRepository: OfflineVaultRepository? = null,
  private val networkMonitor: NetworkMonitor? = null,
) : ViewModel() {

  private val _currentTab = MutableStateFlow(MainTab.CALCULATOR)
  val currentTab: StateFlow<MainTab> = _currentTab.asStateFlow()

  private val _isDarkTheme = MutableStateFlow(true)
  val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

  private val _calcState = MutableStateFlow(CalculatorUiState())
  val calcState: StateFlow<CalculatorUiState> = _calcState.asStateFlow()

  private val _tradeState = MutableStateFlow(TradeUiState())
  val tradeState: StateFlow<TradeUiState> = _tradeState.asStateFlow()

  private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
  val events: SharedFlow<String> = _events.asSharedFlow()

  private val _isSendingCrypto = MutableStateFlow(false)
  val isSendingCrypto: StateFlow<Boolean> = _isSendingCrypto.asStateFlow()

  private val _isSubmittingEscrow = MutableStateFlow(false)
  val isSubmittingEscrow: StateFlow<Boolean> = _isSubmittingEscrow.asStateFlow()

  private val _isUpdatingUsername = MutableStateFlow(false)
  val isUpdatingUsername: StateFlow<Boolean> = _isUpdatingUsername.asStateFlow()

  private val _inFlightOps = MutableStateFlow<Set<String>>(emptySet())

  private val _manualOfflineHold = MutableStateFlow(false)
  val manualOfflineHold: StateFlow<Boolean> = _manualOfflineHold.asStateFlow()

  private val _isSyncingOffline = MutableStateFlow(false)
  val isSyncingOffline: StateFlow<Boolean> = _isSyncingOffline.asStateFlow()

  private val _isQueueingOffline = MutableStateFlow(false)
  val isQueueingOffline: StateFlow<Boolean> = _isQueueingOffline.asStateFlow()

  private val _integrityReport = MutableStateFlow<VaultIntegrityReport?>(null)
  val integrityReport: StateFlow<VaultIntegrityReport?> = _integrityReport.asStateFlow()

  val isDeviceOnline: StateFlow<Boolean> = networkMonitor?.isDeviceOnline ?: MutableStateFlow(true)

  val isEffectivelyOffline: StateFlow<Boolean> = combine(isDeviceOnline, _manualOfflineHold) { online, hold ->
    !online || hold
  }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

  val offlineTransactions: StateFlow<List<DecryptedVaultTransaction>> =
    (offlineRepository?.observeDecryptedTransactions(currentUserId) ?: emptyFlow())
      .catch { error -> Log.w(TAG, "Error observing encrypted vault ledger", error) }
      .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  val pendingOfflineDebits: StateFlow<Map<String, Double>> = offlineTransactions
    .map { list ->
      val totals = mutableMapOf<String, Double>()
      for (tx in list) {
        if (tx.syncStatus == OfflineSyncStatus.QUEUED_OFFLINE || tx.syncStatus == OfflineSyncStatus.SYNCING) {
          totals[tx.assetCode] = (totals[tx.assetCode] ?: 0.0) + tx.amount
        }
      }
      totals.toMap()
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyMap())

  val marketSnapshot: StateFlow<MarketSnapshot> = marketRepository.snapshot

  val profileState: StateFlow<UiState<UserProfile?>> = repository.observeProfile(currentUserId)
    .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
    .catch { error -> handleStreamError("profile", error) { emit(UiState.Error(it)) } }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  val holdingsState: StateFlow<UiState<List<WalletHolding>>> = combine(
    repository.observeHoldings(currentUserId),
    marketRepository.snapshot,
  ) { list, snap ->
    UiState.Success(list.sortedByDescending { snap.usdValueOf(it.assetCode) * it.balance }) as UiState<List<WalletHolding>>
  }.catch { error -> handleStreamError("holdings", error) { emit(UiState.Error(it)) } }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  val transactionsState: StateFlow<UiState<List<WalletTransaction>>> = repository.observeTransactions(currentUserId)
    .map<List<WalletTransaction>, UiState<List<WalletTransaction>>> { UiState.Success(it) }
    .catch { error -> handleStreamError("transactions", error) { emit(UiState.Error(it)) } }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  val savedPairsState: StateFlow<UiState<List<SavedPair>>> = repository.observeSavedPairs(currentUserId)
    .map<List<SavedPair>, UiState<List<SavedPair>>> { UiState.Success(it) }
    .catch { error -> handleStreamError("savedPairs", error) { emit(UiState.Error(it)) } }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  val incomingTransfersState: StateFlow<UiState<List<PeerTransfer>>> = repository.observeIncomingTransfers(currentUserId)
    .map<List<PeerTransfer>, UiState<List<PeerTransfer>>> { list ->
      UiState.Success(list.filter { it.parsedStatus == TransferStatus.PENDING })
    }
    .catch { error -> handleStreamError("transfers", error) { emit(UiState.Error(it)) } }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  val escrowsState: StateFlow<UiState<List<EscrowDeal>>> = repository.observeEscrows(currentUserId)
    .map<List<EscrowDeal>, UiState<List<EscrowDeal>>> { UiState.Success(it) }
    .catch { error -> handleStreamError("escrows", error) { emit(UiState.Error(it)) } }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  init {
    retryInitialize()
    viewModelScope.launch {
      while (isActive) {
        marketRepository.refreshRates()
        delay(60_000L)
      }
    }
    if (offlineRepository != null) {
      viewModelScope.launch {
        transactionsState.collect { state ->
          if (state is UiState.Success && state.data.isNotEmpty()) {
            offlineRepository.archiveSyncedTransactions(currentUserId, state.data)
          }
        }
      }
      viewModelScope.launch {
        offlineTransactions.collect {
          _integrityReport.value = offlineRepository.verifyFullDatabaseIntegrity(currentUserId)
        }
      }
      viewModelScope.launch {
        combine(
          isEffectivelyOffline,
          offlineTransactions.map { list ->
            list.any {
              it.syncStatus == OfflineSyncStatus.QUEUED_OFFLINE || it.syncStatus == OfflineSyncStatus.SYNCING
            }
          },
        ) { offline, hasPending -> !offline && hasPending }
          .distinctUntilChanged()
          .collect { shouldSync ->
            if (shouldSync) {
              syncOfflineQueue(manual = false)
            }
          }
      }
    }
  }

  fun retryInitialize() {
    viewModelScope.launch {
      repository.ensureUserInitialized(currentUserId, initialDisplayName)
        .onFailure { err ->
          _events.tryEmit(err.localizedMessage ?: "Failed to initialize wallet")
        }
    }
  }

  private suspend inline fun handleStreamError(
    label: String,
    error: Throwable,
    emitError: (String) -> Unit,
  ) {
    val firestoreError = generateSequence(error) { it.cause }
      .filterIsInstance<FirebaseFirestoreException>()
      .firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    val rootError = firestoreError ?: error
    Log.w(TAG, "Error observing $label", rootError)
    emitError(rootError.localizedMessage ?: "Failed to observe $label")
  }

  fun selectTab(tab: MainTab) {
    _currentTab.value = tab
  }

  fun toggleTheme() {
    _isDarkTheme.value = !_isDarkTheme.value
  }

  fun refreshMarketRates() {
    viewModelScope.launch {
      marketRepository.refreshRates()
      _events.tryEmit("Live market rates synced")
    }
  }

  // --- Calculator Actions ---
  fun onCalcKey(token: String) {
    val curr = _calcState.value
    when (token) {
      "AC" -> _calcState.value = curr.copy(
        expression = "0",
        evaluatedValue = 0.0,
        hasSyntaxError = false,
        justEvaluated = false,
      )
      "⌫" -> {
        val next = if (curr.expression.length <= 1) "0" else curr.expression.dropLast(1)
        updateExpression(next, justEvaluated = false)
      }
      "=" -> {
        val evaluated = ExpressionEvaluator.evaluate(curr.expression, allowTrailingOperator = false)
        if (evaluated != null) {
          val converted = marketSnapshot.value.convert(evaluated, curr.baseCode, curr.targetCode)
          val entry = CalcHistoryEntry(
            id = System.nanoTime().toString(),
            expression = curr.expression,
            result = evaluated,
            baseCode = curr.baseCode,
            targetCode = curr.targetCode,
            convertedResult = converted,
            timestampMs = System.currentTimeMillis(),
          )
          val formatted = formatPlainResult(evaluated)
          _calcState.value = curr.copy(
            expression = formatted,
            evaluatedValue = evaluated,
            hasSyntaxError = false,
            justEvaluated = true,
            history = (listOf(entry) + curr.history).take(30),
          )
        } else {
          _calcState.value = curr.copy(hasSyntaxError = true)
        }
      }
      else -> {
        val isOperator = token in setOf("+", "-", "×", "÷")
        val isBinaryOnlyOp = token in setOf("+", "×", "÷")
        val isDigit = token.length == 1 && token[0].isDigit()
        val isDecimal = token == "."

        val baseExpr = if (curr.justEvaluated && (isDigit || isDecimal || token == "(")) {
          "0"
        } else {
          curr.expression
        }

        // Guard percentage: valid only after a digit, closing paren, or existing %
        if (token == "%") {
          val last = baseExpr.lastOrNull()
          if (baseExpr == "0" || last == null || (!last.isDigit() && last != ')' && last != '%')) {
            return
          }
        }

        // Guard closing parenthesis: only when an open paren exists and last token is a value
        if (token == ")") {
          val openCount = baseExpr.count { it == '(' }
          val closeCount = baseExpr.count { it == ')' }
          val last = baseExpr.lastOrNull()
          if (openCount <= closeCount || last == null || (!last.isDigit() && last != ')' && last != '%')) {
            return
          }
        }

        // Prevent duplicate decimal point inside the active numeric token
        if (isDecimal) {
          val activeSegment = baseExpr.takeLastWhile { it.isDigit() || it == '.' }
          if ('.' in activeSegment) return
        }

        val lastChar = baseExpr.lastOrNull()
        val lastIsOp = lastChar != null && lastChar.toString() in setOf("+", "-", "×", "÷")

        val next = when {
          baseExpr == "0" && isDigit -> token
          baseExpr == "0" && isDecimal -> "0."
          baseExpr == "0" && token == "(" -> "("
          baseExpr == "0" && token == "-" -> "-"
          isDecimal && (lastChar == null || lastChar in charArrayOf('+', '-', '×', '÷', '(', ')', '%')) ->
            "${baseExpr}0."
          isBinaryOnlyOp && lastChar == '(' -> return
          baseExpr == "-" && isBinaryOnlyOp -> return
          isOperator && lastIsOp -> {
            val prevTwo = baseExpr.takeLast(2)
            when {
              token == "-" && lastChar in charArrayOf('×', '÷') -> "$baseExpr-"
              (prevTwo == "×-" || prevTwo == "÷-" || prevTwo == "(-") && token == "-" -> return
              prevTwo == "×-" || prevTwo == "÷-" -> baseExpr.dropLast(2) + token
              prevTwo == "(-" && isBinaryOnlyOp -> return
              else -> baseExpr.dropLast(1) + token
            }
          }
          else -> baseExpr + token
        }
        updateExpression(next, justEvaluated = false)
      }
    }
  }

  private fun updateExpression(expr: String, justEvaluated: Boolean) {
    val evaluated = ExpressionEvaluator.evaluate(expr, allowTrailingOperator = true)
    val isPartialUnaryMinus = expr == "-" || expr.endsWith("(-")
    _calcState.value = _calcState.value.copy(
      expression = expr,
      evaluatedValue = when {
        evaluated != null -> evaluated
        isPartialUnaryMinus -> 0.0
        else -> _calcState.value.evaluatedValue
      },
      hasSyntaxError = evaluated == null && expr.isNotBlank() && !isPartialUnaryMinus,
      justEvaluated = justEvaluated,
    )
  }

  private fun formatPlainResult(value: Double): String {
    if (value == value.toLong().toDouble() && kotlin.math.abs(value) < 1e12) {
      return value.toLong().toString()
    }
    return "%.6f".format(java.util.Locale.US, value).trimEnd('0').trimEnd('.')
  }

  fun setCalcCurrencies(baseCode: String, targetCode: String) {
    _calcState.value = _calcState.value.copy(baseCode = baseCode, targetCode = targetCode)
  }

  fun swapCalcCurrencies() {
    val curr = _calcState.value
    _calcState.value = curr.copy(baseCode = curr.targetCode, targetCode = curr.baseCode)
  }

  fun restoreHistoryEntry(entry: CalcHistoryEntry) {
    _calcState.value = _calcState.value.copy(
      expression = entry.expression,
      evaluatedValue = entry.result,
      baseCode = entry.baseCode,
      targetCode = entry.targetCode,
      hasSyntaxError = false,
      justEvaluated = false,
    )
  }

  fun loadPairInCalculator(baseCode: String, quoteCode: String, amount: Double = 1.0) {
    _calcState.value = _calcState.value.copy(
      baseCode = baseCode,
      targetCode = quoteCode,
      expression = formatPlainResult(amount),
      evaluatedValue = amount,
      hasSyntaxError = false,
      justEvaluated = false,
    )
    _currentTab.value = MainTab.CALCULATOR
  }

  fun sendCalcAmountToTrade() {
    val curr = _calcState.value
    val baseAsset = AssetCatalog.find(curr.baseCode)
    val targetAsset = AssetCatalog.find(curr.targetCode)
    val cryptoCode = when {
      baseAsset.category.name == "CRYPTO" -> baseAsset.code
      targetAsset.category.name == "CRYPTO" -> targetAsset.code
      else -> "BTC"
    }
    val fiatCode = when {
      targetAsset.code in AssetCatalog.settlementFiats.map { it.code } -> targetAsset.code
      baseAsset.code in AssetCatalog.settlementFiats.map { it.code } -> baseAsset.code
      else -> "USD"
    }
    val cryptoAmt = marketSnapshot.value.convert(curr.evaluatedValue.coerceAtLeast(0.0), curr.baseCode, cryptoCode)
    val formattedAmt = NumberFormatter.formatTruncatedInput(cryptoAmt, 8).takeIf { it != "0" } ?: "0.01"
    _tradeState.value = _tradeState.value.copy(
      cryptoCode = cryptoCode,
      fiatCode = fiatCode,
      amountInput = formattedAmt,
    )
    _currentTab.value = MainTab.TRADE
  }

  // --- Pairs Actions ---
  fun togglePinPair(baseCode: String, quoteCode: String, currentlyPinned: Boolean) {
    viewModelScope.launch {
      repository.toggleSavedPair(currentUserId, baseCode, quoteCode, currentlyPinned)
        .onFailure { _events.tryEmit(it.localizedMessage ?: "Failed to update pinned pair") }
    }
  }

  // --- Trade Actions ---
  fun setTradeSide(isBuy: Boolean) {
    _tradeState.value = _tradeState.value.copy(isBuy = isBuy)
  }

  fun setTradeCrypto(code: String) {
    _tradeState.value = _tradeState.value.copy(cryptoCode = code)
  }

  fun setTradeFiat(code: String) {
    _tradeState.value = _tradeState.value.copy(fiatCode = code)
  }

  fun setTradeAmountInput(value: String) {
    val cleaned = value.filter { it.isDigit() || it == '.' }
    if (cleaned.count { it == '.' } <= 1) {
      _tradeState.value = _tradeState.value.copy(amountInput = cleaned)
    }
  }

  fun setTradeTimeframe(tf: String) {
    _tradeState.value = _tradeState.value.copy(timeframe = tf)
  }

  fun applyTradePercent(fraction: Double, holdings: List<WalletHolding>) {
    val state = _tradeState.value
    val snap = marketSnapshot.value
    if (state.isBuy) {
      val fiatBal = holdings.firstOrNull { it.assetCode == state.fiatCode }?.balance ?: 0.0
      val spendable = (fiatBal * fraction) / 1.001
      val cryptoAmt = snap.convert(spendable, state.fiatCode, state.cryptoCode)
      _tradeState.value = state.copy(amountInput = NumberFormatter.formatTruncatedInput(cryptoAmt, 8))
    } else {
      val cryptoBal = holdings.firstOrNull { it.assetCode == state.cryptoCode }?.balance ?: 0.0
      _tradeState.value = state.copy(amountInput = NumberFormatter.formatTruncatedInput(cryptoBal * fraction, 8))
    }
  }

  fun submitTrade() {
    val state = _tradeState.value
    val amount = state.amountInput.toDoubleOrNull()
    if (amount == null || amount <= 0.0) {
      _events.tryEmit("Enter a valid order amount")
      return
    }
    val snap = marketSnapshot.value
    val cryptoPriceUsd = snap.usdValueOf(state.cryptoCode)
    val fiatPriceUsd = snap.usdValueOf(state.fiatCode)
    val fiatTotal = snap.convert(amount, state.cryptoCode, state.fiatCode)
    val feeFiat = fiatTotal * 0.001

    viewModelScope.launch {
      _tradeState.value = _tradeState.value.copy(isSubmitting = true)
      repository.executeTrade(
        userId = currentUserId,
        isBuy = state.isBuy,
        cryptoCode = state.cryptoCode,
        cryptoAmount = amount,
        fiatCode = state.fiatCode,
        fiatTotal = fiatTotal,
        feeFiat = feeFiat,
        cryptoPriceUsd = cryptoPriceUsd,
        fiatPriceUsd = fiatPriceUsd,
      ).onSuccess {
        _events.tryEmit(
          "${if (state.isBuy) "Bought" else "Sold"} ${formatPlainResult(amount)} ${state.cryptoCode}"
        )
      }.onFailure { err ->
        _events.tryEmit(err.localizedMessage ?: "Trade failed")
      }
      _tradeState.value = _tradeState.value.copy(isSubmitting = false)
    }
  }

  // --- Wallet / Send / Receive Actions ---
  fun depositFiat(fiatCode: String, amount: Double) {
    val priceUsd = marketSnapshot.value.usdValueOf(fiatCode)
    viewModelScope.launch {
      repository.depositFiat(currentUserId, fiatCode, amount, priceUsd)
        .onSuccess { _events.tryEmit("Added $amount $fiatCode to your wallet") }
        .onFailure { _events.tryEmit(it.localizedMessage ?: "Deposit failed") }
    }
  }

  fun updatePreferredFiat(fiatCode: String) {
    viewModelScope.launch {
      repository.updatePreferredFiat(currentUserId, fiatCode)
        .onFailure { _events.tryEmit(it.localizedMessage ?: "Failed to update display currency") }
    }
  }

  suspend fun checkUsernameAvailability(rawUsername: String): Result<VaultRepository.UsernameAvailability> =
    repository.checkUsernameAvailability(rawUsername, currentUserId)

  fun claimUsername(
    rawUsername: String,
    newDisplayName: String?,
    onSuccess: (String) -> Unit,
  ) {
    viewModelScope.launch {
      _isUpdatingUsername.value = true
      try {
        repository.claimUsername(currentUserId, rawUsername, newDisplayName)
          .onSuccess { tag ->
            _events.tryEmit("Username @$tag is now active")
            onSuccess(tag)
          }
          .onFailure { err ->
            _events.tryEmit(err.localizedMessage ?: "Failed to update username")
          }
      } finally {
        _isUpdatingUsername.value = false
      }
    }
  }

  suspend fun lookupPayTag(tag: String): Result<PayTagEntry?> = repository.lookupPayTag(tag)

  fun sendCrypto(
    senderTag: String,
    assetCode: String,
    amount: Double,
    recipientInput: String,
    note: String?,
    onSuccess: () -> Unit,
  ) {
    val priceUsd = marketSnapshot.value.usdValueOf(assetCode)
    viewModelScope.launch {
      _isSendingCrypto.value = true
      repository.sendCrypto(
        userId = currentUserId,
        senderTag = senderTag,
        assetCode = assetCode,
        amount = amount,
        priceUsd = priceUsd,
        recipientInput = recipientInput,
        note = note,
      ).onSuccess { msg ->
        _events.tryEmit(msg)
        onSuccess()
      }.onFailure { err ->
        _events.tryEmit(err.localizedMessage ?: "Transfer failed")
      }
      _isSendingCrypto.value = false
    }
  }

  fun claimTransfer(transfer: PeerTransfer) {
    val opKey = "claim_${transfer.id}"
    if (opKey in _inFlightOps.value) return
    val priceUsd = marketSnapshot.value.usdValueOf(transfer.assetCode)
    viewModelScope.launch {
      _inFlightOps.value = _inFlightOps.value + opKey
      try {
        repository.claimIncomingTransfer(currentUserId, transfer, priceUsd)
          .onSuccess { _events.tryEmit("Claimed ${transfer.amount} ${transfer.assetCode} from @${transfer.senderTag}") }
          .onFailure { _events.tryEmit(it.localizedMessage ?: "Could not claim transfer") }
      } finally {
        _inFlightOps.value = _inFlightOps.value - opKey
      }
    }
  }

  fun receiveSampleDeposit(assetCode: String, amount: Double) {
    val priceUsd = marketSnapshot.value.usdValueOf(assetCode)
    viewModelScope.launch {
      repository.receiveExternalDeposit(currentUserId, assetCode, amount, priceUsd)
        .onSuccess { _events.tryEmit("Received $amount $assetCode into your wallet") }
        .onFailure { _events.tryEmit(it.localizedMessage ?: "Receive failed") }
    }
  }

  // --- Escrow Actions ---
  fun createEscrow(
    buyerTag: String,
    sellerTagInput: String,
    assetCode: String,
    amount: Double,
    title: String,
    agreedTerms: String?,
    onSuccess: () -> Unit,
  ) {
    val priceUsd = marketSnapshot.value.usdValueOf(assetCode)
    viewModelScope.launch {
      _isSubmittingEscrow.value = true
      repository.createEscrow(
        userId = currentUserId,
        buyerTag = buyerTag,
        sellerTagInput = sellerTagInput,
        assetCode = assetCode,
        amount = amount,
        priceUsd = priceUsd,
        title = title,
        agreedTerms = agreedTerms,
      ).onSuccess { msg ->
        _events.tryEmit(msg)
        onSuccess()
      }.onFailure { err ->
        _events.tryEmit(err.localizedMessage ?: "Could not create escrow")
      }
      _isSubmittingEscrow.value = false
    }
  }

  fun markEscrowDelivered(escrow: EscrowDeal) {
    val opKey = "escrow_${escrow.id}"
    if (opKey in _inFlightOps.value) return
    viewModelScope.launch {
      _inFlightOps.value = _inFlightOps.value + opKey
      try {
        repository.updateEscrowStatus(currentUserId, escrow, EscrowStatus.DELIVERED)
          .onSuccess { _events.tryEmit("Marked \"${escrow.title}\" as delivered") }
          .onFailure { _events.tryEmit(it.localizedMessage ?: "Update failed") }
      } finally {
        _inFlightOps.value = _inFlightOps.value - opKey
      }
    }
  }

  fun releaseEscrow(escrow: EscrowDeal) {
    val opKey = "escrow_${escrow.id}"
    if (opKey in _inFlightOps.value) return
    viewModelScope.launch {
      _inFlightOps.value = _inFlightOps.value + opKey
      try {
        repository.updateEscrowStatus(currentUserId, escrow, EscrowStatus.RELEASED)
          .onSuccess { _events.tryEmit("Escrow released to @${escrow.sellerTag}") }
          .onFailure { _events.tryEmit(it.localizedMessage ?: "Release failed") }
      } finally {
        _inFlightOps.value = _inFlightOps.value - opKey
      }
    }
  }

  fun refundEscrow(escrow: EscrowDeal) {
    val opKey = "escrow_${escrow.id}"
    if (opKey in _inFlightOps.value) return
    viewModelScope.launch {
      _inFlightOps.value = _inFlightOps.value + opKey
      try {
        repository.updateEscrowStatus(currentUserId, escrow, EscrowStatus.REFUNDED)
          .onSuccess { _events.tryEmit("Refunded escrow to @${escrow.buyerTag}") }
          .onFailure { _events.tryEmit(it.localizedMessage ?: "Refund failed") }
      } finally {
        _inFlightOps.value = _inFlightOps.value - opKey
      }
    }
  }

  fun disputeEscrow(escrow: EscrowDeal, reason: String) {
    val opKey = "escrow_${escrow.id}"
    if (opKey in _inFlightOps.value) return
    viewModelScope.launch {
      _inFlightOps.value = _inFlightOps.value + opKey
      try {
        repository.updateEscrowStatus(currentUserId, escrow, EscrowStatus.DISPUTED, reason)
          .onSuccess { _events.tryEmit("Escrow flagged as disputed") }
          .onFailure { _events.tryEmit(it.localizedMessage ?: "Could not file dispute") }
      } finally {
        _inFlightOps.value = _inFlightOps.value - opKey
      }
    }
  }

  fun claimEscrowFunds(escrow: EscrowDeal) {
    val opKey = "escrow_${escrow.id}"
    if (opKey in _inFlightOps.value) return
    val priceUsd = marketSnapshot.value.usdValueOf(escrow.assetCode)
    viewModelScope.launch {
      _inFlightOps.value = _inFlightOps.value + opKey
      try {
        repository.claimEscrowFunds(currentUserId, escrow, priceUsd)
          .onSuccess { _events.tryEmit("Credited ${escrow.amount} ${escrow.assetCode} to your wallet") }
          .onFailure { _events.tryEmit(it.localizedMessage ?: "Could not claim escrow funds") }
      } finally {
        _inFlightOps.value = _inFlightOps.value - opKey
      }
    }
  }

  // --- Offline Encrypted Vault Actions ---
  fun setOfflineHold(hold: Boolean) {
    _manualOfflineHold.value = hold
    if (hold) {
      _events.tryEmit("Offline Hold active: sends encrypt locally and deduct when back online")
    }
  }

  fun queueOfflineSend(
    senderTag: String,
    assetCode: String,
    amount: Double,
    recipientInput: String,
    note: String?,
    holdings: List<WalletHolding>,
    onSuccess: (DecryptedVaultTransaction) -> Unit,
  ) {
    val offlineRepo = offlineRepository ?: run {
      _events.tryEmit("Offline vault unavailable")
      return
    }
    val onlineBalance = holdings.firstOrNull { it.assetCode == assetCode }?.balance ?: 0.0
    val priceUsd = marketSnapshot.value.usdValueOf(assetCode)
    viewModelScope.launch {
      _isQueueingOffline.value = true
      try {
        offlineRepo.queueOfflineSend(
          userId = currentUserId,
          senderTag = senderTag,
          assetCode = assetCode,
          amount = amount,
          priceUsd = priceUsd,
          recipientInput = recipientInput,
          note = note,
          availableOnlineBalance = onlineBalance,
        ).onSuccess { tx ->
          _events.tryEmit("Encrypted & queued $amount $assetCode offline (${tx.integrityMacPreview}…)")
          onSuccess(tx)
        }.onFailure { err ->
          _events.tryEmit(err.localizedMessage ?: "Could not queue offline transaction")
        }
      } finally {
        _isQueueingOffline.value = false
      }
    }
  }

  fun cancelOfflineTransaction(txId: String) {
    val offlineRepo = offlineRepository ?: return
    viewModelScope.launch {
      offlineRepo.cancelQueuedOfflineSend(currentUserId, txId)
        .onSuccess { _events.tryEmit("Cancelled offline transfer and released reserved balance") }
        .onFailure { _events.tryEmit(it.localizedMessage ?: "Cancel failed") }
    }
  }

  fun retryFailedOfflineTransaction(txId: String) {
    val offlineRepo = offlineRepository ?: return
    viewModelScope.launch {
      offlineRepo.retryFailedTransaction(currentUserId, txId)
      if (!isEffectivelyOffline.value) {
        syncOfflineQueue(manual = true)
      }
    }
  }

  fun syncOfflineQueue(manual: Boolean = true) {
    val offlineRepo = offlineRepository ?: return
    if (isEffectivelyOffline.value) {
      if (manual) _events.tryEmit("Still offline — queued transfers will settle when back online")
      return
    }
    if (_isSyncingOffline.value) return
    viewModelScope.launch {
      _isSyncingOffline.value = true
      try {
        val profileTag = (profileState.value as? UiState.Success)?.data?.payTag ?: "vault_user"
        val summary = offlineRepo.syncPendingTransactions(
          userId = currentUserId,
          senderTag = profileTag,
          marketSnapshot = marketSnapshot.value,
        )
        when {
          summary.settledCount > 0 && summary.failedCount == 0 ->
            _events.tryEmit("Synced & deducted ${summary.settledCount} offline transfer${if (summary.settledCount > 1) "s" else ""}")
          summary.settledCount > 0 && summary.failedCount > 0 ->
            _events.tryEmit("Settled ${summary.settledCount}; ${summary.failedCount} failed (${summary.firstError ?: "check vault"})")
          summary.failedCount > 0 ->
            _events.tryEmit(summary.firstError ?: "Offline settlement failed")
          manual && summary.remainingQueuedCount == 0 ->
            _events.tryEmit("All encrypted offline transfers are settled")
        }
      } finally {
        _isSyncingOffline.value = false
      }
    }
  }

  fun verifyOfflineVaultIntegrity() {
    val offlineRepo = offlineRepository ?: return
    viewModelScope.launch {
      val report = offlineRepo.verifyFullDatabaseIntegrity(currentUserId)
      _integrityReport.value = report
      if (report.tamperedRecords == 0) {
        _events.tryEmit("Verified ${report.verifiedRecords}/${report.totalRecords} AES-256-GCM + HMAC records")
      } else {
        _events.tryEmit("Warning: ${report.tamperedRecords} record(s) failed integrity verification")
      }
    }
  }

  override fun onCleared() {
    super.onCleared()
    networkMonitor?.release()
  }

  private companion object {
    const val TAG = "VaultViewModel"
  }
}
