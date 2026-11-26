package com.gamadev.prism.data

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap

import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import java.text.Collator
import java.time.LocalDate
import java.security.MessageDigest

/** App shortcut from Android (LauncherApps) — in the iOS menu as a monochrome glyph + text. */
class AppShortcut(val label: String, val glyph: ImageBitmap?, val info: android.content.pm.ShortcutInfo)

data class AppEntry(
    val key: String,
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val icon: ImageBitmap,
    /** ApplicationInfo.category (for folder names + App Library). */
    val category: Int = android.content.pm.ApplicationInfo.CATEGORY_UNDEFINED,
    val installTime: Long = 0L,
    internal val iconRevision: AppIconRevision? = null,
)

internal data class AppListSnapshot(val apps: List<AppEntry>, val pausedProfiles: Set<Long>)

data class AppIconRevision(
    val updatedAt: Long?,
    val packName: String?,
    val packUpdatedAt: Long?,
    val calendarDay: Int?,
)

/**
 * Layers for Dark/Clear/Tinted — computed only when needed (≈ 2 bitmaps per app) and stored as Compose state,
 * so the icons redraw as soon as their layers are done.
 */
object IconLayerCache {
    val map = androidx.compose.runtime.mutableStateMapOf<String, IconLayers>()
}

/**
 * App list via LauncherApps (like Launcher3), incl. a callback on install/uninstall.
 * The layout (pages/dock/folders) is managed by HomeModel + LayoutStore.
 */
