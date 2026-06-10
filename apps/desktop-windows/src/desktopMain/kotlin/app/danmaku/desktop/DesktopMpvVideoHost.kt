package app.danmaku.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.awt.BorderLayout
import java.awt.Canvas
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.LayoutManager
import java.awt.MouseInfo
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.event.MouseWheelEvent
import java.awt.event.MouseWheelListener
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.Box as SwingBox

data class DesktopMpvVideoControlsState(
    val visible: Boolean,
    val hasMedia: Boolean,
    val title: String,
    val status: String,
    val overlayStatus: String,
    val positionMs: Long,
    val durationMs: Long?,
    val isPlaying: Boolean,
    val volumePercent: Int,
    val playbackRate: Float,
    val audioText: String,
    val subtitleText: String,
    val aspectText: String,
    val isFullscreen: Boolean,
    val canOpenMedia: Boolean,
    val canCycleAudio: Boolean,
    val canCycleSubtitle: Boolean,
)

class DesktopMpvVideoControlsActions(
    val onShowHome: () -> Unit,
    val onShowLibrary: () -> Unit,
    val onOpenMediaFile: () -> Unit,
    val onPlayPause: () -> Unit,
    val onSeekBackward: () -> Unit,
    val onSeekBackwardLarge: () -> Unit,
    val onSeekForward: () -> Unit,
    val onSeekForwardLarge: () -> Unit,
    val onSeekTo: (Long) -> Unit,
    val onSetVolume: (Int) -> Unit,
    val onCyclePlaybackRate: () -> Unit,
    val onCycleAudioTrack: () -> Unit,
    val onCycleSubtitleTrack: () -> Unit,
    val onCycleAspectMode: () -> Unit,
    val onToggleFullscreen: () -> Unit,
)

@Composable
fun DesktopMpvVideoHost(
    onWindowIdChanged: (Long?) -> Unit,
    onUserInput: () -> Unit = {},
    onMpvPointerMove: (x: Int, y: Int, width: Int, height: Int) -> Unit = { _, _, _, _ -> },
    onMpvPrimaryClick: (x: Int, y: Int, width: Int, height: Int) -> Unit = { _, _, _, _ -> },
    onMpvWheel: (x: Int, y: Int, width: Int, height: Int, rotation: Int) -> Unit = { _, _, _, _, _ -> },
    onPrimaryClick: (x: Int, y: Int, width: Int, height: Int) -> Unit = { _, _, _, _ -> },
    controlsState: DesktopMpvVideoControlsState? = null,
    controlsActions: DesktopMpvVideoControlsActions? = null,
    modifier: Modifier = Modifier,
) {
    SwingPanel(
        factory = {
            DesktopMpvVideoPanel(
                onWindowIdChanged = onWindowIdChanged,
                onUserInput = onUserInput,
                onMpvPointerMove = onMpvPointerMove,
                onMpvPrimaryClick = onMpvPrimaryClick,
                onMpvWheel = onMpvWheel,
                onPrimaryClick = onPrimaryClick,
            )
        },
        update = { panel ->
            panel.onWindowIdChanged = onWindowIdChanged
            panel.onUserInput = onUserInput
            panel.onMpvPointerMove = onMpvPointerMove
            panel.onMpvPrimaryClick = onMpvPrimaryClick
            panel.onMpvWheel = onMpvWheel
            panel.onPrimaryClick = onPrimaryClick
            panel.updateControls(controlsState, controlsActions)
        },
        modifier = modifier,
    )
}

