package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.remote.TradingSignalRecord
import com.example.domain.model.LiveMarketSignal
import com.example.domain.model.MarketPair
import java.util.concurrent.ConcurrentHashMap

object SignalNotificationManager {
  const val CHANNEL_ID = "sweep_signals_channel"
  const val EXTRA_SYMBOL = "extra_symbol"
  const val EXTRA_TIMEFRAME = "extra_timeframe"

  private val recentlyNotified = ConcurrentHashMap<String, Long>()

  fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val existing = manager.getNotificationChannel(CHANNEL_ID)
      if (existing == null) {
        val channel =
          NotificationChannel(
              CHANNEL_ID,
              "SMC Liquidity Sweep Signals",
              NotificationManager.IMPORTANCE_HIGH,
            )
            .apply {
              description =
                "Real-time Buy/Sell market entry, stop-loss, and take-profit alerts when SMC liquidity sweeps align with 26 TradingView indicators."
              enableVibration(true)
            }
        manager.createNotificationChannel(channel)
      }
    }
  }

  fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }

  fun notifyIfNewSweep(context: Context, signal: LiveMarketSignal): Boolean {
    val sweepTs = signal.smcAnalysis.sweepMarker?.candleIndex ?: 0
    val dedupKey = "${signal.pair.symbol}_${signal.timeframe.label}_${signal.smcAnalysis.direction}_$sweepTs"
    val now = System.currentTimeMillis()
    val lastSent = recentlyNotified[dedupKey]
    if (lastSent != null && (now - lastSent) < 15 * 60 * 1000L) {
      return false
    }
    recentlyNotified[dedupKey] = now

    val smc = signal.smcAnalysis
    val pair = signal.pair
    val title =
      "${smc.direction} ${pair.symbol} (${signal.timeframe.label}) • ${signal.alignedCount}/26 TV"
    val body =
      "${smc.eventType.label} | Entry: ${pair.formatPrice(smc.entryPrice)} | SL: ${pair.formatPrice(smc.stopLoss)} | TP1: ${pair.formatPrice(smc.takeProfit1)} (R:R 1:${"%.1f".format(smc.riskRewardRatio)})"
    return showSignalNotification(
      context = context,
      title = title,
      body = body,
      symbol = pair.symbol,
      timeframe = signal.timeframe.label,
      notificationId = dedupKey.hashCode(),
    )
  }

  fun notifyFirebaseSignal(
    context: Context,
    record: TradingSignalRecord,
  ): Boolean {
    val direction = record.direction.uppercase()
    if (direction != "BUY" && direction != "SELL") return false

    val dedupKey = "fb_${record.id}_$direction"
    val now = System.currentTimeMillis()
    val lastSent = recentlyNotified[dedupKey]
    if (lastSent != null && (now - lastSent) < 15 * 60 * 1000L) {
      return false
    }
    recentlyNotified[dedupKey] = now

    val pair = MarketPair.fromSymbol(record.symbol)
    val title =
      "NEW $direction SIGNAL • ${pair.symbol} (${record.timeframe}) • ${record.alignedCount}/26 TV"
    val eventLabel = record.smcEvent.ifBlank { "SMC Setup" }
    val body =
      "$eventLabel | Entry: ${pair.formatPrice(record.entryPrice)} | SL: ${pair.formatPrice(record.stopLoss)} | TP1: ${pair.formatPrice(record.takeProfit1)} (R:R 1:${"%.1f".format(record.riskRewardRatio)})"

    return showSignalNotification(
      context = context,
      title = title,
      body = body,
      symbol = pair.symbol,
      timeframe = record.timeframe,
      notificationId = dedupKey.hashCode(),
    )
  }

  fun notifyTradeLevelHit(
    context: Context,
    signalId: String,
    symbol: String,
    timeframe: String,
    direction: String,
    hitType: String,
    priceFormatted: String,
  ): Boolean {
    val dedupKey = "hit_${signalId}_$hitType"
    val now = System.currentTimeMillis()
    val lastSent = recentlyNotified[dedupKey]
    if (lastSent != null && (now - lastSent) < 30 * 60 * 1000L) {
      return false
    }
    recentlyNotified[dedupKey] = now

    val title = "$hitType Triggered • $direction $symbol ($timeframe)"
    val body =
      if (hitType == "SL_HIT") {
        "Stop-Loss level reached near $priceFormatted. Review position or close in Signal Journal."
      } else {
        "Take-Profit target reached near $priceFormatted. Lock in profits in Signal Journal."
      }
    return showSignalNotification(
      context = context,
      title = title,
      body = body,
      symbol = symbol,
      timeframe = timeframe,
      notificationId = dedupKey.hashCode(),
    )
  }

  fun showSignalNotification(
    context: Context,
    title: String,
    body: String,
    symbol: String,
    timeframe: String,
    notificationId: Int = (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
  ): Boolean {
    ensureChannel(context)
    if (!hasNotificationPermission(context)) return false

    val intent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_SYMBOL, symbol)
        putExtra(EXTRA_TIMEFRAME, timeframe)
      }
    val pendingIntent =
      PendingIntent.getActivity(
        context,
        notificationId,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val notification =
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()

    NotificationManagerCompat.from(context).notify(notificationId, notification)
    return true
  }
}
