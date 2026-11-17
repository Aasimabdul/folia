package com.simbuild.folia

import com.simbuild.folia.ui.theme.AppThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {

    @Test
    fun storedNamesRoundTrip() {
        AppThemeMode.entries.forEach { mode ->
            assertEquals(mode, AppThemeMode.fromName(mode.name))
        }
    }

    @Test
    fun missingOrUnknownValueFollowsTheSystem() {
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromName(null))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromName(""))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromName("SEPIA"))
    }
}
