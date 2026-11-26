package com.gamadev.prism.ui.home

import androidx.compose.ui.text.withLink
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamadev.prism.R
import com.gamadev.prism.data.FoldableStyle
import com.gamadev.prism.data.Haptics
import com.gamadev.prism.data.OwnStatusBar
import com.gamadev.prism.glass.LocalGlassBackdrop
import com.gamadev.prism.motion.Motion
import com.gamadev.prism.ui.Inter
import com.gamadev.prism.ui.welcome.GlassBackButton
import com.gamadev.prism.ui.welcome.GlassToggle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gamadev.prism.design.SettingsAppearance
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Sections of the settings — every setting exactly where you'd look for it. */
private enum class Category { General, Appearance, HomeScreen, Permissions, Advanced, About }

/**
 * Launcher settings in the style of the iOS Settings app [recording 09:39, Duo]:
 * wide (unfolded/tablet): sidebar on the left (≈ ⅓, max 320 pt) with header card + sections (icon 29 pt, row 52 pt,
 * selection as a grey capsule), on the right the selected section with a small title at the top and round groups (radius 24).
 * Narrow (folded/phone): list with a large title, tapping slides the section in (back button).
 * Duo: the right column stays free for status symbols + round back/close button (like the original).
 */
@Composable
fun LauncherSettingsSheet(model: HomeModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val appear = remember { Animatable(0f) }
    LaunchedEffect(model.settingsClosing) {
        if (model.settingsClosing) {
            appear.animateTo(0f, Motion.wpeSheet.spec())
            onClose()
        } else {
            appear.animateTo(1f, Motion.wpeSheet.spec())
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val backdrop = rememberLayerBackdrop()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current.density
        val screenW = constraints.maxWidth / density; val screenH = constraints.maxHeight / density
        val duo = FoldableStyle.duoStyle
        val wide = screenW >= WIDE_MIN
        // Duo: keep the right column (dock column) free
        val rightRoom = if (duo) DUO_RIGHT_ROOM else 0f
        var selected by rememberSaveable { mutableStateOf<Category?>(null) }
        androidx.activity.compose.BackHandler(enabled = !wide && selected != null) { selected = null }
        // Back on the top level closes the settings (like leaving an app)
        androidx.activity.compose.BackHandler(enabled = wide || selected == null) { model.closeSettings() }

        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { translationY = (1f - appear.value) * screenH * density }
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            Box(
                Modifier.fillMaxSize().layerBackdrop(backdrop).background(DETAIL_BG)
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                if (wide) {
                    val sideW = minOf(SIDEBAR_MAX, screenW * SIDEBAR_SHARE)
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.width(Dp(sideW)).fillMaxHeight().background(SIDEBAR_BG)) {
                            Sidebar(selected ?: Category.General, tick, onSelect = { selected = it })
                        }
                        Box(Modifier.fillMaxHeight().weight(1f).padding(end = Dp(rightRoom))) {
                            AnimatedContent(selected, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) }, label = "cat") { c ->
                                Detail(c ?: Category.General, model, tick, inlineTitle = true)
                            }
                        }
                    }
                } else {
                    AnimatedContent(selected, transitionSpec = {
                        if (targetState != null) slideInHorizontally(Motion.settingsNavigation.spec()) { it } togetherWith slideOutHorizontally(Motion.settingsNavigation.spec()) { -it / 3 }
                        else slideInHorizontally(Motion.settingsNavigation.spec()) { -it / 3 } togetherWith slideOutHorizontally(Motion.settingsNavigation.spec()) { it }
                    }, label = "nav", modifier = Modifier.fillMaxSize().padding(end = Dp(rightRoom))) { c ->
                        if (c == null) Box(Modifier.fillMaxSize().background(DETAIL_BG)) { CategoryList(tick, largeTitle = true) { selected = it } }
                        else Box(Modifier.fillMaxSize().background(DETAIL_BG)) { Detail(c, model, tick, inlineTitle = false, onBack = { selected = null }) }
                    }
                }
            }
            // Duo: status symbols + round glass button in the right column; otherwise button top right
            CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                if (duo && OwnStatusBar.enabled) {
                    val info by com.gamadev.prism.data.rememberStatusInfo()
                    DuoStatusSymbols(info, (screenW - DUO_COLUMN_FROM_RIGHT) * density, density, PRIMARY)
                }

            }
        }
    }
}

// ── Sidebar / list ─────────────────────────────────────────────────────────────

