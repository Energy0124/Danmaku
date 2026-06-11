package app.danmaku.desktop

import kotlin.math.abs

internal class DesktopShellMpvOscActions(
    private val hostPlatform: DesktopHostPlatform,
    private val executor: DesktopMpvCommandExecutor,
    private val appendDiagnostic: (String, String) -> Unit,
) {
    fun forwardBinding(
        bindingName: String,
        x: Int? = null,
        y: Int? = null,
        sourceWidth: Int? = null,
        sourceHeight: Int? = null,
    ) {
        if (hostPlatform != DesktopHostPlatform.WINDOWS) {
            return
        }
        val args = buildList {
            add("script-binding")
            add("danmaku_osc/$bindingName")
            if (x != null && y != null) {
                add(
                    listOfNotNull(
                        x.coerceAtLeast(0),
                        y.coerceAtLeast(0),
                        sourceWidth?.coerceAtLeast(1),
                        sourceHeight?.coerceAtLeast(1),
                    ).joinToString(separator = ","),
                )
            }
        }
        runCatching {
            executor.execute(DesktopMpvCommand(args))
        }.onFailure { error ->
            appendDiagnostic("mpv", "Custom OSC input forward failed: ${error.message ?: error::class.simpleName}")
        }
    }

    fun forwardWheel(
        x: Int,
        y: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        rotation: Int,
    ) {
        val bindingName = if (rotation < 0) {
            "danmaku-osc-wheel-up"
        } else {
            "danmaku-osc-wheel-down"
        }
        repeat(abs(rotation).coerceIn(1, 5)) {
            forwardBinding(bindingName, x, y, sourceWidth, sourceHeight)
        }
    }
}
