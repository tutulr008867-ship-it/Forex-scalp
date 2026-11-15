package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.MarketPair
import com.example.notifications.SignalNotificationManager
import com.example.ui.auth.signOutUser
import com.example.ui.theme.SweepPulseTheme
import com.example.ui.viewmodel.TradingViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlin.math.roundToInt

@Composable
fun AlertsScreen(
  viewModel: TradingViewModel,
  isDarkTheme: Boolean,
  onToggleTheme: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = SweepPulseTheme.colors
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }

  var hasPermission by remember {
    mutableStateOf(SignalNotificationManager.hasNotificationPermission(context))
  }
  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasPermission = granted
    }

  val alertConfigsState by viewModel.alertConfigs.collectAsStateWithLifecycle()
  val autoScanEnabled by viewModel.autoScanEnabled.collectAsStateWithLifecycle()
  val fcmToken by viewModel.fcmToken.collectAsStateWithLifecycle()

  val configs = (alertConfigsState as? UiState.Success)?.data.orEmpty()
  val global = configs.firstOrNull { it.symbol == "ALL" }

  var masterEnabled by remember(global) { mutableStateOf(global?.enabled ?: true) }
  var tf1m by remember(global) { mutableStateOf(global?.tf1m ?: true) }
  var tf5m by remember(global) { mutableStateOf(global?.tf5m ?: true) }
  var tf15m by remember(global) { mutableStateOf(global?.tf15m ?: true) }
  var tf30m by remember(global) { mutableStateOf(global?.tf30m ?: true) }
  var requireSweep by remember(global) { mutableStateOf(global?.requireLiquiditySweep ?: true) }
  var minConfluence by remember(global) {
    mutableFloatStateOf((global?.minConfluence ?: 15L).toFloat())
  }

  LaunchedEffect(Unit) {
    SignalNotificationManager.ensureChannel(context)
  }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Interface Theme Appearance Card
      item {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = colors.surfaceElevated,
          border = BorderStroke(1.dp, colors.border),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.weight(1f),
            ) {
              Icon(
                imageVector = if (isDarkTheme) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                contentDescription = null,
                tint = if (isDarkTheme) colors.smcCyan else colors.liquidity,
              )
              Column {
                Text(
                  text = if (isDarkTheme) "Dark Obsidian Terminal" else "Light Daylight Desk",
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text = "Switch global trading desk appearance between dark and light mode",
                  style = MaterialTheme.typography.bodyMedium,
                  color = colors.textSecondary,
                )
              }
            }
            Switch(
              checked = isDarkTheme,
              onCheckedChange = { onToggleTheme() },
              colors =
                SwitchDefaults.colors(
                  checkedThumbColor = colors.onAccent,
                  checkedTrackColor = colors.smcCyan,
                ),
              modifier = Modifier.testTag("alerts_theme_switch"),
            )
          }
        }
      }

      // Notification Permission & Push Test Card
      item {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = colors.surfaceElevated,
          border =
            BorderStroke(
              1.dp,
              if (hasPermission) colors.bull.copy(alpha = 0.45f) else colors.liquidity.copy(alpha = 0.55f),
            ),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector =
                  if (hasPermission) Icons.Rounded.NotificationsActive
                  else Icons.Rounded.NotificationsOff,
                contentDescription = null,
                tint = if (hasPermission) colors.bull else colors.liquidity,
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text =
                    if (hasPermission) "Real-Time Entry & SL Push Active"
                    else "Enable Push Notifications",
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text =
                    if (hasPermission)
                      "High-priority notifications fire immediately on new SMC Liquidity Sweeps with Entry, SL, and TP1."
                    else
                      "Grant notification access so SweepPulse can alert you on real-time market entries and stop-loss levels.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = colors.textSecondary,
                )
              }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Button(
                  onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                  colors =
                    ButtonDefaults.buttonColors(
                      containerColor = colors.liquidity,
                      contentColor = colors.onAccent,
                    ),
                  modifier = Modifier.weight(1f).testTag("grant_notifications_button"),
                ) {
                  Text("Enable Notifications", fontWeight = FontWeight.Bold)
                }
              }
              Button(
                onClick = {
                  if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                  } else {
                    val best = viewModel.bestActiveSignal()
                    if (best != null) {
                      val smc = best.smcAnalysis
                      val pair = best.pair
                      val sent =
                        SignalNotificationManager.showSignalNotification(
                          context = context,
                          title = "${smc.direction} ${pair.symbol} (${best.timeframe.label}) • ${best.alignedCount}/26 TV",
                          body =
                            "${smc.eventType.label} | Entry: ${pair.formatPrice(smc.entryPrice)} | SL: ${pair.formatPrice(smc.stopLoss)} | TP1: ${pair.formatPrice(smc.takeProfit1)}",
                          symbol = pair.symbol,
                          timeframe = best.timeframe.label,
                        )
                      viewModel.notifyBanner(
                        if (sent) "Live entry & SL alert sent for ${pair.symbol} (${best.timeframe.label})"
                        else "Enable notification permission to receive push alerts."
                      )
                    } else {
                      viewModel.notifyBanner("Scanning live markets—try again in a moment.")
                      viewModel.refreshCurrentTimeframe()
                    }
                  }
                },
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = colors.bull,
                    contentColor = colors.onAccent,
                  ),
                modifier = Modifier.weight(1f).testTag("test_push_button"),
              ) {
                Text("Test Live Entry/SL Alert", fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }

      // Global Alert Rules Synced to Firebase
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
              text = "SMC & 26-Indicator Trigger Rules",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Master Signal Alerts", style = MaterialTheme.typography.bodyLarge)
                Text(
                  "Enable live entry/SL alerts across watched pairs",
                  style = MaterialTheme.typography.bodyMedium,
                  color = colors.textSecondary,
                )
              }
              Switch(
                checked = masterEnabled,
                onCheckedChange = { masterEnabled = it },
                colors = SwitchDefaults.colors(checkedThumbColor = colors.onAccent, checkedTrackColor = colors.bull),
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Require SMC Liquidity Sweep", style = MaterialTheme.typography.bodyLarge)
                Text(
                  "Only alert when SSL or BSL stop-hunt sweep is confirmed",
                  style = MaterialTheme.typography.bodyMedium,
                  color = colors.textSecondary,
                )
              }
              Switch(
                checked = requireSweep,
                onCheckedChange = { requireSweep = it },
                colors = SwitchDefaults.colors(checkedThumbColor = colors.onAccent, checkedTrackColor = colors.liquidity),
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Auto-Refresh Live Scanner (35s)", style = MaterialTheme.typography.bodyLarge)
                Text(
                  "Continuously polls market feeds while terminal is open",
                  style = MaterialTheme.typography.bodyMedium,
                  color = colors.textSecondary,
                )
              }
              Switch(
                checked = autoScanEnabled,
                onCheckedChange = { viewModel.toggleAutoScan() },
                colors = SwitchDefaults.colors(checkedThumbColor = colors.onAccent, checkedTrackColor = colors.smcCyan),
              )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(
                text = "Monitored Timeframes",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
              )
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                FilterChip(selected = tf1m, onClick = { tf1m = !tf1m }, label = { Text("1m") })
                FilterChip(selected = tf5m, onClick = { tf5m = !tf5m }, label = { Text("5m") })
                FilterChip(selected = tf15m, onClick = { tf15m = !tf15m }, label = { Text("15m") })
                FilterChip(selected = tf30m, onClick = { tf30m = !tf30m }, label = { Text("30m") })
              }
            }

            Column {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text("Minimum 26-Indicator Confluence", style = MaterialTheme.typography.bodyMedium)
                Text(
                  text = "${minConfluence.roundToInt()} / 26",
                  style = MaterialTheme.typography.labelLarge,
                  color = colors.smcCyan,
                )
              }
              Slider(
                value = minConfluence,
                onValueChange = { minConfluence = it },
                valueRange = 10f..24f,
                steps = 13,
                colors = SliderDefaults.colors(thumbColor = colors.smcCyan, activeTrackColor = colors.smcCyan),
              )
            }

            Button(
              onClick = {
                viewModel.saveGlobalAlertPreferences(
                  enabled = masterEnabled,
                  tf1m = tf1m,
                  tf5m = tf5m,
                  tf15m = tf15m,
                  tf30m = tf30m,
                  minConfluence = minConfluence.roundToInt(),
                  requireSweep = requireSweep,
                )
              },
              colors = ButtonDefaults.buttonColors(containerColor = colors.smcCyan, contentColor = colors.onAccent),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth().testTag("save_alert_rules_button"),
            ) {
              Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text("Save Alert Rules to Firebase", fontWeight = FontWeight.Bold)
            }
          }
        }
      }

      // Per-Pair Watchlist Toggles
      item {
        Text(
          text = "PAIR WATCHLIST ALERTS",
          style = MaterialTheme.typography.labelSmall,
          color = colors.textMuted,
          modifier = Modifier.padding(top = 4.dp),
        )
      }

      items(MarketPair.entries) { pair ->
        val pairCfg = configs.firstOrNull { it.symbol == pair.symbol }
        val enabled = pairCfg?.enabled ?: true
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = colors.surface,
          border = BorderStroke(1.dp, colors.border),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column {
              Text(pair.displayName, style = MaterialTheme.typography.titleMedium)
              Text(pair.assetClass.label, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
            }
            Switch(
              checked = enabled,
              onCheckedChange = { viewModel.togglePairAlert(pair, it) },
              colors = SwitchDefaults.colors(checkedThumbColor = colors.onAccent, checkedTrackColor = colors.bull),
            )
          }
        }
      }

      // Firebase & Account Card
      item {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = colors.surfaceElevated,
          border = BorderStroke(1.dp, colors.border),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text("Firebase Cloud & Notification Service", style = MaterialTheme.typography.titleMedium)
            Text(
              text = "Account: ${Firebase.auth.currentUser?.email ?: "Signed in"}",
              style = MaterialTheme.typography.bodyMedium,
              color = colors.textSecondary,
            )
            Text(
              text = "Local Notification Service: Active (listening for new BUY & SELL signals from Firebase)",
              style = MaterialTheme.typography.labelSmall,
              color = colors.bull,
            )
            Text(
              text =
                if (!fcmToken.isNullOrBlank()) "FCM Token synced (${fcmToken!!.take(18)}...)"
                else "FCM client service registered (enable Cloud Messaging in Firebase Console for background server pushes).",
              style = MaterialTheme.typography.labelSmall,
              color = colors.textMuted,
            )
            OutlinedButton(
              onClick = { signOutUser(credentialManager, scope) },
              border = BorderStroke(1.dp, colors.bear.copy(alpha = 0.6f)),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth().testTag("sign_out_button"),
            ) {
              Icon(Icons.AutoMirrored.Rounded.Logout, null, tint = colors.bear, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text("Sign Out", color = colors.bear)
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(16.dp)) }
    }
  }
}
