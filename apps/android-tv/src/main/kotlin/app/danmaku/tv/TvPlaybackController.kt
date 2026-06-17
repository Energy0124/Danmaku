package app.danmaku.tv

import androidx.media3.common.Player
import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.library.LanPlaybackPreparation
import app.danmaku.player.android.Media3PlaybackController

internal interface TvPlaybackController {
    val androidPlayer: Player?

    fun load(preparation: LanPlaybackPreparation)

    fun dispatch(command: PlaybackCommand)

    fun snapshot(): PlaybackSnapshot
}

internal class Media3TvPlaybackController(
    private val delegate: Media3PlaybackController,
) : TvPlaybackController {
    override val androidPlayer: Player
        get() = delegate.player

    override fun load(preparation: LanPlaybackPreparation) {
        delegate.load(preparation)
    }

    override fun dispatch(command: PlaybackCommand) {
        delegate.dispatch(command)
    }

    override fun snapshot(): PlaybackSnapshot =
        delegate.snapshot()
}
