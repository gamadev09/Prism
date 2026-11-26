package com.gamadev.prism.ui.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.gamadev.prism.design.DesignTokens
import com.gamadev.prism.design.SettingsAppearance
import com.gamadev.prism.design.IconAppearance
import org.junit.Assert.*
import org.junit.Test

class SettingsColorsTest {
    @Test fun settingsAppearanceCanOverrideTheSystem() {
        assertFalse(SettingsAppearance.System.isDark(false))
        assertTrue(SettingsAppearance.System.isDark(true))
        assertFalse(SettingsAppearance.Light.isDark(true))
        assertTrue(SettingsAppearance.Dark.isDark(false))
    }

    @Test fun textRetainsContrastInBothAppearances() {
        val original = IconAppearance.systemDark
        try {
            for (dark in listOf(false, true)) {
                IconAppearance.systemDark = dark
                val colours = DesignTokens.Settings
                for (background in listOf(colours.card, colours.background, colours.sidebar)) {
                    for (text in listOf(colours.primary, colours.secondary)) {
                        val light = maxOf(text.luminance(), background.luminance())
                        val shade = minOf(text.luminance(), background.luminance())
                        assertTrue("Text contrast in dark=$dark", (light + 0.05f) / (shade + 0.05f) >= 4.5f)
                    }
                }
            }
        } finally { IconAppearance.systemDark = original }
    }

    @Test fun darkSettingsUseDarkCardsAndLightText() {
        val original = IconAppearance.systemDark
        try {
            IconAppearance.systemDark = true
            assertEquals(Color.Black, DesignTokens.Settings.background)
            assertEquals(Color.White, DesignTokens.Settings.primary)
            assertTrue(DesignTokens.Settings.card.luminance() < 0.1f)
        } finally { IconAppearance.systemDark = original }
    }
}
