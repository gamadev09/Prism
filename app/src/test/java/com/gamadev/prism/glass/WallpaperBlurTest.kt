package com.gamadev.prism.glass

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperBlurTest {
    @Test fun opaqueWallpaperMatchesTheOriginalBoxBlur() {
        val original = IntArray(9) { if (it == 4) -1 else 0xFF000000.toInt() }
        val pixels = original.copyOf()
        WallpaperRenderer.blurPixels(pixels, 3, 3, 1, preserveAlpha = false)

        // Slow independent reference using a clamped three-pixel average.
        var expected = original.map { it and 255 }
        repeat(3) {
            for (horizontal in listOf(true, false)) {
                expected = List(9) { index ->
                    val x = index % 3
                    val y = index / 3
                    (-1..1).sumOf { offset ->
                        val sampleX = (x + if (horizontal) offset else 0).coerceIn(0, 2)
                        val sampleY = (y + if (horizontal) 0 else offset).coerceIn(0, 2)
                        expected[sampleY * 3 + sampleX]
                    } / 3
                }
            }
        }
        assertArrayEquals(expected.map { 0xFF000000.toInt() or (it * 0x010101) }.toIntArray(), pixels)
    }

    @Test fun folderIconBlurKeepsTransparentWallpaperVisible() {
        val pixels = IntArray(11)
        pixels[5] = 0xFFFF0000.toInt()
        WallpaperRenderer.blurPixels(pixels, 11, 1, 1, preserveAlpha = true)
        assertEquals(0, pixels.first())
        assertEquals(0, pixels.last())
        assertTrue((pixels[4] ushr 24) in 1..254)
        assertEquals(0xFF0000, pixels[4] and 0xFFFFFF)
    }
}
