package com.simbuild.folia

import com.simbuild.folia.ui.theme.PageStyle
import com.simbuild.folia.ui.theme.ReadingThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class PageStyleTest {

    @Test
    fun missingOrUnknownValueKeepsOriginalColours() {
        assertEquals(PageStyle.ORIGINAL, PageStyle.fromName(null))
        assertEquals(PageStyle.ORIGINAL, PageStyle.fromName(""))
        assertEquals(PageStyle.ORIGINAL, PageStyle.fromName("PURPLE"))
    }

    @Test
    fun storedNamesRoundTrip() {
        PageStyle.entries.forEach { assertEquals(it, PageStyle.fromName(it.name)) }
    }

    @Test
    fun originalPagesStayOriginalInDarkApp() {
        assertEquals(ReadingThemeMode.LIGHT, PageStyle.ORIGINAL.resolve(appIsDark = true))
        assertEquals(ReadingThemeMode.LIGHT, PageStyle.ORIGINAL.resolve(appIsDark = false))
    }

    @Test
    fun autoFollowsTheAppTheme() {
        assertEquals(ReadingThemeMode.DARK, PageStyle.AUTO.resolve(appIsDark = true))
        assertEquals(ReadingThemeMode.LIGHT, PageStyle.AUTO.resolve(appIsDark = false))
    }

    @Test
    fun darkAndSepiaIgnoreTheAppTheme() {
        assertEquals(ReadingThemeMode.DARK, PageStyle.DARK.resolve(appIsDark = false))
        assertEquals(ReadingThemeMode.SEPIA, PageStyle.SEPIA.resolve(appIsDark = true))
    }

    @Test
    fun quickToggleCyclesOriginalDarkSepia() {
        assertEquals(PageStyle.DARK, PageStyle.ORIGINAL.next())
        assertEquals(PageStyle.SEPIA, PageStyle.DARK.next())
        assertEquals(PageStyle.ORIGINAL, PageStyle.SEPIA.next())
        assertEquals(PageStyle.ORIGINAL, PageStyle.AUTO.next())
    }
}
