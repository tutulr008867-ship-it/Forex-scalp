package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.remote.TradingSignalRecord
import com.example.domain.model.LiveMarketSignal
import com.example.domain.model.MarketPair
import com.example.domain.model.Timeframe
import com.example.ui.theme.SweepPulseTheme
import kotlin.math.max

@Composable
fun SummaryDashboard(
  allLiveSignals: List<LiveMarketSignal>,
  savedSignals: List<TradingSignalRecord>,
  selectedTimeframe: Timeframe,
  isScanning: Boolean,
  onOpenSignal: (MarketPair, Timeframe) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = SweepPulseTheme.colors
  val tfSignals = remember(allLiveSignals, selectedTimeframe) {
    allLiveSignals.filter { it.timeframe == selectedTimeframe }
  }
  val activeJournalCount = remember(savedSignals) { savedSignals.count { it.status == "ACTIVE" } }
  val liveSignalCount = tfSignals.size
  val buyCount = remember(tfSignals) { tfSignals.count { it.smcAnalysis.direction == "BUY" } }
  val sellCount = remember(tfSignals) { tfSignals.count { it.smcAnalysis.direction == "SELL" } }

  val recentSweepSignal = remember(allLiveSignals, tfSignals) {
    tfSignals
      .filter { it.smcAnalysis.sweepDetected }
      .maxByOrNull { (it.smcAnalysis.sweepMarker?.candleIndex ?: 0) + it.alignedCount }
      ?: allLiveSignals
        .filter { it.smcAnalysis.sweepDetected }
        .maxByOrNull { it.updatedAtMs + it.alignedCount * 1000L }
  }

  val totalSweepsDetected = remember(tfSignals) {
    tfSignals.count { it.smcAnalysis.sweepDetected }
  }

  val topSetup = remember(tfSignals) {
    tfSignals.maxByOrNull { (if (it.smcAnalysis.sweepDetected) 100 else 0) + it.alignedCount }
  }

  val resolvedJournal = remember(savedSignals) {
    savedSignals.filter { it.status == "TP_HIT" || it.status == "SL_HIT" }
  }
  val journalSummary =
    if (resolvedJournal.isEmpty()) "$activeJournalCount active • ${savedSignals.size} saved"
    else {
      val tpRate = ((resolvedJournal.count { it.status == "TP_HIT" } * 100f) / resolvedJournal.size).toInt()
      "$tpRate% TP rate (${resolvedJournal.size} closed)"
    }

  Column(
    modifier = modifier.fillMaxWidth().testTag("summary_dashboard"),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      DashboardKpiCard(
        title = "ACTIVE SIGNALS COUNT",
        primaryValue = "$liveSignalCount",
        badgeText = "${buyCount}B / ${sellCount}S",
        badgeColor = if (buyCount >= sellCount) colors.bull else colors.bear,
        subtitle = "${selectedTimeframe.label} live • $activeJournalCount journal active",
        iconTint = colors.smcCyan,
        icon = Icons.Rounded.ShowChart,
        modifier = Modifier.weight(1f).testTag("dashboard_active_signals_card"),
      )
      DashboardKpiCard(
        title = "26-TV TOP CONFLUENCE",
        primaryValue = topSetup?.let { "${it.alignedCount}/26" } ?: if (isScanning) "..." else "—",
        badgeText = topSetup?.pair?.symbol ?: "SCAN",
        badgeColor =
          when (topSetup?.smcAnalysis?.direction) {
            "BUY" -> colors.bull
            "SELL" -> colors.bear
            else -> colors.smcCyan
          },
        subtitle = journalSummary,
        iconTint = colors.bull,
        icon = Icons.Rounded.Radar,
        modifier = Modifier.weight(1f).testTag("dashboard_confluence_card"),
      )
    }

    val sweepDetected = recentSweepSignal != null
    val sweepAccent = if (sweepDetected) colors.liquidity else colors.smcCyan
    val sweepBgBrush =
      if (sweepDetected) {
        Brush.horizontalGradient(
          listOf(colors.liquidityContainer.copy(alpha = 0.8f), colors.surfaceElevated)
        )
      } else {
        Brush.horizontalGradient(
          listOf(colors.smcCyanContainer.copy(alpha = 0.55f), colors.surfaceElevated)
        )
      }

    Surface(
      shape = RoundedCornerShape(16.dp),
      color = Color.Transparent,
      border =
        BorderStroke(
          1.dp,
          if (sweepDetected) colors.liquidity.copy(alpha = 0.65f) else colors.border,
        ),
      modifier =
        Modifier
          .fillMaxWidth()
          .background(sweepBgBrush, RoundedCornerShape(16.dp))
          .let { base ->
            if (recentSweepSignal != null) {
              base.clickable { onOpenSignal(recentSweepSignal.pair, recentSweepSignal.timeframe) }
            } else {
              base
            }
          }
          .testTag("dashboard_recent_sweep_card"),
    ) {
      Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f),
          ) {
            Icon(
              imageVector = Icons.Rounded.Bolt,
              contentDescription = "Recent Liquidity Sweep Status",
              tint = sweepAccent,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = "RECENT LIQUIDITY SWEEP",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }

          Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (sweepDetected) colors.liquidity else colors.surfaceCard,
            border =
              if (sweepDetected) null else BorderStroke(1.dp, colors.smcCyan.copy(alpha = 0.45f)),
          ) {
            Text(
              text =
                if (sweepDetected) "DETECTED ($totalSweepsDetected)"
                else if (isScanning) "SCANNING"
                else "NONE",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = if (sweepDetected) colors.onAccent else colors.smcCyan,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
          }
        }

        if (recentSweepSignal != null) {
          val smc = recentSweepSignal.smcAnalysis
          val pair = recentSweepSignal.pair
          val isBuy = smc.direction == "BUY"
          val dirColor = if (isBuy) colors.bull else colors.bear

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Text(
                  text = "${smc.direction} ${pair.symbol}",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = dirColor,
                )
                Text(
                  text = "${recentSweepSignal.timeframe.label} • ${recentSweepSignal.alignedCount}/26 TV",
                  style = MaterialTheme.typography.labelMedium,
                  color = colors.smcCyan,
                )
                Text(
                  text = formatRelativeAge(recentSweepSignal.updatedAtMs),
                  style = MaterialTheme.typography.labelSmall,
                  color = colors.textMuted,
                )
              }
              Text(
                text =
                  "${smc.eventType.label} @ ${pair.formatPrice(smc.sweptLiquidityLevel)}  |  Entry ${pair.formatPrice(smc.entryPrice)}  |  SL ${pair.formatPrice(smc.stopLoss)}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
              )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
              contentDescription = "Open swept signal terminal",
              tint = colors.liquidity,
              modifier = Modifier.size(18.dp),
            )
          }
        } else {
          Text(
            text =
              if (isScanning)
                "Evaluating SSL/BSL stop-hunt sweeps and 26 TradingView indicators across BTCUSD, XAUUSD, and Forex..."
              else
                "No confirmed liquidity sweep on ${selectedTimeframe.label} yet. Switch timeframes or run a refresh scan.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
          )
        }
      }
    }
  }
}

