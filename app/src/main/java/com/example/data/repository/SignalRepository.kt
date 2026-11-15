package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.remote.AlertConfigRecord
import com.example.data.remote.OperationType
import com.example.data.remote.TradingSignalRecord
import com.example.data.remote.handleFirestoreError
import com.example.domain.model.LiveMarketSignal
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class SignalRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(
    db = FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    ),
    auth = Firebase.auth,
  )

  private fun requireUserId(): String =
    auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

  fun observeSignals(userId: String): Flow<List<TradingSignalRecord>> = flow {
    val path = "users/$userId/signals"
    emitAll(
      db.collection("users")
        .document(userId)
        .collection("signals")
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            TradingSignalRecord::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  suspend fun saveSignalFromLiveAnalysis(
    live: LiveMarketSignal,
    docId: String = "sig_${live.pair.symbol}_${live.timeframe.label}_${UUID.randomUUID().toString().take(8)}",
  ): Result<String> {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("signals").document(docId)
    val smc = live.smcAnalysis
    val ind = live.indicatorSummary
    val payload =
      mapOf(
        "userId" to uid,
        "symbol" to live.pair.symbol,
        "timeframe" to live.timeframe.label,
        "direction" to smc.direction,
        "strength" to live.combinedStrength.code,
        "smcEvent" to smc.eventType.code,
        "entryPrice" to smc.entryPrice,
        "stopLoss" to smc.stopLoss,
        "takeProfit1" to smc.takeProfit1,
        "takeProfit2" to smc.takeProfit2,
        "takeProfit3" to smc.takeProfit3,
        "sweptLiquidityLevel" to smc.sweptLiquidityLevel,
        "riskRewardRatio" to smc.riskRewardRatio,
        "buyIndicators" to ind.buyCount.toLong(),
        "sellIndicators" to ind.sellCount.toLong(),
        "neutralIndicators" to ind.neutralCount.toLong(),
        "status" to "ACTIVE",
        "notes" to smc.structureNotes.take(480),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    return try {
      docRef.set(payload).await()
      Result.success(docId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun getSignalById(signalId: String): Result<TradingSignalRecord?> {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("signals").document(signalId)
    return try {
      val snap = docRef.get().await()
      Result.success(
        snap.toObject(
          TradingSignalRecord::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
        )
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun resolveSignalStatus(
    signalId: String,
    newStatus: String,
    notes: String,
  ): Result<Unit> {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("signals").document(signalId)
    return try {
      docRef
        .update(
          mapOf(
            "status" to newStatus,
            "notes" to notes.take(480),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun refreshActiveSignalLevels(
    signalId: String,
    live: LiveMarketSignal,
  ): Result<Unit> {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("signals").document(signalId)
    val smc = live.smcAnalysis
    val ind = live.indicatorSummary
    return try {
      docRef
        .update(
          mapOf(
            "direction" to smc.direction,
            "strength" to live.combinedStrength.code,
            "smcEvent" to smc.eventType.code,
            "entryPrice" to smc.entryPrice,
            "stopLoss" to smc.stopLoss,
            "takeProfit1" to smc.takeProfit1,
            "takeProfit2" to smc.takeProfit2,
            "takeProfit3" to smc.takeProfit3,
            "sweptLiquidityLevel" to smc.sweptLiquidityLevel,
            "riskRewardRatio" to smc.riskRewardRatio,
            "buyIndicators" to ind.buyCount.toLong(),
            "sellIndicators" to ind.sellCount.toLong(),
            "neutralIndicators" to ind.neutralCount.toLong(),
            "notes" to smc.structureNotes.take(480),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun deleteSignal(signalId: String): Result<Unit> {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("signals").document(signalId)
    return try {
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      Result.failure(e)
    }
  }

  fun observeAlertConfigs(userId: String): Flow<List<AlertConfigRecord>> = flow {
    val path = "users/$userId/alert_configs"
    emitAll(
      db.collection("users")
        .document(userId)
        .collection("alert_configs")
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            AlertConfigRecord::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  suspend fun saveAlertConfig(config: AlertConfigRecord): Result<Unit> {
    val uid = requireUserId()
    val configId = config.id.ifBlank { config.symbol }
    val docRef = db.collection("users").document(uid).collection("alert_configs").document(configId)
    return try {
      val existing = docRef.get().await()
      if (!existing.exists()) {
        val createPayload =
          mapOf(
            "userId" to uid,
            "symbol" to config.symbol,
            "enabled" to config.enabled,
            "tf1m" to config.tf1m,
            "tf5m" to config.tf5m,
            "tf15m" to config.tf15m,
            "tf30m" to config.tf30m,
            "minConfluence" to config.minConfluence.coerceIn(1L, 26L),
            "requireLiquiditySweep" to config.requireLiquiditySweep,
            "fcmToken" to config.fcmToken.take(512),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        docRef.set(createPayload).await()
      } else {
        val updatePayload =
          mapOf(
            "enabled" to config.enabled,
            "tf1m" to config.tf1m,
            "tf5m" to config.tf5m,
            "tf15m" to config.tf15m,
            "tf30m" to config.tf30m,
            "minConfluence" to config.minConfluence.coerceIn(1L, 26L),
            "requireLiquiditySweep" to config.requireLiquiditySweep,
            "fcmToken" to config.fcmToken.take(512),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        docRef.update(updatePayload).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      Result.failure(e)
    }
  }
}
