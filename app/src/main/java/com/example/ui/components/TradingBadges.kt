package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.SignalStrength
import com.example.ui.theme.SweepPulseTheme

@Composable
fun strengthColors(strength: SignalStrength): Pair<Color, Color> {
  val colors = SweepPulseTheme.colors
  return when (strength) {
    SignalStrength.STRONG_BUY,
    SignalStrength.BUY -> colors.bull to colors.bullContainer
    SignalStrength.STRONG_SELL,
    SignalStrength.SELL -> colors.bear to colors.bearContainer
    SignalStrength.NEUTRAL -> colors.neutralSlate to colors.surfaceCard
  }
}

@Composable
fun DirectionBadge(
  strength: SignalStrength,
  modifier: Modifier = Modifier,
) {
  val (fg, bg) = strengthColors(strength)
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = bg,
    border = BorderStroke(1.dp, fg.copy(alpha = 0.45f)),
    modifier = modifier,
  ) {
    Text(
      text = strength.label,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Bold,
      color = fg,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )
  }
}

@Composable
fun SweepStatusBadge(
  sweepDetected: Boolean,
  eventLabel: String,
  isBullish: Boolean,
  modifier: Modifier = Modifier,
) {
  val colors = SweepPulseTheme.colors
  val accent = if (sweepDetected) colors.liquidity else colors.smcCyan
  val bg = if (sweepDetected) colors.liquidityContainer else colors.smcCyanContainer
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = bg,
    border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
    modifier = modifier,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      if (sweepDetected) {
        Icon(
          imageVector = Icons.Rounded.Bolt,
          contentDescription = "Liquidity Sweep",
          tint = colors.liquidity,
          modifier = Modifier.size(13.dp),
        )
      }
      Text(
        text = eventLabel,
        style = MaterialTheme.typography.labelSmall,
        color = accent,
        maxLines = 1,
      )
    }
  }
}

@Composable
fun ConfluenceBar26(
  buyCount: Int,
  neutralCount: Int,
  sellCount: Int,
  modifier: Modifier = Modifier,
) {
  val colors = SweepPulseTheme.colors
  val total = (buyCount + neutralCount + sellCount).coerceAtLeast(1).toFloat()
  val buyFrac = (buyCount / total).coerceIn(0f, 1f)
  val neutralFrac = (neutralCount / total).coerceIn(0f, 1f)
  val sellFrac = (sellCount / total).coerceIn(0f, 1f)

  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = "BUY $buyCount",
        style = MaterialTheme.typography.labelSmall,
        color = colors.bull,
      )
      Text(
        text = "NEUTRAL $neutralCount  •  26 TV INDICATORS",
        style = MaterialTheme.typography.labelSmall,
        color = colors.textSecondary,
      )
      Text(
        text = "SELL $sellCount",
        style = MaterialTheme.typography.labelSmall,
        color = colors.bear,
      )
    }

    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(7.dp)
          .clip(RoundedCornerShape(4.dp))
          .background(colors.surfaceCard),
      horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      if (buyFrac > 0f) {
        Box(
          modifier =
            Modifier
              .weight(buyFrac)
              .fillMaxHeight()
              .background(colors.bull, RoundedCornerShape(4.dp))
        )
      }
      if (neutralFrac > 0f) {
        Box(
          modifier =
            Modifier
              .weight(neutralFrac)
              .fillMaxHeight()
              .background(colors.neutralSlate.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
        )
      }
      if (sellFrac > 0f) {
        Box(
          modifier =
            Modifier
              .weight(sellFrac)
              .fillMaxHeight()
              .background(colors.bear, RoundedCornerShape(4.dp))
        )
      }
    }
  }
}

@Composable
fun TradeLevelBox(
  label: String,
  value: String,
  accent: Color,
  modifier: Modifier = Modifier,
) {
  val colors = SweepPulseTheme.colors
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = colors.surfaceCard,
    border = BorderStroke(1.dp, colors.border),
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = colors.textSecondary,
      )
      Text(
        text = value,
        style = MaterialTheme.typography.labelLarge,
        color = accent,
        maxLines = 1,
      )
    }
  }
}
