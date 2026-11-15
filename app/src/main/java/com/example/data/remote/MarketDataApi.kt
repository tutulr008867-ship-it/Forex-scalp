package com.example.data.remote

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface YahooFinanceApi {
  @GET("v8/finance/chart/{symbol}")
  suspend fun getChart(
    @Path("symbol") symbol: String,
    @Query("interval") interval: String,
    @Query("range") range: String,
    @Header("User-Agent") userAgent: String = "Mozilla/5.0 (Android 15; Mobile) AppleWebKit/537.36",
  ): YahooChartResponse
}

interface BinanceVisionApi {
  @GET("api/v3/klines")
  suspend fun getKlines(
    @Query("symbol") symbol: String,
    @Query("interval") interval: String,
    @Query("limit") limit: Int = 120,
  ): List<List<Any>>
}

data class YahooChartResponse(
  val chart: YahooChartBody? = null,
)

data class YahooChartBody(
  val result: List<YahooChartResult>? = null,
  val error: YahooChartError? = null,
)

data class YahooChartError(
  val code: String? = null,
  val description: String? = null,
)

data class YahooChartResult(
  val meta: YahooChartMeta? = null,
  val timestamp: List<Long>? = null,
  val indicators: YahooIndicators? = null,
)

data class YahooChartMeta(
  val regularMarketPrice: Double? = null,
  val chartPreviousClose: Double? = null,
  val previousClose: Double? = null,
)

data class YahooIndicators(
  val quote: List<YahooQuote>? = null,
)

data class YahooQuote(
  val open: List<Double?>? = null,
  val high: List<Double?>? = null,
  val low: List<Double?>? = null,
  val close: List<Double?>? = null,
  val volume: List<Double?>? = null,
)
