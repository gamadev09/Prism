package com.gamadev.prism.data

import android.app.ActivityOptions
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The origin is in the source view's coordinates. Android owns the destination and return surfaces. */
internal object LaunchMotion {
    private const val PREFS = "launch_motion"
    private const val ORIGIN = "animate_from_origin"

    var originEnabled by mutableStateOf(true)
        private set

    fun load(context: Context) {
        originEnabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ORIGIN, true)
    }

    fun setOriginEnabled(context: Context, enabled: Boolean) {
        originEnabled = enabled
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ORIGIN, enabled).apply()
    }

    fun options(source: View, bounds: Rect): Bundle? {
        if (!originEnabled || bounds.isEmpty || !source.isAttachedToWindow) return null
        return ActivityOptions.makeScaleUpAnimation(
            source, bounds.left, bounds.top, bounds.width(), bounds.height(),
        ).toBundle()
    }
}
