package com.example.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Immutable
data class SweepPulseColors(
  val isDark: Boolean,
  val bg: Color,
  val surface: Color,
  val surfaceElevated: Color,
  val surfaceCard: Color,
  val border: Color,
  val bull: Color,
  val bullContainer: Color,
  val bear: Color,
  val bearContainer: Color,
  val liquidity: Color,
  val liquidityContainer: Color,
  val smcCyan: Color,
  val smcCyanContainer: Color,
  val neutralSlate: Color,
  val textPrimary: Color,
  val textSecondary: Color,
  val textMuted: Color,
  val onAccent: Color,
)

private val DarkSweepPulseColors =
  SweepPulseColors(
    isDark = true,
    bg = ObsidianBg,
    surface = ObsidianSurface,
    surfaceElevated = ObsidianSurfaceElevated,
    surfaceCard = ObsidianSurfaceCard,
    border = ObsidianBorder,
    bull = EmeraldBull,
    bullContainer = EmeraldBullContainer,
    bear = CrimsonBear,
    bearContainer = CrimsonBearContainer,
    liquidity = GoldLiquidity,
    liquidityContainer = GoldContainer,
    smcCyan = CyanSmc,
    smcCyanContainer = CyanContainer,
    neutralSlate = NeutralSlate,
    textPrimary = TextPrimary,
    textSecondary = TextSecondary,
    textMuted = TextMuted,
    onAccent = ObsidianBg,
  )

private val LightSweepPulseColors =
  SweepPulseColors(
    isDark = false,
    bg = DaylightBg,
    surface = DaylightSurface,
    surfaceElevated = DaylightSurfaceElevated,
    surfaceCard = DaylightSurfaceCard,
    border = DaylightBorder,
    bull = DaylightBull,
    bullContainer = DaylightBullContainer,
    bear = DaylightBear,
    bearContainer = DaylightBearContainer,
    liquidity = DaylightLiquidity,
    liquidityContainer = DaylightLiquidityContainer,
    smcCyan = DaylightCyanSmc,
    smcCyanContainer = DaylightCyanContainer,
    neutralSlate = DaylightNeutralSlate,
    textPrimary = DaylightTextPrimary,
    textSecondary = DaylightTextSecondary,
    textMuted = DaylightTextMuted,
    onAccent = Color.White,
  )

val LocalSweepPulseColors = staticCompositionLocalOf { DarkSweepPulseColors }

private val DarkMaterialColorScheme =
  darkColorScheme(
    primary = EmeraldBull,
    onPrimary = ObsidianBg,
    primaryContainer = EmeraldBullContainer,
    onPrimaryContainer = EmeraldBull,
    secondary = GoldLiquidity,
    onSecondary = ObsidianBg,
    secondaryContainer = GoldContainer,
    onSecondaryContainer = GoldLiquidity,
    tertiary = CyanSmc,
    onTertiary = ObsidianBg,
    tertiaryContainer = CyanContainer,
    onTertiaryContainer = CyanSmc,
    error = CrimsonBear,
    onError = TextPrimary,
    errorContainer = CrimsonBearContainer,
    onErrorContainer = CrimsonBear,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = ObsidianSurface,
    surfaceContainerHigh = ObsidianSurfaceElevated,
    surfaceContainerHighest = ObsidianSurfaceCard,
    outline = ObsidianBorder,
    outlineVariant = ObsidianBorder,
  )

private val LightMaterialColorScheme =
  lightColorScheme(
    primary = DaylightBull,
    onPrimary = Color.White,
    primaryContainer = DaylightBullContainer,
    onPrimaryContainer = DaylightBull,
    secondary = DaylightLiquidity,
    onSecondary = Color.White,
    secondaryContainer = DaylightLiquidityContainer,
    onSecondaryContainer = DaylightLiquidity,
    tertiary = DaylightCyanSmc,
    onTertiary = Color.White,
    tertiaryContainer = DaylightCyanContainer,
    onTertiaryContainer = DaylightCyanSmc,
    error = DaylightBear,
    onError = Color.White,
    errorContainer = DaylightBearContainer,
    onErrorContainer = DaylightBear,
    background = DaylightBg,
    onBackground = DaylightTextPrimary,
    surface = DaylightSurface,
    onSurface = DaylightTextPrimary,
    surfaceVariant = DaylightSurfaceElevated,
    onSurfaceVariant = DaylightTextSecondary,
    surfaceContainer = DaylightSurface,
    surfaceContainerHigh = DaylightSurfaceElevated,
    surfaceContainerHighest = DaylightSurfaceCard,
    outline = DaylightBorder,
    outlineVariant = DaylightBorder,
  )

object SweepPulseTheme {
  val colors: SweepPulseColors
    @Composable
    @ReadOnlyComposable
    get() = LocalSweepPulseColors.current
}

@Composable
fun SweepPulseTheme(
  darkTheme: Boolean = true,
  content: @Composable () -> Unit,
) {
  val palette = if (darkTheme) DarkSweepPulseColors else LightSweepPulseColors
  val colorScheme = if (darkTheme) DarkMaterialColorScheme else LightMaterialColorScheme
  val view = LocalView.current

  if (!view.isInEditMode) {
    SideEffect {
      view.context.findActivity()?.window?.let { window ->
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
      }
    }
  }

  CompositionLocalProvider(LocalSweepPulseColors provides palette) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      content = content,
    )
  }
}

private tailrec fun Context.findActivity(): Activity? =
  when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
  }
