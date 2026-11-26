package com.gamadev.prism.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamadev.prism.data.AppEntry
import com.gamadev.prism.design.ContinuousRoundedShape
import com.gamadev.prism.design.DesignTokens
import com.gamadev.prism.glass.GlassSurface
import com.gamadev.prism.motion.Motion
import com.gamadev.prism.motion.InteractiveFloat
import com.gamadev.prism.ui.Inter
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Pure rendering of an icon (gestures run centrally in HomeScreen).
 * Cell = T-LABEL-W wide, icon top centre, label baseline at T-LABEL-BASELINE.
 * The caller positions it so the icon top-left lies on the grid (see [cellOffsetX]).
 */
@Composable
fun AppIconView(
    app: AppEntry,
    showLabel: Boolean,
    wallpaper: ImageBitmap,
    pressAlpha: () -> Float,
    jiggle: () -> Float,
    clockSeconds: () -> Float,
    badgeScale: () -> Float,
    showBadge: Boolean,
    modifier: Modifier = Modifier,
    /** The minus badge shows what really lies behind it (open folder: panel above the home screen) instead of the wallpaper. */
    badgeOnGlass: Boolean = false,
    labelAlpha: () -> Float = { 1f },
) {
    val iconSize = DesignTokens.iconSize.dp
    IconCell(
        phaseKey = app.key,
        label = if (showLabel) app.label else null,
        wallpaper = wallpaper,
        pressAlpha = pressAlpha,
        jiggle = jiggle,
        clockSeconds = clockSeconds,
        badgeScale = badgeScale,
        showBadge = showBadge,
        modifier = modifier,
        notifications = com.gamadev.prism.data.NotificationBadges.countFor(app),
        badgeOnGlass = badgeOnGlass,
        labelAlpha = labelAlpha,
    ) {
        StyledIcon(app, wallpaper, Modifier.size(iconSize))
    }
}

/** Current wallpaper for glass icon styles (Clear/Tinted) in places without their own wallpaper parameter. */
val LocalWallpaper = androidx.compose.runtime.staticCompositionLocalOf<ImageBitmap?> { null }

/**
 * App icon in the chosen style wherever no wallpaper is passed down (folders, App Library, search).
 * [shape]: the squircle — the default style FILLS it with the icon image instead of clipping (a clip path is
 * rasterised into a CPU mask and uploaded every frame while the icon scales, e.g. opening an App Library category:
 * ≈ 10 % of the RenderThread on LineageOS; a convex path filled with an image shader is drawn by the GPU directly).
 * [pressDark]: darkening while held, inside the shape.
 */
@Composable
fun StyledAppIcon(app: AppEntry, modifier: Modifier, shape: androidx.compose.ui.graphics.Shape? = null, pressDark: () -> Float = { 0f }) {
    val wp = LocalWallpaper.current
    val style = com.gamadev.prism.design.IconAppearance.effective
    val default = wp == null || style == com.gamadev.prism.design.IconStyle.Default ||
        (style != com.gamadev.prism.design.IconStyle.Tinted && com.gamadev.prism.data.IconLayerCache.map[app.key] == null)
    if (shape != null && default) {
        androidx.compose.foundation.layout.Spacer(modifier.drawWithCache {
            val bmp = app.icon.asAndroidBitmap()
            val shader = android.graphics.BitmapShader(bmp, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP).apply {
                setLocalMatrix(android.graphics.Matrix().apply { setScale(size.width / bmp.width, size.height / bmp.height) })
            }
            val brush = androidx.compose.ui.graphics.ShaderBrush(shader)
            val outline = shape.createOutline(size, layoutDirection, this)
            onDrawBehind {
                drawOutline(outline, brush)
                val d = pressDark(); if (d > 0f) drawOutline(outline, androidx.compose.ui.graphics.Color.Black.copy(alpha = d))
            }
        })
        return
    }
    val m = if (shape != null) modifier.clip(shape) else modifier
    if (wp == null) Image(bitmap = app.icon, contentDescription = app.label, modifier = m)
    else StyledIcon(app, wp, m)
}