@Composable
private fun Sidebar(selected: Category?, tick: Int, onSelect: (Category) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(SIDEBAR_TOP.dp))
        HeaderCard(compact = true)
        Spacer(Modifier.height(18.dp))
        Category.entries.forEach { c ->
            if (!visible(c)) return@forEach
            val sel = c == selected
            Row(
                // narrow sidebar (other foldables): a long title wraps into two lines like on iOS, the row gets taller
                Modifier.fillMaxWidth().heightIn(min = ROW_H.dp).clip(RoundedCornerShape((ROW_H / 2).dp)).background(if (sel) SELECTED else Color.Transparent)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(c) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryIcon(c)
                Spacer(Modifier.width(12.dp))
                BasicText(categoryTitle(c), style = LABEL.copy(fontSize = 16.sp, lineHeight = 19.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun CategoryList(tick: Int, largeTitle: Boolean, onSelect: (Category) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(24.dp))
        if (largeTitle) BasicText(stringResource(R.string.launcher_settings), Modifier.padding(bottom = 18.dp),
            style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = PRIMARY))
        HeaderCard(compact = false)
        Spacer(Modifier.height(SECTION_GAP.dp))
        val cats = Category.entries.filter { visible(it) }
        Group {
            cats.forEachIndexed { i, c ->
                Row(
                    Modifier.fillMaxWidth().height(ROW_H.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(c) }
                        .padding(start = 16.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryIcon(c); Spacer(Modifier.width(14.dp))
                    BasicText(categoryTitle(c), Modifier.weight(1f), style = LABEL)
                    Chevron()
                }
                if (i < cats.lastIndex) Divider(start = 59f)
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

/** Header card like "Apple Account": launcher icon, name, version. */
@Composable
private fun HeaderCard(compact: Boolean) {
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(GROUP_CORNER.dp)).background(if (compact) Color.Transparent else CARD)
            .padding(horizontal = if (compact) 4.dp else 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiquidyAppIcon(58f)
        Spacer(Modifier.width(14.dp))
        Column {
            BasicText(stringResource(R.string.settings_app_name), style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = PRIMARY))
            BasicText(stringResource(R.string.settings_header_sub, version), style = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = GREY))
        }
    }
}

// ── Sections ────────────────────────────────────────────────────────────────────────

@Composable
private fun Detail(c: Category, model: HomeModel, tick: Int, inlineTitle: Boolean, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    var subPage by rememberSaveable(c) { mutableStateOf(SubPage.None) }
    androidx.activity.compose.BackHandler(enabled = subPage != SubPage.None) { subPage = SubPage.None }
    AnimatedContent(subPage, transitionSpec = {
        if (targetState != SubPage.None) slideInHorizontally(Motion.settingsNavigation.spec()) { it } togetherWith slideOutHorizontally(Motion.settingsNavigation.spec()) { -it / 3 }
        else slideInHorizontally(Motion.settingsNavigation.spec()) { -it / 3 } togetherWith slideOutHorizontally(Motion.settingsNavigation.spec()) { it }
    }, label = "sub") { sub ->
        Box(Modifier.fillMaxSize().background(DETAIL_BG)) {
            when (sub) {
                SubPage.IconPacks -> IconPackPage(categoryTitle(c)) { subPage = SubPage.None }
                SubPage.Glass -> GlassPage(categoryTitle(c)) { subPage = SubPage.None }
                SubPage.Report -> ReportPage(categoryTitle(c), onClose = { subPage = SubPage.None; model.closeSettings() }) { subPage = SubPage.None }
                SubPage.None -> Column(Modifier.fillMaxSize()) {
                    if (onBack != null) Box(Modifier.fillMaxWidth().height(56.dp)) {
                        GlassBackButton(onClick = onBack,
                            modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp),
                            label = stringResource(R.string.settings_ui_back))
                        BasicText(categoryTitle(c),
                            Modifier.align(Alignment.Center).padding(horizontal = 72.dp),
                            style = LABEL.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(Modifier.weight(1f)) {
                        DetailBody(c, model, tick, inlineTitle) { subPage = it }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailBody(c: Category, model: HomeModel, tick: Int, inlineTitle: Boolean, openSub: (SubPage) -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(TOP_PAD.dp))
        // "General" with a large header card like iOS (icon, title, description), otherwise a small title at the top
        if (c == Category.General) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(GROUP_CORNER.dp)).background(CARD).padding(20.dp)) {
                Box(Modifier.size(60.dp)) { CategoryIcon(c, 60f) }
                Spacer(Modifier.height(14.dp))
                BasicText(categoryTitle(c), style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = PRIMARY))
                Spacer(Modifier.height(6.dp))
                BasicText(stringResource(R.string.settings_general_desc), style = TextStyle(fontFamily = Inter, fontSize = 15.sp, color = GREY))
            }
            Spacer(Modifier.height(SECTION_GAP.dp))
        } else if (inlineTitle) BasicText(categoryTitle(c), Modifier.padding(start = 0.dp, bottom = 18.dp),
            style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = PRIMARY))
        when (c) {
            Category.General -> {
                val isDefault = remember(tick) { isDefaultHome(context) }
                Section(stringResource(R.string.settings_sec_default), footer = stringResource(R.string.settings_default_footer)) {
                    LinkRow(stringResource(R.string.settings_default_home), stringResource(if (isDefault) R.string.settings_yes else R.string.settings_no)) {
                        open(context, android.content.Intent(android.provider.Settings.ACTION_HOME_SETTINGS))
                    }
                }
                Section(stringResource(R.string.settings_sec_feedback), footer = stringResource(R.string.settings_haptics_footer)) {
                    ToggleRow(stringResource(R.string.welcome_haptics), Haptics.enabled) { Haptics.set(context, it) }
                }
                RefreshRateGroup()
            }
            Category.Appearance -> {
                SettingsAppearanceGroup()
                Section(stringResource(R.string.settings_sec_icons_wallpaper)) {
                    LinkRow(stringResource(R.string.settings_icons)) { model.closeSettings(); model.openCustomise() }
                    Divider()
                    LinkRow(stringResource(R.string.edit_wallpaper)) { model.closeSettings(); model.wallpaperEditorOpen = true }
                    Divider()
                    // Align Glass: only for a readable still wallpaper (a live wallpaper can't be refracted anyway)
                    if (com.gamadev.prism.glass.SystemWallpaper.canRead()) {
                        LinkRow(stringResource(R.string.glass_align_title)) { model.closeSettings(); com.gamadev.prism.glass.GlassAlign.open = true }
                        Divider()
                    }
                    // Icon packs: own subpage with a preview per pack
                    val chosen = com.gamadev.prism.data.IconPacks.selected
                    val label = remember(chosen) { chosen?.let { com.gamadev.prism.data.IconPacks.available(context).firstOrNull { p -> p.packageName == it }?.label } }
                    LinkRow(stringResource(R.string.settings_sec_iconpack), label ?: stringResource(R.string.settings_iconpack_system)) { openSub(SubPage.IconPacks) }
                }
                // Live 4×2 Icon Shape visual preview picker
                Section(stringResource(R.string.settings_icon_shape)) {
                    val shapes = com.gamadev.prism.design.IconShape.entries
                    val curShape = com.gamadev.prism.design.IconAppearance.iconShape
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        shapes.chunked(4).forEach { rowShapes ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowShapes.forEach { sh ->
                                    val selected = sh == curShape
                                    val previewShape = com.gamadev.prism.design.IconAppearance.shape(11.dp, sh)
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(if (selected) ACCENT.copy(alpha = 0.14f) else Color.Transparent)
                                            .clickable {
                                                com.gamadev.prism.design.IconAppearance.iconShape = sh
                                                com.gamadev.prism.design.IconAppearance.save(context)
                                            }
                                            .padding(vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Box(
                                            Modifier
                                                .size(40.dp)
                                                .clip(previewShape)
                                                .background(
                                                    androidx.compose.ui.graphics.Brush.linearGradient(
                                                        listOf(Color(0xFF4A90E2), Color(0xFF5E5CE6))
                                                    )
                                                )
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        BasicText(
                                            sh.name,
                                            maxLines = 1,
                                            style = TextStyle(
                                                fontFamily = Inter,
                                                fontSize = 11.sp,
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (selected) ACCENT else GREY,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                // like iOS: Settings › Appearance › Liquid Glass (own page with preview + clear ↔ tinted slider + Fast Glass)
                Section(null) {
                    val t = com.gamadev.prism.glass.GlassClarity.amount
                    val fast = com.gamadev.prism.glass.GlassClarity.fastGlass
                    LinkRow(stringResource(R.string.settings_sec_glass), stringResource(when {
                        fast -> R.string.settings_fast_glass
                        t < 0.25f -> R.string.settings_glass_clear
                        t > 0.75f -> R.string.settings_glass_tinted
                        else -> R.string.settings_glass_between
                    })) { openSub(SubPage.Glass) }
                }
            }
            Category.HomeScreen -> {
                if (FoldableStyle.supportsDuo(context)) Section(stringResource(R.string.settings_sec_layout), footer = stringResource(R.string.settings_duo_footer)) {
                    ToggleRow(stringResource(R.string.settings_duo), FoldableStyle.duoStyle) { FoldableStyle.set(context, it) }
                }
                Section(stringResource(R.string.settings_sec_pages)) {
                    LinkRow(stringResource(R.string.edit_pages)) { model.closeSettings(); model.pagesOverviewRequested = true }
                }
                Section(stringResource(R.string.settings_sec_search), footer = stringResource(R.string.settings_web_suggest_footer)) {
                    ToggleRow(stringResource(R.string.settings_web_suggest), com.gamadev.prism.data.UniversalSearch.WebSuggestions.enabled) {
                        com.gamadev.prism.data.UniversalSearch.WebSuggestions.set(context, it)
                    }
                }
                Section(stringResource(R.string.settings_app_motion), footer = stringResource(R.string.settings_app_motion_footer)) {
                    ToggleRow(stringResource(R.string.settings_app_motion_origin), com.gamadev.prism.data.LaunchMotion.originEnabled) {
                        com.gamadev.prism.data.LaunchMotion.setOriginEnabled(context, it)
                    }
                }
            }
            Category.Permissions -> {
                fun granted(p: String) = context.checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val cal = remember(tick) { granted(android.Manifest.permission.READ_CALENDAR) }
                // Photos: "selected photos only" (Android 14) counts as allowed too
                val photos = remember(tick) { granted(android.Manifest.permission.READ_MEDIA_IMAGES) || granted(android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) }
                val contacts = remember(tick) { granted(android.Manifest.permission.READ_CONTACTS) }
                val location = remember(tick) { granted(android.Manifest.permission.ACCESS_COARSE_LOCATION) }
                val badges = remember(tick) { com.gamadev.prism.data.NotificationBadges.hasAccess(context) }
                val files = remember(tick) { com.gamadev.prism.glass.SystemWallpaper.canRead() }
                val usage = remember(tick) { model.hasUsageAccess() }
                val overlay = remember(tick) { com.gamadev.prism.data.AppLockOverlay.canOverlay(context) }
                // Every row says what the permission is needed for — nobody should grant anything blindly
                Section(stringResource(R.string.settings_sec_access), footer = stringResource(R.string.settings_perm_footer)) {
                    PermissionRow(stringResource(R.string.welcome_perm_calendar), cal, stringResource(R.string.settings_perm_calendar_why)) { openAppSettings(context) }
                    Divider()
                    PermissionRow(stringResource(R.string.welcome_perm_photos), photos, stringResource(R.string.settings_perm_photos_why)) { openAppSettings(context) }
                    Divider()
                    PermissionRow(stringResource(R.string.settings_perm_contacts), contacts, stringResource(R.string.settings_perm_contacts_why)) { openAppSettings(context) }
                    Divider()
                    PermissionRow(stringResource(R.string.settings_perm_location), location, stringResource(R.string.settings_perm_location_why)) { openAppSettings(context) }
                    Divider()
                    PermissionRow(stringResource(R.string.welcome_perm_badges), badges, stringResource(R.string.settings_perm_badges_why)) {
                        com.gamadev.prism.data.NotificationBadges.requestAccess(context)
                    }
                    Divider()
                    PermissionRow(stringResource(R.string.settings_usage_access), usage, stringResource(R.string.settings_perm_usage_why)) {
                        open(context, android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS,
                            android.net.Uri.fromParts("package", context.packageName, null)))
                    }
                    Divider()
                    PermissionRow(stringResource(R.string.settings_perm_files), files, stringResource(R.string.settings_perm_files_why)) {
                        open(context, android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            android.net.Uri.fromParts("package", context.packageName, null)))
                    }
                    Divider()
                    PermissionRow(stringResource(R.string.settings_perm_overlay), overlay, stringResource(R.string.settings_perm_overlay_why)) {
                        open(context, android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.fromParts("package", context.packageName, null)))
                    }
                }
            }
            Category.Advanced -> {
                LayoutBackupSection(model)
                // Report a Problem: own subpage (explanation, view diagnostics, GitHub issue prefilled with diagnostics)
                Section(null) {
                    LinkRow(stringResource(R.string.settings_report)) { openSub(SubPage.Report) }
                }
                // Run the welcome/setup again (permissions, appearance, Duo style, status bar) — nothing is reset
                Section(null) {
                    LinkRow(stringResource(R.string.settings_restart_setup)) {
                        model.closeSettings()
                        com.gamadev.prism.ui.welcome.WelcomeStore.restartRequested = true
                    }
                }
            }
            Category.About -> {
                val info = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull() }
                Section(null) {
                    ValueRow(stringResource(R.string.settings_version), info?.versionName ?: "–")
                    Divider()
                    ValueRow(stringResource(R.string.settings_build), (info?.longVersionCode ?: 0L).toString())
                    Divider()
                    ValueRow("Android", android.os.Build.VERSION.RELEASE)
                    Divider()
                    ValueRow(stringResource(R.string.settings_device), android.os.Build.MODEL)
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun allowed(on: Boolean) = stringResource(if (on) R.string.settings_allowed else R.string.settings_not_allowed)

private fun visible(c: Category) = true

@Composable
private fun categoryTitle(c: Category) = stringResource(when (c) {
    Category.General -> R.string.settings_section_general
    Category.Appearance -> R.string.settings_cat_appearance
    Category.HomeScreen -> R.string.settings_cat_home
    Category.Permissions -> R.string.settings_cat_permissions
    Category.Advanced -> R.string.settings_cat_advanced
    Category.About -> R.string.settings_section_about
})

/** The app icon (res/mipmap ic_launcher) as a tile: background + foreground like the adaptive icon (visible 72 of 108). */
@Composable
private fun LiquidyAppIcon(sizePt: Float) {
    Box(Modifier.size(sizePt.dp).clip(com.gamadev.prism.design.ContinuousRoundedShape((sizePt * TILE_CORNER).dp)), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_background), null,
            Modifier.requiredSize((sizePt * ADAPTIVE_SCALE).dp))
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_foreground), null,
            Modifier.requiredSize((sizePt * ADAPTIVE_SCALE).dp))
    }
}

/**
 * Category tile like in the iOS 27 settings [MEASURED user screenshot 10-15]: continuous corners (≈ 23 % of the
 * edge), slight gradient downwards (≈ 6 % darker) and a fine light rim at the top/bottom; white glyph.
 * Gear and Appearance drawn ourselves in iOS style, the other glyphs Material Symbols Rounded (Apache-2.0) —
 * no SF Symbols. Colours from the screenshot (blue 2/140/252, grey 155→141, black 9→0), orange/indigo [ESTIMATED].
 */
@Composable
private fun CategoryIcon(c: Category, sizePt: Float = ICON) {
    val bg = when (c) {
        Category.General, Category.About -> Color(0xFF99989E)
        Category.Appearance -> Color(0xFF0A0A0A)
        Category.HomeScreen, Category.Permissions -> Color(0xFF028CFC)
            Category.Advanced -> Color(0xFF636366)
    }
    val bottom = Color(bg.red * TILE_SHADE, bg.green * TILE_SHADE, bg.blue * TILE_SHADE)
    val shape = com.gamadev.prism.design.ContinuousRoundedShape((sizePt * TILE_CORNER).dp)
    Box(
        Modifier.size(sizePt.dp).clip(shape).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(bg, bottom)))
            .drawWithContent {
                drawContent()
                // Light rim: brighter at the top and bottom, barely at the sides (Liquid Glass)
                val outline = shape.createOutline(size, layoutDirection, this)
                drawOutline(outline, androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to Color.White.copy(alpha = TILE_RIM), 0.25f to Color.Transparent, 0.75f to Color.Transparent, 1f to Color.White.copy(alpha = TILE_RIM * 0.6f)),
                    style = Stroke(TILE_RIM_W.dp.toPx() * 2))
            },
        contentAlignment = Alignment.Center,
    ) {
        when (c) {
            Category.General -> Canvas(Modifier.size((sizePt * GEAR_SIZE).dp)) { iosGear() }
            Category.Appearance -> Canvas(Modifier.size((sizePt * GEAR_SIZE).dp)) { contrastGlyph() }
            else -> androidx.compose.foundation.Image(
                androidx.compose.ui.graphics.vector.rememberVectorPainter(when (c) {
                    Category.HomeScreen -> SettingsGlyphs.GridView
                                    Category.Permissions -> SettingsGlyphs.PanTool
                    Category.Advanced -> SettingsGlyphs.Build
                    else -> SettingsGlyphs.Info
                }), contentDescription = null,
                modifier = Modifier.size((sizePt * TILE_GLYPH).dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White),
            )
        }
    }
}

/** Gear like the iOS settings [screenshot 10-15]: ≈ 26 fine teeth, inner ring, three spokes, hub. */
private fun DrawScope.iosGear() {
    val s = size.minDimension; val c = center
    val teeth = GEAR_TEETH
    val ro = s * 0.5f; val rt = s * 0.43f // tooth tip / tooth root
    val ring = androidx.compose.ui.graphics.Path().apply {
        for (k in 0 until teeth) {
            val a0 = (k.toFloat() / teeth) * 2 * Math.PI.toFloat()
            val step = 2 * Math.PI.toFloat() / teeth
            fun pt(r: Float, a: Float) = Offset(c.x + r * kotlin.math.cos(a), c.y + r * kotlin.math.sin(a))
            val p0 = pt(rt, a0); val p1 = pt(ro, a0 + step * 0.18f); val p2 = pt(ro, a0 + step * 0.48f); val p3 = pt(rt, a0 + step * 0.66f)
            if (k == 0) moveTo(p0.x, p0.y) else lineTo(p0.x, p0.y)
            lineTo(p1.x, p1.y); lineTo(p2.x, p2.y); lineTo(p3.x, p3.y)
        }
        close()
        // Hole: the inner ring stays as a narrow rim
        addOval(androidx.compose.ui.geometry.Rect(c, s * 0.355f))
        fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
    }
    drawPath(ring, Color.White)
    drawCircle(Color.White, s * 0.30f, c, style = Stroke(s * 0.045f))
    // three spokes (top right, top left, bottom) from the hub to the inner ring
    for (a in listOf(-30f, -150f, 90f)) {
        val r = Math.toRadians(a.toDouble()); val d = Offset(kotlin.math.cos(r).toFloat(), kotlin.math.sin(r).toFloat())
        drawLine(Color.White, c, c + d * (s * 0.30f), s * 0.05f, StrokeCap.Round)
    }
    drawCircle(Color.White, s * 0.06f, c)
}

/** Appearance like iOS [screenshot 10-15]: circle filled white on the left, only outlined on the right; small inner circle the other way round. */
private fun DrawScope.contrastGlyph() {
    val s = size.minDimension; val c = center; val r = s * 0.44f; val w = s * 0.085f
    drawCircle(Color.White, r - w / 2, c, style = Stroke(w))
    drawArc(Color.White, 90f, 180f, true, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r))
    val ri = r * 0.38f
    drawArc(Color.Black, 90f, 180f, true, Offset(c.x - ri, c.y - ri), Size(2 * ri, 2 * ri))
    drawArc(Color.White, -90f, 180f, true, Offset(c.x - ri, c.y - ri), Size(2 * ri, 2 * ri))
}

// ── Building blocks ───────────────────────────────────────────────────────────────────────

@Composable
private fun Section(title: String?, footer: String? = null, content: @Composable () -> Unit) {
    if (title != null) BasicText(title, Modifier.padding(start = 20.dp, bottom = 8.dp),
        style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = GREY))
    Group(content)
    if (footer != null) BasicText(footer, Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
        style = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = GREY))
    Spacer(Modifier.height(SECTION_GAP.dp))
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(GROUP_CORNER.dp)).background(CARD)) { Column(Modifier.fillMaxWidth()) { content() } }
}

