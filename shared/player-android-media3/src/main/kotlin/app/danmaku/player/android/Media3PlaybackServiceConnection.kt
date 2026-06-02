package app.danmaku.player.android

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import java.util.concurrent.Executor

class Media3PlaybackServiceConnection(
    context: Context,
) : AutoCloseable {
    private val controllerFuture = MediaController.Builder(
        context,
        SessionToken(context, ComponentName(context, DanmakuPlaybackService::class.java)),
    ).buildAsync()

    fun connect(
        executor: Executor,
        onConnected: (Media3PlaybackController) -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        controllerFuture.addListener(
            {
                runCatching {
                    Media3PlaybackController(controllerFuture.get())
                }.onSuccess(onConnected)
                    .onFailure(onFailure)
            },
            executor,
        )
    }

    override fun close() {
        MediaController.releaseFuture(controllerFuture)
    }
}
