package com.example.domain.engine

import com.example.domain.model.Candle
import com.example.domain.model.IndicatorCategory
import com.example.domain.model.IndicatorReading
import com.example.domain.model.IndicatorSummary
import com.example.domain.model.SignalStrength
import com.example.domain.model.SignalVote
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Computes all 26 TradingView Technical Analysis indicators:
 * - 11 Oscillators: RSI(14), Stoch %K(14,3,3), CCI(20), ADX(14), AO, Momentum(10),
 *   MACD(12,26,9), Stoch RSI Fast(3,3,14,14), Williams %R(14), Bull Bear Power(13), UO(7,14,28)
 * - 15 Moving Averages: EMA(10,20,30,50,100,200), SMA(10,20,30,50,100,200),
 *   Ichimoku Base Line(9,26,52,26), VWMA(20), Hull MA(9)
 */
object TradingViewIndicatorsEngine {

  fun evaluate(candles: List<Candle>, decimals: Int = 2): IndicatorSummary {
    if (candles.size < 30) {
      val fallback =
        (1..26).map { idx ->
          IndicatorReading(
            index = idx,
            name = "Indicator $idx",
            category =
              if (idx <= 11) IndicatorCategory.OSCILLATOR else IndicatorCategory.MOVING_AVERAGE,
            formattedValue = "—",
            rawValue = 0.0,
            vote = SignalVote.NEUTRAL,
          )
        }
      return IndicatorSummary(
        readings = fallback,
        buyCount = 0,
        neutralCount = 26,
        sellCount = 0,
        oscillatorBuy = 0,
        oscillatorNeutral = 11,
        oscillatorSell = 0,
        maBuy = 0,
        maNeutral = 15,
        maSell = 0,
        strength = SignalStrength.NEUTRAL,
      )
    }

    val closes = candles.map { it.close }
    val highs = candles.map { it.high }
    val lows = candles.map { it.low }
    val volumes = candles.map { max(it.volume, 1.0) }
    val lastClose = closes.last()
    val prevClose = closes[closes.size - 2]

    val readings = mutableListOf<IndicatorReading>()

    // --- 11 OSCILLATORS ---

    // 1. RSI (14)
    val rsiSeries = rsiSeries(closes, 14)
    val rsi = rsiSeries.lastOrNull() ?: 50.0
    val prevRsi = rsiSeries.getOrElse(rsiSeries.size - 2) { rsi }
    val rsiVote =
      when {
        rsi < 30.0 && rsi > prevRsi -> SignalVote.BUY
        rsi > 70.0 && rsi < prevRsi -> SignalVote.SELL
        rsi <= 35.0 -> SignalVote.BUY
        rsi >= 65.0 -> SignalVote.SELL
        else -> SignalVote.NEUTRAL
      }
    readings += osc(1, "RSI (14)", rsi, "%.2f".format(rsi), rsiVote)

    // 2. Stochastic %K (14, 3, 3)
    val (stochK, stochD, prevK, prevD) = stochastic(highs, lows, closes, 14, 3, 3)
    val stochVote =
      when {
        stochK < 20.0 && stochK > stochD && stochK > prevK -> SignalVote.BUY
        stochK > 80.0 && stochK < stochD && stochK < prevK -> SignalVote.SELL
        stochK < 25.0 && prevK <= prevD && stochK > stochD -> SignalVote.BUY
        stochK > 75.0 && prevK >= prevD && stochK < stochD -> SignalVote.SELL
        else -> SignalVote.NEUTRAL
      }
    readings += osc(2, "Stochastic %K (14,3,3)", stochK, "%.2f".format(stochK), stochVote)

    // 3. CCI (20)
    val cciSeries = cciSeries(highs, lows, closes, 20)
    val cci = cciSeries.lastOrNull() ?: 0.0
    val prevCci = cciSeries.getOrElse(cciSeries.size - 2) { cci }
    val cciVote =
      when {
        cci < -100.0 && cci > prevCci -> SignalVote.BUY
        cci > 100.0 && cci < prevCci -> SignalVote.SELL
        else -> SignalVote.NEUTRAL
      }
    readings += osc(3, "CCI (20)", cci, "%.2f".format(cci), cciVote)

    // 4. ADX (14)
    val (adx, plusDi, minusDi) = adx(highs, lows, closes, 14)
    val adxVote =
      when {
        adx > 20.0 && plusDi > minusDi -> SignalVote.BUY
        adx > 20.0 && minusDi > plusDi -> SignalVote.SELL
        else -> SignalVote.NEUTRAL
      }
    readings += osc(4, "ADX (14)", adx, "%.2f".format(adx), adxVote)

    // 5. Awesome Oscillator
    val medians = candles.map { (it.high + it.low) / 2.0 }
    val ao5 = smaSeries(medians, 5)
    val ao34 = smaSeries(medians, 34)
    val ao = (ao5.lastOrNull() ?: 0.0) - (ao34.lastOrNull() ?: 0.0)
    val prevAo = (ao5.getOrElse(ao5.size - 2) { 0.0 }) - (ao34.getOrElse(ao34.size - 2) { 0.0 })
    val aoVote =
      when {
        ao > 0.0 && ao > prevAo -> SignalVote.BUY
        ao < 0.0 && ao < prevAo -> SignalVote.SELL
        else -> SignalVote.NEUTRAL
      }
    readings += osc(5, "Awesome Oscillator", ao, formatOsc(ao, decimals), aoVote)

    // 6. Momentum (10)
    val mom = if (closes.size > 10) lastClose - closes[closes.size - 11] else 0.0
    val prevMom = if (closes.size > 11) prevClose - closes[closes.size - 12] else mom
    val momVote =
      when {
        mom > 0.0 && mom >= prevMom -> SignalVote.BUY
        mom < 0.0 && mom <= prevMom -> SignalVote.SELL
        else -> SignalVote.NEUTRAL
      }
    readings += osc(6, "Momentum (10)", mom, formatOsc(mom, decimals), momVote)

    // 7. MACD Level (12, 26)
    val ema12 = emaSeries(closes, 12)
    val ema26 = emaSeries(closes, 26)
    val macdLine = ema12.zip(ema26) { a, b -> a - b }
    val macdSignal = emaSeries(macdLine, 9)
    val macdVal = macdLine.lastOrNull() ?: 0.0
    val macdSigVal = macdSignal.lastOrNull() ?: 0.0
    val macdVote =
      when {
        macdVal > macdSigVal -> SignalVote.BUY
        macdVal < macdSigVal -> SignalVote.SELL
        else -> SignalVote.NEUTRAL
      }
    readings += osc(7, "MACD Level (12,26)", macdVal, formatOsc(macdVal, decimals), macdVote)

    // 8. Stochastic RSI Fast (3, 3, 14, 14)
    val stochRsi = stochRsiK(rsiSeries, 14, 3)
    val stochRsiVote =
      when {
        stochRsi < 20.0 -> SignalVote.BUY
        stochRsi > 80.0 -> SignalVote.SELL
        else -> SignalVote.NEUTRAL
      }
    readings += osc(8, "Stochastic RSI Fast (3,3,14,14)", stochRsi, "%.2f".format(stochRsi), stochRsiVote)

    // 9. Williams Percent Range (14)
    val wr = williamsR(highs, lows, closes, 14)
    val wrVote =
      when {
        wr < -80.0 -> SignalVote.BUY
        wr > -20.0 -> SignalVote.SELL
        else -> SignalVote.NEUTRAL
      }
    readings += osc(9, "Williams %R (14)", wr, "%.2f".format(wr), wrVote)

    // 10. Bull Bear Power (13)
    val ema13 = emaSeries(closes, 13).lastOrNull() ?: lastClose
    val bullPower = highs.last() - ema13
    val bearPower = lows.last() - ema13
    val bbp = bullPower + bearPower
    val bbpVote =
      when {
        bbp > 0.0 && bullPower > 0.0 -> SignalVote.BUY
        bbp < 0.0 && bearPower < 0.0 -> SignalVote.SELL
        else -> SignalVote.NEUTRAL
      }
    readings += osc(10, "Bull Bear Power (13)", bbp, formatOsc(bbp, decimals), bbpVote)

    // 11. Ultimate Oscillator (7, 14, 28)
    val uo = ultimateOscillator(highs, lows, closes)
    val uoVote =
      when {
        uo > 70.0 -> SignalVote.SELL
        uo < 30.0 -> SignalVote.BUY
        uo > 55.0 -> SignalVote.BUY
        uo < 45.0 -> SignalVote.SELL
        else -> SignalVote.NEUTRAL
      }
    readings += osc(11, "Ultimate Oscillator (7,14,28)", uo, "%.2f".format(uo), uoVote)

    // --- 15 MOVING AVERAGES ---
    var idx = 12
    val periods = listOf(10, 20, 30, 50, 100, 200)
    for (p in periods) {
      val emaVal = emaSeries(closes, min(p, closes.size)).lastOrNull() ?: lastClose
      readings += ma(idx++, "EMA ($p)", emaVal, decimals, lastClose)

      val smaVal = smaLast(closes, min(p, closes.size))
      readings += ma(idx++, "SMA ($p)", smaVal, decimals, lastClose)
    }

    // 24. Ichimoku Base Line (Kijun-sen 26)
    val kijunPeriod = min(26, closes.size)
    val kijunHigh = highs.takeLast(kijunPeriod).maxOrNull() ?: lastClose
    val kijunLow = lows.takeLast(kijunPeriod).minOrNull() ?: lastClose
    val kijun = (kijunHigh + kijunLow) / 2.0
    readings += ma(idx++, "Ichimoku Base Line (9,26,52,26)", kijun, decimals, lastClose)

    // 25. Volume Weighted Moving Average (20)
    val vwmaVal = vwmaLast(closes, volumes, min(20, closes.size))
    readings += ma(idx++, "VWMA (20)", vwmaVal, decimals, lastClose)

    // 26. Hull Moving Average (9)
    val hmaVal = hullMaLast(closes, min(9, closes.size))
    readings += ma(idx, "Hull Moving Average (9)", hmaVal, decimals, lastClose)

    val oscillators = readings.filter { it.category == IndicatorCategory.OSCILLATOR }
    val movingAverages = readings.filter { it.category == IndicatorCategory.MOVING_AVERAGE }

    val oscBuy = oscillators.count { it.vote == SignalVote.BUY }
    val oscNeutral = oscillators.count { it.vote == SignalVote.NEUTRAL }
    val oscSell = oscillators.count { it.vote == SignalVote.SELL }

    val maBuy = movingAverages.count { it.vote == SignalVote.BUY }
    val maNeutral = movingAverages.count { it.vote == SignalVote.NEUTRAL }
    val maSell = movingAverages.count { it.vote == SignalVote.SELL }

    val totalBuy = oscBuy + maBuy
    val totalNeutral = oscNeutral + maNeutral
    val totalSell = oscSell + maSell

    val strength = classifyStrength(totalBuy, totalSell)

    return IndicatorSummary(
      readings = readings,
      buyCount = totalBuy,
      neutralCount = totalNeutral,
      sellCount = totalSell,
      oscillatorBuy = oscBuy,
      oscillatorNeutral = oscNeutral,
      oscillatorSell = oscSell,
      maBuy = maBuy,
      maNeutral = maNeutral,
      maSell = maSell,
      strength = strength,
    )
  }