/**
 * Icon in the chosen iOS style (IconAppearance, recording7):
 *  Default = original · Dark = colourful motif on a dark gradient · Clear = monochrome motif on glass ·
 *  Tinted = tinted motif on a tinted ground. Light/dark variant depending on the setting.
 */
@Composable
fun StyledIcon(
    app: AppEntry,
    wallpaper: ImageBitmap,
    modifier: Modifier,
    style: com.gamadev.prism.design.IconStyle = com.gamadev.prism.design.IconAppearance.effective,
    dark: Boolean = com.gamadev.prism.design.IconAppearance.dark,
) {
    val layers = com.gamadev.prism.data.IconLayerCache.map[app.key]
    if (style == com.gamadev.prism.design.IconStyle.Default ||
        (layers == null && style != com.gamadev.prism.design.IconStyle.Tinted)) {
        Image(bitmap = app.icon, contentDescription = app.label, modifier = modifier)
        return
    }
    val ia = com.gamadev.prism.design.IconAppearance
    when (style) {
        com.gamadev.prism.design.IconStyle.Dark -> Box(modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(DARK_TOP, DARK_BOTTOM)))) {
            Image(layers!!.glyph, app.label, Modifier.matchParentSize())
        }
        com.gamadev.prism.design.IconStyle.Clear -> com.gamadev.prism.glass.GlassSurface(
            wallpaper = wallpaper,
            shape = androidx.compose.ui.graphics.RectangleShape, // the cell already clips to the squircle
            modifier = modifier,
            tint = if (dark) CLEAR_DARK_TINT else 0f,
            whiteTint = if (dark) 0f else CLEAR_LIGHT_TINT,
        ) {
            Image(
                layers?.glyph ?: app.icon, app.label, Modifier.matchParentSize(),
                colorFilter = if (dark) clearGlyphDarkFilter else clearGlyphLightFilter,
            )
        }
        else -> if (!dark) { // Tinted, Light: opaque plate + light glyph (iOS 27, T-TINT, measured 10-16)
            Box(modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(ia.tintPlate(top = true), ia.tintPlate(top = false))))) {
                Image(layers?.glyph ?: app.icon, app.label, Modifier.matchParentSize(),
                    colorFilter = lightIconFilter)
            }
        } else { // Tinted, Dark: deep dark tinted plate + vibrant tinted glyph (matches Tinted Light's glyph quality)
            val bgTop = ia.tint(TINT_DARK_BG_TOP_V, TINT_DARK_BG_S)
            val bgBottom = ia.tint(TINT_DARK_BG_BOTTOM_V, TINT_DARK_BG_S)
            Box(modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(bgTop, bgBottom)))) {
                Image(
                    layers?.glyph ?: app.icon, app.label, Modifier.matchParentSize(),
                    colorFilter = darkIconDuotoneFilter,
                )
            }
        }
    }
}

// Compose shares the same filter across icons; changing a tint does not regenerate any bitmap.
private val lightIconFilter by androidx.compose.runtime.derivedStateOf {
    val tint = com.gamadev.prism.design.IconAppearance
    androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(
        tint.duotone(tint.tintPlate(), tint.tintGlyph(), DesignTokens.TINT_ICON_GAIN, DesignTokens.TINT_ICON_FLOOR),
    ))
}

// Shaded white/silver monochrome filters for Clear icons (preserves inner glyph shading like iOS 27 Clear).
private val clearGlyphLightFilter by lazy {
    val ia = com.gamadev.prism.design.IconAppearance
    androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(
        ia.duotone(Color(0xFFB8BCC2), Color(0xFFFFFFFF), DesignTokens.TINT_ICON_GAIN, 0.35f),
    ))
}

private val clearGlyphDarkFilter by lazy {
    val ia = com.gamadev.prism.design.IconAppearance
    androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(
        ia.duotone(Color(0xFF9EA2A8), Color(0xFFEAECEF), DesignTokens.TINT_ICON_GAIN, 0.30f),
    ))
}

