package app.danmaku.desktop

import java.nio.file.Path

object DesktopMpvWindowsOptions {
    fun forWindowId(
        windowId: Long,
        controlScriptPath: Path? = null,
    ): Map<String, String> {
        val unsignedWindowId = windowId.toULong()
        require(unsignedWindowId != 0uL) { "windowId must not be zero" }
        return embeddedPlaybackOptions(controlScriptPath) + mapOf("wid" to unsignedWindowId.toString())
    }

    private fun embeddedPlaybackOptions(controlScriptPath: Path?): Map<String, String> =
        buildMap {
            put("input-default-bindings", "yes")
            put("input-cursor", "yes")
            put("input-vo-keyboard", "yes")
            if (controlScriptPath == null) {
                put("osc", "yes")
                put("script-opts", "osc-layout=bottombar,osc-visibility=always")
            } else {
                put("osc", "no")
                put("scripts", controlScriptPath.toAbsolutePath().normalize().toString())
            }
        }
}
