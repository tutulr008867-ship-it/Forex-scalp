package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.IndicatorReading
import com.example.domain.model.SignalVote
import com.example.ui.theme.SweepPulseTheme

@Composable
fun IndicatorCategorySummaryCard(
  title: String,
  buy: Int,
  neutral: Int,
  sell: Int,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = SweepPulseTheme.colors
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = if (selected) colors.surfaceCard else colors.surface,
    border = BorderStroke(1.dp, if (selected) colors.smcCyan else colors.border),
    modifier = modifier.clickable(onClick = onClick),
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("B:$buy", style = MaterialTheme.typography.labelSmall, color = colors.bull)
        Text("N:$neutral", style = MaterialTheme.typography.labelSmall, color = colors.neutralSlate)
        Text("S:$sell", style = MaterialTheme.typography.labelSmall, color = colors.bear)
      }
    }
  }
}

@Composable
fun IndicatorRowItem(reading: IndicatorReading) {
  val colors = SweepPulseTheme.colors
  val voteColor =
    when (reading.vote) {
      SignalVote.BUY -> colors.bull
      SignalVote.SELL -> colors.bear
      SignalVote.NEUTRAL -> colors.neutralSlate
    }
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = colors.surface,
    border = BorderStroke(1.dp, colors.border.copy(alpha = 0.65f)),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "${reading.index}. ${reading.name}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = reading.category.label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMuted,
          )
        }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            text = reading.formattedValue,
            style = MaterialTheme.typography.labelLarge,
            color = colors.textSecondary,
          )
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = voteColor.copy(alpha = 0.14f),
          ) {
            Text(
              text = reading.vote.label,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = voteColor,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
          }
        }
      }
      HorizontalDivider(color = colors.border.copy(alpha = 0.25f))
    }
  }
}
