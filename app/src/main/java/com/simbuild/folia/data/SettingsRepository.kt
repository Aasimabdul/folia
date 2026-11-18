package com.simbuild.folia.data

import android.content.Context
import com.simbuild.folia.ui.theme.AppThemeMode
import com.simbuild.folia.ui.theme.PageStyle

/** Small key-value store for user settings (no extra dependency, stays tiny and fast). */
class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var themeMode: AppThemeMode
        get() = AppThemeMode.fromName(prefs.getString(KEY_THEME_MODE, null))
        set(value) {
            prefs.edit().putString(KEY_THEME_MODE, value.name).apply()
        }

    /** How document pages are colored in the reader. Default: original colours, even in dark mode. */
    var pageStyle: PageStyle
        get() = PageStyle.fromName(prefs.getString(KEY_PAGE_STYLE, null))
        set(value) {
            prefs.edit().putString(KEY_PAGE_STYLE, value.name).apply()
        }

    /** Softens bright white pages in dark mode (original colours only). On by default. */
    var dimPagesInDarkMode: Boolean
        get() = prefs.getBoolean(KEY_DIM_PAGES, true)
        set(value) {
            prefs.edit().putBoolean(KEY_DIM_PAGES, value).apply()
        }

    private companion object {
        const val PREFS_NAME = "folia_settings"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_PAGE_STYLE = "page_style"
        const val KEY_DIM_PAGES = "dim_pages_in_dark_mode"
    }
}
