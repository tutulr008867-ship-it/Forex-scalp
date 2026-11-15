package com.example.ui.theme

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore(name = "sweeppulse_theme_prefs")

object ThemePreferences {
  private val KEY_DARK_THEME = booleanPreferencesKey("is_dark_theme")

  fun isDarkThemeFlow(context: Context): Flow<Boolean> =
    context.applicationContext.themeDataStore.data
      .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
      .map { prefs -> prefs[KEY_DARK_THEME] ?: true }

  suspend fun setDarkTheme(context: Context, isDark: Boolean) {
    context.applicationContext.themeDataStore.edit { prefs ->
      prefs[KEY_DARK_THEME] = isDark
    }
  }
}
