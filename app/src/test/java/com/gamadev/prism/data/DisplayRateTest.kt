package com.gamadev.prism.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayRateTest {
    @Test fun systemHasNoForcedRate() {
        assertEquals(0f, DisplayRate.supportedRate(0, listOf(60f, 120f)), 0f)
    }
    @Test fun supportsFractionalPanelModes() {
        assertEquals(119.88f, DisplayRate.supportedRate(120, listOf(59.94f, 119.88f)), 0f)
    }
    @Test fun unsupportedRateFallsBackToSystem() {
        assertEquals(0f, DisplayRate.supportedRate(120, listOf(60f, 90f)), 0f)
    }
    @Test fun doesNotForceAnotherRateWhenSelectedModeDisappears() {
        assertEquals(0f, DisplayRate.supportedRate(90, emptyList()), 0f)
    }
}
