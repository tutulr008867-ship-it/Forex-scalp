package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.repository.MarketDataRepository
import com.example.data.repository.SignalRepository
import com.example.domain.model.MarketPair
import com.example.domain.model.Timeframe
import com.example.notifications.FirebaseSignalNotificationService
import com.example.notifications.SignalNotificationManager
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.authStateFlow
import com.example.ui.components.ThemeToggleButton
import com.example.ui.screens.AlertsScreen
import com.example.ui.screens.MatrixScannerScreen
import com.example.ui.screens.SignalsScreen
import com.example.ui.screens.TerminalDetailScreen
import com.example.ui.theme.SweepPulseTheme
import com.example.ui.theme.ThemePreferences
import com.example.ui.viewmodel.TradingViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

const val ROUTE_SIGNALS = "signals"
const val ROUTE_SCANNER = "scanner"
const val ROUTE_ALERTS = "alerts"
const val ROUTE_TERMINAL = "terminal/{symbol}/{timeframe}"

class MainActivity : ComponentActivity() {
  private val deepLinkTarget = mutableStateOf<Pair<MarketPair, Timeframe>?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    SignalNotificationManager.ensureChannel(this)
    extractDeepLink(intent)

    setContent {
      val context = LocalContext.current
      val scope = rememberCoroutineScope()
      val isDarkTheme by
        ThemePreferences.isDarkThemeFlow(context).collectAsStateWithLifecycle(initialValue = true)
      val onToggleTheme = {
        val nextDark = !isDarkTheme
        scope.launch { ThemePreferences.setDarkTheme(context, nextDark) }
        Unit
      }

      SweepPulseTheme(darkTheme = isDarkTheme) {
        AppNavigation(
          isDarkTheme = isDarkTheme,
          onToggleTheme = onToggleTheme,
          deepLinkTarget = deepLinkTarget.value,
          onDeepLinkConsumed = { deepLinkTarget.value = null },
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    extractDeepLink(intent)
  }

  private fun extractDeepLink(intent: Intent?) {
    val sym = intent?.getStringExtra(SignalNotificationManager.EXTRA_SYMBOL) ?: return
    val tf = intent.getStringExtra(SignalNotificationManager.EXTRA_TIMEFRAME) ?: "15m"
    deepLinkTarget.value = MarketPair.fromSymbol(sym) to Timeframe.fromLabel(tf)
  }
}

@Composable
fun AppNavigation(
  isDarkTheme: Boolean,
  onToggleTheme: () -> Unit,
  deepLinkTarget: Pair<MarketPair, Timeframe>?,
  onDeepLinkConsumed: () -> Unit,
  auth: FirebaseAuth = Firebase.auth,
) {
  val currentUser by
    auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
  val user = currentUser

  if (user == null) {
    AuthScreen(
      isDarkTheme = isDarkTheme,
      onToggleTheme = onToggleTheme,
      onAuthSuccess = {},
    )
  } else {
    val uid = user.uid
    key(uid) {
      val tradingViewModel: TradingViewModel =
        viewModel(
          key = uid,
          factory =
            viewModelFactory {
              initializer {
                val app =
                  checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                  }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                TradingViewModel(
                  signalRepository = SignalRepository(db, auth),
                  marketDataRepository = MarketDataRepository.create(),
                  currentUserId = uid,
                )
              }
            },
        )
      SweepPulseAppContent(
        viewModel = tradingViewModel,
        isDarkTheme = isDarkTheme,
        onToggleTheme = onToggleTheme,
        deepLinkTarget = deepLinkTarget,
        onDeepLinkConsumed = onDeepLinkConsumed,
      )
    }
  }
}

@Composable
private fun SweepPulseAppContent(
  viewModel: TradingViewModel,
  isDarkTheme: Boolean,
  onToggleTheme: () -> Unit,
  deepLinkTarget: Pair<MarketPair, Timeframe>?,
  onDeepLinkConsumed: () -> Unit,
) {
  val colors = SweepPulseTheme.colors
  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = navBackStackEntry?.destination
  val snackbarHostState = remember { SnackbarHostState() }
  val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

  val context = LocalContext.current
  val serviceScope = rememberCoroutineScope()

  DisposableEffect(viewModel.currentUserId) {
    val databaseId = context.getString(R.string.firestore_database_id)
    val db = FirebaseFirestore.getInstance(databaseId)
    val localNotificationService =
      FirebaseSignalNotificationService(
        context = context.applicationContext,
        signalRepository = SignalRepository(db, Firebase.auth),
        userId = viewModel.currentUserId,
      )
    localNotificationService.start(serviceScope)
    onDispose {
      localNotificationService.stop()
    }
  }

  LaunchedEffect(viewModel) {
    viewModel.sweepNotificationEvents.collect { signal ->
      SignalNotificationManager.notifyIfNewSweep(context, signal)
    }
  }

  LaunchedEffect(viewModel) {
    viewModel.tradeAlertEvents.collect { alert ->
      SignalNotificationManager.notifyTradeLevelHit(
        context = context,
        signalId = alert.signalId,
        symbol = alert.symbol,
        timeframe = alert.timeframe,
        direction = alert.direction,
        hitType = alert.hitType,
        priceFormatted = alert.priceFormatted,
      )
    }
  }

  LaunchedEffect(statusBanner) {
    val msg = statusBanner
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      viewModel.clearStatusBanner()
    }
  }

