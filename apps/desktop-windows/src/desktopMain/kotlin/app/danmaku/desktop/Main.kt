package app.danmaku.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.danmaku.domain.PlaybackSnapshot

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Danmaku",
    ) {
        DesktopShell(snapshot = PlaybackSnapshot())
    }
}

@Composable
private fun DesktopShell(snapshot: PlaybackSnapshot) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Danmaku",
                    style = MaterialTheme.typography.h4,
                )
                Text("Windows playback foundation")
                Text("Player state: ${snapshot.status}")
                Text("Next: connect the libmpv adapter and render surface.")
            }
        }
    }
}