@Composable
private fun Divider(start: Float = 20f) = Box(Modifier.padding(start = start.dp, end = 20.dp).fillMaxWidth().height(0.5.dp).background(SEPARATOR))

@Composable
private fun RefreshRateGroup() {
    val context = LocalContext.current
    val rates = com.gamadev.prism.data.DisplayRate
    Section(stringResource(R.string.settings_refresh_rate), footer = stringResource(R.string.settings_refresh_footer)) {
        Column(Modifier.selectableGroup()) {
            (listOf(0) + rates.available).forEachIndexed { index, hz ->
                if (index != 0) Divider()
                val selected = hz == rates.selected || (hz == 0 && rates.selected !in rates.available)
                Row(Modifier.fillMaxWidth().heightIn(min = ROW_H.dp)
                    .selectable(selected = selected, role = Role.RadioButton) { rates.select(context, hz) }
                    .padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(if (hz == 0) stringResource(R.string.settings_ui_system)
                        else stringResource(R.string.settings_refresh_hz, hz), Modifier.weight(1f), style = LABEL)
                    if (selected) BasicText("✓", style = LABEL.copy(color = ACCENT))
                }
            }
        }
    }
}

@Composable
private fun SettingsAppearanceGroup() {
    val context = LocalContext.current
    Section(stringResource(R.string.settings_ui_appearance)) {
        Column(Modifier.selectableGroup()) {
            SettingsAppearance.entries.forEachIndexed { index, option ->
                if (index != 0) Divider()
                val label = stringResource(when (option) {
                    SettingsAppearance.System -> R.string.settings_ui_system
                    SettingsAppearance.Light -> R.string.settings_ui_light
                    SettingsAppearance.Dark -> R.string.settings_ui_dark
                })
                val selected = SettingsAppearance.current == option
                Row(
                    Modifier.fillMaxWidth().heightIn(min = ROW_H.dp)
                        .selectable(selected = selected, role = Role.RadioButton) { SettingsAppearance.select(context, option) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(label, Modifier.weight(1f), style = LABEL)
                    if (selected) Canvas(Modifier.size(16.dp)) {
                        val stroke = 2.dp.toPx()
                        val middle = Offset(size.width * 0.38f, size.height * 0.73f)
                        drawLine(ACCENT, Offset(size.width * 0.10f, size.height * 0.47f), middle, stroke, StrokeCap.Round)
                        drawLine(ACCENT, middle, Offset(size.width * 0.90f, size.height * 0.22f), stroke, StrokeCap.Round)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = ROW_H.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(start = 20.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, Modifier.weight(1f).padding(end = 10.dp), style = LABEL)
        GlassToggle(checked, onChange)
    }
}

@Composable
private fun ValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().height(ROW_H.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, Modifier.weight(1f), style = LABEL)
        BasicText(value, style = LABEL.copy(color = GREY))
    }
}

/**
 * Subpage "Icon Pack": per pack a card with name, checkmark and a preview row of four default apps
 * (Phone, Messages, Photos, Calendar), as they look with this pack. "‹ Back" at the top like iOS.
 */
@Composable
private fun IconPackPage(backLabel: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val packs = remember { com.gamadev.prism.data.IconPacks.available(context) }
    val sample = remember { previewApps(context) }
    val chosen = com.gamadev.prism.data.IconPacks.selected
    val noRipple = remember { MutableInteractionSource() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(TOP_PAD.dp))
        Row(Modifier.clickable(noRipple, null, onClick = onBack).padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(10.dp, 17.dp)) {
                val sw = 2.4.dp.toPx(); val c = ACCENT
                drawLine(c, androidx.compose.ui.geometry.Offset(size.width * 0.9f, size.height * 0.05f), androidx.compose.ui.geometry.Offset(size.width * 0.1f, size.height * 0.5f), sw, androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(c, androidx.compose.ui.geometry.Offset(size.width * 0.1f, size.height * 0.5f), androidx.compose.ui.geometry.Offset(size.width * 0.9f, size.height * 0.95f), sw, androidx.compose.ui.graphics.StrokeCap.Round)
            }
            Spacer(Modifier.width(6.dp))
            BasicText(backLabel, style = LABEL.copy(color = ACCENT))
        }
        BasicText(stringResource(R.string.settings_sec_iconpack), Modifier.padding(bottom = 18.dp),
            style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = PRIMARY))
        (listOf<com.gamadev.prism.data.IconPacks.Pack?>(null) + packs).forEach { pack ->
            val selected = chosen == pack?.packageName
            Column(Modifier.fillMaxWidth().padding(bottom = 14.dp).clip(RoundedCornerShape(GROUP_CORNER.dp)).background(CARD)
                .clickable(noRipple, null) { com.gamadev.prism.data.IconPacks.select(context, pack?.packageName) }
                .padding(horizontal = 20.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(pack?.label ?: stringResource(R.string.settings_iconpack_system), Modifier.weight(1f), maxLines = 1,
                        style = LABEL.copy(fontWeight = FontWeight.SemiBold))
                    if (selected) Canvas(Modifier.size(16.dp, 14.dp)) {
                        val sw = 2.4.dp.toPx(); val c = ACCENT
                        drawLine(c, androidx.compose.ui.geometry.Offset(size.width * 0.05f, size.height * 0.55f), androidx.compose.ui.geometry.Offset(size.width * 0.38f, size.height * 0.92f), sw, androidx.compose.ui.graphics.StrokeCap.Round)
                        drawLine(c, androidx.compose.ui.geometry.Offset(size.width * 0.38f, size.height * 0.92f), androidx.compose.ui.geometry.Offset(size.width * 0.95f, size.height * 0.08f), sw, androidx.compose.ui.graphics.StrokeCap.Round)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly) {
                    sample.forEach { (cn, label) -> PreviewIcon(pack?.packageName, cn, label) }
                }
            }
        }
        BasicText(stringResource(if (packs.isEmpty()) R.string.settings_iconpack_none else R.string.settings_iconpack_footer),
            Modifier.padding(start = 20.dp, end = 20.dp, bottom = 30.dp), style = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = GREY))
    }
}

@Composable
private fun PreviewIcon(pack: String?, cn: android.content.ComponentName, label: String) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val px = (PREVIEW_ICON * density).toInt()
    val img by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, pack, cn) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val orig = context.packageManager.getActivityIcon(cn)
                val themed = pack?.let { com.gamadev.prism.data.IconPacks.iconFrom(context, it, cn, orig, px) }
                com.gamadev.prism.data.IconRenderer.render(themed ?: orig, px, fullBleed = themed != null)
            }.getOrNull()
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width((PREVIEW_ICON + 18f).dp)) {
        Box(Modifier.size(PREVIEW_ICON.dp).clip(com.gamadev.prism.design.ContinuousRoundedShape((PREVIEW_ICON * ICON_CORNER_SHARE).dp))
            .background(SELECTED)) {
            img?.let { androidx.compose.foundation.Image(it, null, Modifier.fillMaxSize()) }
            // fine rim: white glyphs would otherwise be invisible on the white card
            Box(Modifier.fillMaxSize().border(0.5.dp, Color.Black.copy(alpha = 0.12f), com.gamadev.prism.design.ContinuousRoundedShape((PREVIEW_ICON * ICON_CORNER_SHARE).dp)))
        }
        Spacer(Modifier.height(5.dp))
        BasicText(label, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = Inter, fontSize = 12.sp, color = PRIMARY))
    }
}

