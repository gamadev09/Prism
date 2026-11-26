package com.gamadev.prism.data

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList

/** Per-app language preference backed by Android 13+'s LocaleManager. */
internal enum class AppLanguage(val tag: String?) {
    System(null),
    English("en"),
    Spanish("es");

    companion object {
        fun current(context: Context): AppLanguage {
            val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            if (locales == null || locales.isEmpty) return System
            return when (locales.get(0)?.language) {
                "es" -> Spanish
                "en" -> English
                else -> System
            }
        }

        fun select(context: Context, language: AppLanguage) {
            val manager = context.getSystemService(LocaleManager::class.java) ?: return
            val target = if (language.tag == null) LocaleList.getEmptyLocaleList()
            else LocaleList.forLanguageTags(language.tag)
            if (manager.applicationLocales != target) {
                manager.applicationLocales = target
            }
        }
    }
}
