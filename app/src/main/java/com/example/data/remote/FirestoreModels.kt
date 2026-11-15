package com.example.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class TradingSignalRecord(
  @DocumentId val id: String = "",
  val userId: String = "",
  val symbol: String = "",
  val timeframe: String = "15m",
  val direction: String = "BUY",
  val strength: String = "BUY",
  val smcEvent: String = "",
  val entryPrice: Double = 0.0,
  val stopLoss: Double = 0.0,
  val takeProfit1: Double = 0.0,
  val takeProfit2: Double = 0.0,
  val takeProfit3: Double = 0.0,
  val sweptLiquidityLevel: Double = 0.0,
  val riskRewardRatio: Double = 0.0,
  val buyIndicators: Long = 0L,
  val sellIndicators: Long = 0L,
  val neutralIndicators: Long = 0L,
  val status: String = "ACTIVE",
  val notes: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val isBuy: Boolean
    get() = direction.equals("BUY", ignoreCase = true)

  val alignedCount: Int
    get() = (if (isBuy) buyIndicators else sellIndicators).toInt()
}

@IgnoreExtraProperties
data class AlertConfigRecord(
  @DocumentId val id: String = "",
  val userId: String = "",
  val symbol: String = "ALL",
  val enabled: Boolean = true,
  val tf1m: Boolean = true,
  val tf5m: Boolean = true,
  val tf15m: Boolean = true,
  val tf30m: Boolean = true,
  val minConfluence: Long = 15L,
  val requireLiquiditySweep: Boolean = true,
  val fcmToken: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun isTimeframeEnabled(tfLabel: String): Boolean =
    when (tfLabel) {
      "1m" -> tf1m
      "5m" -> tf5m
      "15m" -> tf15m
      "30m" -> tf30m
      else -> true
    }
}
