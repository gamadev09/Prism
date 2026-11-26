package com.gamadev.prism.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.gamadev.prism.design.DesignTokens

internal class GlassWallpaperSample(val wallpaper: ImageBitmap, val radius: Float, val bitmap: ImageBitmap)
internal val LocalIconGlassWallpaper = staticCompositionLocalOf<GlassWallpaperSample?> { null }

/**
 * Liquid Glass surface, stage 1 (RESEARCH §2.8):
 *  - samples a shared blurred wallpaper at its own screen position,
 *  - tint (darken/brighten),
 *  - rim: light rim top/bottom, dark rim at the sides (iOS 27, measured on pill & dock).
 * Refraction (AGSL) follows as stage 2.
 */
@Composable
fun GlassSurface(
    wallpaper: ImageBitmap,
    shape: Shape,
    modifier: Modifier = Modifier,
    blur: Dp = DesignTokens.glassBlur.dp,
    tint: Float = DesignTokens.glassTint.value,
    whiteTint: Float = DesignTokens.glassWhiteTint.value,
    /**
     * Transformation the surrounding element currently gets via graphicsLayer (rotation °, offset x/y px; pivot =
     * centre of this surface). It's undone when drawing the wallpaper crop → the background stands still,
     * only the glass moves (otherwise the wallpaper in the folder icon would jiggle along in edit mode).
     */
    counterTransform: (() -> Triple<Float, Float, Float>)? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val coords = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    val matrix = remember { Matrix() }
    val radius = if (com.gamadev.prism.tuning.DebugOptions.glassOff) 0f else with(LocalDensity.current) { blur.toPx() }
    val shared = LocalIconGlassWallpaper.current
    val sample = when {
        radius <= 0.5f || wallpaper.width == 1 -> wallpaper
        shared != null && shared.wallpaper === wallpaper && shared.radius == radius -> shared.bitmap
        else -> rememberBlurredWallpaper(wallpaper, radius) ?: wallpaper
    }
    Box(modifier) {
        Box(Modifier.matchParentSize().clip(shape)) {
            Canvas(Modifier.matchParentSize().onGloballyPositioned { coords[0] = it }) {
                counterTransform?.invoke()
                val c = coords[0]
                if (c != null && c.isAttached) {
                    matrix.reset()
                    c.transformFrom(c.findRootCoordinates(), matrix)
                    withTransform({ transform(matrix) }) { drawImage(sample) }
                }
                if (tint > 0f) drawRect(Color.Black.copy(alpha = tint))
                if (whiteTint > 0f) drawRect(Color.White.copy(alpha = whiteTint))
            }
        }
        content()
        GlassRim(shape, Modifier.matchParentSize())
    }
}

/**
 * Liquid Glass edge of app icons and widgets (iOS 27, all icon styles): a hairline of light along the top and bottom
 * edge with a short glow inwards, the sides almost without (T-OBJ-RIM, measured on the user's iPhone 10-18). Drawn inside
 * the already clipped shape, so the outer half of each stroke is cut off.
 */
fun drawObjectRim(scope: androidx.compose.ui.graphics.drawscope.DrawScope, shape: Shape) = with(scope) {
    val outline = shape.createOutline(size, layoutDirection, this)
    fun band(a: Float) = Brush.verticalGradient(0f to Color.White.copy(alpha = a), RIM_FADE to Color.Transparent,
        1f - RIM_FADE to Color.Transparent, 1f to Color.White.copy(alpha = a * RIM_BOTTOM))
    drawOutline(outline, band(DesignTokens.objectRimGlow.value), style = Stroke(width = (RIM_GLOW_W * density) * 2f))
    drawOutline(outline, band(DesignTokens.objectRimLine.value), style = Stroke(width = (RIM_LINE_W * density) * 2f))
}

private const val RIM_LINE_W = 0.45f // dp: the hairline (×2 because half the stroke lies outside the clip)
private const val RIM_GLOW_W = 2.6f  // dp: the glow fading inwards
private const val RIM_FADE = 0.22f   // share of the height over which the light runs out along the sides
private const val RIM_BOTTOM = 0.9f  // bottom edge slightly weaker than the top

@Composable
fun GlassRim(shape: Shape, modifier: Modifier, lightAlpha: Float = DesignTokens.glassRimHighlight.value) {
    val shadowAlpha = DesignTokens.glassRimShadow.value
    val rimWidthDp = DesignTokens.glassRimWidth.dp
    Spacer(modifier.drawWithCache {
        val w = rimWidthDp.toPx()
        if (w <= 0f) return@drawWithCache onDrawBehind { }
        val light = Color.White.copy(alpha = lightAlpha)
        val dark = Color.Black.copy(alpha = shadowAlpha)
        val brush = Brush.verticalGradient(0f to light, 0.3f to dark, 0.7f to dark, 1f to light)
        val inner = Size(size.width - w, size.height - w)
        val outline = shape.createOutline(inner, layoutDirection, this)
        val stroke = Stroke(width = w)
        onDrawBehind {
            translate(w / 2f, w / 2f) {
                drawOutline(outline, brush, style = stroke)
            }
        }
    })
}
