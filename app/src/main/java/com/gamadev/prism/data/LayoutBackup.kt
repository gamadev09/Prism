package com.gamadev.prism.data

import org.json.JSONObject

data class LayoutBackup(val layout: SavedLayout, val icons: JSONObject?, val installationId: String?)

object LayoutBackupCodec {
    const val MAX_BYTES = 1024 * 1024
    private const val FORMAT = "liquidy-layout"

    fun encode(backup: LayoutBackup): String = JSONObject()
        .put("format", FORMAT).put("version", 1)
        .put("createdAt", java.time.Instant.now().toString())
        .put("installationId", backup.installationId ?: "")
        .put("layout", JSONObject(LayoutJson.encode(backup.layout)))
        .apply { backup.icons?.let { put("icons", it) } }.toString(2)

    fun decode(text: String): LayoutBackup {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        // Bound nesting before asking the JSON parser to recurse. Ignore braces inside strings.
        var depth = 0; var quoted = false; var escaped = false
        for (c in text) {
            if (quoted) {
                if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false
            } else when (c) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= 24) }
                '}', ']' -> { depth--; require(depth >= 0) }
            }
        }
        require(depth == 0 && !quoted)
        val root = JSONObject(text)
        require(root.getString("format") == FORMAT && root.getInt("version") == 1)
        val body = root.getJSONObject("layout")
        body.getJSONArray("pages"); body.getJSONArray("dock")
        body.getJSONObject("folders"); body.getJSONObject("widgets")
        body.getJSONArray("hidden"); body.getJSONArray("hiddenApps"); body.getJSONArray("hiddenPages")
        if (body.has("today")) body.getJSONArray("today")
        if (root.has("icons")) root.getJSONObject("icons")
        val layout = LayoutJson.decode(body.toString())
        require(layout.pages.size in 1..64 && layout.hiddenPages.size <= 64)
        require(layout.widgets.size <= 1024 && layout.folders.size <= 1024 && layout.dock.size <= 24)
        fun key(id: String) { require(id.isNotBlank() && id.length <= 512) }
        fun keys(ids: Collection<String>) { require(ids.size <= 4096); ids.forEach(::key) }
        layout.pages.forEach(::keys); keys(layout.dock); keys(layout.hidden); keys(layout.hiddenApps)
        layout.today?.let(::keys)
        layout.folders.forEach { (id, folder) ->
            key(id); require(id.startsWith("folder:") && folder.name.length <= 256); keys(folder.apps)
            require(folder.apps.none { it.startsWith("folder:") || it.startsWith("widget:") || it.startsWith("gap:") })
        }
        layout.hiddenPages.forEach { require(it.pos in 0..127); keys(it.items) }
        fun widget(spec: WidgetSpec, level: Int) {
            require(level <= 8)
            require(spec.type in setOf("ios:clock", "ios:calendar", "ios:battery", "ios:weather", "ios:photos", "ios:reminders", "android", "stack"))
            require((spec.cols to spec.rows) in setOf(2 to 2, 4 to 2, 4 to 4))
            require((spec.config?.length ?: 0) <= 65536 && (spec.provider?.length ?: 0) <= 512)
            require(spec.stack.size <= 32)
            if (spec.type == "stack") require(spec.stack.isNotEmpty() && spec.stack.none { it.type == "stack" })
            else require(spec.stack.isEmpty())
            spec.stack.forEach { widget(it, level + 1) }
        }
        layout.widgets.forEach { (id, spec) -> key(id); require(id.startsWith("widget:")); widget(spec, 0) }
        val references = layout.pages.flatten() + layout.dock + layout.hiddenPages.flatMap { it.items } + layout.today.orEmpty()
        references.forEach { id ->
            if (id.startsWith("folder:")) require(id in layout.folders)
            if (id.startsWith("widget:")) require(id in layout.widgets)
        }
        require(layout.today.orEmpty().all { it in layout.widgets })
        val widgetReferences = references.filter { it.startsWith("widget:") }
        require(widgetReferences.distinct().size == widgetReferences.size)
        val icons = root.optJSONObject("icons")
        icons?.let {
            require(it.getString("style") in setOf("Default", "Dark", "Clear", "Tinted"))
            require(it.getString("variant") in setOf("Light", "Dark", "Auto"))
            require(it.getString("source") in setOf("Device", "Wallpaper", "Custom"))
            require(it.getDouble("hue") in 0.0..360.0 && it.getDouble("sat") in 0.0..1.0)
            it.getBoolean("dim"); it.getBoolean("large")
        }
        return LayoutBackup(layout, icons, root.optString("installationId").ifEmpty { null })
    }
}
