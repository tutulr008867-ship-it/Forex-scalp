package com.example.domain.engine

import com.example.domain.model.Candle
import com.example.domain.model.IndicatorSummary
import com.example.domain.model.MarketPair
import com.example.domain.model.PriceZone
import com.example.domain.model.SignalStrength
import com.example.domain.model.SmcAnalysis
import com.example.domain.model.SmcEventType
import com.example.domain.model.SweepMarker
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Smart Money Concepts (SMC) Engine:
 * 1. Fractal Swing Highs (Buy-Side Liquidity / BSL) & Swing Lows (Sell-Side Liquidity / SSL)
 * 2. Liquidity Sweep Detection (Stop-hunt wick beyond prior BSL/SSL with rejection close inside)
 * 3. Change of Character (CHoCH) & Break of Structure (BOS)
 * 4. Bullish/Bearish Fair Value Gaps (FVG) & Order Blocks (OB)
 * 5. Precision Entry, ATR-buffered Stop-Loss, TP1/TP2/TP3, and Risk-Reward Ratio
 */
object SmcEngine {

  fun analyze(
    pair: MarketPair,
    candles: List<Candle>,
    indicators: IndicatorSummary,
  ): Pair<SmcAnalysis, SignalStrength> {
    if (candles.size < 20) {
      val price = candles.lastOrNull()?.close ?: 1.0
      val fallback =
        SmcAnalysis(
          eventType = SmcEventType.CONSOLIDATION,
          direction = if (indicators.buyCount >= indicators.sellCount) "BUY" else "SELL",
          entryPrice = price,
          stopLoss = price * 0.995,
          takeProfit1 = price * 1.007,
          takeProfit2 = price * 1.012,
          takeProfit3 = price * 1.018,
          sweptLiquidityLevel = price,
          riskRewardRatio = 2.4,
          sweepDetected = false,
          sweepMarker = null,
          orderBlocks = emptyList(),
          fairValueGaps = emptyList(),
          swingHighs = emptyList(),
          swingLows = emptyList(),
          marketBias = "NEUTRAL",
          structureNotes = "Insufficient bars for structural sweep detection.",
        )
      return fallback to indicators.strength
    }

    val atr = computeAtr(candles, 14)
    val lastIdx = candles.lastIndex
    val lastPrice = candles.last().close

    // 1. Detect 3-bar left/right fractal swing highs & lows
    val swingHighs = mutableListOf<Pair<Int, Double>>()
    val swingLows = mutableListOf<Pair<Int, Double>>()
    for (i in 3 until candles.size - 2) {
      val h = candles[i].high
      val l = candles[i].low
      val isHigh =
        h >= candles[i - 1].high &&
          h >= candles[i - 2].high &&
          h > candles[i - 3].high &&
          h >= candles[i + 1].high &&
          h > candles[i + 2].high
      val isLow =
        l <= candles[i - 1].low &&
          l <= candles[i - 2].low &&
          l < candles[i - 3].low &&
          l <= candles[i + 1].low &&
          l < candles[i + 2].low
      if (isHigh) swingHighs += i to h
      if (isLow) swingLows += i to l
    }

    // 2. Detect Fair Value Gaps (3-candle imbalances) in recent 45 bars
    val fvgList = mutableListOf<PriceZone>()
    val startScan = max(2, candles.size - 45)
    for (i in startScan until candles.size) {
      val cPrev = candles[i - 2]
      val cNext = candles[i]
      if (cNext.low > cPrev.high && (cNext.low - cPrev.high) > atr * 0.12) {
        fvgList +=
          PriceZone(
            startIndex = i - 2,
            endIndex = lastIdx,
            top = cNext.low,
            bottom = cPrev.high,
            isBullish = true,
            label = "Bullish FVG",
          )
      } else if (cNext.high < cPrev.low && (cPrev.low - cNext.high) > atr * 0.12) {
        fvgList +=
          PriceZone(
            startIndex = i - 2,
            endIndex = lastIdx,
            top = cPrev.low,
            bottom = cNext.high,
            isBullish = false,
            label = "Bearish FVG",
          )
      }
    }

    // 3. Detect Order Blocks (last opposite candle before impulsive displacement)
    val orderBlocks = mutableListOf<PriceZone>()
    for (i in startScan until candles.size - 1) {
      val c = candles[i]
      val next = candles[i + 1]
      val bodyNext = abs(next.close - next.open)
      if (c.close < c.open && next.close > next.open && bodyNext > atr * 0.75) {
        orderBlocks +=
          PriceZone(
            startIndex = i,
            endIndex = lastIdx,
            top = c.high,
            bottom = c.low,
            isBullish = true,
            label = "Bullish OB",
          )
      } else if (c.close > c.open && next.close < next.open && bodyNext > atr * 0.75) {
        orderBlocks +=
          PriceZone(
            startIndex = i,
            endIndex = lastIdx,
            top = c.high,
            bottom = c.low,
            isBullish = false,
            label = "Bearish OB",
          )
      }
    }

    // 4. Detect most recent Liquidity Sweep (SSL or BSL) within recent 18 bars
    val recentWindowStart = max(5, candles.size - 18)
    var latestBullSweep: SweepMarker? = null
    var latestBearSweep: SweepMarker? = null

    for (i in recentWindowStart until candles.size) {
      val c = candles[i]
      val priorLows = swingLows.filter { it.first < i - 1 }.takeLast(4)
      val priorHighs = swingHighs.filter { it.first < i - 1 }.takeLast(4)

      // SSL Sweep: wick dips below prior swing low, closes back above (or next bar reclaims)
      val sweptLow =
        priorLows.lastOrNull { (_, level) ->
          c.low < level && (c.close >= level || (i + 1 < candles.size && candles[i + 1].close > level))
        }
      if (sweptLow != null) {
        latestBullSweep =
          SweepMarker(
            candleIndex = i,
            sweptLevel = sweptLow.second,
            sweepExtreme = c.low,
            isBullish = true,
            label = "SSL SWEEP",
          )
      }

      // BSL Sweep: wick spikes above prior swing high, closes back below (or next bar rejects)
      val sweptHigh =
        priorHighs.lastOrNull { (_, level) ->
          c.high > level && (c.close <= level || (i + 1 < candles.size && candles[i + 1].close < level))
        }
      if (sweptHigh != null) {
        latestBearSweep =
          SweepMarker(
            candleIndex = i,
            sweptLevel = sweptHigh.second,
            sweepExtreme = c.high,
            isBullish = false,
            label = "BSL SWEEP",
          )
      }
    }

    // Determine dominant SMC setup
    val activeSweep =
      when {
        latestBullSweep != null && latestBearSweep != null ->
          if (latestBullSweep.candleIndex >= latestBearSweep.candleIndex) latestBullSweep
          else latestBearSweep
        latestBullSweep != null -> latestBullSweep
        latestBearSweep != null -> latestBearSweep
        else -> null
      }

    // Check recent Break of Structure / CHoCH
    val latestSwingHigh = swingHighs.lastOrNull()?.second ?: (candles.takeLast(12).maxOf { it.high })
    val latestSwingLow = swingLows.lastOrNull()?.second ?: (candles.takeLast(12).minOf { it.low })
    val bullishStructureBreak = lastPrice > latestSwingHigh
    val bearishStructureBreak = lastPrice < latestSwingLow

    val isBullishDirection: Boolean
    val eventType: SmcEventType
    val sweepDetected: Boolean
    val sweptLevel: Double
    val stopLoss: Double

    if (activeSweep != null && activeSweep.isBullish) {
      isBullishDirection = true
      sweepDetected = true
      sweptLevel = activeSweep.sweptLevel
      eventType =
        if (bullishStructureBreak || indicators.buyCount >= indicators.sellCount) {
          SmcEventType.SSL_SWEEP_CHOCH
        } else {
          SmcEventType.SSL_SWEEP_BOS
        }
      val minSweepLow = min(activeSweep.sweepExtreme, candles.takeLast(6).minOf { it.low })
      stopLoss = min(minSweepLow - atr * 0.35, lastPrice - atr * 1.1)
    } else if (activeSweep != null && !activeSweep.isBullish) {
      isBullishDirection = false
      sweepDetected = true
      sweptLevel = activeSweep.sweptLevel
      eventType =
        if (bearishStructureBreak || indicators.sellCount >= indicators.buyCount) {
          SmcEventType.BSL_SWEEP_CHOCH
        } else {
          SmcEventType.BSL_SWEEP_BOS
        }
      val maxSweepHigh = max(activeSweep.sweepExtreme, candles.takeLast(6).maxOf { it.high })
      stopLoss = max(maxSweepHigh + atr * 0.35, lastPrice + atr * 1.1)
    } else {
      sweepDetected = false
      isBullishDirection = indicators.buyCount >= indicators.sellCount
      if (isBullishDirection) {
        val recentBullZone = fvgList.lastOrNull { it.isBullish } ?: orderBlocks.lastOrNull { it.isBullish }
        eventType =
          if (recentBullZone != null) SmcEventType.BULLISH_FVG_OB else SmcEventType.CONSOLIDATION
        sweptLevel = latestSwingLow
        stopLoss = min(latestSwingLow - atr * 0.35, lastPrice - atr * 1.3)
      } else {
        val recentBearZone = fvgList.lastOrNull { !it.isBullish } ?: orderBlocks.lastOrNull { !it.isBullish }
        eventType =
          if (recentBearZone != null) SmcEventType.BEARISH_FVG_OB else SmcEventType.CONSOLIDATION
        sweptLevel = latestSwingHigh
        stopLoss = max(latestSwingHigh + atr * 0.35, lastPrice + atr * 1.3)
      }
    }

    val direction = if (isBullishDirection) "BUY" else "SELL"
    val risk = max(abs(lastPrice - stopLoss), atr * 0.6)
    val tp1 = if (isBullishDirection) lastPrice + risk * 1.5 else lastPrice - risk * 1.5
    val tp2 = if (isBullishDirection) lastPrice + risk * 2.5 else lastPrice - risk * 2.5
    val tp3 = if (isBullishDirection) lastPrice + risk * 3.8 else lastPrice - risk * 3.8
    val rr = if (risk > 0.0) abs(tp2 - lastPrice) / risk else 2.5

    // Combine SMC Liquidity Sweep + 26 TradingView Indicators into overall strength
    val combinedStrength =
      when {
        isBullishDirection && sweepDetected && indicators.buyCount >= 14 -> SignalStrength.STRONG_BUY
        isBullishDirection && (sweepDetected || indicators.buyCount >= 13) -> SignalStrength.BUY
        !isBullishDirection && sweepDetected && indicators.sellCount >= 14 ->
          SignalStrength.STRONG_SELL
        !isBullishDirection && (sweepDetected || indicators.sellCount >= 13) -> SignalStrength.SELL
        else -> indicators.strength
      }

    val notes = buildString {
      if (sweepDetected) {
        append(eventType.label)
        append(" @ ")
        append(pair.formatPrice(sweptLevel))
        append(" • ")
      } else {
        append(eventType.label)
        append(" • ")
      }
      val aligned = if (isBullishDirection) indicators.buyCount else indicators.sellCount
      append("$aligned/26 TV indicators aligned ($direction)")
      if (fvgList.isNotEmpty()) append(" • ${fvgList.size} FVG")
      if (orderBlocks.isNotEmpty()) append(" • ${orderBlocks.size} OB")
    }

    val analysis =
      SmcAnalysis(
        eventType = eventType,
        direction = direction,
        entryPrice = lastPrice,
        stopLoss = stopLoss,
        takeProfit1 = tp1,
        takeProfit2 = tp2,
        takeProfit3 = tp3,
        sweptLiquidityLevel = sweptLevel,
        riskRewardRatio = rr,
        sweepDetected = sweepDetected,
        sweepMarker = activeSweep,
        orderBlocks = orderBlocks.takeLast(3),
        fairValueGaps = fvgList.takeLast(4),
        swingHighs = swingHighs.takeLast(6),
        swingLows = swingLows.takeLast(6),
        marketBias = if (isBullishDirection) "BULLISH" else "BEARISH",
        structureNotes = notes,
      )

    return analysis to combinedStrength
  }

  private fun computeAtr(candles: List<Candle>, period: Int): Double {
    if (candles.size < 2) return 0.0
    val trs = mutableListOf<Double>()
    for (i in 1 until candles.size) {
      val c = candles[i]
      val prevClose = candles[i - 1].close
      trs += max(c.high - c.low, max(abs(c.high - prevClose), abs(c.low - prevClose)))
    }
    return trs.takeLast(min(period, trs.size)).average()
  }
}
