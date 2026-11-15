package com.example.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SweepPulseMessagingService : FirebaseMessagingService() {

  override fun onNewToken(token: String) {
    super.onNewToken(token)
    Log.d(TAG, "FCM token refreshed")
    _latestToken.value = token
  }

  override fun onMessageReceived(message: RemoteMessage) {
    super.onMessageReceived(message)
    val data = message.data
    val symbol = data["symbol"] ?: "BTCUSD"
    val timeframe = data["timeframe"] ?: "15m"
    val direction = data["direction"] ?: "BUY"
    val entry = data["entryPrice"] ?: data["entry"]
    val stopLoss = data["stopLoss"] ?: data["sl"]
    val takeProfit = data["takeProfit"] ?: data["tp1"]
    val smcEvent = data["smcEvent"] ?: "Liquidity Sweep"
    val confluence = data["confluence"] ?: ""

    val title =
      message.notification?.title
        ?: "$direction $symbol ($timeframe) • $smcEvent"

    val body =
      message.notification?.body
        ?: buildString {
          if (!entry.isNullOrBlank()) append("Entry: $entry")
          if (!stopLoss.isNullOrBlank()) append(" | SL: $stopLoss")
          if (!takeProfit.isNullOrBlank()) append(" | TP: $takeProfit")
          if (confluence.isNotBlank()) append(" | $confluence/26 TV")
          if (isEmpty()) append("Real-time SMC liquidity sweep market alert.")
        }

    SignalNotificationManager.showSignalNotification(
      context = applicationContext,
      title = title,
      body = body,
      symbol = symbol,
      timeframe = timeframe,
    )
  }

  companion object {
    private const val TAG = "SweepPulseFCM"
    private val _latestToken = MutableStateFlow<String?>(null)
    val latestToken = _latestToken.asStateFlow()

    fun updateCachedToken(token: String) {
      _latestToken.value = token
    }
  }
}