private class DesktopMpvVideoPanel(
    var onWindowIdChanged: (Long?) -> Unit,
    var onUserInput: () -> Unit,
    var onMpvPointerMove: (x: Int, y: Int, width: Int, height: Int) -> Unit,
    var onMpvPrimaryClick: (x: Int, y: Int, width: Int, height: Int) -> Unit,
    var onMpvWheel: (x: Int, y: Int, width: Int, height: Int, rotation: Int) -> Unit,
    var onPrimaryClick: (x: Int, y: Int, width: Int, height: Int) -> Unit,
) : JLayeredPane() {
    private var controlsState: DesktopMpvVideoControlsState? = null
    private var controlsActions: DesktopMpvVideoControlsActions? = null
    private var isUpdatingControls = false
    private var lastPublishedWindowId: Long? = null
    private var pendingPublishedWindowId: Long? = null
    private var windowIdPublishQueued = false
    private var lastObservedPointer: Point? = null
    private val pointerPollTimer = Timer(100) {
        revealOnPointerMovement()
    }.apply {
        isRepeats = true
    }
    private val titleLabel = overlayLabel(fontStyle = Font.BOLD, fontSize = 16f)
    private val overlayStatusLabel = overlayLabel(foreground = TEXT_MUTED, fontSize = 13f)
    private val statusLabel = overlayLabel(foreground = TEXT_MUTED, fontSize = 14f)
    private val positionLabel = overlayLabel(fontSize = 13f, horizontalAlignment = SwingConstants.LEFT)
    private val durationLabel = overlayLabel(fontSize = 13f, horizontalAlignment = SwingConstants.RIGHT)
    private val progressSlider = overlaySlider(0, 1, 0)
    private val volumeLabel = overlayLabel(foreground = TEXT_MUTED, fontSize = 13f)
    private val volumeSlider = overlaySlider(0, 100, 100).apply {
        preferredSize = Dimension(130, 30)
        maximumSize = Dimension(130, 30)
    }
    private val homeButton = overlayButton("Home")
    private val libraryButton = overlayButton("Library")
    private val openButton = overlayButton("Open")
    private val backLargeButton = overlayButton("<<30")
    private val backButton = overlayButton("-10")
    private val playPauseButton = overlayButton("Play")
    private val forwardButton = overlayButton("+10")
    private val forwardLargeButton = overlayButton("30>>")
    private val rateButton = overlayButton("1.0x")
    private val audioButton = overlayButton("Audio")
    private val subtitleButton = overlayButton("Sub")
    private val aspectButton = overlayButton("Default")
    private val fullscreenButton = overlayButton("Full")
    private val centerMessageLabel = overlayLabel(fontStyle = Font.BOLD, fontSize = 18f, horizontalAlignment = SwingConstants.CENTER)
    private val centerDetailLabel = overlayLabel(foreground = TEXT_MUTED, fontSize = 13f, horizontalAlignment = SwingConstants.CENTER)
    private val videoCanvas = object : Canvas() {
        init {
            background = Color.BLACK
            isFocusable = true
        }

        override fun addNotify() {
            super.addNotify()
            publishVideoWindowId()
            pointerPollTimer.start()
        }

        override fun removeNotify() {
            pointerPollTimer.stop()
            lastObservedPointer = null
            queueVideoWindowId(null)
            super.removeNotify()
        }
    }
    private val topOverlay = TranslucentPanel(BorderLayout()).apply {
        border = BorderFactory.createEmptyBorder(10, 18, 10, 18)
        add(
            JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                isOpaque = false
                add(homeButton)
                add(libraryButton)
            },
            BorderLayout.WEST,
        )
        add(
            JPanel().apply {
                isOpaque = false
                layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
                add(titleLabel)
                add(overlayStatusLabel)
            },
            BorderLayout.CENTER,
        )
        add(statusLabel, BorderLayout.EAST)
    }
    private val bottomOverlay = TranslucentPanel(GridBagLayout()).apply {
        border = BorderFactory.createEmptyBorder(10, 18, 12, 18)
        buildBottomOverlay()
    }
    private val centerOverlay = TranslucentPanel(BorderLayout()).apply {
        border = BorderFactory.createEmptyBorder(18, 24, 18, 24)
        add(centerMessageLabel, BorderLayout.CENTER)
        add(centerDetailLabel, BorderLayout.SOUTH)
    }
    private val inputListener = object : MouseAdapter(), MouseWheelListener {
        override fun mousePressed(e: MouseEvent) {
            onUserInput()
            val point = e.toPanelPoint()
            onMpvPointerMove(point.x, point.y, width, height)
            if (SwingUtilities.isLeftMouseButton(e)) {
                onMpvPrimaryClick(point.x, point.y, width, height)
                onPrimaryClick(point.x, point.y, width, height)
            }
        }

        override fun mouseClicked(e: MouseEvent) {
            onUserInput()
            val point = e.toPanelPoint()
            onMpvPointerMove(point.x, point.y, width, height)
        }

        override fun mouseEntered(e: MouseEvent) {
            onUserInput()
            val point = e.toPanelPoint()
            onMpvPointerMove(point.x, point.y, width, height)
        }

        override fun mouseWheelMoved(e: MouseWheelEvent) {
            onUserInput()
            val point = e.toPanelPoint()
            onMpvWheel(point.x, point.y, width, height, e.wheelRotation)
        }
    }
    private val motionListener = object : MouseMotionAdapter() {
        override fun mouseMoved(e: MouseEvent) {
            onUserInput()
            val point = e.toPanelPoint()
            onMpvPointerMove(point.x, point.y, width, height)
        }

        override fun mouseDragged(e: MouseEvent) {
            onUserInput()
            val point = e.toPanelPoint()
            onMpvPointerMove(point.x, point.y, width, height)
        }
    }

    init {
        layout = null
        isOpaque = true
        background = Color.BLACK
        add(videoCanvas, DEFAULT_LAYER)
        add(topOverlay, PALETTE_LAYER)
        add(bottomOverlay, PALETTE_LAYER)
        add(centerOverlay, PALETTE_LAYER)
        installUserInputListeners(this)
        installControlActions()
        updateControls(null, null)
    }

    override fun paintComponent(g: Graphics) {
        g.color = Color.BLACK
        g.fillRect(0, 0, width, height)
        super.paintComponent(g)
    }

    override fun doLayout() {
        super.doLayout()
        videoCanvas.setBounds(0, 0, width, height)
        topOverlay.setBounds(0, 0, width, TOP_OVERLAY_HEIGHT)
        bottomOverlay.setBounds(0, (height - BOTTOM_OVERLAY_HEIGHT).coerceAtLeast(0), width, BOTTOM_OVERLAY_HEIGHT)
        val centerWidth = minOf(520, (width - 48).coerceAtLeast(240))
        val centerHeight = 112
        centerOverlay.setBounds(
            ((width - centerWidth) / 2).coerceAtLeast(0),
            ((height - centerHeight) / 2).coerceAtLeast(0),
            centerWidth,
            centerHeight,
        )
    }

    fun updateControls(
        state: DesktopMpvVideoControlsState?,
        actions: DesktopMpvVideoControlsActions?,
    ) {
        controlsState = state
        controlsActions = actions
        val showVideoCanvas = state == null || state.hasMedia
        if (videoCanvas.isVisible != showVideoCanvas) {
            videoCanvas.isVisible = showVideoCanvas
        }
        if (showVideoCanvas) {
            publishVideoWindowId()
        }
        val showControls = state != null && state.visible
        topOverlay.isVisible = showControls
        bottomOverlay.isVisible = showControls
        centerOverlay.isVisible = state != null && !state.hasMedia
        if (state == null) {
            repaint()
            return
        }

        isUpdatingControls = true
        titleLabel.text = state.title
        overlayStatusLabel.text = state.overlayStatus
        statusLabel.text = state.status
        positionLabel.text = state.positionMs.formatMpvOverlayTime()
        durationLabel.text = state.durationMs?.formatMpvOverlayTime() ?: "--:--"
        progressSlider.isEnabled = state.hasMedia && state.durationMs != null
        progressSlider.maximum = state.durationMs?.coerceAtLeast(1L)?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 1
        progressSlider.value = state.positionMs.coerceAtLeast(0L)
            .coerceAtMost(progressSlider.maximum.toLong())
            .toInt()
        volumeLabel.text = "${state.volumePercent}%"
        volumeSlider.isEnabled = state.hasMedia
        volumeSlider.value = state.volumePercent.coerceIn(0, 100)
        playPauseButton.text = if (state.isPlaying) "Pause" else "Play"
        rateButton.text = "${state.playbackRate}x"
        audioButton.text = state.audioText.abbreviateForButton()
        subtitleButton.text = state.subtitleText.abbreviateForButton()
        aspectButton.text = state.aspectText
        fullscreenButton.text = if (state.isFullscreen) "Exit full" else "Full"
        centerMessageLabel.text = "No media loaded"
        centerDetailLabel.text = if (state.canOpenMedia) "Open a media file or play an item from the library" else "mpv is starting"

        openButton.isEnabled = state.canOpenMedia
        listOf(backLargeButton, backButton, playPauseButton, forwardButton, forwardLargeButton, rateButton, aspectButton)
            .forEach { it.isEnabled = state.hasMedia }
        fullscreenButton.isEnabled = state.hasMedia || state.isFullscreen
        audioButton.isEnabled = state.hasMedia && state.canCycleAudio
        subtitleButton.isEnabled = state.hasMedia && state.canCycleSubtitle
        isUpdatingControls = false
        revalidate()
        repaint()
    }

    private fun publishVideoWindowId() {
        val windowId = runCatching {
            Pointer.nativeValue(Native.getComponentPointer(videoCanvas))
        }.getOrNull()?.takeIf { it != 0L }
        queueVideoWindowId(windowId)
    }

    private fun queueVideoWindowId(windowId: Long?) {
        if (windowId == lastPublishedWindowId && !windowIdPublishQueued) {
            return
        }
        pendingPublishedWindowId = windowId
        if (windowIdPublishQueued) {
            return
        }
        windowIdPublishQueued = true
        SwingUtilities.invokeLater {
            windowIdPublishQueued = false
            val nextWindowId = pendingPublishedWindowId
            pendingPublishedWindowId = null
            if (nextWindowId == lastPublishedWindowId) {
                return@invokeLater
            }
            lastPublishedWindowId = nextWindowId
            onWindowIdChanged(nextWindowId)
        }
    }

    private fun TranslucentPanel.buildBottomOverlay() {
        val progressConstraints = GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            insets = Insets(0, 0, 8, 0)
        }
        add(
            JPanel(BorderLayout(10, 0)).apply {
                isOpaque = false
                add(positionLabel.apply { preferredSize = Dimension(62, 24) }, BorderLayout.WEST)
                add(progressSlider, BorderLayout.CENTER)
                add(durationLabel.apply { preferredSize = Dimension(62, 24) }, BorderLayout.EAST)
            },
            progressConstraints,
        )

        val rowConstraints = GridBagConstraints().apply {
            gridx = 0
            gridy = 1
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
        }
        add(
            JPanel().apply {
                isOpaque = false
                layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.X_AXIS)
                add(openButton)
                addGap()
                add(backLargeButton)
                addGap()
                add(backButton)
                addGap()
                add(playPauseButton)
                addGap()
                add(forwardButton)
                addGap()
                add(forwardLargeButton)
                addGap(14)
                add(overlayLabel("Vol", foreground = TEXT_MUTED, fontSize = 13f))
                addGap(6)
                add(volumeLabel.apply { preferredSize = Dimension(44, 28) })
                add(volumeSlider)
                addGap(10)
                add(rateButton)
                addGap()
                add(audioButton)
                addGap()
                add(subtitleButton)
                addGap()
                add(aspectButton)
                add(SwingBox.createHorizontalGlue())
                add(fullscreenButton)
            },
            rowConstraints,
        )
    }

    private fun installControlActions() {
        homeButton.onOverlayClick { controlsActions?.onShowHome?.invoke() }
        libraryButton.onOverlayClick { controlsActions?.onShowLibrary?.invoke() }
        openButton.onOverlayClick { controlsActions?.onOpenMediaFile?.invoke() }
        backLargeButton.onOverlayClick { controlsActions?.onSeekBackwardLarge?.invoke() }
        backButton.onOverlayClick { controlsActions?.onSeekBackward?.invoke() }
        playPauseButton.onOverlayClick { controlsActions?.onPlayPause?.invoke() }
        forwardButton.onOverlayClick { controlsActions?.onSeekForward?.invoke() }
        forwardLargeButton.onOverlayClick { controlsActions?.onSeekForwardLarge?.invoke() }
        rateButton.onOverlayClick { controlsActions?.onCyclePlaybackRate?.invoke() }
        audioButton.onOverlayClick { controlsActions?.onCycleAudioTrack?.invoke() }
        subtitleButton.onOverlayClick { controlsActions?.onCycleSubtitleTrack?.invoke() }
        aspectButton.onOverlayClick { controlsActions?.onCycleAspectMode?.invoke() }
        fullscreenButton.onOverlayClick { controlsActions?.onToggleFullscreen?.invoke() }
        progressSlider.addChangeListener {
            if (isUpdatingControls || progressSlider.valueIsAdjusting) return@addChangeListener
            val state = controlsState ?: return@addChangeListener
            if (!state.hasMedia || state.durationMs == null) return@addChangeListener
            onUserInput()
            controlsActions?.onSeekTo?.invoke(progressSlider.value.toLong().coerceIn(0L, state.durationMs))
        }
        volumeSlider.addChangeListener {
            if (isUpdatingControls || volumeSlider.valueIsAdjusting) return@addChangeListener
            val state = controlsState ?: return@addChangeListener
            if (!state.hasMedia) return@addChangeListener
            onUserInput()
            controlsActions?.onSetVolume?.invoke(volumeSlider.value.coerceIn(0, 100))
        }
    }

    private fun JButton.onOverlayClick(action: () -> Unit) {
        addActionListener {
            onUserInput()
            action()
        }
    }

    private fun JPanel.addGap(width: Int = 8) {
        add(SwingBox.createHorizontalStrut(width))
    }

    private fun installUserInputListeners(component: Component) {
        component.addMouseListener(inputListener)
        component.addMouseWheelListener(inputListener)
        component.addMouseMotionListener(motionListener)
        if (component is Container) {
            component.components.forEach(::installUserInputListeners)
        }
    }

    private fun revealOnPointerMovement() {
        if (!isShowing || width <= 0 || height <= 0) {
            lastObservedPointer = null
            return
        }
        val screenPointer = MouseInfo.getPointerInfo()?.location ?: return
        val screenOrigin = runCatching { locationOnScreen }.getOrNull() ?: return
        val relativePointer = Point(
            screenPointer.x - screenOrigin.x,
            screenPointer.y - screenOrigin.y,
        )
        val pointerInside = relativePointer.x in 0 until width && relativePointer.y in 0 until height
        if (!pointerInside) {
            lastObservedPointer = null
            return
        }
        if (lastObservedPointer != relativePointer) {
            lastObservedPointer = relativePointer
            onUserInput()
            onMpvPointerMove(relativePointer.x, relativePointer.y, width, height)
        }
    }

    private fun MouseEvent.toPanelPoint(): Point =
        SwingUtilities.convertPoint(component, point, this@DesktopMpvVideoPanel)

    private class TranslucentPanel(layoutManager: LayoutManager) : JPanel(layoutManager) {
        init {
            isOpaque = false
        }

        override fun paintComponent(g: Graphics) {
            g.color = OVERLAY_BACKGROUND
            g.fillRect(0, 0, width, height)
            super.paintComponent(g)
        }
    }

    companion object {
        const val TOP_OVERLAY_HEIGHT = 74
        const val BOTTOM_OVERLAY_HEIGHT = 126
        val OVERLAY_BACKGROUND = Color(0, 0, 0, 156)
        val BUTTON_BACKGROUND = Color(255, 255, 255, 42)
        val BUTTON_HOVER_BACKGROUND = Color(255, 255, 255, 64)
        val BUTTON_PRESSED_BACKGROUND = Color(255, 47, 104, 180)
        val BUTTON_DISABLED_BACKGROUND = Color(255, 255, 255, 20)
        val TEXT_MUTED = Color(190, 190, 190)
    }
}

