package app.danmaku.desktop

import app.danmaku.provider.dandanplay.DandanplayConnection
import app.danmaku.provider.dandanplay.DandanplayDanmakuClient
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DandanplayLiveSmokeTest {
    @Test
    fun fetchesKnownEpisodeCommentsWithLocalCredentials() {
        if (System.getProperty("danmaku.dandanplay.liveSmoke") != "true") return

        val defaults = DandanplayLocalCredentialDefaults.load()
        assertNotNull(defaults?.appId, "local dandanplay AppId must be configured for the live smoke test")
        assertNotNull(defaults.appSecret, "local dandanplay AppSecret must be configured for the live smoke test")

        val connection = DandanplayConnection(
            baseUrl = defaults.baseUrl,
            appId = defaults.appId,
            appSecret = defaults.appSecret,
            authenticationMode = defaults.authenticationMode,
        )
        val comments = DandanplayDanmakuClient(connection).fetchComments(
            episodeId = KNOWN_EPISODE_ID,
            withRelated = true,
        )

        assertTrue(comments.isNotEmpty(), "known dandanplay episode should return parsed comments")
    }

    private companion object {
        const val KNOWN_EPISODE_ID = 123450001L
    }
}
