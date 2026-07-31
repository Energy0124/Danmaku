package app.danmaku.tv

import android.app.Application

class DanmakuTvApplication : Application() {
    internal val container: TvApplicationContainer by lazy {
        TvApplicationContainer(this)
    }
}
