package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.AlertConfigRecord
import com.example.data.remote.TradingSignalRecord
import com.example.data.repository.MarketDataRepository
import com.example.data.repository.SignalRepository
import com.example.domain.model.AssetClass
import com.example.domain.model.LiveMarketSignal
import com.example.domain.model.MarketPair
import com.example.domain.model.SignalStrength
import com.example.domain.model.Timeframe
import com.example.notifications.SweepPulseMessagingService
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.messaging.FirebaseMessaging
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>

  data class Success<T>(val data: T) : UiState<T>

  data class Error(val message: String) : UiState<Nothing>
}

data class TradeLevelAlertEvent(
  val signalId: String,
  val symbol: String,
  val timeframe: String,
  val direction: String,
  val hitType: String,
  val priceFormatted: String,
)

enum class SignalStrengthFilter(val label: String) {
  ALL("All Strengths"),
  STRONG_ONLY("Strong Only"),
  STRONG_BUY("Strong Buy"),
  BUY("Buy"),
  NEUTRAL("Neutral"),
  SELL("Sell"),
  STRONG_SELL("Strong Sell");

  fun matches(strength: SignalStrength): Boolean =
    when (this) {
      ALL -> true
      STRONG_ONLY -> strength == SignalStrength.STRONG_BUY || strength == SignalStrength.STRONG_SELL
      STRONG_BUY -> strength == SignalStrength.STRONG_BUY
      BUY -> strength == SignalStrength.BUY
      NEUTRAL -> strength == SignalStrength.NEUTRAL
      SELL -> strength == SignalStrength.SELL
      STRONG_SELL -> strength == SignalStrength.STRONG_SELL
    }

  fun matchesCode(code: String): Boolean {
    val parsed = SignalStrength.entries.firstOrNull { it.code == code } ?: return this == ALL
    return matches(parsed)
  }
}

