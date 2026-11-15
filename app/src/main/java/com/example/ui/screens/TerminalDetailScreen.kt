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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.IndicatorCategory
import com.example.domain.model.MarketPair
import com.example.domain.model.Timeframe
import com.example.notifications.SignalNotificationManager
import com.example.ui.components.ConfluenceBar26
import com.example.ui.components.DirectionBadge
import com.example.ui.components.IndicatorCategorySummaryCard
import com.example.ui.components.IndicatorRowItem
import com.example.ui.components.SmcCandlestickChart
import com.example.ui.components.SweepStatusBadge
import com.example.ui.components.ThemeToggleButton
import com.example.ui.components.TradeLevelBox
import com.example.ui.theme.SweepPulseTheme
import com.example.ui.viewmodel.TradingViewModel

@Composable
fun TerminalDetailScreen(
  pair: MarketPair,
  initialTimeframe: Timeframe,
  viewModel: TradingViewModel,
  isDarkTheme: Boolean,
  onToggleTheme: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = SweepPulseTheme.colors
  val context = LocalContext.current
  var activeTf by remember(pair, initialTimeframe) { mutableStateOf(initialTimeframe) }
  var selectedCategory by remember { mutableStateOf<IndicatorCategory?>(null) }

  val liveMap by viewModel.liveSignals.collectAsStateWithLifecycle()
  val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()

  LaunchedEffect(pair) {
    viewModel.loadTerminalPair(pair)
  }

  val signal = liveMap[pair to activeTf]

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 880.dp).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
          ) {
            IconButton(
              onClick = onBack,
              modifier = Modifier.testTag("terminal_back_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back to Signals",
                tint = MaterialTheme.colorScheme.onBackground,
              )
            }
            Column {
              Text(
                text = pair.displayName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
              )
              Text(
                text =
                  signal?.let {
                    "${pair.formatPrice(it.lastPrice)}  (${if (it.changePct >= 0) "+" else ""}${"%.2f".format(it.changePct)}%)"
                  } ?: "Loading market feed...",
                style = MaterialTheme.typography.labelLarge,
                color = if ((signal?.changePct ?: 0.0) >= 0.0) colors.bull else colors.bear,
              )
            }
          }
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            signal?.let { DirectionBadge(strength = it.combinedStrength) }
            ThemeToggleButton(
              isDarkTheme = isDarkTheme,
              onToggleTheme = onToggleTheme,
              showLabel = false,
              tag = "terminal_theme_toggle",
            )
            IconButton(
              onClick = { viewModel.loadTerminalPair(pair) },
              modifier = Modifier.testTag("terminal_refresh_button"),
            ) {
              if (isScanning) {
                CircularProgressIndicator(
                  modifier = Modifier.size(20.dp),
                  color = colors.bull,
                  strokeWidth = 2.dp,
                )
              } else {
                Icon(
                  imageVector = Icons.Rounded.Refresh,
                  contentDescription = "Refresh pair",
                  tint = colors.smcCyan,
                )
              }
            }
          }
        }
      }

      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Timeframe.entries.forEach { tf ->
            val tfSig = liveMap[pair to tf]
            val selected = tf == activeTf
            val tfColor =
              when (tfSig?.smcAnalysis?.direction) {
                "BUY" -> colors.bull
                "SELL" -> colors.bear
                else -> colors.textSecondary
              }
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = if (selected) colors.surfaceCard else colors.surfaceElevated,
              border =
                BorderStroke(
                  1.5.dp,
                  if (selected) colors.smcCyan else colors.border,
                ),
              modifier =
                Modifier
                  .weight(1f)
                  .clickable { activeTf = tf }
                  .testTag("terminal_tf_${tf.label}"),
            ) {
              Column(
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
              ) {
                Text(
                  text = tf.label,
                  style = MaterialTheme.typography.labelLarge,
                  color = if (selected) colors.smcCyan else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text =
                    if (tfSig != null) "${tfSig.smcAnalysis.direction} ${tfSig.alignedCount}/26"
                    else "—",
                  style = MaterialTheme.typography.labelSmall,
                  color = tfColor,
                )
                if (tfSig?.smcAnalysis?.sweepDetected == true) {
                  Text(
                    text = "SWEEP",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.liquidity,
                  )
                }
              }
            }
          }
        }
      }

      if (signal == null) {
        item {
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.surfaceElevated,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Box(
              modifier = Modifier.fillMaxWidth().height(260.dp).padding(16.dp),
              contentAlignment = Alignment.Center,
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                if (isScanning) {
                  CircularProgressIndicator(color = colors.bull)
                  Text(
                    text = "Fetching ${pair.symbol} ${activeTf.label} live OHLCV candles...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                  )
                } else {
                  Text(
                    text = "Unable to load ${pair.symbol} (${activeTf.label}) candles",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  Button(
                    onClick = { viewModel.loadTerminalPair(pair) },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.smcCyan, contentColor = colors.onAccent),
                  ) {
                    Text("Retry Live Feed", fontWeight = FontWeight.Bold)
                  }
                }
              }
            }
          }
        }
      } else {
        val smc = signal.smcAnalysis
        val ind = signal.indicatorSummary

        item {
          SmcCandlestickChart(signal = signal)
        }

        item {
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.surfaceElevated,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth(),
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
                Text(
                  text = "SMC Execution Plan",
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                SweepStatusBadge(
                  sweepDetected = smc.sweepDetected,
                  eventLabel = smc.eventType.code,
                  isBullish = smc.direction == "BUY",
                )
              }

              Text(
                text = smc.structureNotes,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                TradeLevelBox("ENTRY", pair.formatPrice(smc.entryPrice), colors.smcCyan, Modifier.weight(1f))
                TradeLevelBox("STOP LOSS", pair.formatPrice(smc.stopLoss), colors.bear, Modifier.weight(1f))
                TradeLevelBox("SWEPT LIQ", pair.formatPrice(smc.sweptLiquidityLevel), colors.liquidity, Modifier.weight(1f))
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                TradeLevelBox("TP1 (1.5R)", pair.formatPrice(smc.takeProfit1), colors.bull, Modifier.weight(1f))
                TradeLevelBox("TP2 (2.5R)", pair.formatPrice(smc.takeProfit2), colors.bull, Modifier.weight(1f))
                TradeLevelBox("TP3 (3.8R)", pair.formatPrice(smc.takeProfit3), colors.bull, Modifier.weight(1f))
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Button(
                  onClick = { viewModel.saveSignalToFirestore(signal) },
                  colors =
                    ButtonDefaults.buttonColors(
                      containerColor = colors.bull,
                      contentColor = colors.onAccent,
                    ),
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.weight(1f).testTag("terminal_save_button"),
                ) {
                  Icon(Icons.Rounded.BookmarkAdd, null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("Save Trade", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                  onClick = {
                    val sent =
                      SignalNotificationManager.showSignalNotification(
                        context = context,
                        title = "${smc.direction} ${pair.symbol} (${activeTf.label}) • ${signal.alignedCount}/26 TV",
                        body =
                          "${smc.eventType.label} | Entry: ${pair.formatPrice(smc.entryPrice)} | SL: ${pair.formatPrice(smc.stopLoss)} | TP1: ${pair.formatPrice(smc.takeProfit1)}",
                        symbol = pair.symbol,
                        timeframe = activeTf.label,
                      )
                    viewModel.notifyBanner(
                      if (sent) "Entry & SL push alert sent for ${pair.symbol}"
                      else "Enable notification permission in Alerts tab first."
                    )
                  },
                  shape = RoundedCornerShape(12.dp),
                  border = BorderStroke(1.dp, colors.liquidity),
                  modifier = Modifier.weight(1f).testTag("terminal_push_button"),
                ) {
                  Icon(Icons.Rounded.NotificationsActive, null, tint = colors.liquidity, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("Push Alert", color = colors.liquidity)
                }
              }
            }
          }
        }

        item {
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.surfaceElevated,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Text(
                text = "26 TradingView Technical Indicators",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )

              ConfluenceBar26(
                buyCount = ind.buyCount,
                neutralCount = ind.neutralCount,
                sellCount = ind.sellCount,
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                IndicatorCategorySummaryCard(
                  title = "Oscillators (11)",
                  buy = ind.oscillatorBuy,
                  neutral = ind.oscillatorNeutral,
                  sell = ind.oscillatorSell,
                  selected = selectedCategory == IndicatorCategory.OSCILLATOR,
                  onClick = {
                    selectedCategory =
                      if (selectedCategory == IndicatorCategory.OSCILLATOR) null
                      else IndicatorCategory.OSCILLATOR
                  },
                  modifier = Modifier.weight(1f),
                )
                IndicatorCategorySummaryCard(
                  title = "Moving Avgs (15)",
                  buy = ind.maBuy,
                  neutral = ind.maNeutral,
                  sell = ind.maSell,
                  selected = selectedCategory == IndicatorCategory.MOVING_AVERAGE,
                  onClick = {
                    selectedCategory =
                      if (selectedCategory == IndicatorCategory.MOVING_AVERAGE) null
                      else IndicatorCategory.MOVING_AVERAGE
                  },
                  modifier = Modifier.weight(1f),
                )
              }
            }
          }
        }

        val filteredReadings =
          ind.readings.filter { selectedCategory == null || it.category == selectedCategory }

        items(filteredReadings, key = { it.index }) { reading ->
          IndicatorRowItem(reading = reading)
        }
      }

      item { Spacer(modifier = Modifier.height(16.dp)) }
    }
  }
}
