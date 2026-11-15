package com.example.data.repository

import com.example.data.remote.BinanceVisionApi
import com.example.data.remote.YahooFinanceApi
import com.example.domain.engine.SmcEngine
import com.example.domain.engine.TradingViewIndicatorsEngine
import com.example.domain.model.Candle
import com.example.domain.model.LiveMarketSignal
import com.example.domain.model.MarketPair
import com.example.domain.model.Timeframe
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class MarketDataRepository(
  private val yahooApi: YahooFinanceApi,
  private val binanceApi: BinanceVisionApi,
) {
  companion object {
    fun create(): MarketDataRepository {
      val okHttp =
        OkHttpClient.Builder()
          .connectTimeout(12, TimeUnit.SECONDS)
          .readTimeout(15, TimeUnit.SECONDS)
          .build()
      val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
      val yahoo =
        Retrofit.Builder()
          .baseUrl("https://query1.finance.yahoo.com/")
          .client(okHttp)
          .addConverterFactory(MoshiConverterFactory.create(moshi))
          .build()
          .create(YahooFinanceApi::class.java)
      val binance =
        Retrofit.Builder()
          .baseUrl("https://data-api.binance.vision/")
          .client(okHttp)
          .addConverterFactory(MoshiConverterFactory.create(moshi))
          .build()
          .create(BinanceVisionApi::class.java)
      return MarketDataRepository(yahoo, binance)
    }
  }

  suspend fun fetchAndAnalyze(pair: MarketPair, timeframe: Timeframe): Result<LiveMarketSignal> =
    withContext(Dispatchers.IO) {
      runCatching {
        val (candles, source, prevCloseMeta) = fetchCandlesWithFallback(pair, timeframe)
        require(candles.size >= 15) {
          "Insufficient live market candles returned for ${pair.symbol} (${timeframe.label})."
        }
        val trimmed = candles.takeLast(120)
        val lastPrice = trimmed.last().close
        val refClose = prevCloseMeta ?: trimmed.first().open
        val changePct =
          if (refClose > 0.0) ((lastPrice - refClose) / refClose) * 100.0 else 0.0

        val indicatorSummary = TradingViewIndicatorsEngine.evaluate(trimmed, pair.decimals)
        val (smcAnalysis, combinedStrength) = SmcEngine.analyze(pair, trimmed, indicatorSummary)

        LiveMarketSignal(
          pair = pair,
          timeframe = timeframe,
          candles = trimmed,
          lastPrice = lastPrice,
          changePct = changePct,
          smcAnalysis = smcAnalysis,
          indicatorSummary = indicatorSummary,
          combinedStrength = combinedStrength,
          dataSource = source,
          updatedAtMs = System.currentTimeMillis(),
        )
      }
    }

  suspend fun scanPairsForTimeframe(
    pairs: List<MarketPair>,
    timeframe: Timeframe,
  ): List<Result<LiveMarketSignal>> = coroutineScope {
    pairs.map { pair -> async { fetchAndAnalyze(pair, timeframe) } }.awaitAll()
  }

  suspend fun scanPairAllTimeframes(pair: MarketPair): List<Result<LiveMarketSignal>> =
    coroutineScope {
      Timeframe.entries.map { tf -> async { fetchAndAnalyze(pair, tf) } }.awaitAll()
    }

  private suspend fun fetchCandlesWithFallback(
    pair: MarketPair,
    timeframe: Timeframe,
  ): Triple<List<Candle>, String, Double?> {
    // Prefer Binance Vision for 24/7 BTCUSD and as fallback for supported spot proxies
    if (pair == MarketPair.BTCUSD && pair.binanceSymbol != null) {
      val binanceResult = runCatching { fetchFromBinance(pair.binanceSymbol, timeframe) }
      if (binanceResult.isSuccess && binanceResult.getOrNull()?.size ?: 0 >= 25) {
        return Triple(binanceResult.getOrThrow(), "Binance Vision Spot", null)
      }
    }

    val yahooResult = runCatching { fetchFromYahoo(pair, timeframe) }
    if (yahooResult.isSuccess && yahooResult.getOrNull()?.first?.size ?: 0 >= 15) {
      val (candles, prevClose) = yahooResult.getOrThrow()
      return Triple(candles, "Yahoo Finance Live", prevClose)
    }

    val binanceSymbol = pair.binanceSymbol
    if (binanceSymbol != null) {
      val fallback = fetchFromBinance(binanceSymbol, timeframe)
      if (fallback.size >= 15) {
        return Triple(fallback, "Binance Vision Proxy", null)
      }
    }

    throw yahooResult.exceptionOrNull()
      ?: IllegalStateException("Market feed unavailable for ${pair.symbol}")
  }

  private suspend fun fetchFromYahoo(
    pair: MarketPair,
    timeframe: Timeframe,
  ): Pair<List<Candle>, Double?> {
    val resp =
      yahooApi.getChart(
        symbol = pair.yahooTicker,
        interval = timeframe.yahooInterval,
        range = timeframe.yahooRange,
      )
    val result =
      resp.chart?.result?.firstOrNull()
        ?: throw IllegalStateException(
          resp.chart?.error?.description ?: "No chart data for ${pair.symbol}"
        )
    val timestamps = result.timestamp ?: emptyList()
    val quote = result.indicators?.quote?.firstOrNull()
    val opens = quote?.open ?: emptyList()
    val highs = quote?.high ?: emptyList()
    val lows = quote?.low ?: emptyList()
    val closes = quote?.close ?: emptyList()
    val volumes = quote?.volume ?: emptyList()

    val candles = mutableListOf<Candle>()
    val count = minOf(timestamps.size, opens.size, highs.size, lows.size, closes.size)
    for (i in 0 until count) {
      val o = opens[i] ?: continue
      val h = highs[i] ?: continue
      val l = lows[i] ?: continue
      val c = closes[i] ?: continue
      if (o <= 0.0 || h <= 0.0 || l <= 0.0 || c <= 0.0) continue
      val v = volumes.getOrNull(i) ?: 100.0
      candles +=
        Candle(
          timestampMs = timestamps[i] * 1000L,
          open = o,
          high = h,
          low = l,
          close = c,
          volume = if (v > 0.0) v else 100.0,
        )
    }
    val prevClose = result.meta?.chartPreviousClose ?: result.meta?.previousClose
    return candles to prevClose
  }

  private suspend fun fetchFromBinance(symbol: String, timeframe: Timeframe): List<Candle> {
    val raw = binanceApi.getKlines(symbol = symbol, interval = timeframe.binanceInterval, limit = 120)
    return raw.mapNotNull { row ->
      if (row.size < 6) return@mapNotNull null
      val ts = (row[0] as? Number)?.toLong() ?: return@mapNotNull null
      val o = row[1].toString().toDoubleOrNull() ?: return@mapNotNull null
      val h = row[2].toString().toDoubleOrNull() ?: return@mapNotNull null
      val l = row[3].toString().toDoubleOrNull() ?: return@mapNotNull null
      val c = row[4].toString().toDoubleOrNull() ?: return@mapNotNull null
      val v = row[5].toString().toDoubleOrNull() ?: 100.0
      Candle(ts, o, h, l, c, v)
    }
  }
}
