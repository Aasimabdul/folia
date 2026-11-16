package com.simbuild.folia.ui.theme

import androidx.compose.ui.graphics.Color

val FoliaPrimary = Color(0xFF2E7D32)
val FoliaPrimaryLight = Color(0xFF4CAF50)
val FoliaPrimaryDark = Color(0xFF1B5E20)
val FoliaSecondary = Color(0xFF455A64)

// Reading Mode Themes
val DarkReadingBg = Color(0xFF1E1E1E)
val DarkReadingSurface = Color(0xFF252526)
val DarkReadingText = Color(0xFFE0E0E0)

val LightReadingBg = Color(0xFFF9F9FB)
val LightReadingSurface = Color(0xFFFFFFFF)
val LightReadingText = Color(0xFF1C1B1F)

val SepiaReadingBg = Color(0xFFFBF0D9)
val SepiaReadingSurface = Color(0xFFF3E5C8)
val SepiaReadingText = Color(0xFF3E2D1D)

// Folio Figma Design System Palette
val FolioForestGreen = Color(0xFF223624)
val FolioSageCard = Color(0xFFE8EFE6)
val FolioSageCardDark = Color(0xFF243024)
val FolioPillBg = Color(0xFFF1F4F0)
val FolioPillBgDark = Color(0xFF2C2C2C)
val FolioSubtleText = Color(0xFF758276)
val FolioSubtleTextDark = Color(0xFFA0AFA1)
val FolioAddBtnBg = Color(0xFFE2EBE1)
val FolioAddBtnBgDark = Color(0xFF2E3D30)

// Format Badges
val FormatPdf = Color(0xFFE53935)
val FormatEpub = Color(0xFF2E7D32)
val FormatOffice = Color(0xFF1E88E5)
val FormatComic = Color(0xFFFB8C00)
val FormatText = Color(0xFF8E24AA)
val FormatDefault = Color(0xFF546E7A)

fun getFormatBadgeColor(format: String): Color = when (format.uppercase()) {
    "PDF" -> FormatPdf
    "EPUB", "MOBI", "FB2" -> FormatEpub
    "DOCX", "XLSX", "PPTX", "DOC", "XLS", "PPT" -> FormatOffice
    "CBZ", "CBR" -> FormatComic
    "TXT", "MARKDOWN", "MD", "CSV", "HTML" -> FormatText
    else -> FormatDefault
}
