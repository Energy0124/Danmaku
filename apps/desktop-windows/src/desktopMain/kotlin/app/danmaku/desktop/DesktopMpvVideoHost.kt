package app.danmaku.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.awt.BorderLayout
import java.awt.Canvas
import java.awt.Color
import javax.swing.JPanel

@Composable
fun DesktopMpvVideoHost(
    onWindowIdChanged: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    SwingPanel(
        factory = {
            DesktopMpvVideoPanel(onWindowIdChanged)
        },
        update = { panel ->
            panel.onWindowIdChanged = onWindowIdChanged
        },
        modifier = modifier,
    )
}

private class DesktopMpvVideoPanel(
    var onWindowIdChanged: (Long?) -> Unit,
) : JPanel(BorderLayout()) {
    init {
        background = Color.BLACK
        add(
            object : Canvas() {
                init {
                    background = Color.BLACK
                }

                override fun addNotify() {
                    super.addNotify()
                    val windowId = runCatching {
                        Pointer.nativeValue(Native.getComponentPointer(this))
                    }.getOrNull()?.takeIf { it != 0L }
                    onWindowIdChanged(windowId)
                }

                override fun removeNotify() {
                    onWindowIdChanged(null)
                    super.removeNotify()
                }
            },
            BorderLayout.CENTER,
        )
    }
}
