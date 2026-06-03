package app.danmaku.player.android

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import java.util.concurrent.Executor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class Media3PlaybackServiceConnection(
    context: Context,
) : AutoCloseable {
    private val mainHandler = Handler(Looper.getMainLooper())
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
        if (Looper.myLooper() == Looper.getMainLooper()) {
            MediaController.releaseFuture(controllerFuture)
            return
        }
        val released = CountDownLatch(1)
        mainHandler.post {
            try {
                MediaController.releaseFuture(controllerFuture)
            } finally {
                released.countDown()
            }
        }
        released.await(5, TimeUnit.SECONDS)
    }
}