class TradingViewModel(
  private val signalRepository: SignalRepository,
  private val marketDataRepository: MarketDataRepository,
  val currentUserId: String,
) : ViewModel() {

  private val _selectedTimeframe = MutableStateFlow(Timeframe.M15)
  val selectedTimeframe: StateFlow<Timeframe> = _selectedTimeframe.asStateFlow()

  private val _selectedTimeframeFilter = MutableStateFlow<Timeframe?>(Timeframe.M15)
  val selectedTimeframeFilter: StateFlow<Timeframe?> = _selectedTimeframeFilter.asStateFlow()

  private val _selectedPairFilter = MutableStateFlow<MarketPair?>(null)
  val selectedPairFilter: StateFlow<MarketPair?> = _selectedPairFilter.asStateFlow()

  private val _selectedAssetFilter = MutableStateFlow<AssetClass?>(null)
  val selectedAssetFilter: StateFlow<AssetClass?> = _selectedAssetFilter.asStateFlow()

  private val _selectedStrengthFilter = MutableStateFlow(SignalStrengthFilter.ALL)
  val selectedStrengthFilter: StateFlow<SignalStrengthFilter> = _selectedStrengthFilter.asStateFlow()

  private val _sweepOnlyFilter = MutableStateFlow(false)
  val sweepOnlyFilter: StateFlow<Boolean> = _sweepOnlyFilter.asStateFlow()

  private val _liveSignals =
    MutableStateFlow<Map<Pair<MarketPair, Timeframe>, LiveMarketSignal>>(emptyMap())
  val liveSignals: StateFlow<Map<Pair<MarketPair, Timeframe>, LiveMarketSignal>> =
    _liveSignals.asStateFlow()

  private val _isScanning = MutableStateFlow(false)
  val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

  private val _isMatrixScanning = MutableStateFlow(false)
  val isMatrixScanning: StateFlow<Boolean> = _isMatrixScanning.asStateFlow()

  private val _scanError = MutableStateFlow<String?>(null)
  val scanError: StateFlow<String?> = _scanError.asStateFlow()

  private val _autoScanEnabled = MutableStateFlow(true)
  val autoScanEnabled: StateFlow<Boolean> = _autoScanEnabled.asStateFlow()

  private val _statusBanner = MutableStateFlow<String?>(null)
  val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

  private val _sweepNotificationEvents = MutableSharedFlow<LiveMarketSignal>(extraBufferCapacity = 16)
  val sweepNotificationEvents: SharedFlow<LiveMarketSignal> = _sweepNotificationEvents.asSharedFlow()

  private val _tradeAlertEvents = MutableSharedFlow<TradeLevelAlertEvent>(extraBufferCapacity = 16)
  val tradeAlertEvents: SharedFlow<TradeLevelAlertEvent> = _tradeAlertEvents.asSharedFlow()

  val fcmToken: StateFlow<String?> = SweepPulseMessagingService.latestToken

  val savedSignals: StateFlow<UiState<List<TradingSignalRecord>>> =
    signalRepository
      .observeSignals(currentUserId)
      .map<List<TradingSignalRecord>, UiState<List<TradingSignalRecord>>> { UiState.Success(it) }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing signals", rootError)
        emit(UiState.Error(rootError.message ?: "Failed to observe saved signals"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  val alertConfigs: StateFlow<UiState<List<AlertConfigRecord>>> =
    signalRepository
      .observeAlertConfigs(currentUserId)
      .map<List<AlertConfigRecord>, UiState<List<AlertConfigRecord>>> { UiState.Success(it) }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing alert configs", rootError)
        emit(UiState.Error(rootError.message ?: "Failed to observe alert configs"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  init {
    fetchFcmTokenSafely()
    viewModelScope.launch {
      while (true) {
        if (_autoScanEnabled.value && !_isScanning.value) {
          scanTimeframe(_selectedTimeframe.value)
        }
        delay(AUTO_SCAN_INTERVAL_MS)
      }
    }
  }

  private fun fetchFcmTokenSafely() {
    runCatching {
      val messaging = FirebaseMessaging.getInstance()
      messaging.subscribeToTopic("smc_signals_all")
      messaging.token.addOnSuccessListener { token ->
        if (!token.isNullOrBlank()) {
          SweepPulseMessagingService.updateCachedToken(token)
          viewModelScope.launch { syncGlobalConfigTokenIfNeeded(token) }
        }
      }.addOnFailureListener { e ->
        Log.d(TAG, "FCM token unavailable: ${e.message}")
      }
    }
  }

  private suspend fun syncGlobalConfigTokenIfNeeded(token: String) {
    val existing =
      (alertConfigs.value as? UiState.Success)?.data?.firstOrNull { it.symbol == "ALL" }
    val updated =
      (existing
        ?: AlertConfigRecord(
          id = "global",
          userId = currentUserId,
          symbol = "ALL",
          enabled = true,
          tf1m = true,
          tf5m = true,
          tf15m = true,
          tf30m = true,
          minConfluence = 15L,
          requireLiquiditySweep = true,
          fcmToken = token,
        )).copy(fcmToken = token)
    signalRepository.saveAlertConfig(updated)
  }

  fun selectTimeframe(timeframe: Timeframe) {
    selectTimeframeFilter(timeframe)
  }

  fun selectTimeframeFilter(timeframe: Timeframe?) {
    _selectedTimeframeFilter.value = timeframe
    if (timeframe != null) {
      _selectedTimeframe.value = timeframe
      viewModelScope.launch { scanTimeframe(timeframe) }
    } else {
      scanFullMatrix()
    }
  }

  fun selectPairFilter(pair: MarketPair?) {
    _selectedPairFilter.value = pair
    if (pair != null) {
      _selectedAssetFilter.value = null
      val hasDataForPair =
        Timeframe.entries.any { _liveSignals.value.containsKey(pair to it) }
      if (!hasDataForPair) {
        loadTerminalPair(pair)
      }
    }
  }

  fun selectAssetFilter(assetClass: AssetClass?) {
    _selectedAssetFilter.value = assetClass
    if (assetClass != null) {
      _selectedPairFilter.value = null
    }
  }

  fun selectStrengthFilter(filter: SignalStrengthFilter) {
    _selectedStrengthFilter.value = filter
  }

  fun toggleSweepOnlyFilter() {
    _sweepOnlyFilter.value = !_sweepOnlyFilter.value
  }

  fun clearAllFilters() {
    _selectedPairFilter.value = null
    _selectedAssetFilter.value = null
    _selectedStrengthFilter.value = SignalStrengthFilter.ALL
    _sweepOnlyFilter.value = false
    _selectedTimeframeFilter.value = Timeframe.M15
    _selectedTimeframe.value = Timeframe.M15
  }

  fun toggleAutoScan() {
    _autoScanEnabled.value = !_autoScanEnabled.value
  }

  fun clearStatusBanner() {
    _statusBanner.value = null
  }

  fun notifyBanner(message: String) {
    _statusBanner.value = message
  }

  fun refreshCurrentTimeframe() {
    viewModelScope.launch { scanTimeframe(_selectedTimeframe.value) }
  }

  fun loadTerminalPair(pair: MarketPair) {
    viewModelScope.launch {
      _isScanning.value = true
      _scanError.value = null
      val results = marketDataRepository.scanPairAllTimeframes(pair)
      val successes = results.mapNotNull { it.getOrNull() }
      if (successes.isEmpty()) {
        val err = results.firstNotNullOfOrNull { it.exceptionOrNull() }
        _scanError.value = err?.localizedMessage ?: "Failed to load market candles for ${pair.symbol}"
      } else {
        _liveSignals.update { current ->
          current.toMutableMap().apply {
            successes.forEach { put(it.pair to it.timeframe, it) }
          }
        }
      }
      _isScanning.value = false
    }
  }

  fun scanFullMatrix() {
    if (_isMatrixScanning.value) return
    viewModelScope.launch {
      _isMatrixScanning.value = true
      _scanError.value = null
      var anySuccess = false
      for (tf in Timeframe.entries) {
        val results = marketDataRepository.scanPairsForTimeframe(MarketPair.entries, tf)
        val successes = results.mapNotNull { it.getOrNull() }
        if (successes.isNotEmpty()) {
          anySuccess = true
          _liveSignals.update { current ->
            current.toMutableMap().apply {
              successes.forEach { put(it.pair to it.timeframe, it) }
            }
          }
          emitMatchingSweepEvents(successes)
          checkActiveTradeLevels(successes)
        }
      }
      if (!anySuccess) {
        _scanError.value = "Unable to reach live market feeds. Check connection and retry."
      }
      _isMatrixScanning.value = false
    }
  }

  private suspend fun scanTimeframe(timeframe: Timeframe) {
    _isScanning.value = true
    _scanError.value = null
    val results = marketDataRepository.scanPairsForTimeframe(MarketPair.entries, timeframe)
    val successes = results.mapNotNull { it.getOrNull() }
    if (successes.isEmpty()) {
      val firstErr = results.firstNotNullOfOrNull { it.exceptionOrNull() }
      _scanError.value =
        firstErr?.localizedMessage ?: "Market data unavailable. Pull or tap refresh to retry."
    } else {
      _liveSignals.update { current ->
        current.toMutableMap().apply {
          successes.forEach { put(it.pair to it.timeframe, it) }
        }
      }
      emitMatchingSweepEvents(successes)
      checkActiveTradeLevels(successes)
    }
    _isScanning.value = false
  }

  private fun checkActiveTradeLevels(signals: List<LiveMarketSignal>) {
    val activeRecords =
      (savedSignals.value as? UiState.Success)?.data?.filter { it.status == "ACTIVE" }.orEmpty()
    if (activeRecords.isEmpty()) return

    val byKey = signals.associateBy { it.pair.symbol to it.timeframe.label }
    for (rec in activeRecords) {
      val live = byKey[rec.symbol to rec.timeframe] ?: continue
      val latestCandle = live.candles.lastOrNull() ?: continue
      val pair = live.pair
      val hitType =
        if (rec.isBuy) {
          when {
            rec.stopLoss > 0.0 && latestCandle.low <= rec.stopLoss -> "SL_HIT"
            rec.takeProfit1 > 0.0 && latestCandle.high >= rec.takeProfit1 -> "TP_HIT"
            else -> null
          }
        } else {
          when {
            rec.stopLoss > 0.0 && latestCandle.high >= rec.stopLoss -> "SL_HIT"
            rec.takeProfit1 > 0.0 && latestCandle.low <= rec.takeProfit1 -> "TP_HIT"
            else -> null
          }
        }
      if (hitType != null) {
        _tradeAlertEvents.tryEmit(
          TradeLevelAlertEvent(
            signalId = rec.id,
            symbol = rec.symbol,
            timeframe = rec.timeframe,
            direction = rec.direction,
            hitType = hitType,
            priceFormatted = pair.formatPrice(live.lastPrice),
          )
        )
      }
    }
  }

  private fun emitMatchingSweepEvents(signals: List<LiveMarketSignal>) {
    val configs = (alertConfigs.value as? UiState.Success)?.data.orEmpty()
    val global = configs.firstOrNull { it.symbol == "ALL" }
    val globalEnabled = global?.enabled ?: true
    if (!globalEnabled) return

    val minConfluence = (global?.minConfluence ?: 15L).toInt()
    val requireSweep = global?.requireLiquiditySweep ?: true

    for (sig in signals) {
      val pairConfig = configs.firstOrNull { it.symbol == sig.pair.symbol }
      if (pairConfig != null && !pairConfig.enabled) continue
      val tfEnabled =
        pairConfig?.isTimeframeEnabled(sig.timeframe.label)
          ?: global?.isTimeframeEnabled(sig.timeframe.label)
          ?: true
      if (!tfEnabled) continue
      if (requireSweep && !sig.smcAnalysis.sweepDetected) continue
      if (sig.alignedCount < minConfluence) continue

      _sweepNotificationEvents.tryEmit(sig)
    }
  }

  fun bestActiveSignal(): LiveMarketSignal? =
    _liveSignals.value.values
      .sortedByDescending { (if (it.smcAnalysis.sweepDetected) 100 else 0) + it.alignedCount }
      .firstOrNull()

  fun saveSignalToFirestore(signal: LiveMarketSignal) {
    viewModelScope.launch {
      val res = signalRepository.saveSignalFromLiveAnalysis(signal)
      _statusBanner.value =
        if (res.isSuccess) {
          "Saved ${signal.smcAnalysis.direction} ${signal.pair.symbol} (${signal.timeframe.label}) to Signal Journal"
        } else {
          "Could not save signal: ${res.exceptionOrNull()?.localizedMessage}"
        }
    }
  }

  fun resolveSignal(signalId: String, newStatus: String, notes: String) {
    viewModelScope.launch {
      val res = signalRepository.resolveSignalStatus(signalId, newStatus, notes)
      if (res.isFailure) {
        _statusBanner.value = "Failed to update signal status."
      }
    }
  }

  fun refreshSavedSignalLevels(record: TradingSignalRecord) {
    viewModelScope.launch {
      val pair = MarketPair.fromSymbol(record.symbol)
      val tf = Timeframe.fromLabel(record.timeframe)
      val cached = _liveSignals.value[pair to tf]
      val live =
        cached ?: marketDataRepository.fetchAndAnalyze(pair, tf).getOrNull()
      if (live == null) {
        _statusBanner.value = "Unable to fetch live levels for ${pair.symbol} (${tf.label})."
        return@launch
      }
      _liveSignals.update { it + ((pair to tf) to live) }
      val res = signalRepository.refreshActiveSignalLevels(record.id, live)
      _statusBanner.value =
        if (res.isSuccess) {
          "Synced ${pair.symbol} (${tf.label}) Entry/SL & ${live.alignedCount}/26 confluence"
        } else {
          "Could not sync levels: ${res.exceptionOrNull()?.localizedMessage}"
        }
    }
  }

  fun deleteSignal(signalId: String) {
    viewModelScope.launch { signalRepository.deleteSignal(signalId) }
  }

  fun saveGlobalAlertPreferences(
    enabled: Boolean,
    tf1m: Boolean,
    tf5m: Boolean,
    tf15m: Boolean,
    tf30m: Boolean,
    minConfluence: Int,
    requireSweep: Boolean,
  ) {
    viewModelScope.launch {
      val token = fcmToken.value.orEmpty()
      val config =
        AlertConfigRecord(
          id = "global",
          userId = currentUserId,
          symbol = "ALL",
          enabled = enabled,
          tf1m = tf1m,
          tf5m = tf5m,
          tf15m = tf15m,
          tf30m = tf30m,
          minConfluence = minConfluence.toLong(),
          requireLiquiditySweep = requireSweep,
          fcmToken = token,
        )
      val res = signalRepository.saveAlertConfig(config)
      runCatching {
        val messaging = FirebaseMessaging.getInstance()
        if (enabled) messaging.subscribeToTopic("smc_signals_all")
        else messaging.unsubscribeFromTopic("smc_signals_all")
      }
      _statusBanner.value =
        if (res.isSuccess) "Alert rules synced to Firebase"
        else "Alert rule sync failed: ${res.exceptionOrNull()?.localizedMessage}"
    }
  }

  fun togglePairAlert(pair: MarketPair, enabled: Boolean) {
    viewModelScope.launch {
      val existing =
        (alertConfigs.value as? UiState.Success)?.data?.firstOrNull { it.symbol == pair.symbol }
      val config =
        (existing
          ?: AlertConfigRecord(
            id = pair.symbol,
            userId = currentUserId,
            symbol = pair.symbol,
            enabled = enabled,
            tf1m = true,
            tf5m = true,
            tf15m = true,
            tf30m = true,
            minConfluence = 15L,
            requireLiquiditySweep = true,
            fcmToken = fcmToken.value.orEmpty(),
          )).copy(enabled = enabled)
      signalRepository.saveAlertConfig(config)
      runCatching {
        val topic = "smc_pair_${pair.symbol.lowercase()}"
        val messaging = FirebaseMessaging.getInstance()
        if (enabled) messaging.subscribeToTopic(topic)
        else messaging.unsubscribeFromTopic(topic)
      }
    }
  }

  private companion object {
    const val TAG = "TradingVM"
    const val STOP_TIMEOUT_MILLIS = 5000L
    const val AUTO_SCAN_INTERVAL_MS = 35_000L
  }
}
