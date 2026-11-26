package com.gamadev.prism.data

import com.gamadev.prism.data.IconRenderer.MaskFit
import org.junit.Assert.assertEquals
import org.junit.Test

class IconMaskTest {
    @Test fun enlargesUnderfilledLogoFromScreenshot() {
        assertEquals(MaskFit.Enlarge, IconRenderer.maskFit(29, 26, 190, 273))
    }

    @Test fun rejectsOpaquePlateFromScreenshot() {
        assertEquals(MaskFit.Reject, IconRenderer.maskFit(114, 114, 190, 12_994))
    }

    @Test fun keepsNormallySizedSymbol() {
        assertEquals(MaskFit.Keep, IconRenderer.maskFit(72, 50, 190, 2_680))
    }
    @Test fun rejectsFilledDiskRatherThanErasingItsDetail() {
        assertEquals(MaskFit.Reject, IconRenderer.maskFit(150, 150, 190, 17_600))
    }

    @Test fun fitsOversizedOrbitWithoutClipping() {
        assertEquals(MaskFit.Shrink, IconRenderer.maskFit(188, 172, 190, 6_000))
    }

    @Test fun retainsRingWithTransparentCentre() {
        assertEquals(MaskFit.Keep, IconRenderer.maskFit(100, 100, 190, 3_000))
    }
}
