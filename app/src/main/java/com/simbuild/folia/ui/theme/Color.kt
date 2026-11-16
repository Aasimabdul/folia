package com.simbuild.folia.ui.theme

import androidx.compose.ui.graphics.Color

// Core Folia identity: warm paper, ink, and a restrained electric-blue accent.
val FoliaPrimary = Color(0xFF4263EB)
val FoliaPrimaryLight = Color(0xFF91A5FF)
val FoliaPrimaryDark = Color(0xFF2F49B7)
val FoliaSecondary = Color(0xFF626A7A)

// Reading surfaces
val DarkReadingBg = Color(0xFF111214)
val DarkReadingSurface = Color(0xFF1A1C1F)
val DarkReadingText = Color(0xFFF2F2F0)

val LightReadingBg = Color(0xFFF7F7F5)
val LightReadingSurface = Color(0xFFFFFFFF)
val LightReadingText = Color(0xFF171717)

val SepiaReadingBg = Color(0xFFFBF0D9)
val SepiaReadingSurface = Color(0xFFF3E5C8)
val SepiaReadingText = Color(0xFF3E2D1D)

// Minimalist Folia home-screen palette (legacy names retained for source compatibility).
val FolioForestGreen = Color(0xFF4263EB)
val FolioSageCard = Color(0xFFECEEF8)
val FolioSageCardDark = Color(0xFF232630)
val FolioPillBg = Color(0xFFF0F1F4)
val FolioPillBgDark = Color(0xFF23252B)
val FolioSubtleText = Color(0xFF737783)
val FolioSubtleTextDark = Color(0xFFA4A8B3)
val FolioAddBtnBg = Color(0xFFE5E9FF)
val FolioAddBtnBgDark = Color(0xFF272D45)

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