/** Four default apps for the preview (Phone, Messages, Photos, Calendar) — each the launcher activity. */
private fun previewApps(c: android.content.Context): List<Pair<android.content.ComponentName, String>> {
    val pm = c.packageManager
    fun launcherOf(i: android.content.Intent): Pair<android.content.ComponentName, String>? {
        val pkg = runCatching { pm.resolveActivity(i, 0)?.activityInfo?.packageName }.getOrNull()?.takeIf { it != "android" } ?: return null
        val cn = pm.getLaunchIntentForPackage(pkg)?.component ?: return null
        val label = runCatching { pm.getActivityInfo(cn, 0).loadLabel(pm).toString() }.getOrDefault(pkg)
        return cn to label
    }
    val main = android.content.Intent.ACTION_MAIN
    return listOfNotNull(
        launcherOf(android.content.Intent(android.content.Intent.ACTION_DIAL)),
        launcherOf(android.content.Intent.makeMainSelectorActivity(main, android.content.Intent.CATEGORY_APP_MESSAGING)),
        launcherOf(android.content.Intent.makeMainSelectorActivity(main, android.content.Intent.CATEGORY_APP_GALLERY)),
        launcherOf(android.content.Intent.makeMainSelectorActivity(main, android.content.Intent.CATEGORY_APP_CALENDAR)),
    ).distinctBy { it.first }.take(4)
}

