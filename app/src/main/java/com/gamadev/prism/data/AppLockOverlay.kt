package com.gamadev.prism.data

import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.gamadev.prism.R

/**
 * Locks hidden apps even when opened outside the launcher (Recents, notifications, Settings)
 * using UsageStatsManager + a blurred TYPE_APPLICATION_OVERLAY window + BiometricPrompt.
 */
object AppLockOverlay {
    private val main = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var wallpaperBackdrop: android.graphics.Bitmap? = null
    private var lockedPackages: Set<String> = emptySet()
    private var unlockedPackage: String? = null
    private var lastForeground: String? = null
    private var overlayView: View? = null
    private var lockGlyphView: LockGlyphView? = null
    private var lockedShownFor: String? = null
    private var authenticatingFor: String? = null
    private var activeCancel: CancellationSignal? = null
    private var polling = false

    private val poll = object : Runnable {
        override fun run() {
            val ctx = appContext
            if (ctx == null || lockedPackages.isEmpty() || !canOverlay(ctx)) {
                polling = false
                dismissOverlay()
                return
            }
            checkForeground(ctx)
            main.postDelayed(this, POLL_MS)
        }
    }

    fun canOverlay(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun sync(context: Context, hiddenAppKeys: Set<String>) {
        appContext = context.applicationContext
        val pkgs = hiddenAppKeys.mapNotNull { key ->
            key.substringBefore('/').takeIf { it.isNotBlank() && it != context.packageName }
        }.toSet()
        lockedPackages = pkgs
        if (pkgs.isEmpty()) {
            unlockedPackage = null
            dismissOverlay()
            main.removeCallbacks(poll)
            polling = false
        } else if (!polling && canOverlay(context)) {
            polling = true
            main.post(poll)
        }
    }

    fun setWallpaperBackdrop(bmp: android.graphics.Bitmap?) {
        wallpaperBackdrop = bmp
    }

    /** Authorize a hidden app when launched directly from Prism's authenticated Hidden folder. */
    fun allowFromLauncher(pkg: String) {
        val wasAuth = authenticatingFor == pkg
        authenticatingFor = null
        unlockedPackage = pkg
        if (lockedShownFor == pkg) {
            if (wasAuth) animateUnlockAndDismiss() else dismissOverlay()
        }
    }

    private fun animateUnlockAndDismiss() {
        val root = overlayView ?: return
        val glyph = lockGlyphView
        root.visibility = View.VISIBLE
        if (glyph == null) {
            dismissOverlay()
            return
        }
        val anim = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 280L
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener { va ->
                val p = va.animatedValue as Float
                glyph.unlockProgress = p
                glyph.invalidate()
                if (p > 0.55f) {
                    root.alpha = (1f - (p - 0.55f) / 0.45f).coerceIn(0f, 1f)
                }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    dismissOverlay()
                }
            })
        }
        anim.start()
    }

    fun hideForAuth() {
        overlayView?.visibility = View.GONE
    }

    fun onAuthCancel() {
        authenticatingFor = null
        overlayView?.visibility = View.VISIBLE
    }

    fun lockAll() {
        unlockedPackage = null
    }

    private fun checkForeground(context: Context) {
        if (authenticatingFor != null) return
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return
        val now = System.currentTimeMillis()
        val events = runCatching { usm.queryEvents(now - LOOKBACK_MS, now) }.getOrNull() ?: return
        val ev = UsageEvents.Event()
        var top: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            if (ev.eventType == UsageEvents.Event.ACTIVITY_RESUMED && ev.packageName != "com.android.systemui") {
                top = ev.packageName
            }
        }
        val fg = top ?: lastForeground ?: return
        lastForeground = fg
        if (fg != unlockedPackage && fg != context.packageName) {
            if (unlockedPackage != null && fg !in lockedPackages) unlockedPackage = null
        }
        if (fg in lockedPackages && fg != unlockedPackage) {
            if (lockedShownFor != fg) showOverlay(context, fg)
        } else if (lockedShownFor != null && fg != lockedShownFor) {
            dismissOverlay()
        }
    }

    private fun showOverlay(context: Context, pkg: String) {
        dismissOverlay()
        if (!canOverlay(context)) return
        val wm = context.getSystemService(WindowManager::class.java) ?: return
        val dp = context.resources.displayMetrics.density
        val label = runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)

        val isDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val bgColor = if (isDark) Color.BLACK else Color.rgb(242, 242, 247)
        val cardColor = if (isDark) Color.rgb(28, 28, 30) else Color.WHITE
        val cardStroke = if (isDark) Color.argb(40, 255, 255, 255) else Color.argb(24, 0, 0, 0)
        val primaryText = if (isDark) Color.WHITE else Color.rgb(28, 28, 30)
        val secondaryText = if (isDark) Color.argb(180, 255, 255, 255) else Color.argb(155, 0, 0, 0)
        val btnBg = if (isDark) Color.argb(45, 255, 255, 255) else Color.argb(22, 0, 0, 0)

        val root = android.widget.FrameLayout(context).apply {
            setBackgroundColor(bgColor)
        }

        // Centered lock card matching system dark/light theme
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply {
                setColor(cardColor)
                cornerRadius = 32f * dp
                setStroke((1f * dp).toInt().coerceAtLeast(1), cardStroke)
            }
            setPadding((28 * dp).toInt(), (30 * dp).toInt(), (28 * dp).toInt(), (24 * dp).toInt())
            layoutParams = android.widget.FrameLayout.LayoutParams(
                (300 * dp).toInt(),
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            )
        }

        // Crisp iOS-style animated vector padlock glyph
        val lockIcon = LockGlyphView(context, primaryText, dp).apply {
            layoutParams = LinearLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt())
        }
        lockGlyphView = lockIcon
        val title = TextView(context).apply {
            text = label
            textSize = 22f
            setTextColor(primaryText)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, (12 * dp).toInt(), 0, (6 * dp).toInt())
        }
        val subtitle = TextView(context).apply {
            text = context.getString(R.string.app_lock_subtitle)
            textSize = 14f
            setTextColor(secondaryText)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (22 * dp).toInt())
        }

        fun pillButton(text: String, bg: Int, onClick: () -> Unit): TextView = TextView(context).apply {
            this.text = text
            textSize = 15f
            setTextColor(primaryText)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(bg)
                cornerRadius = 999f
            }
            setPadding((24 * dp).toInt(), (12 * dp).toInt(), (24 * dp).toInt(), (12 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = (10 * dp).toInt() }
            setOnClickListener { onClick() }
        }

        val unlockBtn = pillButton(context.getString(R.string.app_lock_unlock), btnBg) {
            launchBiometricUnlock(context, pkg)
        }
        val homeBtn = pillButton(context.getString(R.string.app_lock_home), Color.TRANSPARENT) {
            goHome(context)
        }

        card.addView(lockIcon)
        card.addView(title)
        card.addView(subtitle)
        card.addView(unlockBtn)
        card.addView(homeBtn)
        root.addView(card)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE,
        )

        runCatching {
            wm.addView(root, params)
            overlayView = root
            lockedShownFor = pkg
        }
    }

    private fun authenticate(context: Context, pkg: String) {
        activeCancel?.cancel()
        val cancel = CancellationSignal()
        activeCancel = cancel
        val auth = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val bm = context.getSystemService(BiometricManager::class.java)
        val km = context.getSystemService(KeyguardManager::class.java)
        if (km?.isDeviceSecure != true || bm?.canAuthenticate(auth) != BiometricManager.BIOMETRIC_SUCCESS) {
            unlockedPackage = pkg
            dismissOverlay()
            return
        }
        val prompt = BiometricPrompt.Builder(context)
            .setTitle(context.getString(R.string.app_lock_title))
            .setSubtitle(context.getString(R.string.app_lock_subtitle))
            .setAllowedAuthenticators(auth)
            .build()
        prompt.authenticate(cancel, ContextCompat.getMainExecutor(context), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                unlockedPackage = pkg
                dismissOverlay()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // Keep overlay visible so user can tap Unlock again or Go Home
            }
        })
    }

    private fun launchBiometricUnlock(context: Context, pkg: String) {
        authenticatingFor = pkg
        runCatching {
            context.startActivity(
                Intent(context, AppLockAuthActivity::class.java)
                    .putExtra(AppLockAuthActivity.EXTRA_PKG, pkg)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
        }.onFailure {
            onAuthCancel()
        }
    }

    private fun goHome(context: Context) {
        // Keep the overlay covering the locked app until Home has completely taken over (zero flash of the locked app).
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        main.postDelayed({ dismissOverlay() }, 280L)
    }

    private fun dismissOverlay() {
        activeCancel?.cancel()
        activeCancel = null
        val v = overlayView ?: return
        overlayView = null
        lockGlyphView = null
        lockedShownFor = null
        val ctx = appContext ?: return
        runCatching { ctx.getSystemService(WindowManager::class.java)?.removeViewImmediate(v) }
    }

    private class LockGlyphView(context: Context, color: Int, dp: Float) : View(context) {
        var unlockProgress: Float = 0f
        private val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = android.graphics.Paint.Style.FILL
        }
        private val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 4.2f * dp
            strokeCap = android.graphics.Paint.Cap.ROUND
        }
        override fun onDraw(canvas: android.graphics.Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            val p = unlockProgress.coerceIn(0f, 1f)
            canvas.save()
            // Shackle pops upward and tilts open around its right anchor as p -> 1
            canvas.translate(0f, -h * 0.11f * p)
            canvas.rotate(22f * p, w * 0.72f, h * 0.42f)
            val shackle = android.graphics.RectF(w * 0.28f, h * 0.14f, w * 0.72f, h * 0.56f)
            canvas.drawArc(shackle, 180f, 180f, false, strokePaint)
            canvas.restore()
            val body = android.graphics.RectF(w * 0.18f, h * 0.42f, w * 0.82f, h * 0.88f)
            canvas.drawRoundRect(body, w * 0.14f, w * 0.14f, fillPaint)
        }
    }

    private const val POLL_MS = 350L
    private const val LOOKBACK_MS = 4_000L
}
