package app.danmaku.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.danmaku.domain.DanmakuEvent
import app.danmaku.domain.DanmakuStyle
import app.danmaku.domain.MeasuredDanmakuEvent
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.ScrollingDanmakuLaneScheduler
import app.danmaku.domain.ScrollingDanmakuLayoutConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import javax.swing.JFileChooser

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
    val selectionStore = remember { LocalLibrarySelectionStore.default() }
    val catalogStore = remember { DesktopLibraryCatalogStore.default() }
    val server = remember(catalogStore) {
        LocalLibraryServer(progressStore = catalogStore).apply {
            start()
        }
    }
    val discoveryAnnouncer = remember(server) {
        LocalLibraryDiscoveryAnnouncer(server.localPort).apply {
            start()
        }
    }
    val scope = rememberCoroutineScope()
    var selectedLibraryRoot by remember { mutableStateOf(selectionStore.load()) }
    var indexedLibrary by remember {
        mutableStateOf(selectedLibraryRoot?.let(catalogStore::load))
    }
    var libraryError by remember { mutableStateOf<String?>(null) }
    var isIndexing by remember { mutableStateOf(false) }
    val networkUrls = remember(server) { server.networkUrls() }

    fun indexLibrary(root: Path) {
        scope.launch {
            isIndexing = true
            try {
                runCatching {
                    withContext(Dispatchers.IO) {
                        LocalMediaLibraryIndexer.index(
                            root = root,
                            cachedItems = catalogStore.load(root)
                                ?.fileMetadataByRelativePath
                                .orEmpty(),
                        ).also {
                            catalogStore.replace(root, it)
                            selectionStore.save(root)
                        }
                    }
                }.onSuccess { library ->
                    server.publish(library)
                    indexedLibrary = library
                    selectedLibraryRoot = root.toAbsolutePath().normalize()
                    libraryError = null
                }.onFailure { error ->
                    libraryError = error.message
                }
            } finally {
                isIndexing = false
            }
        }
    }

    LaunchedEffect(Unit) {
        indexedLibrary?.let(server::publish)
        selectedLibraryRoot?.let(::indexLibrary)
    }

    DisposableEffect(server, discoveryAnnouncer) {
        onDispose {
            discoveryAnnouncer.close()
            server.close()
            catalogStore.close()
        }
    }

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
                Text("Synthetic overlay demo: collision-aware shared lane scheduler")
                SyntheticOverlayDemo()
                Text("Windows anime library server")
                Button(
                    onClick = {
                        selectLibraryDirectory()?.let(::indexLibrary)
                    },
                    enabled = !isIndexing,
                ) {
                    Text(if (isIndexing) "Indexing..." else "Choose anime folder and index")
                }
                Button(
                    onClick = {
                        selectedLibraryRoot?.let(::indexLibrary)
                    },
                    enabled = selectedLibraryRoot != null && !isIndexing,
                ) {
                    Text("Rescan indexed folder")
                }
                Text("Indexed folder: ${selectedLibraryRoot ?: "None selected"}")
                networkUrls.forEach { url ->
                    Text("Library URL: $url")
                }
                Text("LAN discovery: UDP port ${app.danmaku.domain.LanLibraryServerAnnouncement.DEFAULT_DISCOVERY_PORT}")
                Text("Pairing code: ${server.pairingToken}")
                libraryError?.let { Text("Library error: $it") }
                Text("Indexed episodes: ${indexedLibrary?.catalog?.items?.size ?: 0}")
                indexedLibrary?.scanStats?.let { stats ->
                    Text(
                        "Last scan: ${stats.reusedItemCount} unchanged, " +
                            "${stats.refreshedItemCount} refreshed",
                    )
                }
                LazyColumn(modifier = Modifier.height(140.dp)) {
                    items(indexedLibrary?.catalog?.items.orEmpty()) { item ->
                        Text("${item.seriesTitle} - ${item.episodeTitle}")
                    }
                }
                Text("Next: connect the libmpv render surface and playback clock.")
            }
        }
    }
}

private fun selectLibraryDirectory() =
    JFileChooser().run {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = "Choose anime library folder"
        takeIf { showOpenDialog(null) == JFileChooser.APPROVE_OPTION }
            ?.selectedFile
            ?.toPath()
    }

@Composable
private fun SyntheticOverlayDemo() {
    var playbackPositionMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        val startedAtMs = withFrameMillis { it }
        while (true) {
            withFrameMillis { frameTimeMs ->
                playbackPositionMs = (frameTimeMs - startedAtMs) % DEMO_DURATION_MS
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(Color(0xFF111827), RoundedCornerShape(8.dp))
            .clipToBounds(),
    ) {
        val density = LocalDensity.current
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val schedule = remember(viewportWidthPx, density) {
            val measuredEvents = SYNTHETIC_EVENTS.map { event ->
                MeasuredDanmakuEvent(
                    event = event,
                    widthPx = with(density) {
                        (event.text.length * 9 + 28).dp.toPx()
                    },
                )
            }
            ScrollingDanmakuLaneScheduler.schedule(
                events = measuredEvents,
                config = ScrollingDanmakuLayoutConfig(
                    viewportWidthPx = viewportWidthPx,
                    laneCount = DEMO_LANE_COUNT,
                    travelDurationMs = 6_000,
                    horizontalGapPx = with(density) { 24.dp.toPx() },
                ),
            )
        }

        schedule.visibleAt(playbackPositionMs).forEach { placement ->
            Text(
                text = placement.event.text,
                color = Color.White,
                modifier = Modifier
                    .offset(
                        x = with(density) {
                            placement.leftEdgeAt(playbackPositionMs).toDp()
                        },
                        y = (placement.laneIndex * 44 + 16).dp,
                    )
                    .background(Color(0xB33B82F6), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

private const val DEMO_DURATION_MS = 12_000L
private const val DEMO_LANE_COUNT = 4

private val SYNTHETIC_EVENTS = listOf(
    syntheticEvent(id = "one", timestampMs = 0, text = "Shared Kotlin scheduler"),
    syntheticEvent(id = "two", timestampMs = 650, text = "Windows overlay composition"),
    syntheticEvent(id = "three", timestampMs = 1_100, text = "Collision-aware lanes"),
    syntheticEvent(id = "four", timestampMs = 1_750, text = "Seek-safe deterministic layout"),
    syntheticEvent(id = "five", timestampMs = 2_900, text = "Compose Desktop demo"),
    syntheticEvent(id = "six", timestampMs = 4_200, text = "libmpv clock comes next"),
    syntheticEvent(id = "seven", timestampMs = 6_400, text = "Looping synthetic track"),
    syntheticEvent(id = "eight", timestampMs = 7_200, text = "Danmaku"),
)

private fun syntheticEvent(
    id: String,
    timestampMs: Long,
    text: String,
): DanmakuEvent =
    DanmakuEvent(
        id = id,
        timestampMs = timestampMs,
        text = text,
        style = DanmakuStyle(),
    )