  private fun classifyStrength(buyCount: Int, sellCount: Int): SignalStrength {
    val net = buyCount - sellCount
    return when {
      net >= 10 -> SignalStrength.STRONG_BUY
      net >= 3 -> SignalStrength.BUY
      net <= -10 -> SignalStrength.STRONG_SELL
      net <= -3 -> SignalStrength.SELL
      else -> SignalStrength.NEUTRAL
    }
  }

  private fun osc(
    idx: Int,
    name: String,
    raw: Double,
    formatted: String,
    vote: SignalVote,
  ): IndicatorReading =
    IndicatorReading(
      index = idx,
      name = name,
      category = IndicatorCategory.OSCILLATOR,
      formattedValue = formatted,
      rawValue = raw,
      vote = vote,
    )

  private fun ma(
    idx: Int,
    name: String,
    maVal: Double,
    decimals: Int,
    lastClose: Double,
  ): IndicatorReading {
    val tolerance = lastClose * 0.00015
    val vote =
      when {
        lastClose > maVal + tolerance -> SignalVote.BUY
        lastClose < maVal - tolerance -> SignalVote.SELL
        else -> SignalVote.NEUTRAL
      }
    return IndicatorReading(
      index = idx,
      name = name,
      category = IndicatorCategory.MOVING_AVERAGE,
      formattedValue = "%.${decimals}f".format(maVal),
      rawValue = maVal,
      vote = vote,
    )
  }

