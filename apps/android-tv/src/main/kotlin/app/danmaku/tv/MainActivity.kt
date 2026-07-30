package app.danmaku.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    private val container by lazy {
        TvApplicationContainer(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (
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
