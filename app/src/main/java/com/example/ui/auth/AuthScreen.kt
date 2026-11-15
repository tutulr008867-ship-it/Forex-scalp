package com.example.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CandlestickChart
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.ui.components.ThemeToggleButton
import com.example.ui.theme.SweepPulseTheme

@Composable
fun AuthScreen(
  isDarkTheme: Boolean,
  onToggleTheme: () -> Unit,
  onAuthSuccess: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = SweepPulseTheme.colors
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }
  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    attemptAutoSignIn(
      context = context,
      credentialManager = credentialManager,
      onAuthSuccess = onAuthSuccess,
      onUnauthenticated = {},
      scope = scope,
    )
  }

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(colors.bg)
        .systemBarsPadding(),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier =
        Modifier
          .widthIn(max = 560.dp)
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 24.dp, vertical = 20.dp),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "SWEEPPULSE PRO",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = colors.smcCyan,
          )
          ThemeToggleButton(
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme,
            tag = "auth_theme_toggle",
          )
        }

        // Hero Banner Artwork
        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .height(190.dp)
              .clip(RoundedCornerShape(20.dp))
              .background(colors.surface),
        ) {
          Image(
            painter = painterResource(id = R.drawable.img_terminal_hero),
            contentDescription = "SweepPulse Quantitative SMC Terminal",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
          Box(
            modifier =
              Modifier
                .fillMaxSize()
                .background(
                  Brush.verticalGradient(
                    colors =
                      listOf(
                        Color.Transparent,
                        Color(0xFF0A0E17).copy(alpha = 0.88f),
                      )
                  )
                )
          )
          Row(
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = colors.bull.copy(alpha = 0.2f),
              border = BorderStroke(1.dp, colors.bull.copy(alpha = 0.55f)),
            ) {
              Icon(
                imageVector = Icons.Rounded.CandlestickChart,
                contentDescription = null,
                tint = colors.bull,
                modifier = Modifier.padding(10.dp).size(24.dp),
              )
            }
            Column {
              Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                color = Color(0xFFF3F6FC),
              )
              Text(
                text = "INSTITUTIONAL SMC & 26-INDICATOR TERMINAL",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFFFB300),
              )
            }
          }
        }

        Text(
          text = "Precision Liquidity Sweep Signals & Real-Time Entry/SL Alerts",
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onBackground,
        )

        Text(
          text =
            "Track BTCUSD, XAUUSD, and major Forex pairs across 1m, 5m, 15m, and 30m timeframes with automated Smart Money Concepts and 26 TradingView technical indicators.",
          style = MaterialTheme.typography.bodyLarge,
          color = colors.textSecondary,
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          FeatureBullet(
            icon = Icons.Rounded.Bolt,
            tint = colors.liquidity,
            title = "SSL & BSL Liquidity Sweeps",
            subtitle = "Detects stop-hunts, CHoCH/BOS shifts, Order Blocks & Fair Value Gaps.",
          )
          FeatureBullet(
            icon = Icons.Rounded.Shield,
            tint = colors.smcCyan,
            title = "26 TradingView Indicators",
            subtitle = "Combines 11 oscillators & 15 moving averages into a unified signal score.",
          )
          FeatureBullet(
            icon = Icons.Rounded.NotificationsActive,
            tint = colors.bull,
            title = "Firebase Cloud Sync & Entry/SL Alerts",
            subtitle = "Sync your signal journal, stop-loss levels, and alert rules securely.",
          )
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!errorMessage.isNullOrBlank()) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = errorMessage!!,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.padding(12.dp),
            )
          }
        }

        Button(
          onClick = {
            errorMessage = null
            isLoading = true
            onGoogleSignInClicked(
              context = context,
              credentialManager = credentialManager,
              onAuthSuccess = {
                isLoading = false
                onAuthSuccess()
              },
              onAuthError = { msg ->
                isLoading = false
                errorMessage = msg
              },
              scope = scope,
              onAuthCancelled = { isLoading = false },
            )
          },
          enabled = !isLoading,
          shape = RoundedCornerShape(14.dp),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = colors.bull,
              contentColor = colors.onAccent,
            ),
          modifier =
            Modifier
              .fillMaxWidth()
              .height(54.dp)
              .testTag("google_sign_in_button"),
        ) {
          if (isLoading) {
            CircularProgressIndicator(
              modifier = Modifier.size(22.dp),
              color = colors.onAccent,
              strokeWidth = 2.5.dp,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
              text = "Connecting Terminal...",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
          } else {
            Text(
              text = "Sign in with Google",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun FeatureBullet(
  icon: ImageVector,
  tint: Color,
  title: String,
  subtitle: String,
) {
  val colors = SweepPulseTheme.colors
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = colors.surfaceElevated,
    border = BorderStroke(1.dp, colors.border),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = tint.copy(alpha = 0.14f),
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = tint,
          modifier = Modifier.padding(8.dp).size(20.dp),
        )
      }
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = colors.textSecondary,
        )
      }
    }
  }
}
