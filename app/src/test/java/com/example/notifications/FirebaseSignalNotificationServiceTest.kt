package com.example.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import com.example.data.remote.AlertConfigRecord
import com.example.data.remote.TradingSignalRecord
import com.example.data.repository.SignalRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FirebaseSignalNotificationServiceTest : FirestoreEmulatorTestBase() {

  private fun makeRecord(
    id: String,
    symbol: String = "BTCUSD",
    timeframe: String = "15m",
    direction: String = "BUY",
    status: String = "ACTIVE",
    buyIndicators: Long = 19L,
    sellIndicators: Long = 3L,
  ) =
    TradingSignalRecord(
      id = id,
      userId = "user1",
      symbol = symbol,
      timeframe = timeframe,
      direction = direction,
      strength = if (direction == "BUY") "STRONG_BUY" else "STRONG_SELL",
      smcEvent = if (direction == "BUY") "SSL_SWEEP_CHOCH" else "BSL_SWEEP_CHOCH",
      entryPrice = 67500.0,
      stopLoss = 67100.0,
      takeProfit1 = 68100.0,
      takeProfit2 = 68500.0,
      takeProfit3 = 69000.0,
      sweptLiquidityLevel = 67200.0,
      riskRewardRatio = 2.5,
      buyIndicators = buyIndicators,
      sellIndicators = sellIndicators,
      neutralIndicators = 4L,
      status = status,
      notes = "SMC sweep setup",
    )

  @Test
  fun processSnapshot_ignoresInitialHistoricalSignalsAndNotifiesNewBuyAndSell() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val uid = signInTestUser("notify_tester@test.com")
    val repo = SignalRepository(firestore, auth)
    val notified = mutableListOf<TradingSignalRecord>()

    val service =
      FirebaseSignalNotificationService(
        context = context,
        signalRepository = repo,
        userId = uid,
        notifier = { _, rec ->
          notified += rec
          true
        },
      )

    // Initial historical load -> should NOT notify
    val initial = listOf(makeRecord("old_1", direction = "BUY"))
    val firstEmitted = service.processSnapshot(initial)
    assertTrue(firstEmitted.isEmpty())
    assertTrue(notified.isEmpty())

    // New BUY and SELL signals arrive -> MUST notify
    val second =
      listOf(
        makeRecord("old_1", direction = "BUY"),
        makeRecord("new_buy_1", symbol = "XAUUSD", direction = "BUY"),
        makeRecord("new_sell_1", symbol = "EURUSD", direction = "SELL", buyIndicators = 3L, sellIndicators = 18L),
        makeRecord("neutral_skip", symbol = "GBPUSD", direction = "NEUTRAL"),
        makeRecord("closed_skip", symbol = "USDJPY", direction = "BUY", status = "TP_HIT"),
      )
    val secondEmitted = service.processSnapshot(second)
    assertEquals(2, secondEmitted.size)
    assertEquals(listOf("new_buy_1", "new_sell_1"), notified.map { it.id })
    assertEquals("new_sell_1", service.lastNotifiedRecord.value?.id)

    // Duplicate emission of same IDs -> should NOT re-notify
    val thirdEmitted = service.processSnapshot(second)
    assertTrue(thirdEmitted.isEmpty())
    assertEquals(2, notified.size)
  }

  @Test
  fun filterEligibleNewSignals_respectsAlertConfigs() = runBlocking {
    val configs =
      listOf(
        AlertConfigRecord(
          id = "global",
          symbol = "ALL",
          enabled = true,
          tf1m = false,
          tf5m = true,
          tf15m = true,
          tf30m = true,
          minConfluence = 16L,
        ),
        AlertConfigRecord(
          id = "EURUSD",
          symbol = "EURUSD",
          enabled = false,
        ),
      )

    val incoming =
      listOf(
        makeRecord("pass_buy", symbol = "BTCUSD", timeframe = "15m", direction = "BUY", buyIndicators = 18L),
        makeRecord("skip_tf1m", symbol = "BTCUSD", timeframe = "1m", direction = "BUY", buyIndicators = 18L),
        makeRecord("skip_pair_off", symbol = "EURUSD", timeframe = "15m", direction = "SELL", sellIndicators = 19L),
        makeRecord("skip_low_conf", symbol = "XAUUSD", timeframe = "5m", direction = "BUY", buyIndicators = 12L),
      )

    val eligible =
      FirebaseSignalNotificationService.filterEligibleNewSignals(
        knownIds = emptySet(),
        incoming = incoming,
        configs = configs,
      )
    assertEquals(listOf("pass_buy"), eligible.map { it.id })
  }

  @Test
  fun startAndObserve_liveFirestoreInsertTriggersNotification() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val uid = signInTestUser("notify_live@test.com")
    val repo = SignalRepository(firestore, auth)
    val notified = mutableListOf<TradingSignalRecord>()

    val service =
      FirebaseSignalNotificationService(
        context = context,
        signalRepository = repo,
        userId = uid,
        notifier = { _, rec ->
          notified += rec
          true
        },
      )

    service.start(this)
    // Allow initial empty snapshot to settle
    delay(600)

    val docRef = firestore.collection("users").document(uid).collection("signals").document("live_buy_99")
    withTimeout(5000L) {
      docRef
        .set(
          mapOf(
            "userId" to uid,
            "symbol" to "BTCUSD",
            "timeframe" to "15m",
            "direction" to "BUY",
            "strength" to "STRONG_BUY",
            "smcEvent" to "SSL_SWEEP_CHOCH",
            "entryPrice" to 68000.0,
            "stopLoss" to 67500.0,
            "takeProfit1" to 68750.0,
            "takeProfit2" to 69250.0,
            "takeProfit3" to 69900.0,
            "sweptLiquidityLevel" to 67650.0,
            "riskRewardRatio" to 2.5,
            "buyIndicators" to 19L,
            "sellIndicators" to 3L,
            "neutralIndicators" to 4L,
            "status" to "ACTIVE",
            "notes" to "Live Firestore test",
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
          )
        )
        .await()
    }

    withTimeout(5000L) {
      while (notified.none { it.id == "live_buy_99" }) {
        delay(100)
      }
    }
    assertTrue(notified.any { it.id == "live_buy_99" && it.direction == "BUY" })
    service.stop()
  }
}