class AppRepository(val context: Context, private val iconSizePx: Int) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var reloadJob: kotlinx.coroutines.Job? = null
    private var generation = 0
    private var loadedDate: LocalDate? = null
    private val layerMutex = kotlinx.coroutines.sync.Mutex()
    private val temporarilyUnavailable = mutableSetOf<Pair<UserHandle, String>>()
    private val _apps = MutableStateFlow<AppListSnapshot?>(null)
    private var publishedByKey = emptyMap<String, AppEntry>()
    /** null = not loaded yet. Order: alphabetical. */
    internal val appList: StateFlow<AppListSnapshot?> = _apps

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) {
            if (user != null && packageName != null) temporarilyUnavailable.remove(user to packageName)
            reload(packageName)
        }
        override fun onPackageAdded(packageName: String?, user: UserHandle?) {
            if (user != null && packageName != null) temporarilyUnavailable.remove(user to packageName)
            reload(packageName)
        }
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = reload(packageName)
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) {
            if (user != null) packageNames?.forEach { temporarilyUnavailable.remove(user to it) }
            reload()
        }
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) {
            if (user != null) packageNames?.forEach { temporarilyUnavailable.add(user to it) }
            reload()
        }
    }

    private val dateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (loadedDate != LocalDate.now()) reload()
        }
    }

    fun start() {
        IconPacks.load(context)
        IconPacks.onChanged = { reload() }
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        context.registerReceiver(dateReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }, Context.RECEIVER_NOT_EXPORTED)
        reload()
    }

    fun stop() {
        launcherApps.unregisterCallback(callback)
        context.unregisterReceiver(dateReceiver)
        IconPacks.onChanged = null
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    fun refreshIfNeeded() {
        if (loadedDate != LocalDate.now() && reloadJob?.isActive != true) reload()
    }

    private fun reload(changedPackage: String? = null, retryOnFailure: Boolean = true) {
        val request = ++generation
        reloadJob?.cancel()
        val previous = publishedByKey
        val unavailable = temporarilyUnavailable.toSet()
        val packName = IconPacks.selected
        val date = LocalDate.now()
        reloadJob = scope.launch {
            try {
                val pack = IconPacks.revision(context, packName)
                val processUser = android.os.Process.myUserHandle()
                val profiles = userManager.userProfiles
                check(processUser in profiles) { "Current profile is unavailable" }
                val pausedProfiles = mutableSetOf<Long>()
                val discovered = profiles.flatMap { user ->
                    val serial = userManager.getSerialNumberForUser(user)
                    check(serial >= 0) { "Profile is unavailable" }
                    if (userManager.isQuietModeEnabled(user)) {
                        pausedProfiles += serial
                        return@flatMap emptyList()
                    }
                    // A query failure is not a successful empty profile. Leave the last layout untouched.
                    val activities = launcherApps.getActivityList(null, user)
                    // A usable device exposes system apps to its own profile.
                    check(user != processUser || activities.any { it.componentName.packageName != context.packageName }) {
                        "Current profile returned no launchable apps"
                    }
                    val visible = activities.filter { it.componentName.packageName != context.packageName }.map { info ->
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val component = info.componentName
                        val key = component.flattenToShortString() + "#" + serial
                        val pkg = runCatching { context.packageManager.getPackageInfo(component.packageName, 0) }.getOrNull()
                        val calendar = pack?.let { IconPacks.hasCalendarIcon(context, it, component) } == true
                        val revision = AppIconRevision(pkg?.lastUpdateTime, pack?.packageName, pack?.updatedAt, date.dayOfMonth.takeIf { calendar })
                        val old = previous[key]?.takeIf {
                            component.packageName != changedPackage && it.iconRevision == revision
                        }
                        val bitmap = old?.icon ?: info.getIcon(0).let { original ->
                            val themed = pack?.let { IconPacks.icon(context, it, component, original, iconSizePx, date.dayOfMonth) }
                            IconRenderer.render(themed ?: original, iconSizePx, fullBleed = themed != null)
                        }
                        AppEntry(key, info.label.toString(), component, user, bitmap, info.applicationInfo.category,
                            pkg?.firstInstallTime ?: 0L, revision)
                    }
                    val keys = visible.mapTo(HashSet()) { it.key }
                    visible + previous.values.filter {
                        it.user == user && (user to it.component.packageName) in unavailable && it.key !in keys
                    }
                }
                val collator = Collator.getInstance()
                val sorted = discovered.sortedWith { a, b -> collator.compare(a.label, b.label) }
                withContext(Dispatchers.Main) {
                    if (request != generation) return@withContext
                    val unchanged = sorted.filter { previous[it.key]?.icon === it.icon }.mapTo(HashSet()) { it.key }
                    IconLayerCache.map.keys.retainAll(unchanged)
                    loadedDate = date
                    publishedByKey = sorted.associateBy { it.key }
                    _apps.value = AppListSnapshot(sorted, pausedProfiles)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                android.util.Log.w("LiquidyApps", "App scan failed; kept the last complete layout", failure)
                if (retryOnFailure) {
                    delay(APP_SCAN_RETRY_MS)
                    withContext(Dispatchers.Main) {
                        if (request == generation) reload(changedPackage, retryOnFailure = false)
                    }
                }
            }
        }
    }

    /** Package name heuristic for an iOS-like default dock: phone, messages, browser, music/camera. */
    fun defaultDock(apps: List<AppEntry>): List<AppEntry> {
        val dock = mutableListOf<AppEntry>()
        listOf("dialer", "messaging", "chrome", "music", "camera").forEach { hint ->
            if (dock.size < 4) apps.firstOrNull { it.component.packageName.contains(hint) && it !in dock }?.let { dock += it }
        }
        return dock
    }

    /**
     * Folder name like on iOS from the app category (iOS: App Store category, Android: ApplicationInfo.category).
     * Both apps of the same category → its name, otherwise that of the target icon; without a category "Folder".
     */
    fun folderName(target: AppEntry?, dragged: AppEntry?): String {
        val undefined = android.content.pm.ApplicationInfo.CATEGORY_UNDEFINED
        val c = target?.category?.takeIf { it != undefined } ?: dragged?.category ?: undefined
        return categoryName(c)
    }

    fun categoryName(c: Int): String {
        val res = when (c) {
            android.content.pm.ApplicationInfo.CATEGORY_GAME -> com.gamadev.prism.R.string.folder_games
            android.content.pm.ApplicationInfo.CATEGORY_AUDIO -> com.gamadev.prism.R.string.folder_music
            android.content.pm.ApplicationInfo.CATEGORY_VIDEO -> com.gamadev.prism.R.string.folder_entertainment
            android.content.pm.ApplicationInfo.CATEGORY_IMAGE -> com.gamadev.prism.R.string.folder_photo
            android.content.pm.ApplicationInfo.CATEGORY_SOCIAL -> com.gamadev.prism.R.string.folder_social
            android.content.pm.ApplicationInfo.CATEGORY_NEWS -> com.gamadev.prism.R.string.folder_news
            android.content.pm.ApplicationInfo.CATEGORY_MAPS -> com.gamadev.prism.R.string.folder_navigation
            android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY -> com.gamadev.prism.R.string.folder_productivity
            android.content.pm.ApplicationInfo.CATEGORY_ACCESSIBILITY -> com.gamadev.prism.R.string.folder_utilities
            else -> com.gamadev.prism.R.string.folder_default
        }
        return context.getString(res)
    }

    fun uninstall(app: AppEntry) {
        val intent = android.content.Intent(android.content.Intent.ACTION_DELETE, android.net.Uri.fromParts("package", app.component.packageName, null))
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun isSystemApp(app: AppEntry): Boolean = runCatching {
        val ai = context.packageManager.getApplicationInfo(app.component.packageName, 0)
        (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
    }.getOrDefault(true)

    /**
     * Computes missing style layers in the background (at display size, not at the 1.5× icon resolution)
     * and stores them piece by piece in the cache. Called only when a style other than Default is active.
     */
    suspend fun ensureLayers(apps: Collection<AppEntry>) = layerMutex.withLock {
        kotlinx.coroutines.withContext(Dispatchers.Default) {
            val px = (iconSizePx / ICON_OVERSAMPLE).toInt()
            for (app in apps) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (withContext(Dispatchers.Main) { IconLayerCache.map.containsKey(app.key) }) continue
                val revision = app.iconRevision ?: continue
                val pack = revision.packName?.let { name -> revision.packUpdatedAt?.let { IconPacks.Revision(name, it) } }
                val cacheKey = "${app.key}|$revision|$px|$LAYER_CACHE_VERSION"
                val name = MessageDigest.getInstance("SHA-256").digest(cacheKey.toByteArray())
                    .joinToString("") { "%02x".format(it) }
                val dir = java.io.File(context.cacheDir, "iconlayers").apply { mkdirs() }
                val gf = java.io.File(dir, "${name}_g.png"); val mf = java.io.File(dir, "${name}_m.png")
                val cached = if (gf.exists() && mf.exists()) runCatching {
                    val glyph = android.graphics.BitmapFactory.decodeFile(gf.path).frozen().asImageBitmap()
                    val mask = android.graphics.BitmapFactory.decodeFile(mf.path)
                    // A 1×1 transparent marker means no usable mask was found.
                    IconLayers(glyph, mask.takeUnless { it.width == 1 && it.height == 1 }?.frozen()?.asImageBitmap())
                }.getOrNull() else null
                val layers = cached ?: runCatching {
                    val info = launcherApps.resolveActivity(Intent().setComponent(app.component), app.user)
                        ?: return@runCatching null
                    val orig = info.getIcon(0)
                    val themed = pack?.let { IconPacks.icon(context, it, info.componentName, orig, px,
                        revision.calendarDay ?: LocalDate.now().dayOfMonth) }
                    IconRenderer.layers(themed ?: orig, px, fullBleed = themed != null).also { l ->
                        runCatching {
                            gf.outputStream().use { l.glyph.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                            val mask = l.mono?.asAndroidBitmap()
                                ?: android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
                            mf.outputStream().use { mask.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                        }
                    }
                }.getOrNull() ?: continue
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    if (publishedByKey[app.key]?.icon === app.icon) IconLayerCache.map[app.key] = layers
                }
            }
        }
    }

    /**
     * The native app shortcuts (manifest + dynamic), as Launcher3 shows them on long press: by rank, max. [MAX_SHORTCUTS].
     * Only available if we're the default launcher (hasShortcutHostPermission).
     */
    fun shortcuts(app: AppEntry, glyphPx: Int): List<AppShortcut> {
        if (!runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)) return emptyList()
        val q = LauncherApps.ShortcutQuery()
            .setPackage(app.component.packageName)
            .setActivity(app.component)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC)
        val list = runCatching { launcherApps.getShortcuts(q, app.user) }.getOrNull().orEmpty()
            .filter { it.isEnabled }
            .sortedWith(compareBy({ if (it.isDeclaredInManifest) 0 else 1 }, { it.rank }))
            .take(MAX_SHORTCUTS)
        return list.map { s ->
            val label = (s.shortLabel ?: s.longLabel ?: "").toString()
            val glyph = runCatching { launcherApps.getShortcutIconDrawable(s, context.resources.displayMetrics.densityDpi) }
                .getOrNull()?.let { IconRenderer.glyph(it, glyphPx) }
            AppShortcut(label, glyph, s)
        }
    }

    fun startShortcut(s: AppShortcut, sourceBounds: Rect) {
        runCatching { launcherApps.startShortcut(s.info, sourceBounds, null) }
    }

    fun showAppInfo(app: AppEntry, sourceBounds: Rect) {
        launcherApps.startAppDetailsActivity(app.component, app.user, sourceBounds, null)
    }

    fun launch(app: AppEntry, sourceBounds: Rect, options: Bundle?) {
        launcherApps.startMainActivity(app.component, app.user, sourceBounds, options)
    }

    companion object {
        const val MAX_SHORTCUTS = 4
        /** MainActivity renders icons at 1.5× size (for enlarging); style layers are fine at display size. */
        const val ICON_OVERSAMPLE = 1.5f
    } // Launcher3: at most 4 shortcuts in the popup
}

/** Bump when the glyph computation changes, so old cached layers are recomputed. */
private const val LAYER_CACHE_VERSION = 6
private const val APP_SCAN_RETRY_MS = 650L
