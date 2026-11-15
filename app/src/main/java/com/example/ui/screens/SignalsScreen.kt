package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.MarketPair
import com.example.domain.model.Timeframe
import com.example.notifications.SignalNotificationManager
import com.example.ui.components.EmptySignalCard
import com.example.ui.components.LiveSignalCard
import com.example.ui.components.SavedSignalJournalCard
import com.example.ui.components.SignalFilterBar
import com.example.ui.components.SummaryDashboard
import com.example.ui.theme.SweepPulseTheme
import com.example.ui.viewmodel.SignalStrengthFilter
import com.example.ui.viewmodel.TradingViewModel
import com.example.ui.viewmodel.UiState

@Composable
fun SignalsScreen(
  viewModel: TradingViewModel,
  onOpenTerminal: (MarketPair, Timeframe) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = SweepPulseTheme.colors
  val context = LocalContext.current
  val selectedTf by viewModel.selectedTimeframe.collectAsStateWithLifecycle()
  val selectedTfFilter by viewModel.selectedTimeframeFilter.collectAsStateWithLifecycle()
  val selectedPairFilter by viewModel.selectedPairFilter.collectAsStateWithLifecycle()
  val selectedAsset by viewModel.selectedAssetFilter.collectAsStateWithLifecycle()
  val selectedStrengthFilter by viewModel.selectedStrengthFilter.collectAsStateWithLifecycle()
  val sweepOnly by viewModel.sweepOnlyFilter.collectAsStateWithLifecycle()
  val liveSignalsMap by viewModel.liveSignals.collectAsStateWithLifecycle()
  val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
  val isMatrixScanning by viewModel.isMatrixScanning.collectAsStateWithLifecycle()
  val scanError by viewModel.scanError.collectAsStateWithLifecycle()
  val savedSignalsState by viewModel.savedSignals.collectAsStateWithLifecycle()

  var showJournalTab by rememberSaveable { mutableStateOf(false) }

  val filteredLiveSignals =
    liveSignalsMap.values
      .filter { sig -> selectedTfFilter == null || sig.timeframe == selectedTfFilter }
      .filter { sig -> selectedPairFilter == null || sig.pair == selectedPairFilter }
      .filter { sig -> selectedAsset == null || sig.pair.assetClass == selectedAsset }
      .filter { sig -> selectedStrengthFilter.matches(sig.combinedStrength) }
      .filter { sig -> !sweepOnly || sig.smcAnalysis.sweepDetected }
      .sortedByDescending {
        (if (it.smcAnalysis.sweepDetected) 100 else 0) + it.alignedCount
      }

  val savedList = (savedSignalsState as? UiState.Success)?.data.orEmpty()
  val filteredSavedSignals =
    savedList
      .filter { rec -> selectedPairFilter == null || rec.symbol == selectedPairFilter?.symbol }
      .filter { rec -> selectedTfFilter == null || rec.timeframe == selectedTfFilter?.label }
      .filter { rec ->
        selectedAsset == null || MarketPair.fromSymbol(rec.symbol).assetClass == selectedAsset
      }
      .filter { rec -> selectedStrengthFilter.matchesCode(rec.strength) }

  val hasCustomFilters =
    selectedPairFilter != null ||
      selectedAsset != null ||
      selectedStrengthFilter != SignalStrengthFilter.ALL ||
      sweepOnly ||
      selectedTfFilter == null

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(18.dp))
              .background(colors.surface)
        ) {
          Image(
            painter = painterResource(id = R.drawable.img_terminal_hero),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = if (colors.isDark) 0.35f else 0.12f,
            modifier = Modifier.matchParentSize(),
          )
          Box(
            modifier =
              Modifier
                .matchParentSize()
                .background(
                  Brush.horizontalGradient(
                    listOf(
                      colors.surface.copy(alpha = if (colors.isDark) 0.88f else 0.94f),
                      colors.surfaceElevated.copy(alpha = if (colors.isDark) 0.65f else 0.85f),
                    )
                  )
                )
          )
          Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = "SWEEPPULSE TERMINAL",
                style = MaterialTheme.typography.labelSmall,
                color = colors.liquidity,
              )
              Text(
                text = "SMC & 26-Indicator Signals",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = "BTCUSD • XAUUSD • 7 Major Forex Pairs",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
              )
            }
            IconButton(
              onClick = {
                if (selectedTfFilter == null) viewModel.scanFullMatrix()
                else viewModel.refreshCurrentTimeframe()
              },
              modifier = Modifier.testTag("refresh_signals_button"),
            ) {
              if (isScanning || isMatrixScanning) {
                CircularProgressIndicator(
                  modifier = Modifier.size(22.dp),
                  color = colors.bull,
                  strokeWidth = 2.5.dp,
                )
              } else {
                Icon(
                  imageVector = Icons.Rounded.Refresh,
                  contentDescription = "Refresh live market signals",
                  tint = colors.bull,
                )
              }
            }
          }
        }
      }

      if (isScanning || isMatrixScanning) {
        item {
          LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
            color = colors.bull,
            trackColor = colors.surfaceElevated,
          )
        }
      }

      item {
        SummaryDashboard(
          allLiveSignals = liveSignalsMap.values.toList(),
          savedSignals = savedList,
          selectedTimeframe = selectedTf,
          isScanning = isScanning || isMatrixScanning,
          onOpenSignal = onOpenTerminal,
        )
      }

      item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(MarketPair.entries) { pair ->
            val sig = liveSignalsMap[pair to selectedTf]
            val priceStr = sig?.let { pair.formatPrice(it.lastPrice) } ?: "—"
            val pct = sig?.changePct ?: 0.0
            val pctColor = if (pct >= 0) colors.bull else colors.bear
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = colors.surfaceElevated,
              border =
                BorderStroke(
                  1.dp,
                  if (selectedPairFilter == pair) colors.smcCyan else colors.border,
                ),
              modifier =
                Modifier
                  .clickable { onOpenTerminal(pair, selectedTf) }
                  .testTag("ticker_${pair.symbol}"),
            ) {
              Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
              ) {
                Text(
                  text = pair.symbol,
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text = priceStr,
                  style = MaterialTheme.typography.labelLarge,
                  color = colors.smcCyan,
                )
                Text(
                  text = if (sig != null) "${if (pct >= 0) "+" else ""}${"%.2f".format(pct)}%" else "Scanning",
                  style = MaterialTheme.typography.labelSmall,
                  color = if (sig != null) pctColor else colors.textMuted,
                )
              }
            }
          }
        }
      }

      item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            ModeTabButton(
              label = "Live Scanner (${filteredLiveSignals.size})",
              selected = !showJournalTab,
              onClick = { showJournalTab = false },
              modifier = Modifier.weight(1f).testTag("tab_live_scanner"),
            )
            ModeTabButton(
              label = "Saved Journal (${filteredSavedSignals.size})",
              selected = showJournalTab,
              onClick = { showJournalTab = true },
              modifier = Modifier.weight(1f).testTag("tab_saved_journal"),
            )
          }

          SignalFilterBar(
            selectedTimeframeFilter = selectedTfFilter,
            selectedPairFilter = selectedPairFilter,
            selectedAssetFilter = selectedAsset,
            selectedStrengthFilter = selectedStrengthFilter,
            sweepOnly = sweepOnly,
            onSelectTimeframe = viewModel::selectTimeframeFilter,
            onSelectPair = viewModel::selectPairFilter,
            onSelectAsset = viewModel::selectAssetFilter,
            onSelectStrength = viewModel::selectStrengthFilter,
            onToggleSweepOnly = viewModel::toggleSweepOnlyFilter,
            onClearAll = viewModel::clearAllFilters,
          )
        }
      }

      if (!scanError.isNullOrBlank() && !showJournalTab) {
        item {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            border = BorderStroke(1.dp, colors.bear.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = scanError!!,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
              )
              TextButton(onClick = { viewModel.refreshCurrentTimeframe() }) {
                Text("Retry", color = colors.liquidity)
              }
            }
          }
        }
      }

      if (!showJournalTab) {
        if (filteredLiveSignals.isEmpty() && !isScanning && !isMatrixScanning) {
          item {
            EmptySignalCard(
              title = if (hasCustomFilters) "No Signals Match Active Filters" else "Scanning Live Markets",
              subtitle =
                if (hasCustomFilters)
                  "Adjust Currency Pair, Signal Strength, or Timeframe filters—or reset to view all live SMC setups."
                else
                  "Tap Refresh to fetch real-time OHLCV candles and evaluate SMC + 26 TradingView indicators.",
              onRetry = {
                if (hasCustomFilters) viewModel.clearAllFilters()
                else viewModel.refreshCurrentTimeframe()
              },
              actionLabel = if (hasCustomFilters) "Clear Filters" else "Refresh Scanner",
            )
          }
        } else {
          items(filteredLiveSignals, key = { "${it.pair.symbol}_${it.timeframe.label}" }) { signal ->
            LiveSignalCard(
              signal = signal,
              onOpenTerminal = { onOpenTerminal(signal.pair, signal.timeframe) },
              onSaveSignal = { viewModel.saveSignalToFirestore(signal) },
              onPushAlert = {
                viewModel.saveSignalToFirestore(signal)
                val smc = signal.smcAnalysis
                val pair = signal.pair
                val sent =
                  SignalNotificationManager.showSignalNotification(
                    context = context,
                    title = "${smc.direction} ${pair.symbol} (${signal.timeframe.label}) • ${signal.alignedCount}/26 TV",
                    body =
                      "${smc.eventType.label} | Entry: ${pair.formatPrice(smc.entryPrice)} | SL: ${pair.formatPrice(smc.stopLoss)} | TP1: ${pair.formatPrice(smc.takeProfit1)}",
                    symbol = pair.symbol,
                    timeframe = signal.timeframe.label,
                  )
                if (!sent) {
                  viewModel.notifyBanner("Enable notification permission in Alerts tab to receive push alerts.")
                }
              },
            )
          }
        }
      } else {
        when (val state = savedSignalsState) {
          UiState.Loading -> {
            item {
              Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.bull)
              }
            }
          }
          is UiState.Error -> {
            item {
              EmptySignalCard(
                title = "Journal Sync Issue",
                subtitle = state.message,
                onRetry = {},
              )
            }
          }
          is UiState.Success -> {
            if (filteredSavedSignals.isEmpty()) {
              item {
                EmptySignalCard(
                  title = if (state.data.isEmpty()) "No Saved Signals Yet" else "No Matching Saved Signals",
                  subtitle =
                    if (state.data.isEmpty())
                      "Tap the bookmark icon on any live SMC Liquidity Sweep card to track Entry, SL, and TP outcomes in Firebase."
                    else
                      "No saved journal trades match the selected currency pair, strength, or timeframe filters.",
                  onRetry = {
                    if (state.data.isEmpty()) showJournalTab = false
                    else viewModel.clearAllFilters()
                  },
                  actionLabel = if (state.data.isEmpty()) "Browse Live Signals" else "Clear Filters",
                )
              }
            } else {
              items(filteredSavedSignals, key = { it.id }) { record ->
                SavedSignalJournalCard(
                  record = record,
                  onResolve = { status ->
                    viewModel.resolveSignal(record.id, status, "${record.notes} [$status]")
                  },
                  onRefreshLevels = { viewModel.refreshSavedSignalLevels(record) },
                  onDelete = { viewModel.deleteSignal(record.id) },
                  onOpenTerminal = {
                    onOpenTerminal(
                      MarketPair.fromSymbol(record.symbol),
                      Timeframe.fromLabel(record.timeframe),
                    )
                  },
                )
              }
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(16.dp)) }
    }
  }
}

@Composable
private fun ModeTabButton(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = SweepPulseTheme.colors
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = if (selected) colors.surfaceElevated else colors.surface,
    border = BorderStroke(1.dp, if (selected) colors.smcCyan else colors.border),
    modifier = modifier.clickable(onClick = onClick),
  ) {
    Box(
      modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        color = if (selected) colors.smcCyan else colors.textSecondary,
      )
    }
  }
}
