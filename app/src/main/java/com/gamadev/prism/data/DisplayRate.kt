package com.gamadev.prism.data

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.roundToInt

/** A seamless window refresh-rate preference. All animation clocks remain vsync-driven. */
internal object DisplayRate {
    var selected by mutableIntStateOf(0)
        private set
    var available by mutableStateOf<List<Int>>(emptyList())
        private set

    internal fun supportedRate(requested: Int, rates: List<Float>): Float =
        if (requested <= 0) 0f else rates.firstOrNull { abs(it - requested) < 0.6f } ?: 0f

    fun load(context: Context) {
        selected = context.getSharedPreferences("display_rate", Context.MODE_PRIVATE).getInt("hz", 0)
    }
    fun select(context: Context, hz: Int) {
        if (hz != 0 && hz !in available) return
        selected = hz
        context.getSharedPreferences("display_rate", Context.MODE_PRIVATE).edit().putInt("hz", hz).apply()
    }
    fun apply(activity: Activity) {
        val display = activity.display ?: return
        val current = display.mode
        val modes = display.supportedModes.filter {
            it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight
        }
        available = modes.map { it.refreshRate.roundToInt() }.distinct().sorted()
        val targetMode = if (selected <= 0) {
            // Default: request the highest native refresh rate (e.g. 120 Hz) so all launcher animations run at full display rate.
            modes.maxByOrNull { it.refreshRate }
        } else {
            modes.firstOrNull { abs(it.refreshRate - selected) < 0.6f }
        }
        val rate = targetMode?.refreshRate ?: 0f
        val modeId = targetMode?.modeId ?: 0
        val attributes = activity.window.attributes
        if (attributes.preferredRefreshRate != rate || attributes.preferredDisplayModeId != modeId) {
            attributes.preferredRefreshRate = rate
            attributes.preferredDisplayModeId = modeId
            activity.window.attributes = attributes
        }
    }
}
