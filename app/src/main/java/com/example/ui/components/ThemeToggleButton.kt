package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
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
import com.example.ui.theme.SweepPulseTheme

@Composable
fun ThemeToggleButton(
  isDarkTheme: Boolean,
  onToggleTheme: () -> Unit,
  modifier: Modifier = Modifier,
  showLabel: Boolean = true,
  tag: String = "global_theme_toggle",
) {
  val colors = SweepPulseTheme.colors
  Surface(
    onClick = onToggleTheme,
    shape = RoundedCornerShape(12.dp),
    color = colors.surfaceElevated,
    border = BorderStroke(1.dp, colors.border),
    modifier = modifier.heightIn(min = 40.dp).testTag(tag),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(
        imageVector = if (isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
        contentDescription =
          if (isDarkTheme) "Switch to Light Mode" else "Switch to Dark Mode",
        tint = if (isDarkTheme) colors.liquidity else colors.smcCyan,
        modifier = Modifier.size(16.dp),
      )
      if (showLabel) {
        Text(
          text = if (isDarkTheme) "Light" else "Dark",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
    }
  }
}
