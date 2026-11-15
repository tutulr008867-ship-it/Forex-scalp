package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.remote.TradingSignalRecord
import com.example.domain.model.LiveMarketSignal
import com.example.domain.model.MarketPair
import com.example.ui.theme.SweepPulseTheme

@Composable
fun LiveSignalCard(
  signal: LiveMarketSignal,
  onOpenTerminal: () -> Unit,
  onSaveSignal: () -> Unit,
  onPushAlert: () -> Unit,
) {
  val colors = SweepPulseTheme.colors
  val smc = signal.smcAnalysis
  val ind = signal.indicatorSummary
  val pair = signal.pair
  val isBuy = smc.direction == "BUY"
  val accent = if (isBuy) colors.bull else colors.bear

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = colors.surfaceElevated,
    border =
      BorderStroke(
        1.dp,
        if (smc.sweepDetected) colors.liquidity.copy(alpha = 0.55f) else colors.border,
      ),
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable(onClick = onOpenTerminal)
        .testTag("signal_card_${pair.symbol}"),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = pair.displayName,
              style = MaterialTheme.typography.titleLarge,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = colors.bg,
              border = BorderStroke(1.dp, colors.border),
            ) {
              Text(
                text = signal.timeframe.label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.smcCyan,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
              )
            }
          }
          Text(
            text = "${pair.assetClass.label} • ${signal.dataSource}",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMuted,
          )
        }
        DirectionBadge(strength = signal.combinedStrength)
      }

      SweepStatusBadge(
        sweepDetected = smc.sweepDetected,
        eventLabel = smc.eventType.label,
        isBullish = isBuy,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        TradeLevelBox(
          label = "ENTRY",
          value = pair.formatPrice(smc.entryPrice),
          accent = colors.smcCyan,
          modifier = Modifier.weight(1f),
        )
        TradeLevelBox(
          label = "STOP LOSS",
          value = pair.formatPrice(smc.stopLoss),
          accent = colors.bear,
          modifier = Modifier.weight(1f),
        )
        TradeLevelBox(
          label = "TP1 (${"%.1f".format(smc.riskRewardRatio)}R)",
          value = pair.formatPrice(smc.takeProfit1),
          accent = accent,
          modifier = Modifier.weight(1f),
        )
      }

      ConfluenceBar26(
        buyCount = ind.buyCount,
        neutralCount = ind.neutralCount,
        sellCount = ind.sellCount,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "SL Risk: ${pair.formatPips(smc.entryPrice - smc.stopLoss)} • Tap for Chart & 26 TV",
          style = MaterialTheme.typography.labelSmall,
          color = colors.textSecondary,
          modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          IconButton(
            onClick = onSaveSignal,
            modifier = Modifier.testTag("save_signal_${pair.symbol}"),
          ) {
            Icon(
              imageVector = Icons.Rounded.BookmarkAdd,
              contentDescription = "Save signal to Firebase Journal",
              tint = colors.smcCyan,
            )
          }
          IconButton(
            onClick = onPushAlert,
            modifier = Modifier.testTag("push_signal_${pair.symbol}"),
          ) {
            Icon(
              imageVector = Icons.Rounded.NotificationsActive,
              contentDescription = "Send entry & stop-loss alert",
              tint = colors.liquidity,
            )
          }
        }
      }
    }
  }
}

@Composable
fun SavedSignalJournalCard(
  record: TradingSignalRecord,
  onResolve: (String) -> Unit,
  onRefreshLevels: () -> Unit,
  onDelete: () -> Unit,
  onOpenTerminal: () -> Unit,
) {
  val colors = SweepPulseTheme.colors
  val pair = MarketPair.fromSymbol(record.symbol)
  val isBuy = record.isBuy
  val isActive = record.status == "ACTIVE"

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = colors.surfaceElevated,
    border = BorderStroke(1.dp, colors.border),
    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenTerminal),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = "${record.direction} ${record.symbol}",
            style = MaterialTheme.typography.titleMedium,
            color = if (isBuy) colors.bull else colors.bear,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = record.timeframe,
            style = MaterialTheme.typography.labelSmall,
            color = colors.smcCyan,
          )
        }
        Surface(
          shape = RoundedCornerShape(8.dp),
          color =
            when (record.status) {
              "TP_HIT" -> colors.bull.copy(alpha = 0.16f)
              "SL_HIT" -> colors.bear.copy(alpha = 0.16f)
              else -> colors.liquidity.copy(alpha = 0.16f)
            },
        ) {
          Text(
            text = record.status,
            style = MaterialTheme.typography.labelSmall,
            color =
              when (record.status) {
                "TP_HIT" -> colors.bull
                "SL_HIT" -> colors.bear
                else -> colors.liquidity
              },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        TradeLevelBox("ENTRY", pair.formatPrice(record.entryPrice), colors.smcCyan, Modifier.weight(1f))
        TradeLevelBox("SL", pair.formatPrice(record.stopLoss), colors.bear, Modifier.weight(1f))
        TradeLevelBox("TP1", pair.formatPrice(record.takeProfit1), colors.bull, Modifier.weight(1f))
      }

      Text(
        text = record.notes,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textSecondary,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (isActive) {
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { onResolve("TP_HIT") }) {
              Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(16.dp), tint = colors.bull)
              Spacer(Modifier.size(4.dp))
              Text("TP Hit", color = colors.bull)
            }
            OutlinedButton(onClick = { onResolve("SL_HIT") }) {
              Text("SL Hit", color = colors.bear)
            }
          }
        } else {
          Text(
            text = "Buy ${record.buyIndicators} • Sell ${record.sellIndicators} • Neutral ${record.neutralIndicators}",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMuted,
          )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (isActive) {
            IconButton(
              onClick = onRefreshLevels,
              modifier = Modifier.testTag("sync_saved_${record.id}"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "Sync live entry, SL and confluence",
                tint = colors.smcCyan,
              )
            }
          }
          IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete saved signal", tint = colors.textMuted)
          }
        }
      }
    }
  }
}

@Composable
fun EmptySignalCard(
  title: String,
  subtitle: String,
  onRetry: () -> Unit,
  actionLabel: String = "Refresh Scanner",
) {
  val colors = SweepPulseTheme.colors
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = colors.surfaceElevated,
    border = BorderStroke(1.dp, colors.border),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
      Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
      TextButton(onClick = onRetry) {
        Text(actionLabel, color = colors.bull)
      }
    }
  }
}
