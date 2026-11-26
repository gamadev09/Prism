package com.gamadev.prism.ui.home

import com.gamadev.prism.data.HiddenPage
import com.gamadev.prism.data.SavedFolder
import com.gamadev.prism.data.SavedLayout
import org.junit.Assert.*
import org.junit.Test

class HiddenAppLayoutTest {
    @Test fun hidingAnInstanceRemovesEveryCopyAcrossVisibleAndHiddenContainers() {
        val layout = SavedLayout(
            pages = listOf(listOf("app", "app~i1", "other", "folder:empty", "folder:mixed")),
            dock = listOf("app~i2", "other"),
            folders = mapOf("folder:empty" to SavedFolder("Empty", listOf("app~i3")),
                "folder:mixed" to SavedFolder("Mixed", listOf("app", "other"))),
            hidden = setOf("app", "other"),
            today = listOf("widget:keep"),
            hiddenPages = listOf(HiddenPage(0, listOf("app~i4", "other"))),
        )
        val result = layout.hideAppCopies("app~i1")
        assertFalse(result.folders.containsKey("folder:empty"))
        assertEquals(listOf("other"), result.folders.getValue("folder:mixed").apps)
        assertEquals(listOf("other"), result.dock)
        assertFalse(result.pages.flatten().any { appKeyOf(it) == "app" || it == "folder:empty" })
        assertFalse(result.hiddenPages.flatMap { it.items }.any { appKeyOf(it) == "app" })
        assertEquals(setOf("app"), result.hiddenApps)
        assertEquals(setOf("other"), result.hidden)
        assertEquals(layout.today, result.today)
        assertEquals("app", layout.pages.first().first()) // source remains unchanged
    }

    @Test fun hidingTheLastHomeAppKeepsAUsableEmptyPageAndIsIdempotent() {
        val layout = SavedLayout(listOf(listOf("app")), emptyList(), emptyMap(), emptySet())
        val result = layout.hideAppCopies("app")
        assertEquals(listOf(emptyList<String>()), result.pages)
        assertEquals(result, result.hideAppCopies("app"))
    }
}