  private fun formatOsc(value: Double, decimals: Int): String =
    "%.${min(decimals + 1, 5)}f".format(value)

  private fun smaLast(values: List<Double>, period: Int): Double {
    val n = min(period, values.size)
    if (n <= 0) return 0.0
    return values.takeLast(n).average()
  }

  private fun smaSeries(values: List<Double>, period: Int): List<Double> {
    if (values.isEmpty()) return emptyList()
    val result = MutableList(values.size) { 0.0 }
    var sum = 0.0
    for (i in values.indices) {
      sum += values[i]
      if (i >= period) sum -= values[i - period]
      val count = min(i + 1, period)
      result[i] = sum / count
    }
    return result
  }

  private fun emaSeries(values: List<Double>, period: Int): List<Double> {
    if (values.isEmpty()) return emptyList()
    val p = max(1, period)
    val k = 2.0 / (p + 1.0)
    val result = MutableList(values.size) { values.first() }
    var ema = values.first()
    for (i in values.indices) {
      ema = if (i == 0) values[0] else (values[i] * k) + (ema * (1.0 - k))
      result[i] = ema
    }
    return result
  }

  private fun wmaLast(values: List<Double>, period: Int): Double {
    val n = min(period, values.size)
    if (n <= 0) return 0.0
    val slice = values.takeLast(n)
    var num = 0.0
    var den = 0.0
    for (i in slice.indices) {
      val w = (i + 1).toDouble()
      num += slice[i] * w
      den += w
    }
    return if (den == 0.0) slice.last() else num / den
  }

