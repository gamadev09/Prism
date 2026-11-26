package com.gamadev.prism.ui.home

import com.gamadev.prism.data.SavedLayout

/** Remove every launcher copy of an app, including copies on disabled home pages. */
internal fun SavedLayout.hideAppCopies(key: String): SavedLayout {
    val appKey = appKeyOf(key)
    val remainingFolders = folders.mapValues { (_, folder) ->
        folder.copy(apps = folder.apps.filterNot { appKeyOf(it) == appKey })
    }.filterValues { it.apps.isNotEmpty() }
    fun keep(id: String) = appKeyOf(id) != appKey && (!isFolder(id) || id in remainingFolders)
    fun clean(page: List<String>) = GridPlacement.trim(page.map { if (keep(it)) it else GridPlacement.newGap() })
    return copy(
        pages = pages.map(::clean).filterNot(GridPlacement::isBlank).ifEmpty { listOf(emptyList()) },
        dock = dock.filter(::keep),
        folders = remainingFolders,
        hiddenPages = hiddenPages.map { it.copy(items = clean(it.items)) }.filter { it.items.isNotEmpty() },
        hidden = hidden - appKey,
        hiddenApps = hiddenApps + appKey,
    )
}
