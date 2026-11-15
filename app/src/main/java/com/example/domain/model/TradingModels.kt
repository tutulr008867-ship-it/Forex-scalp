package com.example.domain.model

enum class AssetClass(val label: String) {
  CRYPTO("Crypto"),
  COMMODITIES("Gold"),
  FOREX("Major Forex"),
}

enum class MarketPair(
  val symbol: String,
  val displayName: String,
  val yahooTicker: String,
  val binanceSymbol: String?,
  val assetClass: AssetClass,
  val decimals: Int,
  val pipFactor: Double,
) {
  BTCUSD("BTCUSD", "BTC / USD", "BTC-USD", "BTCUSDT", AssetClass.CRYPTO, 2, 1.0),
  XAUUSD("XAUUSD", "XAU / USD", "GC=F", "PAXGUSDT", AssetClass.COMMODITIES, 2, 10.0),
  EURUSD("EURUSD", "EUR / USD", "EURUSD=X", "EURUSDT", AssetClass.FOREX, 5, 10000.0),
  GBPUSD("GBPUSD", "GBP / USD", "GBPUSD=X", "GBPUSDT", AssetClass.FOREX, 5, 10000.0),
  USDJPY("USDJPY", "USD / JPY", "USDJPY=X", null, AssetClass.FOREX, 3, 100.0),
  USDCHF("USDCHF", "USD / CHF", "USDCHF=X", null, AssetClass.FOREX, 5, 10000.0),
  AUDUSD("AUDUSD", "AUD / USD", "AUDUSD=X", "AUDUSDT", AssetClass.FOREX, 5, 10000.0),
  USDCAD("USDCAD", "USD / CAD", "USDCAD=X", null, AssetClass.FOREX, 5, 10000.0),
  NZDUSD("NZDUSD", "NZD / USD", "NZDUSD=X", null, AssetClass.FOREX, 5, 10000.0);

  fun formatPrice(price: Double): String = "%.${decimals}f".format(price)

  fun formatPips(distance: Double): String {
    val pips = kotlin.math.abs(distance) * pipFactor
    return if (this == BTCUSD) "${"%.1f".format(pips)} pts" else "${"%.1f".format(pips)} pips"
  }

  companion object {
    fun fromSymbol(symbol: String): MarketPair =
      entries.firstOrNull { it.symbol.equals(symbol, ignoreCase = true) } ?: BTCUSD
  }
}

enum class Timeframe(
  val label: String,
  val yahooInterval: String,
  val yahooRange: String,
  val binanceInterval: String,
  val minutes: Int,
) {
  M1("1m", "1m", "1d", "1m", 1),
  M5("5m", "5m", "5d", "5m", 5),
  M15("15m", "15m", "5d", "15m", 15),
  M30("30m", "30m", "1mo", "30m", 30);

  companion object {
    fun fromLabel(label: String): Timeframe =
      entries.firstOrNull { it.label.equals(label, ignoreCase = true) } ?: M15
  }
}

data class Candle(
  val timestampMs: Long,
  val open: Double,
  val high: Double,
  val low: Double,
  val close: Double,
  val volume: Double,
)

enum class SignalVote(val label: String) {
  BUY("BUY"),
  NEUTRAL("NEUTRAL"),
  SELL("SELL"),
}

enum class SignalStrength(val code: String, val label: String) {
  STRONG_BUY("STRONG_BUY", "STRONG BUY"),
  BUY("BUY", "BUY"),
  NEUTRAL("NEUTRAL", "NEUTRAL"),
  SELL("SELL", "SELL"),
  STRONG_SELL("STRONG_SELL", "STRONG SELL"),
}

enum class IndicatorCategory(val label: String) {
  OSCILLATOR("Oscillators (11)"),
  MOVING_AVERAGE("Moving Averages (15)"),
}

data class IndicatorReading(
  val index: Int,
  val name: String,
  val category: IndicatorCategory,
  val formattedValue: String,
  val rawValue: Double,
  val vote: SignalVote,
)

data class IndicatorSummary(
  val readings: List<IndicatorReading>,
  val buyCount: Int,
  val neutralCount: Int,
  val sellCount: Int,
  val oscillatorBuy: Int,
  val oscillatorNeutral: Int,
  val oscillatorSell: Int,
  val maBuy: Int,
  val maNeutral: Int,
  val maSell: Int,
  val strength: SignalStrength,
)

enum class SmcEventType(
  val code: String,
  val label: String,
  val isBullish: Boolean,
  val isSweep: Boolean,
) {
  SSL_SWEEP_CHOCH("SSL_SWEEP_CHoCH", "SSL Sweep + Bullish CHoCH", true, true),
  BSL_SWEEP_CHOCH("BSL_SWEEP_CHoCH", "BSL Sweep + Bearish CHoCH", false, true),
  SSL_SWEEP_BOS("SSL_SWEEP_BOS", "SSL Sweep + Bullish BOS", true, true),
  BSL_SWEEP_BOS("BSL_SWEEP_BOS", "BSL Sweep + Bearish BOS", false, true),
  BULLISH_FVG_OB("BULLISH_FVG_OB", "Bullish OB + FVG Retest", true, false),
  BEARISH_FVG_OB("BEARISH_FVG_OB", "Bearish OB + FVG Retest", false, false),
  CONSOLIDATION("CONSOLIDATION", "Structure Equilibrium", true, false),
}

data class PriceZone(
  val startIndex: Int,
  val endIndex: Int,
  val top: Double,
  val bottom: Double,
  val isBullish: Boolean,
  val label: String,
)

data class SweepMarker(
  val candleIndex: Int,
  val sweptLevel: Double,
  val sweepExtreme: Double,
  val isBullish: Boolean,
  val label: String,
)

data class SmcAnalysis(
  val eventType: SmcEventType,
  val direction: String,
  val entryPrice: Double,
  val stopLoss: Double,
  val takeProfit1: Double,
  val takeProfit2: Double,
  val takeProfit3: Double,
  val sweptLiquidityLevel: Double,
  val riskRewardRatio: Double,
  val sweepDetected: Boolean,
  val sweepMarker: SweepMarker?,
  val orderBlocks: List<PriceZone>,
  val fairValueGaps: List<PriceZone>,
  val swingHighs: List<Pair<Int, Double>>,
  val swingLows: List<Pair<Int, Double>>,
  val marketBias: String,
  val structureNotes: String,
)

data class LiveMarketSignal(
  val pair: MarketPair,
  val timeframe: Timeframe,
  val candles: List<Candle>,
  val lastPrice: Double,
  val changePct: Double,
  val smcAnalysis: SmcAnalysis,
  val indicatorSummary: IndicatorSummary,
  val combinedStrength: SignalStrength,
  val dataSource: String,
  val updatedAtMs: Long,
) {
  val isHighConviction: Boolean
    get() =
      smcAnalysis.sweepDetected &&
        ((smcAnalysis.direction == "BUY" && indicatorSummary.buyCount >= 13) ||
          (smcAnalysis.direction == "SELL" && indicatorSummary.sellCount >= 13))

  val alignedCount: Int
    get() =
      if (smcAnalysis.direction == "BUY") indicatorSummary.buyCount
      else indicatorSummary.sellCount
}
