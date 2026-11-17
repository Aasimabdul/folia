package com.simbuild.folia.data

import android.content.Context
import com.simbuild.folia.ui.theme.AppThemeMode

/** Small key-value store for user settings (no extra dependency, stays tiny and fast). */
class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var themeMode: AppThemeMode
        get() = AppThemeMode.fromName(prefs.getString(KEY_THEME_MODE, null))
        set(value) {
            prefs.edit().putString(KEY_THEME_MODE, value.name).apply()
        }

    private companion object {
        const val PREFS_NAME = "folia_settings"
        const val KEY_THEME_MODE = "theme_mode"
    }
}