@Composable
private fun LinkRow(label: String, value: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(ROW_H.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(start = 20.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, Modifier.weight(1f), style = LABEL)
        if (value != null) { BasicText(value, style = LABEL.copy(color = GREY)); Spacer(Modifier.width(8.dp)) }
        Chevron()
    }
}

private enum class SubPage { None, IconPacks, Glass, Report }

/**
 * Subpage "Liquid Glass" like iOS (Settings › Appearance › Liquid Glass, user screenshot 10-14): preview card
 * (image at the top, dark text part, above it a round glass button + glass search field that refract the text below live), below the
 * clear ↔ tinted slider and the explanation. Image = own wallpaper (no Apple images), otherwise a colourful substitute.
 */
@Composable
private fun GlassPage(backLabel: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val noRipple = remember { MutableInteractionSource() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(TOP_PAD.dp))
        Row(Modifier.clickable(noRipple, null, onClick = onBack).padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(10.dp, 17.dp)) {
                val sw = 2.4.dp.toPx(); val c = ACCENT
                drawLine(c, Offset(size.width * 0.9f, size.height * 0.05f), Offset(size.width * 0.1f, size.height * 0.5f), sw, StrokeCap.Round)
                drawLine(c, Offset(size.width * 0.1f, size.height * 0.5f), Offset(size.width * 0.9f, size.height * 0.95f), sw, StrokeCap.Round)
            }
            Spacer(Modifier.width(6.dp))
            BasicText(backLabel, style = LABEL.copy(color = ACCENT))
        }
        BasicText(stringResource(R.string.settings_sec_glass), Modifier.padding(bottom = 18.dp),
            style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = PRIMARY))
        GlassPreviewCard(Modifier.widthIn(max = PREVIEW_MAX_W.dp).fillMaxWidth().align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(24.dp))
        Group {
            Row(Modifier.fillMaxWidth().height(ROW_H.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassStyleGlyph(filled = false)
                val sliderLabel = stringResource(R.string.settings_sec_glass)
                com.gamadev.prism.ui.welcome.GlassSlider(
                    com.gamadev.prism.glass.GlassClarity.amount,
                    { com.gamadev.prism.glass.GlassClarity.set(context, it) },
                    Modifier.weight(1f).padding(horizontal = 12.dp).semantics { contentDescription = sliderLabel },
                )
                GlassStyleGlyph(filled = true)
            }
        }
        BasicText(stringResource(R.string.settings_glass_footer), Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
            style = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = GREY))
        Section(null, footer = stringResource(R.string.settings_fast_glass_footer)) {
            ToggleRow(
                stringResource(R.string.settings_fast_glass),
                com.gamadev.prism.glass.GlassClarity.fastGlass,
            ) { com.gamadev.prism.glass.GlassClarity.setFastGlass(context, it) }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Preview card: image + text form the glass source, button and search field above are real LiquidGlass (follows the slider). */
@Composable
private fun GlassPreviewCard(modifier: Modifier) {
    val wallpaper = LocalWallpaper.current
    val fallback = remember { if (wallpaper == null) com.gamadev.prism.glass.Wallpaper.generateColorful(800, 800) else null }
    val image = wallpaper ?: fallback!!
    val source = rememberLayerBackdrop()
    Box(modifier.aspectRatio(1f).clip(RoundedCornerShape(PREVIEW_CORNER.dp)).background(PREVIEW_DARK)) {
        // Glass source: image (top ≈ 55 %) + dark text part
        Column(Modifier.matchParentSize().layerBackdrop(source)) {
            androidx.compose.foundation.Image(image, null, Modifier.fillMaxWidth().weight(PREVIEW_IMAGE_SHARE),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            Column(Modifier.fillMaxWidth().weight(1f - PREVIEW_IMAGE_SHARE).background(PREVIEW_DARK).padding(horizontal = 18.dp, vertical = 14.dp)) {
                BasicText(stringResource(R.string.settings_glass_preview_title), maxLines = 1,
                    style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color.White))
                BasicText(stringResource(R.string.settings_glass_preview_sub), maxLines = 1,
                    style = TextStyle(fontFamily = Inter, fontSize = 22.sp, color = Color.White.copy(alpha = 0.55f)))
                Spacer(Modifier.height(8.dp))
                BasicText(stringResource(R.string.settings_glass_preview_body),
                    style = TextStyle(fontFamily = Inter, fontSize = 17.sp, lineHeight = 22.sp, color = Color.White))
            }
        }
        // Glass over the text: round button (filter) + search field, like in the iOS preview
        CompositionLocalProvider(com.gamadev.prism.glass.LocalGlassBackdrop provides source) {
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = PREVIEW_CONTROLS_BOTTOM.dp),
                verticalAlignment = Alignment.CenterVertically) {
                com.gamadev.prism.glass.LiquidGlass(com.gamadev.prism.glass.glassCapsule(), Modifier.size(PREVIEW_CONTROL_H.dp),
                    whiteTint = PREVIEW_GLASS_WHITE, blackTint = 0f) {
                    Canvas(Modifier.matchParentSize()) { // filter glyph: three lines getting shorter
                        val sw = 2.dp.toPx(); val cx = size.width / 2; val cy = size.height / 2
                        listOf(16f, 11f, 6f).forEachIndexed { i, len ->
                            val y = cy + (i - 1) * 6.dp.toPx(); val half = len.dp.toPx() / 2
                            drawLine(Color.White, Offset(cx - half, y), Offset(cx + half, y), sw, StrokeCap.Round)
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                com.gamadev.prism.glass.LiquidGlass(com.gamadev.prism.glass.glassCapsule(), Modifier.weight(1f).height(PREVIEW_CONTROL_H.dp),
                    whiteTint = PREVIEW_GLASS_WHITE, blackTint = 0f) {
                    Row(Modifier.matchParentSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Canvas(Modifier.size(18.dp)) { // magnifier
                            val sw = 2.dp.toPx(); val r = size.width * 0.33f; val c = Offset(size.width * 0.42f, size.height * 0.42f)
                            drawCircle(Color.White, r, c, style = Stroke(sw))
                            drawLine(Color.White, c + Offset(r * 0.72f, r * 0.72f), Offset(size.width * 0.92f, size.height * 0.92f), sw, StrokeCap.Round)
                        }
                        Spacer(Modifier.width(8.dp))
                        BasicText(stringResource(R.string.search_placeholder_glass), style = TextStyle(fontFamily = Inter, fontSize = 17.sp, color = Color.White.copy(alpha = 0.85f)))
                    }
                }
            }
        }
    }
}

/**
 * Subpage "Report a Problem" (layout like the Animation Module): card with icon, title, explanation; below it "Report on
 * GitHub" (opens a new issue, template + diagnostics already in the text field) and "Copy Diagnostics", then the diagnostics
 * themselves to read — nothing is sent without the user.
 */
@Composable
private fun ReportPage(backLabel: String, onClose: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val noRipple = remember { MutableInteractionSource() }
    val report = remember { com.gamadev.prism.data.Diagnostics.report(context) }
    val intro = stringResource(R.string.settings_report_template)
    var copied by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(TOP_PAD.dp))
        Row(Modifier.clickable(noRipple, null, onClick = onBack).padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(10.dp, 17.dp)) {
                val sw = 2.4.dp.toPx(); val c = ACCENT
                drawLine(c, Offset(size.width * 0.9f, size.height * 0.05f), Offset(size.width * 0.1f, size.height * 0.5f), sw, StrokeCap.Round)
                drawLine(c, Offset(size.width * 0.1f, size.height * 0.5f), Offset(size.width * 0.9f, size.height * 0.95f), sw, StrokeCap.Round)
            }
            Spacer(Modifier.width(6.dp))
            BasicText(backLabel, style = LABEL.copy(color = ACCENT))
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(GROUP_CORNER.dp)).background(CARD).padding(20.dp)) {
            HeroTile(MODULE_HERO, Color(0xFFFF3B30), SettingsGlyphs.BugReport)
            Spacer(Modifier.height(14.dp))
            BasicText(stringResource(R.string.settings_report), style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = PRIMARY))
            Spacer(Modifier.height(6.dp))
            BasicText(stringResource(R.string.settings_report_about), style = TextStyle(fontFamily = Inter, fontSize = 16.sp, lineHeight = 21.sp, color = GREY))
        }
        Spacer(Modifier.height(SECTION_GAP.dp))
        Group {
            LinkRow(stringResource(R.string.settings_report_github)) {
                open(context, android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(com.gamadev.prism.data.Diagnostics.issueUrl(context, intro))))
            }
            if (com.gamadev.prism.data.Diagnostics.emailEnabled) {
                Divider()
                LinkRow(stringResource(R.string.settings_report_email)) {
                    open(context, com.gamadev.prism.data.Diagnostics.emailIntent(context, intro))
                }
            }
            Divider()
            LinkRow(stringResource(R.string.settings_diagnostics_copy), if (copied) stringResource(R.string.settings_diagnostics_copied) else null) {
                com.gamadev.prism.data.Diagnostics.copy(context); copied = true
            }
        }
        BasicText(stringResource(R.string.settings_diagnostics_footer), Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
            style = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = GREY))
        Spacer(Modifier.height(SECTION_GAP.dp))
        // Screen recording for the report: consent (system dialog) → recording with a Stop notification → issue opens
        val bugRec = com.gamadev.prism.data.BugRecorder
        val capture = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
            if (r.resultCode == android.app.Activity.RESULT_OK && r.data != null) {
                bugRec.start(context, r.resultCode, r.data!!)
                onClose() // close the settings: the user shows the problem right away on the home screen
            }
        }
        val askCapture = {
            val mpm = context.getSystemService(android.media.projection.MediaProjectionManager::class.java)
            // whole screen, not "share one app": the bug can be anywhere (Android 14+ can preselect this)
            capture.launch(if (android.os.Build.VERSION.SDK_INT >= 34)
                mpm.createScreenCaptureIntent(android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay())
            else mpm.createScreenCaptureIntent())
        }
        val notifications = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { askCapture() } // Stop lives in the notification
        Group {
            if (bugRec.recording) LinkRow(stringResource(R.string.settings_report_recording)) { }
            else LinkRow(stringResource(R.string.settings_report_record)) {
                if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                    notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                else askCapture()
            }
        }
        BasicText(stringResource(R.string.settings_report_record_footer), Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
            style = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = GREY))
        Spacer(Modifier.height(SECTION_GAP.dp))
        BasicText(stringResource(R.string.settings_report_included), Modifier.padding(start = 20.dp, bottom = 8.dp),
            style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = GREY))
        BasicText(report, Modifier.fillMaxWidth().clip(RoundedCornerShape(GROUP_CORNER.dp)).background(CARD).padding(16.dp),
            style = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp, color = PRIMARY))
        Spacer(Modifier.height(40.dp))
    }
}

