package com.example.domain.engine

import com.example.domain.model.Candle
import com.example.domain.model.IndicatorCategory
import com.example.domain.model.MarketPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmcAndIndicatorsEngineTest {

  @Test
  fun evaluate26Indicators_returnsExact11OscillatorsAnd15MovingAverages() {
    val candles =
      (0 until 80).map { i ->
        val base = 2600.0 + i * 0.8
        Candle(
          timestampMs = i * 60_000L,
          open = base - 0.3,
          high = base + 1.2,
          low = base - 1.0,
          close = base + 0.5,
          volume = 1200.0 + i * 10.0,
        )
      }

    val summary = TradingViewIndicatorsEngine.evaluate(candles, decimals = 2)
    assertEquals(26, summary.readings.size)
    assertEquals(11, summary.readings.count { it.category == IndicatorCategory.OSCILLATOR })
    assertEquals(15, summary.readings.count { it.category == IndicatorCategory.MOVING_AVERAGE })
    assertEquals(26, summary.buyCount + summary.neutralCount + summary.sellCount)
    assertTrue("Expected bullish majority in uptrend", summary.buyCount > summary.sellCount)
  }

  @Test
  fun smcEngine_detectsSellSideLiquiditySweepAndBullishSetup() {
    val candles = mutableListOf<Candle>()
    // Build prior price action with a clear swing low around index 10 at 100.0
    for (i in 0 until 35) {
      val price =
        when {
          i == 10 -> 100.0
          i in 7..13 -> 102.0 - (3 - kotlin.math.abs(i - 10)) * 0.5
          i == 31 -> 101.2 // Sweep bar below 100.0, reclaiming above 100.0
          i > 31 -> 102.5 + (i - 31) * 0.6
          else -> 103.5 + (i % 4) * 0.4
        }
      val low =
        when (i) {
          10 -> 100.0
          31 -> 99.1 // Wicks below 100.0 SSL
          else -> price - 0.5
        }
      val close =
        when (i) {
          31 -> 101.6 // Reclaims above 100.0
          else -> price + 0.2
        }
      candles +=
        Candle(
          timestampMs = i * 900_000L,
          open = price,
          high = price + 0.8,
          low = low,
          close = close,
          volume = 1500.0,
        )
    }

    val ind = TradingViewIndicatorsEngine.evaluate(candles, decimals = 2)
    val (smc, _) = SmcEngine.analyze(MarketPair.XAUUSD, candles, ind)

    assertTrue("Expected SSL liquidity sweep detection", smc.sweepDetected)
    assertEquals("BUY", smc.direction)
    assertTrue("SL must be below entry", smc.stopLoss < smc.entryPrice)
    assertTrue("TP1 must be above entry", smc.takeProfit1 > smc.entryPrice)
    assertTrue("TP2 must be above TP1", smc.takeProfit2 > smc.takeProfit1)
  }

  @Test
  fun smcEngine_detectsBuySideLiquiditySweepAndBearishSetup() {
    val candles = mutableListOf<Candle>()
    for (i in 0 until 35) {
      val price =
        when {
          i == 10 -> 110.0
          i in 7..13 -> 107.5 + (3 - kotlin.math.abs(i - 10)) * 0.6
          i == 31 -> 109.2
          i > 31 -> 105.5 - (i - 31) * 1.4
          else -> 106.5 - (i % 4) * 0.2
        }
      val high =
        when (i) {
          10 -> 110.0
          31 -> 111.1 // Wicks above 110.0 BSL
          else -> price + 0.5
        }
      val close =
        when (i) {
          31 -> 108.9 // Rejects below 110.0
          in 32..34 -> price - 0.5
          else -> price - 0.1
        }
      candles +=
        Candle(
          timestampMs = i * 900_000L,
          open = price,
          high = high,
          low = price - 0.7,
          close = close,
          volume = 1500.0,
        )
    }

    val ind = TradingViewIndicatorsEngine.evaluate(candles, decimals = 2)
    val (smc, _) = SmcEngine.analyze(MarketPair.XAUUSD, candles, ind)

    assertTrue("Expected BSL liquidity sweep detection", smc.sweepDetected)
    assertEquals("SELL", smc.direction)
    assertTrue("SL must be above entry", smc.stopLoss > smc.entryPrice)
    assertTrue("TP1 must be below entry", smc.takeProfit1 < smc.entryPrice)
  }
}