private val darkIconDuotoneFilter by androidx.compose.runtime.derivedStateOf {
    val tint = com.gamadev.prism.design.IconAppearance
    androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(
        tint.duotone(
            tint.tint(TINT_DARK_BG_BOTTOM_V, TINT_DARK_BG_S),
            tint.tint(TINT_DARK_FG_V, TINT_DARK_FG_S),
            DesignTokens.TINT_ICON_GAIN,
            DesignTokens.TINT_ICON_FLOOR,
        ),
    ))
}

// Colours/tints [MEASURED roughly] recording7 15.6 / 17.2 / 22.4 s
private val DARK_TOP = Color(0xFF3A3A3C)
private val DARK_BOTTOM = Color(0xFF1C1C1E)
private const val CLEAR_LIGHT_TINT = 0.22f
private const val CLEAR_DARK_TINT = 0.35f
private val CLEAR_GLYPH_LIGHT = Color(0xF2FFFFFF)
private val CLEAR_GLYPH_DARK = Color(0xD9FFFFFF)
private const val TINT_DARK_BG_TOP_V = 0.22f
private const val TINT_DARK_BG_BOTTOM_V = 0.14f
private const val TINT_DARK_BG_S = 0.70f
private const val TINT_DARK_FG_V = 0.94f
private const val TINT_DARK_FG_S = 0.78f
private const val TINT_GLASS_TOP = 0.8f    // a bit more transparent at the top (gloss)
private const val TINT_GLASS_BOTTOM = 0.93f

internal val LocalHomeIconResize = staticCompositionLocalOf<InteractiveFloat?> { null }

/** Liquidy's existing size mode is the resting state for cells outside HomeScreen. */
@Composable
internal fun iconSizeProgress(): () -> Float {
    val animation = LocalHomeIconResize.current
    return remember(animation) {
        { animation?.value ?: if (com.gamadev.prism.design.IconAppearance.largeIcons) 1f else 0f }
    }
}

/**
 * Shared cell for app and folder icons: squircle clip, dimming, label, badge, jiggle.
 * [iconContent] fills the T-ICON square.
 */