  LaunchedEffect(deepLinkTarget) {
    val target = deepLinkTarget ?: return@LaunchedEffect
    navController.navigate("terminal/${target.first.symbol}/${target.second.label}") {
      launchSingleTop = true
    }
    onDeepLinkConsumed()
  }

  val navItems =
    listOf(
      Triple(ROUTE_SIGNALS, "Signals", Icons.Rounded.ShowChart to Icons.Outlined.ShowChart),
      Triple(ROUTE_SCANNER, "Matrix", Icons.Rounded.GridView to Icons.Outlined.GridView),
      Triple(ROUTE_ALERTS, "Alerts", Icons.Rounded.NotificationsActive to Icons.Outlined.Notifications),
    )

  val showChrome =
    currentDestination?.route in listOf(ROUTE_SIGNALS, ROUTE_SCANNER, ROUTE_ALERTS)

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavRail = maxWidth >= 700.dp

    Scaffold(
      containerColor = colors.bg,
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      topBar = {
        if (showChrome) {
          GlobalTerminalTopBar(
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme,
          )
        }
      },
      bottomBar = {
        if (showChrome && !useNavRail) {
          NavigationBar(containerColor = colors.surface) {
            navItems.forEach { (route, label, icons) ->
              val selected = currentDestination?.hierarchy?.any { it.route == route } == true
              NavigationBarItem(
                selected = selected,
                onClick = {
                  navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                  }
                },
                icon = {
                  Icon(
                    imageVector = if (selected) icons.first else icons.second,
                    contentDescription = label,
                  )
                },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                colors =
                  NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.bull,
                    selectedTextColor = colors.bull,
                    indicatorColor = colors.bull.copy(alpha = 0.14f),
                    unselectedIconColor = colors.textMuted,
                    unselectedTextColor = colors.textMuted,
                  ),
                modifier = Modifier.testTag("nav_$route"),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (showChrome && useNavRail) {
          NavigationRail(containerColor = colors.surface) {
            navItems.forEach { (route, label, icons) ->
              val selected = currentDestination?.hierarchy?.any { it.route == route } == true
              NavigationRailItem(
                selected = selected,
                onClick = {
                  navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                  }
                },
                icon = {
                  Icon(
                    imageVector = if (selected) icons.first else icons.second,
                    contentDescription = label,
                  )
                },
                label = { Text(label) },
                colors =
                  NavigationRailItemDefaults.colors(
                    selectedIconColor = colors.smcCyan,
                    selectedTextColor = colors.smcCyan,
                    indicatorColor = colors.smcCyan.copy(alpha = 0.14f),
                    unselectedIconColor = colors.textMuted,
                    unselectedTextColor = colors.textMuted,
                  ),
              )
            }
          }
        }

        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
          NavHost(navController = navController, startDestination = ROUTE_SIGNALS) {
            composable(ROUTE_SIGNALS) {
              SignalsScreen(
                viewModel = viewModel,
                onOpenTerminal = { pair, tf ->
                  navController.navigate("terminal/${pair.symbol}/${tf.label}")
                },
              )
            }
            composable(ROUTE_SCANNER) {
              MatrixScannerScreen(
                viewModel = viewModel,
                onOpenTerminal = { pair, tf ->
                  navController.navigate("terminal/${pair.symbol}/${tf.label}")
                },
              )
            }
            composable(ROUTE_ALERTS) {
              AlertsScreen(
                viewModel = viewModel,
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme,
              )
            }
            composable(
              route = ROUTE_TERMINAL,
              arguments =
                listOf(
                  navArgument("symbol") { type = NavType.StringType },
                  navArgument("timeframe") { type = NavType.StringType },
                ),
            ) { backStack ->
              val symbol = backStack.arguments?.getString("symbol") ?: "BTCUSD"
              val tfLabel = backStack.arguments?.getString("timeframe") ?: "15m"
              TerminalDetailScreen(
                pair = MarketPair.fromSymbol(symbol),
                initialTimeframe = Timeframe.fromLabel(tfLabel),
                viewModel = viewModel,
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme,
                onBack = { navController.popBackStack() },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun GlobalTerminalTopBar(
  isDarkTheme: Boolean,
  onToggleTheme: () -> Unit,
) {
  val colors = SweepPulseTheme.colors
  Surface(color = colors.surface) {
    androidx.compose.foundation.layout.Column {
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Box(
            modifier = Modifier.size(8.dp).background(colors.bull, CircleShape),
          )
          Text(
            text = "SweepPulse",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = "PRO",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = colors.smcCyan,
          )
        }
        ThemeToggleButton(
          isDarkTheme = isDarkTheme,
          onToggleTheme = onToggleTheme,
          tag = "global_theme_toggle",
        )
      }
      HorizontalDivider(color = colors.border.copy(alpha = 0.7f))
    }
  }
}