@Composable
private fun DashboardKpiCard(
  title: String,
  primaryValue: String,
  badgeText: String,
  badgeColor: Color,
  subtitle: String,
  iconTint: Color,
  icon: ImageVector,
  modifier: Modifier = Modifier,
) {
  val colors = SweepPulseTheme.colors
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = colors.surfaceElevated,
    border = BorderStroke(1.dp, colors.border),
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.labelSmall,
          color = colors.textMuted,
          maxLines = 1,
          modifier = Modifier.weight(1f),
        )
        Box(
          modifier =
            Modifier
              .size(24.dp)
              .background(iconTint.copy(alpha = 0.14f), RoundedCornerShape(7.dp)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(14.dp),
          )
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = primaryValue,
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = badgeColor.copy(alpha = 0.16f),
          border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f)),
        ) {
          Text(
            text = badgeText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = badgeColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
          )
        }
      }

      Text(
        text = subtitle,
        style = MaterialTheme.typography.labelSmall,
        color = colors.textSecondary,
        maxLines = 1,
      )
    }
  }
}

private fun formatRelativeAge(updatedAtMs: Long): String {
  val diffSec = max(0L, (System.currentTimeMillis() - updatedAtMs) / 1000L)
  return when {
    diffSec < 60L -> "Just now"
    diffSec < 3600L -> "${diffSec / 60L}m ago"
    else -> "${diffSec / 3600L}h ago"
  }
}
