package app.danmaku.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    internal val container: TvApplicationContainer
        get() = (application as DanmakuTvApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (
            savedInstanceState == null &&
            BuildConfig.TV_QA_FIXTURES_ENABLED &&
            intent.getBooleanExtra(TV_QA_FIXTURE_EXTRA, false)
        ) {
            container.installQaFixture()
        }
        setContent {
            TvApp(container)
        }
    }
}
