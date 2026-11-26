package com.gamadev.prism.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.gamadev.prism.R
import com.gamadev.prism.data.AppEntry
import com.gamadev.prism.design.DesignTokens
import com.gamadev.prism.glass.LiquidGlass
import com.gamadev.prism.glass.glassCapsule
import com.gamadev.prism.motion.Motion
import com.gamadev.prism.ui.Inter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

/**
 * Glass capsule above the dock (T-PILL) that morphs into the search field (M-SEARCH-MORPH, measured recording5):
 *  - Rest: "Search"; page dots while swiping (M-PILL).
 *  - While pulling down it stays sharp (it does NOT lie in the blurred layer) and moves along with ¼ of h.
 *  - From h ≈ 94 pt it morphs (still while dragging) in width into the search field; back below ≈ 62 pt.
 *  - Open: the field stays at its height until the keyboard reaches it, then rides on it and docks at full width.
 */
@Composable
fun SearchPill(
    model: HomeModel,
    pager: PagerState,
    screenWidth: Dp,
    screenHeight: Dp,
    y: Dp,
    forceDots: Boolean = false,
    dotCount: Int = pager.pageCount,
    /**
     * Duo style: at rest the pill is the round magnifier button ([duoButton], screen px) in the right column; on open
     * it morphs into the field at the bottom centre ([duoFieldWidth] wide, centre at [duoFieldCenterX]) [MEASURED] 21:28, 42 s.
     */
    duoButton: Rect? = null,
    duoFieldWidth: Dp = 0.dp,
    duoFieldCenterX: Dp = 0.dp,
    /** Duo: centre y of the field without keyboard (bottom centre, measured ≈ 636 pt unfolded). */
    duoFieldRestY: Dp = 0.dp,
    onLaunch: (AppEntry, Rect) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showDots by remember { mutableStateOf(false) }
    val forceDotsState = androidx.compose.runtime.rememberUpdatedState(forceDots)
    LaunchedEffect(pager) {
        // Edit mode: always dots [MEASURED] IMG_3124
        snapshotFlow { pager.isScrolling || forceDotsState.value }.collectLatest { scrolling ->
            if (scrolling) showDots = true
            else {
                delay(Motion.pillDotsHold.value.roundToInt().toLong())
                showDots = false
            }
        }
    }
    val dotsAlphaState = animateFloatAsState(
        targetValue = if (showDots) 1f else 0f,
        animationSpec = tween(Motion.pillCrossfade.value.roundToInt()),
        label = "pillDots",
    )

    // ── Keyboard / focus (formerly in SearchOverlay) ─────────────────────────────
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    // Keyboard like iOS: peeks out on morph (app-driven), comes up on release without a start delay
    val view = androidx.compose.ui.platform.LocalView.current
    val kbScope = androidx.compose.runtime.rememberCoroutineScope()
    val driver = remember(view) { com.gamadev.prism.ui.search.KeyboardDriver(view, kbScope) }
    val densityForKb = LocalDensity.current
    LaunchedEffect(model.searchMorphOn, model.searchOpen) {
        when {
            model.searchOpen -> {
                focus.requestFocus()
                if (driver.active) driver.animateTo(null, Motion.searchKeyboard, finish = true) else keyboard?.show()
            }
            model.searchMorphOn -> {
                focus.requestFocus() // binds the keyboard without showing it (showKeyboardOnFocus = false)
                driver.start()
                driver.animateTo(with(densityForKb) { Motion.searchKeyboardPeek.value.dp.toPx() }, Motion.searchKeyboard)
            }
            else -> {
                if (driver.active) driver.animateTo(0f, Motion.searchClose, finish = false)?.join()
                focusManager.clearFocus(); keyboard?.hide()
            }
        }
    }

    // ── Geometry (read in layout/draw phases to avoid per-frame recomposition) ──
    val density = LocalDensity.current
    val pages = dotCount // the App Library gets no dot
    val pillWidth = maxOf(DesignTokens.pillWidth.dp, DesignTokens.dotPitch.dp * (pages + 1))
    val pillHeight = DesignTokens.pillHeight.dp
    val fieldHeight = DesignTokens.searchFieldHeight.dp
    val duo = duoButton != null
    val open = model.searchOpen || model.query.isNotEmpty()
    val imeInsets = WindowInsets.ime
    val navInsets = WindowInsets.navigationBars
    val screenHeightPx = with(density) { screenHeight.toPx() }
    val pillWidthPx = with(density) { pillWidth.toPx() }
    val pillHeightPx = with(density) { pillHeight.toPx() }
    val fieldWidthPx = with(density) { (if (duo) duoFieldWidth else screenWidth - DesignTokens.searchFieldSide.dp * 2).toPx() }
    val fieldHeightPx = with(density) { fieldHeight.toPx() }
    val duoFieldRestYPx = with(density) { duoFieldRestY.toPx() }
    val yPx = with(density) { y.toPx() }
    val centerXPx = with(density) { (if (duo) duoFieldCenterX else screenWidth / 2).toPx() }
    val restTopAtClosePx = if (duo) duoFieldRestYPx - fieldHeightPx / 2f
        else yPx + (Motion.searchPillFollow.value * model.searchReleaseH * density.density)

    fun computeRestTopPx(): Float {
        val m = model.searchMorph.value.coerceIn(0f, 1.05f)
        val h = model.searchProgress.value
        val curH = if (duo) fieldHeightPx else pillHeightPx + (fieldHeightPx - pillHeightPx) * m.coerceIn(0f, 1f)
        val centerYClosed = if (duo) {
            val startY = screenHeightPx + fieldHeightPx
            startY + (duoFieldRestYPx - startY) * m.coerceIn(0f, 1f)
        } else {
            yPx + pillHeightPx / 2f + Motion.searchPillFollow.value * (if (model.searchOpen) model.searchReleaseH else h) * density.density
        }
        return centerYClosed - curH / 2f
    }

    val lastTopPx = remember { floatArrayOf(computeRestTopPx()) }
    fun keyboardTopPx(d: androidx.compose.ui.unit.Density): Float = with(d) {
        screenHeightPx - maxOf(imeInsets.getBottom(this), navInsets.getBottom(this)) -
            (DesignTokens.searchFieldGap.dp + fieldHeight).toPx()
    }
    // Only when closing does the field glide back from its last position to the live pill rest position with M-SEARCH-CLOSE.
    val closeT = remember { androidx.compose.animation.core.Animatable(1f) }
    val closeStartTopPx = remember { floatArrayOf(restTopAtClosePx) }
    val closing = remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(open) {
        val targetRest = computeRestTopPx()
        if (!open && lastTopPx[0] < targetRest - 1f) {
            closeStartTopPx[0] = lastTopPx[0]
            closing.value = true
            try {
                closeT.snapTo(0f)
                closeT.animateTo(1f, Motion.searchClose.spec())
            } finally {
                closing.value = false
            }
        }
        closing.value = false
    }
    fun topPx(d: androidx.compose.ui.unit.Density): Float {
        val rest = computeRestTopPx()
        val top = when {
            open -> minOf(rest, keyboardTopPx(d))
            closing.value -> closeStartTopPx[0] + (rest - closeStartTopPx[0]) * closeT.value
            else -> rest
        }
        lastTopPx[0] = top
        return top
    }

    val duoVisible by remember(model, duo) {
        androidx.compose.runtime.derivedStateOf { !duo || model.searchMorph.value >= 0.001f || model.searchOpen }
    }
    if (!duoVisible) return // Duo: field only during search
    // Fast glass also while searching: the live backdrop re-recorded the whole (blurring) home screen every frame of the
    // pull-down (release build: 27 % → 9 % janky frames on the phone, 10-18). Behind the field we show the pre-blurred
    // wallpaper faded in with the search blur instead of the blurred home content (visually near-identical).
    val wb = com.gamadev.prism.glass.LocalWallpaperBlur.current
    val searchSoft = remember(wb) {
        com.gamadev.prism.glass.WallpaperBlur(wb.blurred) {
            val ramp = (model.searchProgress.value / com.gamadev.prism.motion.Motion.searchBlurRamp.value).coerceIn(0f, 1f) *
                DesignTokens.searchBlur.value / DesignTokens.searchBlurOpen.value
            val atRelease = (model.searchReleaseH / com.gamadev.prism.motion.Motion.searchBlurRamp.value).coerceIn(0f, 1f) *
                DesignTokens.searchBlur.value / DesignTokens.searchBlurOpen.value
            val openA = model.searchOpenAmount.value.coerceIn(0f, 1f)
            val handoff = if (model.searchOpen) atRelease + (1f - atRelease) * openA else 0f
            maxOf(wb.amount(), ramp, openA, handoff)
        }
    }
    val showPillContent by remember(model, duo) {
        androidx.compose.runtime.derivedStateOf {
            if (duo) false
            else (1f - (model.searchMorph.value.coerceIn(0f, 1f) / Motion.searchMorphPillOut.value).coerceIn(0f, 1f)) > 0.01f
        }
    }
    val showFieldContent by remember(model, duo) {
        androidx.compose.runtime.derivedStateOf {
            model.searchMorph.value > 0.01f || model.searchOpen || duo
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(com.gamadev.prism.glass.LocalWallpaperBlur provides searchSoft) {
    LiquidGlass(
        shape = glassCapsule(),
        thickness = DesignTokens.pillHeight.value / DesignTokens.dockHeight.value,
        // Search field: clear glass (recording5). The glass source isn't dimmed, but the pill lies above the
        // search dimming → the same dimming as tint, otherwise the field looks light instead of dark-clear.
        whiteTint = DesignTokens.glassWhiteTint.value,
        blackTint = DesignTokens.glassTint.value,
        onWallpaper = true,
        whiteTintProvider = {
            val fieldMix = model.searchMorph.value.coerceIn(0f, 1f)
            DesignTokens.glassWhiteTint.value + (DesignTokens.searchFieldTint.value - DesignTokens.glassWhiteTint.value) * fieldMix
        },
        blackTintProvider = { (DesignTokens.glassTint.value + model.searchDim()).coerceAtMost(1f) },
        modifier = Modifier.layout { measurable, _ ->
            val fieldMix = model.searchMorph.value.coerceIn(0f, 1f)
            val curW = (if (duo) fieldWidthPx else pillWidthPx + (fieldWidthPx - pillWidthPx) * fieldMix).roundToInt().coerceAtLeast(1)
            val curH = (if (duo) fieldHeightPx else pillHeightPx + (fieldHeightPx - pillHeightPx) * fieldMix).roundToInt().coerceAtLeast(1)
            val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(curW, curH))
            val x = (centerXPx - curW / 2f).roundToInt()
            val yPos = topPx(this).roundToInt()
            layout(curW, curH) { placeable.place(x, yPos) }
        },
    ) {
        // Pill content: "Search" or dots
        if (showPillContent) {
            Row(
                Modifier.fillMaxSize().graphicsLayer {
                    val fieldMix = model.searchMorph.value.coerceIn(0f, 1f)
                    val pillAlpha = 1f - (fieldMix / Motion.searchMorphPillOut.value).coerceIn(0f, 1f)
                    alpha = (1f - dotsAlphaState.value) * pillAlpha
                },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MagnifierGlyph(DesignTokens.pillIconSize.dp, DesignTokens.pillContentColor)
                Spacer(Modifier.width(DesignTokens.pillIconSize.dp * PILL_ICON_GAP))
                BasicText(
                    text = stringResource(R.string.search),
                    style = TextStyle(fontFamily = Inter, fontSize = DesignTokens.pillFontSize.value.sp, color = DesignTokens.pillContentColor),
                )
            }
            Box(Modifier.fillMaxSize().graphicsLayer {
                val fieldMix = model.searchMorph.value.coerceIn(0f, 1f)
                val pillAlpha = 1f - (fieldMix / Motion.searchMorphPillOut.value).coerceIn(0f, 1f)
                alpha = dotsAlphaState.value * pillAlpha
            }) {
                Canvas(Modifier.fillMaxSize()) {
                    val pitch = DesignTokens.dotPitch.dp.toPx()
                    val r = DesignTokens.dotSize.dp.toPx() / 2f
                    val first = size.width / 2f - pitch * (pages - 1) / 2f
                    val current = pager.currentPage
                    for (i in 0 until pages) {
                        drawCircle(
                            color = Color.White.copy(alpha = if (i == current) 1f else DesignTokens.dotInactiveAlpha.value),
                            radius = r,
                            center = Offset(first + i * pitch, size.height / 2f),
                        )
                    }
                }
            }
        }
        // Search field content: magnifier, input, placeholder (grows out of the pill)
        if (showFieldContent) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = DesignTokens.searchFieldPadding.dp).graphicsLayer {
                    val fieldMix = model.searchMorph.value.coerceIn(0f, 1f)
                    val fieldContentAlpha = ((fieldMix - Motion.searchMorphFieldIn.value) / (1f - Motion.searchMorphFieldIn.value)).coerceIn(0f, 1f)
                    alpha = if (duo) 1f else fieldContentAlpha
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val glyph = DesignTokens.searchGlyphColor
                MagnifierGlyph(DesignTokens.searchFieldIcon.dp, glyph)
                Spacer(Modifier.width(DesignTokens.searchFieldIconGap.dp))
                Box(Modifier.weight(1f)) {
                    val style = TextStyle(fontFamily = Inter, fontSize = DesignTokens.searchFieldFontSize.value.sp, color = DesignTokens.searchTextColor)
                    if (model.query.isEmpty()) {
                        BasicText(stringResource(R.string.search), style = style.copy(color = DesignTokens.searchPlaceholderColor), maxLines = 1)
                    }
                    BasicTextField(
                        value = model.query,
                        onValueChange = { model.query = it },
                        singleLine = true,
                        textStyle = style,
                        cursorBrush = SolidColor(Color.White),
                        // Focus without opening automatically — the KeyboardDriver controls the keyboard
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, showKeyboardOnFocus = false),
                        keyboardActions = KeyboardActions(onSearch = {
                            // iOS: input opens the best match — an app whose name starts like that, otherwise the web search
                            val app = model.searchResults(1).firstOrNull()?.takeIf { it.label.startsWith(model.query.trim(), ignoreCase = true) }
                            if (app != null) onLaunch(app, Rect.Zero)
                            else if (model.query.isNotBlank()) com.gamadev.prism.data.UniversalSearch.searchWeb(context, model.query)
                            model.closeSearch(animated = false)
                        }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                MicGlyph(DesignTokens.searchMicSize.dp, glyph)
            }
        }
    }
    } // LocalWallpaperBlur (search)
}

private const val DUO_LUPE = 19f // magnifier in the round Duo button (pt)
private const val PILL_ICON_GAP = 0.5f // magnifier → "Search" = half the magnifier width [MEASURED] IMG_3123 (≈ 5 pt)

/**
 * Microphone like in the iOS search field (no SF Symbol), outline with uniform stroke width:
 * outlined capsule, U-bracket as one continuous path, stem and horizontal foot. Proportions after IMG_3131 [MEASURED roughly].
 */
@Composable
private fun MicGlyph(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val h = this.size.height
        val cx = this.size.width / 2
        val stroke = Stroke(width = h * 0.07f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        // capsule
        val capW = h * 0.30f
        drawRoundRect(color, topLeft = Offset(cx - capW / 2, h * 0.05f), size = androidx.compose.ui.geometry.Size(capW, h * 0.52f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(capW / 2), style = stroke)
        // U-bracket: straight down, semicircle, straight up — one path, no seams
        val r = h * 0.27f
        val armTop = h * 0.36f
        val arcCenterY = h * 0.50f
        val u = androidx.compose.ui.graphics.Path().apply {
            moveTo(cx - r, armTop)
            lineTo(cx - r, arcCenterY)
            arcTo(androidx.compose.ui.geometry.Rect(cx - r, arcCenterY - r, cx + r, arcCenterY + r), 180f, -180f, false)
            lineTo(cx + r, armTop)
        }
        drawPath(u, color, style = stroke)
        // Stem + foot
        drawLine(color, Offset(cx, arcCenterY + r), Offset(cx, h * 0.93f), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(cx - h * 0.19f, h * 0.93f), Offset(cx + h * 0.19f, h * 0.93f), stroke.width, StrokeCap.Round)
    }
}

/** Magnifier as a vector (no SF Symbol). Proportions [ESTIMATED] after IMG_3123. */
@Composable
internal fun MagnifierGlyph(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val stroke = this.size.width * 0.12f // the iOS magnifier is slim (IMG_3131)
        val r = this.size.width * 0.33f
        val c = Offset(r + stroke / 2, r + stroke / 2)
        drawCircle(color, radius = r, center = c, style = Stroke(stroke))
        val d = r * 0.7071f
        drawLine(color, Offset(c.x + d, c.y + d), Offset(this.size.width - stroke / 2, this.size.height - stroke / 2), stroke, StrokeCap.Round)
    }
}

