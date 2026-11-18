package com.simbuild.folia.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Light: warm paper, ink and an electric-blue accent.
 * Every Material role is set explicitly (including the surfaceContainer family that dialogs, menus and
 * navigation bars use) so no component falls back to the default purple-grey baseline.
 */
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4263EB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE5E9FF),
    onPrimaryContainer = Color(0xFF1B2A80),
    inversePrimary = Color(0xFF9DB4FF),
    secondary = Color(0xFF626A7A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3E6F0),
    onSecondaryContainer = Color(0xFF1D2433),
    tertiary = Color(0xFF7658B5),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEBDDFF),
    onTertiaryContainer = Color(0xFF2D1B54),
    background = Color(0xFFF7F7F5),
    onBackground = Color(0xFF171717),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171717),
    surfaceVariant = Color(0xFFE7E9F0),
    onSurfaceVariant = Color(0xFF5F6472),
    surfaceTint = Color(0xFF4263EB),
    inverseSurface = Color(0xFF2E3140),
    inverseOnSurface = Color(0xFFF1F1F5),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF8A8FA0),
    outlineVariant = Color(0xFFD9DCE6),
    scrim = Color(0xFF000000),
    surfaceDim = Color(0xFFE8E8E6),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFAF8),
    surfaceContainer = Color(0xFFF0F1F4),
    surfaceContainerHigh = Color(0xFFECEEF8),
    surfaceContainerHighest = Color(0xFFE4E7F2)
)

/**
 * Dark: deep ink-navy surfaces that get slightly lighter as they rise, soft periwinkle accent with a
 * deep-navy "on" color (about 7:1 contrast), and muted blue-grey secondary text.
 */
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF9DB4FF),
    onPrimary = Color(0xFF0B1B66),
    primaryContainer = Color(0xFF2A3A7E),
    onPrimaryContainer = Color(0xFFDDE4FF),
    inversePrimary = Color(0xFF4263EB),
    secondary = Color(0xFFB4BCD0),
    onSecondary = Color(0xFF1E2638),
    secondaryContainer = Color(0xFF2B3447),
    onSecondaryContainer = Color(0xFFDAE0F2),
    tertiary = Color(0xFFC9B7F0),
    onTertiary = Color(0xFF2D1B54),
    tertiaryContainer = Color(0xFF43326F),
    onTertiaryContainer = Color(0xFFEBDDFF),
    background = Color(0xFF0E1117),
    onBackground = Color(0xFFE6E8EE),
    surface = Color(0xFF12161E),
    onSurface = Color(0xFFE6E8EE),
    surfaceVariant = Color(0xFF262E3E),
    onSurfaceVariant = Color(0xFFA7AEBF),
    surfaceTint = Color(0xFF9DB4FF),
    inverseSurface = Color(0xFFE6E8EE),
    inverseOnSurface = Color(0xFF1B2030),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF4A5368),
    outlineVariant = Color(0xFF2C3446),
    scrim = Color(0xFF000000),
    surfaceDim = Color(0xFF0E1117),
    surfaceBright = Color(0xFF2A3140),
    surfaceContainerLowest = Color(0xFF0B0E13),
    surfaceContainerLow = Color(0xFF141922),
    surfaceContainer = Color(0xFF181E29),
    surfaceContainerHigh = Color(0xFF1E2533),
    surfaceContainerHighest = Color(0xFF262E3E)
)

/** How the reader picks its page look. Independent of [AppThemeMode], which styles the app itself. */
enum class ReadingThemeMode {
    DARK,
    LIGHT,
    SEPIA
}

/**
 * How document pages are colored in the reader (chosen in Settings, also cycled by the reader's top-bar button).
 * Independent of [AppThemeMode]: a dark app can still show pages in their original colours.
 */
enum class PageStyle(val label: String, val description: String) {
    ORIGINAL("Original colours", "Pages look exactly like the document (recommended)"),
    AUTO("Match app theme", "Dark pages when the app is dark, original when light"),
    DARK("Always dark pages", "Dark paper with light text, for reading at night"),
    SEPIA("Sepia", "Warm paper, gentle for long reading");

    /** The look to draw for this style, given whether the app theme is currently dark. */
    fun resolve(appIsDark: Boolean): ReadingThemeMode = when (this) {
        ORIGINAL -> ReadingThemeMode.LIGHT
        AUTO -> if (appIsDark) ReadingThemeMode.DARK else ReadingThemeMode.LIGHT
        DARK -> ReadingThemeMode.DARK
        SEPIA -> ReadingThemeMode.SEPIA
    }

    /** Next style for the reader's quick toggle button. */
    fun next(): PageStyle = when (this) {
        ORIGINAL -> DARK
        DARK -> SEPIA
        SEPIA -> ORIGINAL
        AUTO -> ORIGINAL
    }

    companion object {
        /** Parses a stored value; anything unknown or missing keeps pages in their original colours. */
        fun fromName(name: String?): PageStyle = entries.firstOrNull { it.name == name } ?: ORIGINAL
    }
}

/** App-wide appearance chosen by the user in Settings. */
enum class AppThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark");

    companion object {
        /** Parses a stored value; anything unknown or missing means "follow the system". */
        fun fromName(name: String?): AppThemeMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

@Composable
fun FoliaTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // Keep status/navigation bar icons readable on the chosen theme (the user's choice can differ from the system's)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
