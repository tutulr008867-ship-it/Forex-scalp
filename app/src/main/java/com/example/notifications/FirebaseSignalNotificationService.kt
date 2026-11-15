package com.example.notifications

import android.content.Context
import android.util.Log
import com.example.data.remote.AlertConfigRecord
import com.example.data.remote.TradingSignalRecord
import com.example.data.repository.SignalRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Local notification service that watches Firebase Firestore signal streams in real time
 * and triggers high-priority push notifications whenever a new 'BUY' or 'SELL' signal arrives.
 */
class FirebaseSignalNotificationService(
  private val context: Context,
  private val signalRepository: SignalRepository,
  private val userId: String,
  private val notifier: (Context, TradingSignalRecord) -> Boolean =
    SignalNotificationManager::notifyFirebaseSignal,
) {
  private val knownSignalIds = mutableSetOf<String>()
  private var initialSnapshotProcessed = false
  private var latestConfigs: List<AlertConfigRecord> = emptyList()

  private var signalsJob: Job? = null
  private var configsJob: Job? = null

  private val _isListening = MutableStateFlow(false)
  val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

  private val _lastNotifiedRecord = MutableStateFlow<TradingSignalRecord?>(null)
  val lastNotifiedRecord: StateFlow<TradingSignalRecord?> = _lastNotifiedRecord.asStateFlow()

  fun start(scope: CoroutineScope) {
    if (signalsJob?.isActive == true) return
    SignalNotificationManager.ensureChannel(context)
    _isListening.value = true

    configsJob =
      scope.launch {
        signalRepository
          .observeAlertConfigs(userId)
          .catch { err ->
            if (err is CancellationException) throw err
            Log.w(TAG, "Alert config observation paused: ${err.message}")
          }
          .collect { configs ->
            latestConfigs = configs
          }
      }

    signalsJob =
      scope.launch {
        signalRepository
          .observeSignals(userId)
          .catch { err ->
            if (err is CancellationException) throw err
            _isListening.value = false
            Log.w(TAG, "Firebase signal listener error: ${err.message}")
          }
          .collect { records ->
            processSnapshot(records)
          }
      }
  }

  fun stop() {
    signalsJob?.cancel()
    configsJob?.cancel()
    signalsJob = null
    configsJob = null
    _isListening.value = false
  }

  internal fun processSnapshot(records: List<TradingSignalRecord>): List<TradingSignalRecord> {
    if (!initialSnapshotProcessed) {
      knownSignalIds.addAll(records.map { it.id }.filter { it.isNotBlank() })
      initialSnapshotProcessed = true
      return emptyList()
    }

    val newSignals = filterEligibleNewSignals(knownSignalIds, records, latestConfigs)
    knownSignalIds.addAll(records.map { it.id }.filter { it.isNotBlank() })

    for (record in newSignals) {
      val sent = notifier(context, record)
      if (sent) {
        _lastNotifiedRecord.value = record
      }
    }
    return newSignals
  }

  companion object {
    private const val TAG = "FirebaseSignalNotifySvc"

    fun filterEligibleNewSignals(
      knownIds: Set<String>,
      incoming: List<TradingSignalRecord>,
      configs: List<AlertConfigRecord>,
    ): List<TradingSignalRecord> {
      val global = configs.firstOrNull { it.symbol == "ALL" }
      if (global != null && !global.enabled) return emptyList()

      val minConfluence = (global?.minConfluence ?: 10L).toInt()

      return incoming.filter { record ->
        if (record.id.isBlank() || record.id in knownIds) return@filter false
        if (record.status != "ACTIVE") return@filter false

        val dir = record.direction.uppercase()
        if (dir != "BUY" && dir != "SELL") return@filter false

        val pairConfig = configs.firstOrNull { it.symbol == record.symbol }
        if (pairConfig != null && !pairConfig.enabled) return@filter false

        val tfEnabled =
          pairConfig?.isTimeframeEnabled(record.timeframe)
            ?: global?.isTimeframeEnabled(record.timeframe)
            ?: true
        if (!tfEnabled) return@filter false

        record.alignedCount >= minConfluence
      }
    }
  }
}