/** Large tile at the top of subpages (category style: gradient, continuous corners, white glyph). */
@Composable
private fun HeroTile(sizePt: Float, bg: Color, glyph: androidx.compose.ui.graphics.vector.ImageVector) {
    val shape = com.gamadev.prism.design.ContinuousRoundedShape((sizePt * TILE_CORNER).dp)
    Box(Modifier.size(sizePt.dp).clip(shape).background(androidx.compose.ui.graphics.Brush.verticalGradient(
        listOf(bg, Color(bg.red * TILE_SHADE, bg.green * TILE_SHADE, bg.blue * TILE_SHADE)))), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Image(androidx.compose.ui.graphics.vector.rememberVectorPainter(glyph), null,
            Modifier.size((sizePt * TILE_GLYPH).dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White))
    }
}

/** Glyph on the glass slider (iOS): two offset rounded shapes — left only outlined (clear), right filled (tinted). */
@Composable
private fun GlassStyleGlyph(filled: Boolean) = Canvas(Modifier.size(26.dp, 22.dp)) {
    val w = 18.dp.toPx(); val h = 13.dp.toPx(); val r = CornerRadius(4.dp.toPx()); val sw = 1.6.dp.toPx()
    val back = Offset(size.width - w, 0f); val front = Offset(0f, size.height - h)
    if (filled) {
        drawRoundRect(GREY.copy(alpha = 0.55f), back, Size(w, h), r)
        drawRoundRect(GREY, front, Size(w, h), r)
    } else {
        drawRoundRect(GREY, back, Size(w, h), r, style = Stroke(sw))
        drawRoundRect(Color.White, front, Size(w, h), r)
        drawRoundRect(GREY, front, Size(w, h), r, style = Stroke(sw))
    }
}

