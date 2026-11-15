package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.AssetClass
import com.example.domain.model.MarketPair
import com.example.domain.model.Timeframe
import com.example.ui.theme.SweepPulseTheme
import com.example.ui.viewmodel.SignalStrengthFilter

@Composable
fun SignalFilterBar(
  selectedTimeframeFilter: Timeframe?,
  selectedPairFilter: MarketPair?,
  selectedAssetFilter: AssetClass?,
  selectedStrengthFilter: SignalStrengthFilter,
  sweepOnly: Boolean,
  onSelectTimeframe: (Timeframe?) -> Unit,
  onSelectPair: (MarketPair?) -> Unit,
  onSelectAsset: (AssetClass?) -> Unit,
  onSelectStrength: (SignalStrengthFilter) -> Unit,
  onToggleSweepOnly: () -> Unit,
  onClearAll: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = SweepPulseTheme.colors
  val activeFilterCount =
    listOf(
      selectedTimeframeFilter == null,
      selectedPairFilter != null,
      selectedAssetFilter != null,
      selectedStrengthFilter != SignalStrengthFilter.ALL,
      sweepOnly,
    ).count { it }

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = colors.surfaceElevated,
    border = BorderStroke(1.dp, colors.border),
    modifier = modifier.fillMaxWidth().testTag("signal_filter_bar"),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
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
        ) {
          Icon(
            imageVector = Icons.Rounded.FilterList,
            contentDescription = null,
            tint = colors.smcCyan,
            modifier = Modifier.size(16.dp),
          )
          Text(
            text = "FILTERS",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          if (activeFilterCount > 0) {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = colors.smcCyanContainer,
              border = BorderStroke(1.dp, colors.smcCyan.copy(alpha = 0.4f)),
            ) {
              Text(
                text = "$activeFilterCount active",
                style = MaterialTheme.typography.labelSmall,
                color = colors.smcCyan,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
          }
        }

        if (activeFilterCount > 0) {
          Surface(
            onClick = onClearAll,
            shape = RoundedCornerShape(8.dp),
            color = colors.surfaceCard,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.testTag("reset_filters_button"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Clear all filters",
                tint = colors.textSecondary,
                modifier = Modifier.size(13.dp),
              )
              Text(
                text = "Reset",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
              )
            }
          }
        }
      }

      // Timeframe Filter Strip (All + 1m, 5m, 15m, 30m)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        TimeframePill(
          label = "All TFs",
          selected = selectedTimeframeFilter == null,
          onClick = { onSelectTimeframe(null) },
          modifier = Modifier.weight(1.25f).testTag("tf_chip_all"),
        )
        Timeframe.entries.forEach { tf ->
          TimeframePill(
            label = tf.label,
            selected = selectedTimeframeFilter == tf,
            onClick = { onSelectTimeframe(tf) },
            modifier = Modifier.weight(1f).testTag("tf_chip_${tf.label}"),
          )
        }
      }

      // Currency Pair & Asset Class Filter Chips
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        FilterChip(
          selected = selectedPairFilter == null && selectedAssetFilter == null,
          onClick = {
            onSelectPair(null)
            onSelectAsset(null)
          },
          label = { Text("All Pairs") },
          modifier = Modifier.testTag("pair_filter_all"),
        )
        MarketPair.entries.forEach { pair ->
          val selected = selectedPairFilter == pair
          FilterChip(
            selected = selected,
            onClick = { onSelectPair(if (selected) null else pair) },
            label = { Text(pair.symbol) },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = colors.smcCyanContainer,
                selectedLabelColor = colors.smcCyan,
              ),
            modifier = Modifier.testTag("pair_filter_${pair.symbol}"),
          )
        }
        AssetClass.entries.forEach { asset ->
          val selected = selectedAssetFilter == asset
          FilterChip(
            selected = selected,
            onClick = { onSelectAsset(if (selected) null else asset) },
            label = { Text(asset.label) },
            modifier = Modifier.testTag("asset_filter_${asset.name}"),
          )
        }
      }

      // Signal Strength & Liquidity Sweep Chips
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        FilterChip(
          selected = sweepOnly,
          onClick = onToggleSweepOnly,
          label = { Text("Sweeps Only") },
          colors =
            FilterChipDefaults.filterChipColors(
              selectedContainerColor = colors.liquidityContainer,
              selectedLabelColor = colors.liquidity,
            ),
          modifier = Modifier.testTag("sweep_only_chip"),
        )
        SignalStrengthFilter.entries.forEach { filter ->
          val selected = selectedStrengthFilter == filter
          val accent =
            when (filter) {
              SignalStrengthFilter.STRONG_BUY,
              SignalStrengthFilter.BUY -> colors.bull
              SignalStrengthFilter.STRONG_SELL,
              SignalStrengthFilter.SELL -> colors.bear
              SignalStrengthFilter.STRONG_ONLY -> colors.liquidity
              else -> colors.smcCyan
            }
          val container =
            when (filter) {
              SignalStrengthFilter.STRONG_BUY,
              SignalStrengthFilter.BUY -> colors.bullContainer
              SignalStrengthFilter.STRONG_SELL,
              SignalStrengthFilter.SELL -> colors.bearContainer
              SignalStrengthFilter.STRONG_ONLY -> colors.liquidityContainer
              else -> colors.smcCyanContainer
            }
          FilterChip(
            selected = selected,
            onClick = { onSelectStrength(filter) },
            label = { Text(filter.label) },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = container,
                selectedLabelColor = accent,
              ),
            modifier = Modifier.testTag("strength_filter_${filter.name}"),
          )
        }
      }
    }
  }
}

@Composable
private fun TimeframePill(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = SweepPulseTheme.colors
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = if (selected) colors.bull.copy(alpha = 0.16f) else colors.surface,
    border = BorderStroke(1.dp, if (selected) colors.bull else colors.border),
    modifier = modifier.clickable(onClick = onClick),
  ) {
    Box(
      modifier = Modifier.padding(vertical = 8.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        color = if (selected) colors.bull else colors.textSecondary,
        maxLines = 1,
      )
    }
  }
}