private fun overlayLabel(
    text: String = "",
    foreground: Color = Color.WHITE,
    fontStyle: Int = Font.PLAIN,
    fontSize: Float = 14f,
    horizontalAlignment: Int = SwingConstants.LEADING,
): JLabel =
    JLabel(text).apply {
        this.foreground = foreground
        font = font.deriveFont(fontStyle, fontSize)
        this.horizontalAlignment = horizontalAlignment
    }

private fun overlayButton(text: String): JButton =
    object : JButton(text) {
        override fun paintComponent(g: Graphics) {
            g.color = when {
                !isEnabled -> DesktopMpvVideoPanel.BUTTON_DISABLED_BACKGROUND
                model.isPressed -> DesktopMpvVideoPanel.BUTTON_PRESSED_BACKGROUND
                model.isRollover -> DesktopMpvVideoPanel.BUTTON_HOVER_BACKGROUND
                else -> DesktopMpvVideoPanel.BUTTON_BACKGROUND
            }
            g.fillRoundRect(0, 0, width, height, 8, 8)
            foreground = if (isEnabled) {
                Color.WHITE
            } else {
                DesktopMpvVideoPanel.TEXT_MUTED
            }
            super.paintComponent(g)
        }
    }.apply {
        isFocusPainted = false
        isBorderPainted = false
        isContentAreaFilled = false
        isOpaque = false
        border = BorderFactory.createEmptyBorder(7, 10, 7, 10)
        foreground = Color.WHITE
        disabledIcon = null
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        isFocusable = false
        minimumSize = Dimension(48, 34)
        preferredSize = Dimension(preferredSize.width.coerceAtLeast(54), 34)
    }

private fun overlaySlider(
    min: Int,
    max: Int,
    value: Int,
): JSlider =
    JSlider(min, max, value).apply {
        isOpaque = false
        foreground = Color.WHITE
        background = Color.BLACK
        isFocusable = false
    }

private fun Long.formatMpvOverlayTime(): String {
    val totalSeconds = coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private fun String.abbreviateForButton(maxLength: Int = 16): String =
    if (length <= maxLength) {
        this
    } else {
        take(maxLength - 3) + "..."
    }
