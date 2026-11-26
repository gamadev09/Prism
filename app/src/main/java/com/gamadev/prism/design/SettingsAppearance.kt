package com.gamadev.prism.design

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal enum class SettingsAppearance(val storageKey: String) {
    System("system"), Light("light"), Dark("dark");

    fun isDark(systemDark: Boolean) = when (this) {
        System -> systemDark
        Light -> false
        Dark -> true
    }

    companion object {
        private const val PREFS = "settings_ui"
        private const val KEY = "appearance"
        var current by mutableStateOf(System)
            private set

        fun load(context: Context) {
            val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            current = entries.firstOrNull { it.storageKey == saved } ?: System
        }

        fun select(context: Context, appearance: SettingsAppearance) {
            current = appearance
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, appearance.storageKey).apply()
        }
    }
}

/** Shared preference for surfaces outside Settings, including onboarding. */
@androidx.compose.runtime.Composable
internal fun useDarkAppearance(): Boolean =
    SettingsAppearance.current.isDark(androidx.compose.foundation.isSystemInDarkTheme())
