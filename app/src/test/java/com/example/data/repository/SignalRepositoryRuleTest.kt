package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.remote.AlertConfigRecord
import com.example.domain.model.Candle
import com.example.domain.model.IndicatorCategory
import com.example.domain.model.IndicatorReading
import com.example.domain.model.IndicatorSummary
import com.example.domain.model.LiveMarketSignal
import com.example.domain.model.MarketPair
import com.example.domain.model.SignalStrength
import com.example.domain.model.SignalVote
import com.example.domain.model.SmcAnalysis
import com.example.domain.model.SmcEventType
import com.example.domain.model.Timeframe
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SignalRepositoryRuleTest : FirestoreEmulatorTestBase() {

  private fun sampleLiveSignal(): LiveMarketSignal {
    val readings =
      (1..26).map { i ->
        IndicatorReading(
          index = i,
          name = "Ind $i",
          category =
            if (i <= 11) IndicatorCategory.OSCILLATOR else IndicatorCategory.MOVING_AVERAGE,
          formattedValue = "55.00",
          rawValue = 55.0,
          vote =
            when {
              i <= 18 -> SignalVote.BUY
              i <= 22 -> SignalVote.NEUTRAL
              else -> SignalVote.SELL
            },
        )
      }
    val summary =
      IndicatorSummary(
        readings = readings,
        buyCount = 18,
        neutralCount = 4,
        sellCount = 4,
        oscillatorBuy = 7,
        oscillatorNeutral = 2,
        oscillatorSell = 2,
        maBuy = 11,
        maNeutral = 2,
        maSell = 2,
        strength = SignalStrength.STRONG_BUY,
      )
    val smc =
      SmcAnalysis(
        eventType = SmcEventType.SSL_SWEEP_CHOCH,
        direction = "BUY",
        entryPrice = 2645.50,
        stopLoss = 2636.20,
        takeProfit1 = 2659.45,
        takeProfit2 = 2668.75,
        takeProfit3 = 2680.80,
        sweptLiquidityLevel = 2638.00,
        riskRewardRatio = 2.5,
        sweepDetected = true,
        sweepMarker = null,
        orderBlocks = emptyList(),
        fairValueGaps = emptyList(),
        swingHighs = emptyList(),
        swingLows = emptyList(),
        marketBias = "BULLISH",
        structureNotes = "SSL Sweep @ 2638.00 + Bullish CHoCH + 18/26 TV indicators",
      )
    return LiveMarketSignal(
      pair = MarketPair.XAUUSD,
      timeframe = Timeframe.M15,
      candles = listOf(Candle(1L, 2640.0, 2648.0, 2637.0, 2645.5, 1000.0)),
      lastPrice = 2645.50,
      changePct = 0.85,
      smcAnalysis = smc,
      indicatorSummary = summary,
      combinedStrength = SignalStrength.STRONG_BUY,
      dataSource = "Market Feed",
      updatedAtMs = System.currentTimeMillis(),
    )
  }

  @Test
  fun saveAndObserveSignal_authenticatedOwner_succeeds() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = SignalRepository(firestore, auth)
    val docId = "sig_test_${UUID.randomUUID().toString().take(8)}"

    val saveResult = withTimeout(TIMEOUT_MS) { repo.saveSignalFromLiveAnalysis(sampleLiveSignal(), docId) }
    assertTrue("Save failed: ${saveResult.exceptionOrNull()}", saveResult.isSuccess)

    val observed =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeSignals(uid).first { list -> list.any { it.id == docId } }
      }
    val saved = observed.firstOrNull { it.id == docId }
    assertNotNull(saved)
    assertEquals("XAUUSD", saved?.symbol)
    assertEquals(18L, saved?.buyIndicators)

    val fetched = withTimeout(TIMEOUT_MS) { repo.getSignalById(docId) }.getOrNull()
    assertEquals(docId, fetched?.id)

    val updatedLive =
      sampleLiveSignal().let { live ->
        live.copy(
          smcAnalysis = live.smcAnalysis.copy(entryPrice = 2646.00, stopLoss = 2635.50, riskRewardRatio = 2.6)
        )
      }
    val refreshResult = withTimeout(TIMEOUT_MS) { repo.refreshActiveSignalLevels(docId, updatedLive) }
    assertTrue("Refresh levels failed: ${refreshResult.exceptionOrNull()}", refreshResult.isSuccess)

    val resolveResult =
      withTimeout(TIMEOUT_MS) { repo.resolveSignalStatus(docId, "TP_HIT", "Target hit") }
    assertTrue("Resolve failed: ${resolveResult.exceptionOrNull()}", resolveResult.isSuccess)

    val deleteResult = withTimeout(TIMEOUT_MS) { repo.deleteSignal(docId) }
    assertTrue(deleteResult.isSuccess)
  }

  @Test
  fun saveAndObserveAlertConfig_authenticatedOwner_succeeds() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = SignalRepository(firestore, auth)
    val configId = "cfg_${UUID.randomUUID().toString().take(8)}"
    val config =
      AlertConfigRecord(
        id = configId,
        userId = uid,
        symbol = "BTCUSD",
        enabled = true,
        tf1m = true,
        tf5m = true,
        tf15m = true,
        tf30m = false,
        minConfluence = 16L,
        requireLiquiditySweep = true,
        fcmToken = "fcm_test_token_123",
      )

    val saveRes = withTimeout(TIMEOUT_MS) { repo.saveAlertConfig(config) }
    assertTrue("Save alert config failed: ${saveRes.exceptionOrNull()}", saveRes.isSuccess)

    val configs =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeAlertConfigs(uid).first { list -> list.any { it.id == configId } }
      }
    assertTrue(configs.any { it.symbol == "BTCUSD" && !it.tf30m })
  }

  @Test
  fun observeSignals_crossUserAccess_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = SignalRepository(firestore, auth)
    val docId = "sig_cross_${UUID.randomUUID().toString().take(8)}"
    withTimeout(TIMEOUT_MS) { aliceRepo.saveSignalFromLiveAnalysis(sampleLiveSignal(), docId).getOrThrow() }

    signInTestUser(BOB_EMAIL)
    val bobRepo = SignalRepository(firestore, auth)

    val error =
      runCatching {
        withTimeout(FLOW_TIMEOUT_MS) { bobRepo.observeSignals(aliceUid).first() }
      }.exceptionOrNull()
    assertNotNull("Expected PERMISSION_DENIED for cross-user query", error)
    val fsEx = error?.unwrapFirestoreException()
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
  }

  @Test
  fun observeSignals_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    auth.signOut()
    val repo = SignalRepository(firestore, auth)

    val error =
      runCatching {
        withTimeout(FLOW_TIMEOUT_MS) { repo.observeSignals(aliceUid).first() }
      }.exceptionOrNull()
    assertNotNull("Expected PERMISSION_DENIED for unauthenticated user", error)
    val fsEx = error?.unwrapFirestoreException()
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
  }

  private companion object {
    const val ALICE_EMAIL = "alice_trader@test.com"
    const val BOB_EMAIL = "bob_trader@test.com"
    const val TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 4000L
  }
}
