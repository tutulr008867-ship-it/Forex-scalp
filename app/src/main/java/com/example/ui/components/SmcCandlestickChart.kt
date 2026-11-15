package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.LiveMarketSignal
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SweepPulseTheme
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun SmcCandlestickChart(
  signal: LiveMarketSignal,
  modifier: Modifier = Modifier,
) {
  val colors = SweepPulseTheme.colors
  val candles = remember(signal.candles) { signal.candles.takeLast(55) }
  val startOffset = max(0, signal.candles.size - candles.size)
  var selectedLocalIdx by remember(signal.pair, signal.timeframe) { mutableStateOf<Int?>(null) }
  val textMeasurer = rememberTextMeasurer()

  val labelStyle = remember(colors.textSecondary) {
    TextStyle(
      fontFamily = JetBrainsMonoFontFamily,
      fontSize = 9.sp,
      fontWeight = FontWeight.Medium,
      color = colors.textSecondary,
    )
  }

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(colors.surface)
        .border(1.dp, colors.border, RoundedCornerShape(16.dp))
        .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    val inspected = selectedLocalIdx?.let { candles.getOrNull(it) } ?: candles.lastOrNull()
    if (inspected != null) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text =
            "O ${signal.pair.formatPrice(inspected.open)}  H ${signal.pair.formatPrice(inspected.high)}  L ${signal.pair.formatPrice(inspected.low)}  C ${signal.pair.formatPrice(inspected.close)}",
          style = MaterialTheme.typography.labelSmall,
          color = if (inspected.close >= inspected.open) colors.bull else colors.bear,
        )
        if (selectedLocalIdx != null) {
          Text(
            text = "TAP TO RESET",
            style = MaterialTheme.typography.labelSmall,
            color = colors.liquidity,
          )
        }
      }
    }

    Box(modifier = Modifier.fillMaxWidth().height(250.dp)) {
      Canvas(
        modifier =
          Modifier
            .fillMaxSize()
            .pointerInput(candles) {
              detectTapGestures { offset ->
                val chartWidth = size.width * 0.84f
                if (candles.isNotEmpty() && chartWidth > 0f) {
                  val idx =
                    ((offset.x / chartWidth) * candles.size)
                      .roundToInt()
                      .coerceIn(0, candles.lastIndex)
                  selectedLocalIdx = if (selectedLocalIdx == idx) null else idx
                }
              }
            }
            .pointerInput(candles) {
              detectDragGestures { change, _ ->
                val chartWidth = size.width * 0.84f
                if (candles.isNotEmpty() && chartWidth > 0f) {
                  val idx =
                    ((change.position.x / chartWidth) * candles.size)
                      .roundToInt()
                      .coerceIn(0, candles.lastIndex)
                  selectedLocalIdx = idx
                }
              }
            }
      ) {
        if (candles.isEmpty()) return@Canvas

        val rightAxisWidth = size.width * 0.17f
        val chartWidth = size.width - rightAxisWidth
        val chartHeight = size.height

        val smc = signal.smcAnalysis
        val candleMin = candles.minOf { it.low }
        val candleMax = candles.maxOf { it.high }
        val rawMin = minOf(candleMin, smc.stopLoss, smc.takeProfit1, smc.sweptLiquidityLevel)
        val rawMax = maxOf(candleMax, smc.stopLoss, smc.takeProfit1, smc.sweptLiquidityLevel)
        val padding = max((rawMax - rawMin) * 0.08, rawMax * 0.0005)
        val minP = rawMin - padding
        val maxP = rawMax + padding
        val range = max(maxP - minP, 1e-9)

        fun yFor(price: Double): Float {
          val norm = ((price - minP) / range).toFloat().coerceIn(0f, 1f)
          return chartHeight * (1f - norm)
        }

        val gridDash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
        for (step in 1..4) {
          val y = chartHeight * (step / 5f)
          drawLine(
            color = colors.border.copy(alpha = 0.55f),
            start = Offset(0f, y),
            end = Offset(chartWidth, y),
            strokeWidth = 1f,
            pathEffect = gridDash,
          )
        }

        val stepX = chartWidth / candles.size.toFloat()
        val candleBodyWidth = max(3f, stepX * 0.56f)

        for (fvg in smc.fairValueGaps) {
          val localStart = max(0, fvg.startIndex - startOffset)
          if (localStart >= candles.size) continue
          val topY = yFor(fvg.top)
          val bottomY = yFor(fvg.bottom)
          val x = localStart * stepX
          val fvgColor =
            if (fvg.isBullish) colors.smcCyan.copy(alpha = 0.14f)
            else colors.bear.copy(alpha = 0.12f)
          drawRect(
            color = fvgColor,
            topLeft = Offset(x, min(topY, bottomY)),
            size = Size(chartWidth - x, max(2f, kotlin.math.abs(bottomY - topY))),
          )
        }

        for (ob in smc.orderBlocks) {
          val localStart = max(0, ob.startIndex - startOffset)
          if (localStart >= candles.size) continue
          val topY = yFor(ob.top)
          val bottomY = yFor(ob.bottom)
          val x = localStart * stepX
          val obColor =
            if (ob.isBullish) colors.bull.copy(alpha = 0.14f)
            else colors.bear.copy(alpha = 0.14f)
          drawRect(
            color = obColor,
            topLeft = Offset(x, min(topY, bottomY)),
            size = Size(chartWidth - x, max(2f, kotlin.math.abs(bottomY - topY))),
          )
        }

        fun drawPriceLine(price: Double, color: Color, tag: String, dashed: Boolean = true) {
          val y = yFor(price)
          drawLine(
            color = color.copy(alpha = 0.85f),
            start = Offset(0f, y),
            end = Offset(chartWidth, y),
            strokeWidth = 1.6f,
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f) else null,
          )
          val pillTextColor = colors.onAccent
          val measured = textMeasurer.measure(tag, style = labelStyle.copy(color = pillTextColor))
          val boxH = measured.size.height + 6f
          val boxY = (y - boxH / 2f).coerceIn(0f, chartHeight - boxH)
          drawRect(
            color = color,
            topLeft = Offset(chartWidth + 3f, boxY),
            size = Size(rightAxisWidth - 3f, boxH),
          )
          drawText(
            textMeasurer = textMeasurer,
            text = tag,
            topLeft = Offset(chartWidth + 6f, boxY + 3f),
            style = labelStyle.copy(color = pillTextColor, fontWeight = FontWeight.Bold),
          )
        }

        if (smc.sweepDetected) {
          drawPriceLine(smc.sweptLiquidityLevel, colors.liquidity, "LIQ")
        }
        drawPriceLine(smc.stopLoss, colors.bear, "SL", dashed = false)
        drawPriceLine(smc.entryPrice, colors.smcCyan, "ENT", dashed = true)
        drawPriceLine(smc.takeProfit1, colors.bull, "TP1", dashed = true)

        for (i in candles.indices) {
          val c = candles[i]
          val centerX = i * stepX + stepX / 2f
          val isBull = c.close >= c.open
          val color = if (isBull) colors.bull else colors.bear

          val highY = yFor(c.high)
          val lowY = yFor(c.low)
          val openY = yFor(c.open)
          val closeY = yFor(c.close)

          drawLine(
            color = color,
            start = Offset(centerX, highY),
            end = Offset(centerX, lowY),
            strokeWidth = 1.6f,
          )

          val bodyTop = min(openY, closeY)
          val bodyHeight = max(2.2f, kotlin.math.abs(closeY - openY))
          drawRect(
            color = color,
            topLeft = Offset(centerX - candleBodyWidth / 2f, bodyTop),
            size = Size(candleBodyWidth, bodyHeight),
          )
        }

        val marker = smc.sweepMarker
        if (marker != null) {
          val localSweepIdx = marker.candleIndex - startOffset
          if (localSweepIdx in candles.indices) {
            val sx = localSweepIdx * stepX + stepX / 2f
            val sy = yFor(marker.sweepExtreme)
            drawCircle(
              color = colors.liquidity.copy(alpha = 0.28f),
              radius = 15f,
              center = Offset(sx, sy),
            )
            drawCircle(
              color = colors.liquidity,
              radius = 10f,
              center = Offset(sx, sy),
              style = Stroke(width = 2.4f),
            )
          }
        }

        selectedLocalIdx?.let { idx ->
          if (idx in candles.indices) {
            val cx = idx * stepX + stepX / 2f
            val cy = yFor(candles[idx].close)
            drawLine(
              color = colors.textMuted,
              start = Offset(cx, 0f),
              end = Offset(cx, chartHeight),
              strokeWidth = 1.2f,
              pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f),
            )
            drawLine(
              color = colors.textMuted,
              start = Offset(0f, cy),
              end = Offset(chartWidth, cy),
              strokeWidth = 1.0f,
              pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f),
            )
          }
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      LegendDot("ENT", colors.smcCyan)
      LegendDot("SL", colors.bear)
      LegendDot("TP1", colors.bull)
      LegendDot("LIQ SWEEP", colors.liquidity)
      LegendDot("FVG/OB", colors.smcCyan.copy(alpha = 0.6f))
    }
  }
}

@Composable
private fun LegendDot(label: String, color: Color) {
  val colors = SweepPulseTheme.colors
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Box(
      modifier =
        Modifier
          .size(8.dp)
          .background(color, RoundedCornerShape(4.dp))
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = colors.textSecondary,
    )
  }
}
