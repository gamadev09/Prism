package com.gamadev.prism

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalView
import com.gamadev.prism.data.AppRepository
import com.gamadev.prism.data.LayoutStore
import com.gamadev.prism.tuning.DebugOptions
import com.gamadev.prism.ui.home.HomeModel
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.gamadev.prism.design.DesignTokens
import com.gamadev.prism.glass.Wallpaper
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.gamadev.prism.tuning.Tunables
import com.gamadev.prism.ui.debug.DebugPanel
import com.gamadev.prism.ui.debug.DebugState
import com.gamadev.prism.ui.debug.threeFingerLongPress
import com.gamadev.prism.ui.home.HomeScreen
import com.gamadev.prism.ui.home.PagerState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.math.roundToInt

/** The status bar is hidden when opening a folder up to this progress [ESTIMATED]. */
private const val FOLDER_STATUS_FADE = 0.5f

class MainActivity : ComponentActivity() {

    private lateinit var repository: AppRepository
    private var model: HomeModel? = null
    private val homePressed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Light wallpaper → dark status bar icons
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        registerReceiver(screenReceiver, android.content.IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT)
        }, Context.RECEIVER_NOT_EXPORTED)
        // Started as a normal app (app icon, adb am start -n) although we're the default launcher: forward into the ONE
        // home task — otherwise a second instance runs with its own state and an empty card shows
        // in recent apps.
        if (redirectToHomeTask()) return
        Tunables.init(this)
        com.gamadev.prism.data.DisplayRate.load(this)
        com.gamadev.prism.design.IconAppearance.load(this)
        com.gamadev.prism.design.SettingsAppearance.load(this)

        // Pre-render icons at 1.5× size so larger T-ICON values in the debug panel stay sharp
        val iconPx = (DesignTokens.iconSize.value * resources.displayMetrics.density * 1.5f).roundToInt()
        repository = AppRepository(this, iconPx).also { it.start() }
        com.gamadev.prism.data.Haptics.load(this)
        com.gamadev.prism.data.LaunchMotion.load(this)
        com.gamadev.prism.data.FoldableStyle.load(this)
        com.gamadev.prism.data.OwnStatusBar.load(this)
        com.gamadev.prism.glass.GlassClarity.load(this)
        com.gamadev.prism.data.Quirks.load(this)
        com.gamadev.prism.data.UniversalSearch.WebSuggestions.load(this)
        androidWidgets = com.gamadev.prism.ui.widgets.AndroidWidgetHost(this) // before onStart (startListening)
        android.app.WallpaperManager.getInstance(this).addOnColorsChangedListener(colorsListener, android.os.Handler(mainLooper))
        refreshSystemWallpaperId()

        // Launcher: Back only ends edit mode
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val m = model ?: return
                when {
                    m.wallpaperEditorOpen -> m.wallpaperEditorOpen = false
                    m.customiseOpen -> m.closeCustomise()
                    m.settingsOpen -> m.closeSettings()
                    m.widgetPickerOpen -> { m.widgetPickerOpen = false; m.addingToStack = false }
                    m.searchOpen -> m.closeSearch()
                    m.openCategory != null -> m.closeCategory()
                    m.libListActive -> m.closeLibList()
                    m.openFolder != null -> m.closeFolder()
                    else -> m.exitEdit()
                }
            }
        })
        handleDebugIntent(intent)

        setContent {
            val requestedHz = com.gamadev.prism.data.DisplayRate.selected
            LaunchedEffect(requestedHz) { com.gamadev.prism.data.DisplayRate.apply(this@MainActivity) }
            androidx.compose.runtime.DisposableEffect(Unit) {
                val manager = getSystemService(android.hardware.display.DisplayManager::class.java)
                val listener = object : android.hardware.display.DisplayManager.DisplayListener {
                    override fun onDisplayAdded(id: Int) = Unit
                    override fun onDisplayRemoved(id: Int) = Unit
                    override fun onDisplayChanged(id: Int) {
                        if (display?.displayId == id) com.gamadev.prism.data.DisplayRate.apply(this@MainActivity)
                    }
                }
                manager.registerDisplayListener(listener, android.os.Handler(mainLooper))
                onDispose { manager.unregisterDisplayListener(listener) }
            }
            val appList by repository.appList.collectAsState()
            val apps = appList?.apps
            val scope = rememberCoroutineScope()
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            androidx.compose.runtime.SideEffect { com.gamadev.prism.design.IconAppearance.systemDark = systemDark }
            val widgetHost = remember { androidWidgets!! }
            val homeModel = remember { HomeModel(LayoutStore(this), repository, scope).also { model = it } }
            val pager = remember { PagerState() }
            val view = LocalView.current
            LaunchedEffect(appList) { appList?.let { homeModel.onAppsLoaded(it.apps, it.pausedProfiles) } }
            LaunchedEffect(Unit) {
                homeModel.onAndroidWidgetRemoved = { widgetHost.delete(it) }
            }
            // Compute the style layers (Dark/Clear/Tinted) only when needed
            LaunchedEffect(apps, com.gamadev.prism.design.IconAppearance.style) {
                if (com.gamadev.prism.design.IconAppearance.style != com.gamadev.prism.design.IconStyle.Default) apps?.let { repository.ensureLayers(it) }
            }
            LaunchedEffect(Unit) { snapshotFlow { DebugOptions.demoPages.value }.collect { homeModel.checkDemoPages() } }
            // Home button: end edit mode, otherwise go to the first page (like iOS)
            LaunchedEffect(Unit) {
                homePressed.collect {
                    when {
                        // Launcher settings behave like an app → the home gesture/button closes them (instead of an ×)
                        homeModel.settingsOpen -> homeModel.closeSettings()
                        homeModel.searchOpen || homeModel.searchProgress.value > 0f -> homeModel.closeSearch()
                        homeModel.openFolder != null -> homeModel.closeFolder()
                        homeModel.openCategory != null -> homeModel.closeCategory()
                        homeModel.libListActive -> homeModel.closeLibList()
                        homeModel.editing -> homeModel.exitEdit()
                        else -> pager.animateTo(0)
                    }
                }
            }
            // Edit mode [MEASURED] IMG_3124 and open folder [MEASURED] IMG_3130: status bar gone
            // Own status bar (setting): Android bar permanently gone in the launcher (swiping from the top shows it briefly)
            val ownBar = com.gamadev.prism.data.OwnStatusBar.enabled && !com.gamadev.prism.ui.welcome.WelcomeStore.active
            val hideStatus = ownBar || homeModel.editing || homeModel.wallpaperEditorOpen || homeModel.stackEditKey != null
            // Folder: the status bar fades out/in in place with the folder (instead of sliding up)
            val statusFader = remember { com.gamadev.prism.ui.home.StatusBarFader(window.decorView) }
            LaunchedEffect(homeModel.openFolder) {
                val f = homeModel.openFolder ?: return@LaunchedEffect
                if (hideStatus) return@LaunchedEffect // already gone in edit mode
                fun fade() = 1f - (f.progress.value / FOLDER_STATUS_FADE).coerceIn(0f, 1f)
                statusFader.follow(::fade, done = { f.progress.value >= FOLDER_STATUS_FADE || f.progress.targetValue == 0f }, showAtEnd = { f.progress.targetValue == 0f }) // closed again early → leave visible
                // wait until the folder closes, then fade back in with it
                snapshotFlow { f.progress.targetValue }.first { it == 0f }
                statusFader.follow(::fade, done = { f.progress.value <= 0.02f }, showAtEnd = { true })
            }
            LaunchedEffect(hideStatus) {
                applyStatusBar(hideStatus)
            }
            val welcomeActive = com.gamadev.prism.ui.welcome.WelcomeStore.active
            val settingsOpen = homeModel.settingsOpen
            val settingsDark = com.gamadev.prism.design.SettingsAppearance.current.isDark(systemDark)
            LaunchedEffect(homeModel.searchOpen, DesignTokens.wallpaperIsDark, welcomeActive, settingsOpen, settingsDark) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = when {
                    welcomeActive -> !settingsDark
                    settingsOpen -> !settingsDark
                    else -> !homeModel.searchOpen && !DesignTokens.wallpaperIsDark
                }
                controller.isAppearanceLightNavigationBars = if (settingsOpen || welcomeActive) !settingsDark else true
            }
            // Pages may disappear after editing → bring the pager back into a valid range
            LaunchedEffect(homeModel.pages.size) {
                if (pager.currentPage > homeModel.pages.lastIndex) pager.animateTo(homeModel.pages.lastIndex.coerceAtLeast(0))
            }
            // Duo style on narrower foldables: scale the UI down uniformly to the Duo width (design/UiScale)
            val baseDensity = androidx.compose.ui.platform.LocalDensity.current
            val win = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
            androidx.compose.runtime.SideEffect { com.gamadev.prism.data.FoldableStyle.updateWindow(this@MainActivity, win.width, win.height) }
            androidx.compose.runtime.LaunchedEffect(win.width, win.height) {
                com.gamadev.prism.glass.WallpaperCropModel.rememberDisplay(this@MainActivity)
                // Unfolded/folded: Android sometimes still showed the old crop afterwards until something redrew (e.g. starting
                // edit mode). Only report the position (cheap one-way call, no recomputing of the image) → Android corrects immediately
                applyWallpaperLayout()
            }
            val uiScale = com.gamadev.prism.design.UiScale.compute(
                com.gamadev.prism.data.FoldableStyle.duoStyle && com.gamadev.prism.data.FoldableStyle.supportsDuo(this@MainActivity),
                win.width, win.height, baseDensity.density)
            androidx.compose.runtime.SideEffect { com.gamadev.prism.design.UiScale.update(uiScale) }
            androidx.compose.runtime.CompositionLocalProvider(com.gamadev.prism.ui.widgets.LocalAndroidWidgetHost provides widgetHost,
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(baseDensity.density * uiScale, baseDensity.fontScale)) {
            BoxWithConstraints(Modifier.fillMaxSize().threeFingerLongPress { DebugState.toggle() }) {
                val w = constraints.maxWidth
                val h = constraints.maxHeight
                val colorful = DebugOptions.wallpaperStyle.value >= 0.5f
                var wpSettings by remember { mutableStateOf(com.gamadev.prism.glass.WallpaperStore.load(this@MainActivity)) }
                var wallpaper by remember { mutableStateOf(Wallpaper.placeholder()) }
                // Setting that [wallpaper] belongs to — after "Done" the finished draft image is taken over instead of re-rendering
                var renderedFor by remember { mutableStateOf<com.gamadev.prism.glass.WallpaperSettings?>(null) }
                LaunchedEffect(wpSettings, w, h, colorful) {
                    // also re-render on a new window size (foldable unfolded/folded) — otherwise the image for the glass doesn't match
                    if (w <= 0 || h <= 0 || (renderedFor == wpSettings && !colorful && wallpaper.width == w && wallpaper.height == h)) {
                        return@LaunchedEffect
                    }
                    val settings = wpSettings
                    wallpaper = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        val rendered = if (colorful && settings.kind == com.gamadev.prism.glass.WallpaperKind.Pair)
                            Wallpaper.generateColorful(w, h)
                        else com.gamadev.prism.glass.WallpaperRenderer.render(this@MainActivity, settings, w, h)
                        com.gamadev.prism.ui.home.gpuCacheable(rendered)
                    }
                    renderedFor = settings
                }
                // Also set the wallpaper chosen in the editor as the system wallpaper (Android shows that behind our window)
                LaunchedEffect(renderedFor) {
                    val s = renderedFor ?: return@LaunchedEffect
                    if (!colorful) com.gamadev.prism.glass.WallpaperStore.syncSystemWallpaper(this@MainActivity, s, wallpaper)
                    refreshSystemWallpaperId()
                }
                // Which image does Android show? Ours → draw nothing (window transparent). Another one (chosen in Android's
                // settings) → with read access use its pixels for glass/blur, otherwise draw our own image.
                val systemOurs = remember(systemWallpaperId, renderedFor) { com.gamadev.prism.glass.WallpaperStore.systemIsOurs(this@MainActivity) }
                var systemPixels by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
                // With read access the Android image is always the only source (also for ones chosen in the picker — we set those as the Android image)
                LaunchedEffect(systemWallpaperId, canReadSystemWallpaper, w, h, com.gamadev.prism.glass.GlassAlign.correction) {
                    systemPixels = if (colorful || w <= 0 || h <= 0) null else
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                            com.gamadev.prism.glass.SystemWallpaper.read(
                                this@MainActivity, w, h, com.gamadev.prism.glass.GlassAlign.correction,
                            )?.let { com.gamadev.prism.ui.home.gpuCacheable(it) }
                        }
                }
                // Android draws its wallpaper itself behind our transparent window (so it's also right during the
                // swipe-home gesture). For the glass/blur we need the same pixels: Android's crop + zoom is
                // recomputed (glass/WallpaperCropModel) and applied to our copy.
                val shownOurs by androidx.compose.runtime.produceState(wallpaper, wallpaper, systemOurs, wpSettings.kind) {
                    val source = wallpaper
                    value = if (source.width > 1 && systemOurs && wpSettings.kind != com.gamadev.prism.glass.WallpaperKind.Pair)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                            val shown = com.gamadev.prism.glass.WallpaperStore.asShownBySystem(this@MainActivity, source)
                            com.gamadev.prism.ui.home.gpuCacheable(shown)
                        }
                    else source
                }
                val homeWallpaper = when {
                    colorful -> wallpaper
                    // "Pair" already is Android's displayed crop → don't crop again
                    systemOurs && wpSettings.kind != com.gamadev.prism.glass.WallpaperKind.Pair -> shownOurs
                    else -> systemPixels ?: wallpaper
                }
                // Draw ourselves only if we neither know nor may read Android's image (otherwise the glass wouldn't match)
                val drawOwnWallpaper = wallpaper.width > 1 && (colorful || (!systemOurs && systemPixels == null))
                val onLaunch: (com.gamadev.prism.data.AppEntry, androidx.compose.ui.geometry.Rect) -> Unit = { app, bounds ->
                    val source = android.graphics.Rect(
                        bounds.left.roundToInt(), bounds.top.roundToInt(),
                        bounds.right.roundToInt(), bounds.bottom.roundToInt(),
                    )
                    val options = com.gamadev.prism.data.LaunchMotion.options(view, source)
                    val loc = IntArray(2).also { view.getLocationOnScreen(it) }
                    val screenBounds = android.graphics.Rect(source).apply { offset(loc[0], loc[1]) }
                    com.gamadev.prism.data.AppLockOverlay.allowFromLauncher(app.component.packageName)
                    runCatching { repository.launch(app, screenBounds, options) }
                        .onSuccess { homeModel.noteLaunch(app.key); homeModel.onAppOpen() }
                        .onFailure { android.util.Log.w("Liquidy", "Could not launch ${app.component}", it) }
                }
                // Adapt labels/pill text to the wallpaper brightness (the editor preview uses the draft)
                // Brightness of the ACTUALLY displayed image (Android wallpaper), not of our own launcher image
                LaunchedEffect(homeWallpaper, homeModel.wallpaperEditorOpen) { // also after "Cancel" (the draft had switched)
                    com.gamadev.prism.data.AppLockOverlay.setWallpaperBackdrop(homeWallpaper.asAndroidBitmap())
                    DesignTokens.wallpaperIsDark = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        com.gamadev.prism.glass.WallpaperRenderer.luminance(homeWallpaper) < DesignTokens.WALLPAPER_DARK_LUMA
                    }
                }
                // Wallpaper editor: puts frame + controls around the SAME home screen (no rebuild → no flash)
                val editor = remember(homeModel.wallpaperEditorOpen) {
                    if (homeModel.wallpaperEditorOpen) com.gamadev.prism.ui.wallpaper.WallpaperEditorState(wpSettings, wallpaper) else null
                }
                editor?.let { e ->
                    LaunchedEffect(e.draftBitmap) {
                        DesignTokens.wallpaperIsDark = com.gamadev.prism.glass.WallpaperRenderer.luminance(e.draftBitmap) < DesignTokens.WALLPAPER_DARK_LUMA
                    }
                }
                val aligning = com.gamadev.prism.glass.GlassAlign.open
                com.gamadev.prism.ui.wallpaper.WallpaperEditorFrame(editor) {
                    // while aligning the glass only the real wallpaper and the alignment pane are visible
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (aligning) 0f else 1f }) {
                    HomeScreen(model = homeModel, pager = pager, wallpaperSource = editor?.draftBitmap ?: homeWallpaper, onLaunch = onLaunch,
                        drawWallpaper = editor != null || drawOwnWallpaper, wallpaperBlur = { editor?.blurAnim?.value ?: 0f })
                    }
                }
                editor?.let { e ->
                    com.gamadev.prism.ui.wallpaper.WallpaperEditorChrome(
                        state = e,
                        onDone = { chosen, bitmap ->
                            val s = chosen.copy(userChosen = true)
                            // Take over the image immediately (no flash of the old one), copy the photo permanently into app storage
                            wallpaper = bitmap
                            renderedFor = s
                            homeModel.wallpaperEditorOpen = false
                            scope.launch {
                                val kept = com.gamadev.prism.glass.WallpaperStore.keepPhoto(this@MainActivity, s)
                                renderedFor = kept
                                wpSettings = kept
                                com.gamadev.prism.glass.WallpaperStore.save(this@MainActivity, kept)
                            }
                        },
                        onCancel = { homeModel.wallpaperEditorOpen = false },
                    )
                }
                // Welcome on first start (above everything)
                // The preview icon (Customise sheet / welcome) needs the style layers before the first style choice
                val styleSample = homeModel.display.dock.firstOrNull()?.let { homeModel.apps[it] } ?: homeModel.apps.values.firstOrNull()
                LaunchedEffect(styleSample) { styleSample?.let { repository.ensureLayers(listOf(it)) } }
                var showWelcome by remember { mutableStateOf(!com.gamadev.prism.ui.welcome.WelcomeStore.done(this@MainActivity)) }
                LaunchedEffect(com.gamadev.prism.ui.welcome.WelcomeStore.restartRequested) {
                    if (com.gamadev.prism.ui.welcome.WelcomeStore.restartRequested) {
                        com.gamadev.prism.ui.welcome.WelcomeStore.restartRequested = false; showWelcome = true
                    }
                }
                // Finish: setup fades out and zooms in slightly (no hard cut to the home screen)
                androidx.compose.animation.AnimatedVisibility(
                    visible = showWelcome,
                    enter = androidx.compose.animation.EnterTransition.None,
                    exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(WELCOME_EXIT_MS)) +
                        androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(WELCOME_EXIT_MS), targetScale = 1.08f),
                ) { Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (aligning) 0f else 1f }
                    // the setup covers the home screen completely: touches must never reach the icons behind it
                    .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }
                ) { com.gamadev.prism.ui.welcome.WelcomeFlow(
                    isDefaultHome = {
                        val h = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        packageManager.resolveActivity(h, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == packageName
                    },
                    sample = styleSample,
                    wallpaper = homeWallpaper,
                    onFinish = { com.gamadev.prism.ui.welcome.WelcomeStore.markDone(this@MainActivity); showWelcome = false },
                ) } }
                // Manual glass alignment (setup / Settings › Appearance): uncorrected copy as the preview's base
                if (aligning) {
                    val alignBase by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, systemWallpaperId, w, h) {
                        value = com.gamadev.prism.glass.SystemWallpaper.read(this@MainActivity, w, h)
                    }
                    alignBase?.let { base ->
                        com.gamadev.prism.glass.GlassAlignScreen(base, com.gamadev.prism.glass.GlassAlign.correction,
                            onReset = {},
                            onDone = { corr ->
                                com.gamadev.prism.glass.GlassAlign.save(this@MainActivity, systemWallpaperId, corr)
                                com.gamadev.prism.glass.GlassAlign.open = false
                            })
                    }
                    androidx.activity.compose.BackHandler { com.gamadev.prism.glass.GlassAlign.open = false }
                }
                if (DebugState.visible) DebugPanel()
            }
            } // end LocalAndroidWidgetHost
        }
    }

    private fun redirectToHomeTask(): Boolean {
        if (intent?.hasCategory(Intent.CATEGORY_HOME) == true) return false
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val defaultHome = packageManager.resolveActivity(home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        if (defaultHome != packageName) return false
        intent?.extras?.let { home.putExtras(it) } // e.g. --ez debug true
        startActivity(home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
        return true
    }

    /** Identifier of the current Android wallpaper (changes when it's set anywhere). */
    private var systemWallpaperId by androidx.compose.runtime.mutableIntStateOf(-1)
    private val colorsListener = android.app.WallpaperManager.OnColorsChangedListener { _, _ -> refreshSystemWallpaperId() }

    /** Read access to the Android wallpaper (check again after returning from settings). */
    private var canReadSystemWallpaper by mutableStateOf(false)

    private fun refreshSystemWallpaperId() {
        com.gamadev.prism.glass.SystemWallpaper.refreshLive(this)
        canReadSystemWallpaper = com.gamadev.prism.glass.SystemWallpaper.canRead()
        systemWallpaperId = android.app.WallpaperManager.getInstance(this).getWallpaperId(android.app.WallpaperManager.FLAG_SYSTEM)
        com.gamadev.prism.glass.GlassAlign.load(this, systemWallpaperId)
    }

    /** Show the system wallpaper still and screen-filling (no parallax when swiping pages), like our own image. */
    private var wallpaperDimsSet = false

    private fun applyWallpaperLayout() {
        val wm = android.app.WallpaperManager.getInstance(this)
        // Size hint only once and in the background: synchronous binder call, can make the wallpaper service re-render
        // and blocked the main thread on focus change ("Home isn't responding")
        if (!wallpaperDimsSet) {
            wallpaperDimsSet = true
            val m = resources.displayMetrics
            Thread { runCatching { wm.suggestDesiredDimensions(m.widthPixels, m.heightPixels) } }.start()
        }
        runCatching {
            wm.setWallpaperOffsetSteps(0f, 0f)
            window.decorView.windowToken?.let { wm.setWallpaperOffsets(it, 0.5f, 0.5f) }
        }
    }

    private val resumeWork = Runnable {
        if (!homeOnTop) return@Runnable
        if (::repository.isInitialized) repository.refreshIfNeeded()
        refreshSystemWallpaperId()
        model?.refreshSuggestions()
        model?.let { com.gamadev.prism.data.AppLockOverlay.sync(this, it.hiddenApps) }
    }

    override fun onResume() {
        super.onResume()
        com.gamadev.prism.data.DisplayRate.apply(this)
        homeOnTop = true
        resumedAt = android.os.SystemClock.uptimeMillis()
        log("resume armed=$unlockArmed locked=${keyguard.isKeyguardLocked}")
        scheduleUnlockReveal()
        if (leftHome && !unlockArmed && !keyguard.isKeyguardLocked) model?.onAppReturn()
        leftHome = false
        // Defer non-visual resume work until after the return-home / unlock spring settles so it doesn't stall frame 1.
        window.decorView.removeCallbacks(resumeWork)
        window.decorView.postDelayed(resumeWork, 450L)
    }

    private var leftHome = false
    private var statusHidden = false
    private var androidWidgets: com.gamadev.prism.ui.widgets.AndroidWidgetHost? = null

    override fun onStart() { super.onStart(); runCatching { androidWidgets?.host?.startListening() } }
    override fun onStop() {
        super.onStop()
        if (!keyguard.isKeyguardLocked) leftHome = true
        model?.lockHiddenApps()
        model?.let { com.gamadev.prism.data.AppLockOverlay.sync(this, it.hiddenApps) }
        runCatching { androidWidgets?.host?.stopListening() }
    }

    @Deprecated("AppWidget binding/configuration runs via startActivityForResult (AppWidgetHost API)")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        androidWidgets?.onActivityResult(this, requestCode, resultCode)
    }

    /**
     * Hide the status bar in edit mode/folder/editor. Behaviour "transient": if it's shown by swiping from the top,
     * it disappears again by itself (the default would be: it stays and collides with the edit buttons).
     */
    private fun applyStatusBar(hide: Boolean) {
        statusHidden = hide
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hide) c.hide(WindowInsetsCompat.Type.statusBars()) else c.show(WindowInsetsCompat.Type.statusBars())
    }

    /** Arm when Home is covered by the lock screen and reveal on a system lifecycle or unlock signal. */
    private var unlockArmed = false
    /** The home screen is the top activity (stays true while only the screen/keyguard paused us). */
    private var homeOnTop = true
    private var resumedAt = 0L
    private val keyguard by lazy { getSystemService(android.app.KeyguardManager::class.java) }
    private val power by lazy { getSystemService(android.os.PowerManager::class.java) }
    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    com.gamadev.prism.data.AppLockOverlay.lockAll()
                    if (homeOnTop && !unlockArmed) { log("screen off → armed"); unlockArmed = true; model?.prepareUnlock() }
                }
                Intent.ACTION_USER_PRESENT -> {
                    log("user present resumed=${isResumed()} armed=$unlockArmed")
                    // unlocked, but not to us (an app was on top) → reset, so returning home later doesn't play it
                    if (unlockArmed && !isResumed()) { unlockArmed = false; model?.cancelUnlock() } else scheduleUnlockReveal()
                }
            }
        }
    }
    private fun isResumed() = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
    private val revealUnlock = Runnable { playUnlockIfReady() }

    private fun scheduleUnlockReveal() {
        if (!unlockArmed || !isResumed()) return
        val decor = window.decorView
        decor.removeCallbacks(revealUnlock)
        val configuredDelay = (com.gamadev.prism.motion.Motion.unlockRevealDelay.value * 1000).toLong()
        val remaining = configuredDelay - (android.os.SystemClock.uptimeMillis() - resumedAt)
        decor.postOnAnimationDelayed(revealUnlock, remaining.coerceAtLeast(0L))
    }

    private fun playUnlockIfReady() {
        if (!unlockArmed || !isResumed() || keyguard.isKeyguardLocked || !power.isInteractive) return
        if (display?.state != android.view.Display.STATE_ON) return
        unlockArmed = false
        model?.playUnlock()
    }
    private fun log(msg: String) = android.util.Log.d("Unlock", msg)

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) { log("focus"); scheduleUnlockReveal() }
        // Focus back = the system's return animation is done (it now resets the wallpaper zoom) → our transition
        if (hasFocus) { applyStatusBar(statusHidden); applyWallpaperLayout(); model?.smartRotateStacks() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home button/gesture while the launcher is in front → go to the first page (like iOS)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            // Already on the home screen (focused, not brought to the front from outside) → home button: first page.
            if (hasWindowFocus() && (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) == 0) {
                homePressed.tryEmit(Unit)
            }
        }
        handleDebugIntent(intent)
    }

    /** adb shell am start -n com.gamadev.prism/.MainActivity --ez debug true */
    private fun handleDebugIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("debug", false) == true) DebugState.visible = true
        if (intent?.getBooleanExtra("organize_folders", false) == true) {
            intent.removeExtra("organize_folders")
            model?.organizeByCategoryFolders()
        }
        // adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME --ez welcome true
        // Test (debug build only): adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME --es testwp /sdcard/x.png [--es crop l,t,r,b]
        if (intent?.getBooleanExtra("teststack", false) == true && (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            intent.removeExtra("teststack"); model?.debugMakeStack()
        }
        // Test (debug build only): --es testwidget ios:reminders,4,2  → put a widget on page 1
        intent?.getStringExtra("testwidget")?.takeIf { (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0 }?.let { w ->
            intent.removeExtra("testwidget"); val p = w.split(',')
            if (p[0].startsWith("android:")) { // --es testwidget android:pkg/cls,4,2  (before: cmd appwidget grantbind --package …)
                val cn = android.content.ComponentName.unflattenFromString(p[0].removePrefix("android:"))
                androidWidgets?.providers()?.firstOrNull { it.provider == cn }?.let { info ->
                    androidWidgets?.add(this, info, p[1].toInt() to p[2].toInt()) { spec -> if (spec != null) model?.addWidget(spec, 0) }
                }
            } else model?.addWidget(com.gamadev.prism.data.WidgetSpec(p[0], p[1].toInt(), p[2].toInt()), 0)
        }
        if (intent?.getBooleanExtra("testdemo", false) == true && (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            intent.removeExtra("testdemo"); model?.debugDemoLayout()
        }
        val testWp = intent?.getStringExtra("testwp")
        if (testWp != null && (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            val crop = intent.getStringExtra("crop")?.split(',')?.map { it.trim().toInt() }?.let { android.graphics.Rect(it[0], it[1], it[2], it[3]) }
            intent.removeExtra("testwp"); intent.removeExtra("crop")
            com.gamadev.prism.glass.WallpaperStore.testSuppressSync = testWp != "clear"
            Thread {
                runCatching {
                    if (testWp == "clear") { android.app.WallpaperManager.getInstance(this).clear(android.app.WallpaperManager.FLAG_SYSTEM); return@runCatching }
                    val bmp = android.graphics.BitmapFactory.decodeFile(testWp)
                    val id = android.app.WallpaperManager.getInstance(this).setBitmap(bmp, crop, true, android.app.WallpaperManager.FLAG_SYSTEM)
                    android.util.Log.i("IosHome", "Test wallpaper set id=$id ${bmp.width}x${bmp.height} crop=$crop")
                }.onFailure { android.util.Log.w("IosHome", "Test wallpaper: $it") }
            }.start()
        }
        if (intent?.getBooleanExtra("welcome", false) == true) {
            getSharedPreferences("welcome", MODE_PRIVATE).edit().putBoolean("done", false).commit()
            intent.removeExtra("welcome"); recreate()
        }
    }

    /** Emulator test keys — only in debug builds and only with Ctrl, otherwise they trigger while typing in search
     *  (a "W" reset the welcome). Ctrl+D debug panel, Ctrl+W welcome, Ctrl+L test wallpaper. */
    @android.annotation.SuppressLint("RestrictedApi") // Delegate ordinary key events to ComponentActivity.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0 || !event.isCtrlPressed) return super.dispatchKeyEvent(event)
        // Emulator: key W shows the welcome again (test)
        if (event.keyCode == KeyEvent.KEYCODE_W && event.action == KeyEvent.ACTION_UP) {
            getSharedPreferences("welcome", MODE_PRIVATE).edit().putBoolean("done", false).commit(); recreate(); return true
        }
        // Emulator: key L sets a light test wallpaper from /sdcard/Pictures/lightwp.png (comparison with Duo recordings)
        if (event.keyCode == KeyEvent.KEYCODE_L && event.action == KeyEvent.ACTION_UP) {
            Thread {
                runCatching {
                    val bmp = android.graphics.BitmapFactory.decodeFile("/sdcard/Pictures/lightwp.png")
                    android.app.WallpaperManager.getInstance(this).setBitmap(bmp, null, true, android.app.WallpaperManager.FLAG_SYSTEM)
                }.onFailure { android.util.Log.w("IosHome", "Test wallpaper: $it") }
            }.start(); return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_D && event.action == KeyEvent.ACTION_UP) {
            DebugState.toggle(); return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        super.onPause()
        // paused by another app (not by the screen going off or the keyguard)
        if (power.isInteractive && !keyguard.isKeyguardLocked) homeOnTop = false
        if (::repository.isInitialized) Tunables.save(this) // not after forwarding (nothing initialised)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        window.decorView.removeCallbacks(revealUnlock)
        if (::repository.isInitialized) {
            repository.stop()
            android.app.WallpaperManager.getInstance(this).removeOnColorsChangedListener(colorsListener)
        }
        super.onDestroy()
    }
}

private const val WELCOME_EXIT_MS = 450
