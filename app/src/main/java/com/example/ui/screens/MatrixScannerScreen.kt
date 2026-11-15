package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.MarketPair
import com.example.domain.model.Timeframe
import com.example.ui.theme.SweepPulseTheme
import com.example.ui.viewmodel.TradingViewModel

@Composable
fun MatrixScannerScreen(
  viewModel: TradingViewModel,
  onOpenTerminal: (MarketPair, Timeframe) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = SweepPulseTheme.colors
  val liveMap by viewModel.liveSignals.collectAsStateWithLifecycle()
  val isMatrixScanning by viewModel.isMatrixScanning.collectAsStateWithLifecycle()

  LaunchedEffect(Unit) {
    if (liveMap.size < MarketPair.entries.size * 2) {
      viewModel.scanFullMatrix()
    }
  }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 880.dp).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      item {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = colors.surfaceElevated,
          border = BorderStroke(1.dp, colors.border),
          modifier = Modifier.fillMaxWidth(),
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
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "9×4 Multi-Timeframe Matrix",
                  style = MaterialTheme.typography.titleLarge,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text = "Live 26-indicator confluence & SMC liquidity sweep radar across 1m, 5m, 15m, 30m.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = colors.textSecondary,
                )
              }
            }
            Button(
              onClick = { viewModel.scanFullMatrix() },
              enabled = !isMatrixScanning,
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = colors.smcCyan,
                  contentColor = colors.onAccent,
                ),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth().testTag("scan_all_matrix_button"),
            ) {
              if (isMatrixScanning) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = colors.onAccent, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Scanning All 36 Charts...", fontWeight = FontWeight.Bold)
              } else {
                Icon(Icons.Rounded.Radar, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Scan All Pairs & Timeframes", fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }

      if (isMatrixScanning) {
        item {
          LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
            color = colors.smcCyan,
            trackColor = colors.surfaceElevated,
          )
        }
      }

      item {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "PAIR",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMuted,
            modifier = Modifier.weight(1.3f),
          )
          Timeframe.entries.forEach { tf ->
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
              Text(
                text = tf.label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.smcCyan,
              )
            }
          }
        }
      }

      items(MarketPair.entries) { pair ->
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = colors.surfaceElevated,
          border = BorderStroke(1.dp, colors.border),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Column(modifier = Modifier.weight(1.3f)) {
              Text(
                text = pair.symbol,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
              )
              val refSig = Timeframe.entries.firstNotNullOfOrNull { liveMap[pair to it] }
              Text(
                text = refSig?.let { pair.formatPrice(it.lastPrice) } ?: pair.assetClass.label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
              )
            }

            Timeframe.entries.forEach { tf ->
              val cell = liveMap[pair to tf]
              val isBuy = cell?.smcAnalysis?.direction == "BUY"
              val hasSweep = cell?.smcAnalysis?.sweepDetected == true
              val bg =
                when {
                  cell == null -> colors.surface
                  isBuy -> colors.bullContainer
                  else -> colors.bearContainer
                }
              val fg =
                when {
                  cell == null -> colors.textMuted
                  isBuy -> colors.bull
                  else -> colors.bear
                }

              Surface(
                shape = RoundedCornerShape(8.dp),
                color = bg,
                border =
                  BorderStroke(
                    1.dp,
                    if (hasSweep) colors.liquidity else fg.copy(alpha = 0.25f),
                  ),
                modifier =
                  Modifier
                    .weight(1f)
                    .clickable { onOpenTerminal(pair, tf) }
                    .testTag("matrix_cell_${pair.symbol}_${tf.label}"),
              ) {
                Column(
                  modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                  ) {
                    if (hasSweep) {
                      Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = "Sweep",
                        tint = colors.liquidity,
                        modifier = Modifier.size(11.dp),
                      )
                    }
                    Text(
                      text = cell?.smcAnalysis?.direction ?: "—",
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.Bold,
                      color = fg,
                    )
                  }
                  Text(
                    text = if (cell != null) "${cell.alignedCount}/26" else "...",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (cell != null) fg.copy(alpha = 0.9f) else colors.textMuted,
                  )
                }
              }
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(16.dp)) }
    }
  }
}