/** Permission: name + status, below it in grey what it's needed for. */
@Composable
private fun PermissionRow(label: String, granted: Boolean, why: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = ROW_H.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(start = 20.dp, end = 18.dp, top = 11.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(label, style = LABEL)
            BasicText(why, Modifier.padding(top = 3.dp, end = 12.dp), style = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 17.sp, color = GREY))
        }
        BasicText(allowed(granted), style = LABEL.copy(color = GREY)); Spacer(Modifier.width(8.dp))
        Chevron()
    }
}

@Composable
private fun Chevron() = Canvas(Modifier.size(8.dp, 14.dp)) {
    val sw = 2.dp.toPx()
    drawLine(CHEVRON, Offset(0f, 0f), Offset(size.width, size.height / 2), sw, StrokeCap.Round)
    drawLine(CHEVRON, Offset(size.width, size.height / 2), Offset(0f, size.height), sw, StrokeCap.Round)
}

private fun isDefaultHome(c: android.content.Context): Boolean {
    val h = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME)
    return c.packageManager.resolveActivity(h, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == c.packageName
}

private fun open(c: android.content.Context, i: android.content.Intent) { runCatching { c.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }
private fun openAppSettings(c: android.content.Context) = open(c, android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
    android.net.Uri.fromParts("package", c.packageName, null)))

