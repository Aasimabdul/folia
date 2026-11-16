package com.simbuild.folia.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = FoliaPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = FoliaPrimaryDark,
    onPrimaryContainer = Color.White,
    secondary = FoliaSecondary,
    background = DarkReadingBg,
    surface = DarkReadingSurface,
    onBackground = DarkReadingText,
    onSurface = DarkReadingText
)

private val LightColorScheme = lightColorScheme(
    primary = FoliaPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5E9FF),
    onPrimaryContainer = Color(0xFF263B99),
    secondary = FoliaSecondary,
    background = LightReadingBg,
    surface = LightReadingSurface,
    onBackground = LightReadingText,
    onSurface = LightReadingText
)

enum class ReadingThemeMode {
    DARK,
    LIGHT,
    SEPIA
}

@Composable
fun FoliaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
