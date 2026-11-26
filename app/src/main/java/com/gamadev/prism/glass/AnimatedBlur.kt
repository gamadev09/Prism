package com.gamadev.prism.glass

import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import kotlin.math.roundToInt

/** UI-thread-only, bounded cache. Array lookup avoids per-frame key allocations. */
internal object AnimatedBlur {
    private const val MAX_CACHED_RADIUS = 256
    private val decal = arrayOfNulls<RenderEffect>(MAX_CACHED_RADIUS + 1)
    private val clamp = arrayOfNulls<RenderEffect>(MAX_CACHED_RADIUS + 1)

    fun effect(radius: Float, mode: TileMode): RenderEffect? {
        if (!radius.isFinite() || radius <= 0.5f) return null
        val pixels = radius.roundToInt().coerceAtLeast(1)
        val cache = when (mode) {
            TileMode.Decal -> decal
            TileMode.Clamp -> clamp
            else -> null
        }
        // Unusually large debug-tuned radii retain their appearance without growing the cache.
        if (cache == null || pixels > MAX_CACHED_RADIUS) return BlurEffect(radius, radius, mode)
        return cache[pixels] ?: BlurEffect(pixels.toFloat(), pixels.toFloat(), mode).also { cache[pixels] = it }
    }
}