  private fun hullMaLast(values: List<Double>, period: Int): Double {
    val p = max(2, period)
    val half = max(1, p / 2)
    val sqrtP = max(1, sqrt(p.toDouble()).roundToInt())
    if (values.size < p + sqrtP) return wmaLast(values, p)
    val diffSeries = mutableListOf<Double>()
    for (end in p..values.size) {
      val sub = values.subList(0, end)
      diffSeries += 2.0 * wmaLast(sub, half) - wmaLast(sub, p)
    }
    return wmaLast(diffSeries, sqrtP)
  }

  private fun vwmaLast(closes: List<Double>, volumes: List<Double>, period: Int): Double {
    val n = min(period, closes.size)
    if (n <= 0) return 0.0
    var pv = 0.0
    var v = 0.0
    val start = closes.size - n
    for (i in start until closes.size) {
      pv += closes[i] * volumes[i]
      v += volumes[i]
    }
    return if (v <= 0.0) closes.last() else pv / v
  }

  private fun rsiSeries(closes: List<Double>, period: Int): List<Double> {
    if (closes.size < 2) return listOf(50.0)
    val gains = MutableList(closes.size) { 0.0 }
    val losses = MutableList(closes.size) { 0.0 }
    for (i in 1 until closes.size) {
      val diff = closes[i] - closes[i - 1]
      gains[i] = if (diff > 0) diff else 0.0
      losses[i] = if (diff < 0) -diff else 0.0
    }
    val rsi = MutableList(closes.size) { 50.0 }
    var avgGain = gains.take(min(period + 1, gains.size)).average()
    var avgLoss = losses.take(min(period + 1, losses.size)).average()
    for (i in 1 until closes.size) {
      if (i > period) {
        avgGain = (avgGain * (period - 1) + gains[i]) / period
        avgLoss = (avgLoss * (period - 1) + losses[i]) / period
      }
      val rs = if (avgLoss == 0.0) 100.0 else avgGain / avgLoss
      rsi[i] = if (avgLoss == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + rs))
    }
    return rsi
  }

  private data class StochResult(val k: Double, val d: Double, val prevK: Double, val prevD: Double)

  private fun stochastic(
    highs: List<Double>,
    lows: List<Double>,
    closes: List<Double>,
    kPeriod: Int,
    smoothK: Int,
    dPeriod: Int,
  ): StochResult {
    val rawK = mutableListOf<Double>()
    for (i in closes.indices) {
      val start = max(0, i - kPeriod + 1)
      val hh = highs.subList(start, i + 1).maxOrNull() ?: highs[i]
      val ll = lows.subList(start, i + 1).minOrNull() ?: lows[i]
      val range = hh - ll
      rawK += if (range <= 1e-12) 50.0 else ((closes[i] - ll) / range) * 100.0
    }
    val kSeries = smaSeries(rawK, smoothK)
    val dSeries = smaSeries(kSeries, dPeriod)
    val k = kSeries.lastOrNull() ?: 50.0
    val d = dSeries.lastOrNull() ?: 50.0
    val pk = kSeries.getOrElse(kSeries.size - 2) { k }
    val pd = dSeries.getOrElse(dSeries.size - 2) { d }
    return StochResult(k, d, pk, pd)
  }

  private fun cciSeries(
    highs: List<Double>,
    lows: List<Double>,
    closes: List<Double>,
    period: Int,
  ): List<Double> {
    val tp = closes.indices.map { (highs[it] + lows[it] + closes[it]) / 3.0 }
    val smaTp = smaSeries(tp, period)
    return tp.indices.map { i ->
      val start = max(0, i - period + 1)
      val mean = smaTp[i]
      val meanDev = tp.subList(start, i + 1).map { abs(it - mean) }.average()
      if (meanDev <= 1e-12) 0.0 else (tp[i] - mean) / (0.015 * meanDev)
    }
  }

  private fun adx(
    highs: List<Double>,
    lows: List<Double>,
    closes: List<Double>,
    period: Int,
  ): Triple<Double, Double, Double> {
    if (closes.size < period + 2) return Triple(18.0, 20.0, 20.0)
    val tr = MutableList(closes.size) { 0.0 }
    val plusDm = MutableList(closes.size) { 0.0 }
    val minusDm = MutableList(closes.size) { 0.0 }
    for (i in 1 until closes.size) {
      val upMove = highs[i] - highs[i - 1]
      val downMove = lows[i - 1] - lows[i]
      plusDm[i] = if (upMove > downMove && upMove > 0) upMove else 0.0
      minusDm[i] = if (downMove > upMove && downMove > 0) downMove else 0.0
      tr[i] =
        max(
          highs[i] - lows[i],
          max(abs(highs[i] - closes[i - 1]), abs(lows[i] - closes[i - 1])),
        )
    }
    val atr = emaSeries(tr, period)
    val smoothPlus = emaSeries(plusDm, period)
    val smoothMinus = emaSeries(minusDm, period)
    val dxList =
      closes.indices.map { i ->
        val a = atr[i]
        if (a <= 1e-12) 0.0
        else {
          val pdi = 100.0 * smoothPlus[i] / a
          val mdi = 100.0 * smoothMinus[i] / a
          val sum = pdi + mdi
          if (sum <= 1e-12) 0.0 else 100.0 * abs(pdi - mdi) / sum
        }
      }
    val adxVal = emaSeries(dxList, period).lastOrNull() ?: 18.0
    val lastAtr = max(atr.lastOrNull() ?: 1.0, 1e-12)
    val pdi = 100.0 * (smoothPlus.lastOrNull() ?: 0.0) / lastAtr
    val mdi = 100.0 * (smoothMinus.lastOrNull() ?: 0.0) / lastAtr
    return Triple(adxVal, pdi, mdi)
  }

  private fun stochRsiK(rsiSeries: List<Double>, period: Int, smoothK: Int): Double {
    if (rsiSeries.isEmpty()) return 50.0
    val raw = mutableListOf<Double>()
    for (i in rsiSeries.indices) {
      val start = max(0, i - period + 1)
      val sub = rsiSeries.subList(start, i + 1)
      val hi = sub.maxOrNull() ?: rsiSeries[i]
      val lo = sub.minOrNull() ?: rsiSeries[i]
      raw += if (hi - lo <= 1e-12) 50.0 else ((rsiSeries[i] - lo) / (hi - lo)) * 100.0
    }
    return smaSeries(raw, smoothK).lastOrNull() ?: 50.0
  }

  private fun williamsR(
    highs: List<Double>,
    lows: List<Double>,
    closes: List<Double>,
    period: Int,
  ): Double {
    val n = min(period, closes.size)
    val hh = highs.takeLast(n).maxOrNull() ?: closes.last()
    val ll = lows.takeLast(n).minOrNull() ?: closes.last()
    return if (hh - ll <= 1e-12) -50.0 else ((hh - closes.last()) / (hh - ll)) * -100.0
  }

  private fun ultimateOscillator(
    highs: List<Double>,
    lows: List<Double>,
    closes: List<Double>,
  ): Double {
    if (closes.size < 29) return 50.0
    val bp = mutableListOf<Double>()
    val tr = mutableListOf<Double>()
    for (i in 1 until closes.size) {
      val trueLow = min(lows[i], closes[i - 1])
      val trueHigh = max(highs[i], closes[i - 1])
      bp += closes[i] - trueLow
      tr += max(trueHigh - trueLow, 1e-12)
    }
    fun avgRatio(p: Int): Double {
      val bSum = bp.takeLast(p).sum()
      val tSum = tr.takeLast(p).sum()
      return if (tSum <= 1e-12) 0.5 else bSum / tSum
    }
    val avg7 = avgRatio(7)
    val avg14 = avgRatio(14)
    val avg28 = avgRatio(28)
    return 100.0 * ((4.0 * avg7) + (2.0 * avg14) + avg28) / 7.0
  }
}