@Composable
fun IconCell(
    phaseKey: String,
    label: String?,
    wallpaper: ImageBitmap,
    pressAlpha: () -> Float,
    jiggle: () -> Float,
    clockSeconds: () -> Float,
    badgeScale: () -> Float,
    showBadge: Boolean,
    modifier: Modifier = Modifier,
    clipIcon: Boolean = true,
    /** Unread notifications (red badge top right), 0 = none. */
    notifications: Int = 0,
    badgeOnGlass: Boolean = false,
    /** Opacity of the label (the folder fades it out during the folder hint). */
    labelAlpha: () -> Float = { 1f },
    iconContent: @Composable () -> Unit,
) {
    val iconSize = DesignTokens.iconSize.dp
    val shape = ContinuousRoundedShape(DesignTokens.iconCorner.dp)
    val phases = remember(phaseKey) { jigglePhases(phaseKey) }
    val resize = iconSizeProgress()
    val animation = LocalHomeIconResize.current
    val useIconLayer = label != null && if (animation == null) {
        com.gamadev.prism.design.IconAppearance.largeIcons
    } else {
        val scaled by remember(animation) { androidx.compose.runtime.derivedStateOf { animation.isRunning || animation.value != 0f } }
        scaled
    }


    Layout(
        modifier = modifier.graphicsLayer { applyJiggle(jiggle(), clockSeconds(), phases) },
        content = {
            Box(
                Modifier
                    .size(iconSize)
                    .then(if (clipIcon) Modifier.clip(shape) else Modifier)
                    .drawWithContent {
                        drawContent()
                        if (clipIcon) com.gamadev.prism.glass.drawObjectRim(this, shape)
                        val a = pressAlpha()
                        if (a > 0f) {
                            val outline = shape.createOutline(size, layoutDirection, this)
                            drawOutline(outline, Color.Black.copy(alpha = a))
                        }
                    }
            ) { iconContent() }
            if (label != null) {
                BasicText(
                    modifier = Modifier.graphicsLayer {
                        // Give the growing icon space before it reaches the text.
                        alpha = labelAlpha() * (1f - resize() / Motion.ICON_SIZE_LABEL_FADE_SHARE).coerceIn(0f, 1f)
                    },
                    text = label,
                    style = TextStyle(
                        fontFamily = Inter,
                        fontSize = DesignTokens.labelFontSize.value.sp,
                        letterSpacing = DesignTokens.labelLetterSpacing.value.sp,
                        color = DesignTokens.labelColor,
                        shadow = DesignTokens.labelShadow,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showBadge) MinusBadge(wallpaper, Modifier.graphicsLayer {
                val s = badgeScale()
                scaleX = s; scaleY = s
            }, onGlass = badgeOnGlass)
            if (notifications > 0) NotificationBadge(notifications)
        },
    ) { measurables, _ ->
        val cellW = DesignTokens.labelWidth.dp.roundToPx()
        val iconPx = iconSize.roundToPx()
        val icon = measurables[0].measure(Constraints.fixed(iconPx, iconPx))
        val labelP = if (label != null) measurables[1].measure(Constraints(maxWidth = cellW)) else null
        val count = if (notifications > 0) measurables.last().measure(Constraints()) else null
        val badge = if (showBadge) measurables[measurables.size - (if (count != null) 2 else 1)].measure(Constraints()) else null
        val baseline = DesignTokens.labelBaseline.dp.roundToPx()
        val height = if (labelP != null) baseline + (labelP.height - labelP[FirstBaseline]) else iconPx
        val iconX = (cellW - iconPx) / 2
        val grow = (DesignTokens.LARGE_ICON_SCALE - 1f) * iconPx / 2f
        val drop = (height - iconPx) / 2f
        layout(cellW, height) {
            if (useIconLayer) icon.placeWithLayer(iconX, 0) {
                val progress = resize()
                val scale = 1f + (DesignTokens.LARGE_ICON_SCALE - 1f) * progress
                scaleX = scale
                scaleY = scale
                translationY = drop * progress
            } else icon.place(iconX, 0)
            labelP?.place((cellW - labelP.width) / 2, baseline - labelP[FirstBaseline])
            badge?.let {
                val x = iconX + DesignTokens.badgeOffsetX.dp.roundToPx() - it.width / 2
                val y = DesignTokens.badgeOffsetY.dp.roundToPx() - it.height / 2
                if (useIconLayer) it.placeWithLayer(x, y, zIndex = 1f) {
                    val progress = resize()
                    translationX = -grow * progress
                    translationY = (drop - grow) * progress
                } else it.place(x, y, zIndex = 1f)
            }
            // The right edge reaches beyond the icon, and wider numbers grow left.
            count?.let {
                val x = iconX + iconPx + NOTIF_OVERHANG.dp.roundToPx() - it.width
                val y = NOTIF_CENTER_Y.dp.roundToPx() - it.height / 2
                if (useIconLayer) it.placeWithLayer(x, y, zIndex = 1f) {
                    val progress = resize()
                    translationX = grow * progress
                    translationY = (drop - grow) * progress
                } else it.place(x, y, zIndex = 1f)
            }
        }
    }
}

/**
 * Red notification badge like iOS [MEASURED] screen recording 10-08 02:34, frame 391 (3×): 25 pt tall, one digit = circle,
 * more digits = capsule; number white with thousands separator ("17.882").
 */
@Composable
internal fun NotificationBadge(count: Int, modifier: Modifier = Modifier) {
    val text = remember(count) { java.text.NumberFormat.getIntegerInstance().format(count) }
    Box(
        modifier.height(NOTIF_H.dp).widthIn(min = NOTIF_H.dp).clip(androidx.compose.foundation.shape.CircleShape)
            .background(NOTIF_RED).padding(horizontal = NOTIF_PAD.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = TextStyle(fontFamily = Inter, fontSize = NOTIF_FONT.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
            letterSpacing = (-0.2).sp, color = Color.White))
    }
}

// Frosted glass for the minus badge and edit buttons [ESTIMATED] after IMG_3124
internal const val FROST_BLUR = 18f
internal const val FROST_WHITE = 0.18f
internal const val FROST_WHITE_DARK = 0.28f

private const val NOTIF_H = 25f          // pt
private const val NOTIF_OVERHANG = 11.3f // right edge beyond the icon (pt)
private const val NOTIF_CENTER_Y = 2.3f  // centre below the icon top (pt)
private const val NOTIF_PAD = 7f
private const val NOTIF_FONT = 16.5f
private val NOTIF_RED = Color(0xFFFF3B30)

/** Cell x (px) from icon x (px). */
fun cellOffsetX(iconX: Float, density: Float): Float =
    iconX - (DesignTokens.labelWidth.value - DesignTokens.iconSize.value) * density / 2

/** Glass circle with a black minus (T-BADGE). Without blur — the wallpaper below the badge is smooth. */
@Composable
internal fun MinusBadge(wallpaper: ImageBitmap, modifier: Modifier, onGlass: Boolean = false) {
    val frostWhite = if (DesignTokens.wallpaperIsDark) FROST_WHITE_DARK else FROST_WHITE
    // Open folder: the same frosted glass, but from what really lies behind it (home screen below the panel, dimmed
    // the same as there) — the wallpaper crop looked too dark in front of the lighter panel background (user 10-14)
    if (onGlass && com.gamadev.prism.glass.LocalGlassBackdrop.current != null) {
        com.gamadev.prism.glass.LiquidGlass(
            shape = com.gamadev.prism.glass.glassCapsule(),
            modifier = modifier.size(DesignTokens.badgeSize.dp),
            blur = FROST_BLUR.dp,
            whiteTint = frostWhite,
            blackTint = DesignTokens.folderBgDim.value,
        ) { MinusGlyph() }
        return
    }
    // Frosted glass like IMG_3124 (strongly blurred, light, no refraction) — stays light even over dark widgets
    GlassSurface(
        wallpaper = wallpaper,
        shape = CircleShape,
        blur = FROST_BLUR.dp,
        tint = 0f,
        whiteTint = frostWhite,
        modifier = modifier.size(DesignTokens.badgeSize.dp),
    ) { MinusGlyph() }
}

/** Black minus stroke of the badge (T-BADGE). */
@Composable
private fun MinusGlyph() = Canvas(Modifier.fillMaxSize()) {
    val w = DesignTokens.badgeMinusWidth.dp.toPx()
    val h = DesignTokens.badgeMinusHeight.dp.toPx()
    drawRoundRect(
        color = Color.Black,
        topLeft = Offset((size.width - w) / 2, (size.height - h) / 2),
        size = Size(w, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2),
    )
}

// ── Jiggle (M-JIGGLE) ───────────────────────────────────────────────────────
class JigglePhases(val rot: Float, val x: Float, val y: Float)

internal fun jigglePhases(key: String): JigglePhases {
    val r = Random(key.hashCode())
    val tau = (2 * PI).toFloat()
    return JigglePhases(r.nextFloat() * tau, r.nextFloat() * tau, r.nextFloat() * tau)
}

/** Jiggle values (rotation °, offset x/y px) — one source for the icon AND for counter-steering in the glass. */
internal fun jiggleValues(amount: Float, t: Float, p: JigglePhases, density: Float): Triple<Float, Float, Float> {
    if (amount <= 0f) return Triple(0f, 0f, 0f)
    val tau = (2 * PI).toFloat()
    val tr = Motion.jiggleTranslation.value * density
    return Triple(
        amount * Motion.jiggleRotation.value * sin(tau * t / Motion.jiggleRotationPeriod.value + p.rot),
        amount * tr * sin(tau * t / Motion.jigglePeriodX.value + p.x),
        amount * tr * sin(tau * t / Motion.jigglePeriodY.value + p.y),
    )
}

internal fun GraphicsLayerScope.applyJiggle(amount: Float, t: Float, p: JigglePhases) {
    if (amount <= 0f) {
        rotationZ = 0f; translationX = 0f; translationY = 0f
        return
    }
    // Pivot = icon centre (measured on the icon, not on the cell incl. label)
    val iconPx = DesignTokens.iconSize.value * density
    if (size.height > 0f) transformOrigin = TransformOrigin(0.5f, (iconPx / 2f) / size.height)
    val (r, x, y) = jiggleValues(amount, t, p, density)
    rotationZ = r; translationX = x; translationY = y
}
