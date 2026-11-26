package com.gamadev.prism.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LayoutBackupTest {
    private fun example(): LayoutBackup = LayoutBackup(
        SavedLayout(
            pages = listOf(listOf("folder:work", "gap:1", "widget:stack", "app~i1")),
            dock = listOf("app"), folders = mapOf("folder:work" to SavedFolder("Work", listOf("other"))),
            hidden = setOf("removed"), hiddenApps = setOf("private"),
            hiddenPages = listOf(HiddenPage(1, listOf("third"))),
            today = listOf("widget:weather"),
            widgets = mapOf(
                "widget:stack" to WidgetSpec("stack", 2, 2, stack = listOf(
                    WidgetSpec("android", 2, 2, 3, "example/Widget"),
                    WidgetSpec("ios:clock", 2, 2, config = "Europe/Berlin")), smartRotate = false),
                "widget:weather" to WidgetSpec("ios:weather", 4, 2, config = "12;34;City")),
        ), JSONObject().put("style", "Tinted").put("variant", "Auto").put("source", "Custom")
            .put("hue", 222.0).put("sat", 0.5).put("dim", true).put("large", true), "installation-1")

    @Test fun roundTripPreservesGapsInstancesStacksTodayAndHiddenPages() {
        val original = example()
        val restored = LayoutBackupCodec.decode(LayoutBackupCodec.encode(original))
        assertEquals(original.layout, restored.layout)
        assertEquals(original.installationId, restored.installationId)
        assertEquals("Tinted", restored.icons!!.getString("style"))
        assertTrue(restored.icons.getBoolean("large"))
    }

    @Test fun unsupportedVersionAndMalformedInputAreRejected() {
        val json = JSONObject(LayoutBackupCodec.encode(example())).put("version", 99).toString()
        assertThrows(IllegalArgumentException::class.java) { LayoutBackupCodec.decode(json) }
        assertThrows(Exception::class.java) { LayoutBackupCodec.decode("{broken") }
    }

    @Test fun brokenWidgetReferencesAndInvalidDimensionsAreRejected() {
        val original = example()
        val dangling = original.copy(layout = original.layout.copy(widgets = emptyMap()))
        assertThrows(IllegalArgumentException::class.java) { LayoutBackupCodec.decode(LayoutBackupCodec.encode(dangling)) }
        val bad = original.copy(layout = original.layout.copy(widgets = original.layout.widgets +
            ("widget:weather" to WidgetSpec("ios:weather", 3, 2))))
        assertThrows(IllegalArgumentException::class.java) { LayoutBackupCodec.decode(LayoutBackupCodec.encode(bad)) }
    }

    @Test fun oversizedAndDeepFilesAreRejectedBeforeParsing() {
        assertThrows(IllegalArgumentException::class.java) { LayoutBackupCodec.decode(" ".repeat(LayoutBackupCodec.MAX_BYTES + 1)) }
        assertThrows(IllegalArgumentException::class.java) { LayoutBackupCodec.decode("[".repeat(25) + "]".repeat(25)) }
    }

    @Test fun bracesInsideWidgetConfigurationDoNotCountAsNesting() {
        val source = example()
        val widget = source.layout.widgets.getValue("widget:weather").copy(config = "[".repeat(100) + "quoted \" text")
        val backup = source.copy(layout = source.layout.copy(widgets = source.layout.widgets + ("widget:weather" to widget)))
        assertEquals(widget.config, LayoutBackupCodec.decode(LayoutBackupCodec.encode(backup)).layout.widgets.getValue("widget:weather").config)
    }
}
