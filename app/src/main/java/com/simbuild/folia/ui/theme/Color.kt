package com.simbuild.folia.ui.theme

import androidx.compose.ui.graphics.Color

// App color schemes (light and dark) live in Theme.kt. This file holds the reading surfaces used by the
// document reader and the format badge colors.

// Reading surfaces. The dark reading background is the same ink-navy as the dark app background so the
// reader and the home screen feel like one app.
val DarkReadingBg = Color(0xFF0E1117)
val DarkReadingSurface = Color(0xFF12161E)
val DarkReadingText = Color(0xFFE6E8EE)

val LightReadingBg = Color(0xFFF7F7F5)
val LightReadingSurface = Color(0xFFFFFFFF)
val LightReadingText = Color(0xFF171717)

val SepiaReadingBg = Color(0xFFFBF0D9)
val SepiaReadingSurface = Color(0xFFF3E5C8)
val SepiaReadingText = Color(0xFF3E2D1D)

// Format badges
val FormatPdf = Color(0xFFE53935)
val FormatEpub = Color(0xFF4263EB)
val FormatOffice = Color(0xFF3673D9)
val FormatComic = Color(0xFFB87931)
val FormatText = Color(0xFF7658B5)
val FormatDefault = Color(0xFF626A7A)

fun getFormatBadgeColor(format: String): Color = when (format.uppercase()) {
    "PDF" -> FormatPdf
    "EPUB", "MOBI", "FB2" -> FormatEpub
    "DOCX", "XLSX", "PPTX", "DOC", "XLS", "PPT" -> FormatOffice
    "CBZ", "CBR" -> FormatComic
    "TXT", "MARKDOWN", "MD", "CSV", "HTML" -> FormatText
    else -> FormatDefault
}
