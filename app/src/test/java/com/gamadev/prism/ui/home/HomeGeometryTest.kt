package com.gamadev.prism.ui.home

import androidx.compose.ui.geometry.Offset
import com.gamadev.prism.design.DesignTokens
import com.gamadev.prism.design.UiScale
import org.junit.Assert.*
import org.junit.Test

class HomeGeometryTest {
    @Test fun gridRemainsCentredAcrossPhoneSizesAndDensities() {
        for (widthDp in listOf(360f, 393f, 402f, 432f, 480f)) {
            for (density in listOf(1f, 2.625f, 3f)) {
                val scale = UiScale.compute(false, (widthDp * density).toInt(), (960 * density).toInt(), density)
                val geo = HomeGeometry(density * scale, widthDp * density, 960 * density)
                val first = geo.slotPosition(0)
                val last = geo.slotPosition(DesignTokens.GRID_COLUMNS - 1)
                assertEquals(geo.width / 2f, (first.x + last.x + geo.icon) / 2f, 0.01f)
                assertEquals(first.x, geo.width - last.x - geo.icon, 0.01f)
                assertTrue(first.x >= 0f)
                assertTrue(last.x + geo.icon <= geo.width)
            }
        }
    }

    @Test fun everyVisibleCellMapsBackToItsOwnDragTarget() {
        for (width in listOf(360f, 402f, 432f)) {
            val geo = HomeGeometry(1f, width, 960f)
            for (slot in 0 until geo.perPage) {
                val centre = geo.slotPosition(slot) + Offset(geo.icon / 2, geo.icon / 2)
                assertEquals(slot, geo.slotAt(centre, geo.perPage))
                assertTrue(geo.cellHitRect(geo.slotPosition(slot)).contains(centre))
            }
        }
    }

    @Test fun savedSideMarginOverrideMovesTheGridAndItsDragTargets() {
        val margin = DesignTokens.gridSide.tunable
        val previous = margin.value
        val geo = HomeGeometry(2f, 820f, 1800f)
        try {
            margin.value = margin.default
            val centred = geo.slotPosition(0)
            margin.value += 9f
            val adjusted = geo.slotPosition(0)
            assertEquals(18f, adjusted.x - centred.x, 0.01f)
            assertEquals(0, geo.slotAt(adjusted + Offset(geo.icon / 2f, geo.icon / 2f), geo.perPage))
        } finally {
            margin.value = previous
        }
    }

    @Test fun sixRowsFitAboveSearchWithThreeButtonNavigation() {
        val geo = HomeGeometry(1f, 402f, 850f, navBarPx = 56f)
        val lastRow = geo.slotPosition(geo.perPage - 1)
        assertTrue(lastRow.y + DesignTokens.labelBaseline.value + DesignTokens.GRID_LABEL_DESCENT < geo.pillTop)
        assertEquals(geo.perPage - 1, geo.slotAt(lastRow + Offset(geo.icon / 2, geo.icon / 2), geo.perPage))
    }

    @Test fun tallCutoutMovesTheGridAndItsTouchTargetsTogether() {
        val cutout = androidx.compose.ui.geometry.Rect(180f, 0f, 220f, 103f)
        val geo = HomeGeometry(1f, 402f, 874f, cutouts = listOf(cutout))
        val first = geo.slotPosition(0)
        assertTrue(first.y > cutout.bottom)
        assertEquals(0, geo.slotAt(first + Offset(geo.icon / 2, geo.icon / 2), geo.perPage))
    }

    @Test fun gridAndDockShareTheWindowCentre() {
        val geo = HomeGeometry(2.625f, 1080f, 2400f, 60f)
        val gridCentre = (geo.slotPosition(0).x + geo.slotPosition(3).x + geo.icon) / 2f
        val dockCentre = (geo.dockPosition(0, 4).x + geo.dockPosition(3, 4).x + geo.icon) / 2f
        assertEquals(gridCentre, dockCentre, 0.01f)
    }
}