// Dimensions [MEASURED recording 09:39, 0.729 pt/px] or iOS default
private const val WIDE_MIN = 700f
private const val SIDEBAR_MAX = 320f
private const val SIDEBAR_SHARE = 0.34f
private const val DUO_RIGHT_ROOM = 85f        // content ends 85 pt before the right edge (866 of 951)
private const val DUO_COLUMN_FROM_RIGHT = 47f
private const val DUO_BUTTON_Y = 190f
private const val TOP_PAD = 16f
private const val SIDEBAR_TOP = 24f   // header card ≈ 100 pt [MEASURED]
// Liquid Glass preview [MEASURED] user screenshot 10-14 (920 px = 402 pt): card ≈ 370 × 370 pt, corners ≈ 26 pt, image ≈ 55 %,
// button/search field 46 pt tall, bottom edge ≈ 44 pt above the card edge
private const val PREVIEW_MAX_W = 420f
private const val PREVIEW_CORNER = 26f
private const val PREVIEW_IMAGE_SHARE = 0.55f
private const val PREVIEW_CONTROL_H = 46f
private const val PREVIEW_CONTROLS_BOTTOM = 44f
private const val PREVIEW_GLASS_WHITE = 0.1f
private val PREVIEW_DARK = Color(0xFF252321)
private const val ROW_H = 52f
private const val ICON = 29f
private const val TILE_CORNER = 0.225f // corners of the category tile relative to the edge (iOS settings)
private const val TILE_GLYPH = 0.62f   // glyph relative to the tile
private const val TILE_SHADE = 0.94f   // tile ≈ 6 % darker at the bottom [MEASURED] iOS 27 settings
private const val TILE_RIM = 0.22f     // light rim at the top (bottom 60 %) [MEASURED qualitatively]
private const val TILE_RIM_W = 0.6f    // dp width of the light rim
private const val GEAR_SIZE = 0.72f    // gear/Appearance relative to the tile [MEASURED]
private const val GEAR_TEETH = 26
private const val MODULE_HERO = 60f    // pt icon at the top of the module page (iOS submenu header)
private const val GITHUB_URL = "https://github.com/janhurn9400/liquidy"
private const val ADAPTIVE_SCALE = 1.5f // adaptive icon: 108 dp layer, 72 of it visible
private const val GROUP_CORNER = 24f
private const val SECTION_GAP = 26f
private val SIDEBAR_BG get() = com.gamadev.prism.design.DesignTokens.Settings.sidebar
private val DETAIL_BG get() = com.gamadev.prism.design.DesignTokens.Settings.background
private val SELECTED get() = com.gamadev.prism.design.DesignTokens.Settings.selected
private val GREY get() = com.gamadev.prism.design.DesignTokens.Settings.secondary
private val SEPARATOR get() = com.gamadev.prism.design.DesignTokens.Settings.separator
private val CHEVRON get() = com.gamadev.prism.design.DesignTokens.Settings.chevron
private const val PREVIEW_ICON = 54f
/** T-ICON-R / icon size (16.33 / 60). */
private const val ICON_CORNER_SHARE = 16.33f / 60f
private val PRIMARY get() = com.gamadev.prism.design.DesignTokens.Settings.primary
private val CARD get() = com.gamadev.prism.design.DesignTokens.Settings.card
private val ACCENT get() = com.gamadev.prism.design.DesignTokens.Settings.accent
private val LABEL get() = TextStyle(fontFamily = Inter, fontSize = 17.sp, color = PRIMARY)


/** System document picker keeps backup files in a location chosen by the user. */
@Composable
private fun LayoutBackupSection(model: HomeModel) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<com.gamadev.prism.data.LayoutBackup?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var hasRecovery by remember { mutableStateOf(model.layoutRecovery() != null) }
    fun report(id: Int) { status = context.getString(id) }
    val create = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val snapshot = model.layoutBackup()
            busy = true
            scope.launch {
                val result = runCatching {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val bytes = com.gamadev.prism.data.LayoutBackupCodec.encode(snapshot).toByteArray(Charsets.UTF_8)
                        require(bytes.size <= com.gamadev.prism.data.LayoutBackupCodec.MAX_BYTES)
                        checkNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { it.write(bytes) }
                    }
                }
                report(if (result.isSuccess) R.string.layout_backup_saved else R.string.layout_backup_save_failed)
                busy = false
            }
        }
    }
    val open = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val result = runCatching {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val bytes = checkNotNull(context.contentResolver.openInputStream(uri)).use {
                            it.readNBytes(com.gamadev.prism.data.LayoutBackupCodec.MAX_BYTES + 1)
                        }
                        require(bytes.size <= com.gamadev.prism.data.LayoutBackupCodec.MAX_BYTES)
                        com.gamadev.prism.data.LayoutBackupCodec.decode(bytes.toString(Charsets.UTF_8))
                    }
                }
                pending = result.getOrNull()
                if (result.isFailure) report(R.string.layout_backup_invalid)
                busy = false
            }
        }
    }
    Section(stringResource(R.string.layout_backup_title), footer = status ?: stringResource(R.string.layout_backup_footer)) {
        LinkRow(stringResource(if (busy) R.string.layout_backup_working else R.string.layout_backup_save)) {
            if (!busy) create.launch("liquidy-layout-${java.time.LocalDate.now()}.json")
        }
        Divider()
        LinkRow(stringResource(R.string.layout_backup_restore)) {
            if (!busy) open.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
        }
        if (hasRecovery) {
            Divider()
            LinkRow(stringResource(R.string.layout_backup_undo)) {
                if (!busy) {
                    val recovery = model.layoutRecovery()
                    pending = recovery?.let { runCatching { com.gamadev.prism.data.LayoutBackupCodec.decode(it) }.getOrNull() }
                    if (pending == null) report(R.string.layout_backup_invalid)
                }
            }
        }
    }
    pending?.let { backup ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pending = null },
            containerColor = CARD, titleContentColor = PRIMARY, textContentColor = GREY,
            title = { BasicText(stringResource(R.string.layout_backup_restore), style = LABEL.copy(fontWeight = FontWeight.SemiBold)) },
            text = { BasicText(stringResource(R.string.layout_backup_confirm,
                backup.layout.pages.size, backup.layout.folders.size, backup.layout.widgets.size), style = LABEL.copy(color = GREY)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    pending = null
                    val result = runCatching { model.restoreLayoutBackup(backup) }
                    hasRecovery = model.layoutRecovery() != null
                    report(if (result.isSuccess) R.string.layout_backup_restored else R.string.layout_backup_restore_failed)
                }) { BasicText(stringResource(R.string.layout_backup_restore_action), style = LABEL.copy(color = ACCENT)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { pending = null }) {
                    BasicText(stringResource(android.R.string.cancel), style = LABEL.copy(color = ACCENT))
                }
            },
        )
    }
}
