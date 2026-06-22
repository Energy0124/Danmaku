package app.danmaku.provider.external

import app.danmaku.domain.ExternalAnimeId
import app.danmaku.domain.ExternalAnimeListEntry
import app.danmaku.domain.ExternalAnimeProvider
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.test.Test
import kotlin.test.assertNotNull

class LiveExternalAnimeReadbackQaTest {
    @Test
    fun readsConfiguredExternalListEntryWithoutWriting() {
        val config = LiveExternalAnimeReadbackQaConfig.fromEnvironment() ?: return
        val result = runCatching { config.createClient().fetchListEntry(config.animeId) }

        config.reportPath.parent?.createDirectories()
        Files.writeString(
            config.reportPath,
            liveExternalAnimeReadbackReport(
                config = config,
                result = result,
                generatedAt = Instant.now(),
            ),
        )

        result.exceptionOrNull()?.let { failure ->
            throw AssertionError(
                "Live external sync readback failed for ${config.animeId.provider.name}:${config.animeId.value}",
                failure,
            )
        }
        if (config.expectEntry) {
            assertNotNull(
                result.getOrNull(),
                "Expected ${config.animeId.provider.name}:${config.animeId.value} to exist in the provider list. " +
                    "Set DANMAKU_LIVE_EXTERNAL_SYNC_QA_EXPECT_ENTRY=false for absent-entry smoke checks.",
            )
        }
    }
}

private data class LiveExternalAnimeReadbackQaConfig(
    val animeId: ExternalAnimeId,
    val accessToken: String,
    val bangumiBaseUrl: String,
    val bangumiUserAgent: String,
    val reportPath: Path,
    val expectEntry: Boolean,
) {
    fun createClient(): ExternalAnimeTrackingClient =
        when (animeId.provider) {
            ExternalAnimeProvider.MY_ANIME_LIST -> MyAnimeListTrackingClient(
                MyAnimeListTrackingConnection(accessToken = accessToken),
            )

            ExternalAnimeProvider.BANGUMI -> BangumiTrackingClient(
                BangumiTrackingConnection(
                    accessToken = accessToken,
                    baseUri = URI(bangumiBaseUrl),
                    userAgent = bangumiUserAgent,
                ),
            )

            ExternalAnimeProvider.DANDANPLAY -> error("Dandanplay does not support external list readback QA")
        }

    companion object {
        fun fromEnvironment(): LiveExternalAnimeReadbackQaConfig? {
            val provider = env("DANMAKU_LIVE_EXTERNAL_SYNC_QA_PROVIDER")
                ?.let(::parseProvider)
                ?: return null
            val animeIdValue = env("DANMAKU_LIVE_EXTERNAL_SYNC_QA_ANIME_ID")
                ?.toLongOrNull()
                ?: error("Set DANMAKU_LIVE_EXTERNAL_SYNC_QA_ANIME_ID to a positive provider anime ID")
            require(animeIdValue > 0) {
                "DANMAKU_LIVE_EXTERNAL_SYNC_QA_ANIME_ID must be positive"
            }
            val accessToken = when (provider) {
                ExternalAnimeProvider.MY_ANIME_LIST -> env("DANMAKU_MYANIMELIST_ACCESS_TOKEN")
                ExternalAnimeProvider.BANGUMI -> env("DANMAKU_BANGUMI_ACCESS_TOKEN")
                ExternalAnimeProvider.DANDANPLAY -> null
            } ?: error("Set a provider access token env var before running live external sync QA")
            val reportPath = env("DANMAKU_LIVE_EXTERNAL_SYNC_QA_REPORT")
                ?.let(Path::of)
                ?: Path.of("build", "qa", "live-external-sync", "readback-qa.md")
            return LiveExternalAnimeReadbackQaConfig(
                animeId = ExternalAnimeId(provider, animeIdValue),
                accessToken = accessToken,
                bangumiBaseUrl = env("DANMAKU_BANGUMI_BASE_URL") ?: "https://api.bgm.tv/",
                bangumiUserAgent = env("DANMAKU_BANGUMI_USER_AGENT")
                    ?: "Danmaku/0.1 (https://github.com/Energy0124/Danmaku)",
                reportPath = reportPath,
                expectEntry = parseBoolean(
                    env("DANMAKU_LIVE_EXTERNAL_SYNC_QA_EXPECT_ENTRY"),
                    defaultValue = true,
                ),
            )
        }

        private fun env(name: String): String? =
            System.getenv(name)?.trim()?.takeIf(String::isNotEmpty)
    }
}

private fun parseProvider(value: String): ExternalAnimeProvider =
    when (value.trim().uppercase().replace("-", "_")) {
        "MAL", "MYANIMELIST", "MY_ANIME_LIST" -> ExternalAnimeProvider.MY_ANIME_LIST
        "BGM", "BANGUMI" -> ExternalAnimeProvider.BANGUMI
        else -> error("Unsupported live external sync QA provider: $value")
    }

private fun parseBoolean(
    value: String?,
    defaultValue: Boolean,
): Boolean =
    when (value?.trim()?.lowercase()) {
        null -> defaultValue
        "true", "1", "yes", "y" -> true
        "false", "0", "no", "n" -> false
        else -> error("Invalid boolean value for live external sync QA: $value")
    }

private fun liveExternalAnimeReadbackReport(
    config: LiveExternalAnimeReadbackQaConfig,
    result: Result<ExternalAnimeListEntry?>,
    generatedAt: Instant,
): String {
    val failure = result.exceptionOrNull()
    val entry = result.getOrNull()
    return buildString {
        appendLine("# Live External Sync Readback QA")
        appendLine()
        appendLine("- Generated at: `$generatedAt`")
        appendLine("- Provider: `${config.animeId.provider.name}`")
        appendLine("- Anime ID: `${config.animeId.value}`")
        appendLine("- Provider URL: ${config.animeId.webUrl}")
        appendLine("- Expected entry: `${config.expectEntry}`")
        appendLine("- Report path: `${config.reportPath}`")
        appendLine("- Mode: `read-only`")
        appendLine()
        appendLine("## Result")
        appendLine()
        when {
            failure != null -> {
                appendLine("- Request result: `FAIL`")
                appendLine("- Error type: `${failure.javaClass.name}`")
                appendLine("- Error message: `${failure.message ?: "unknown"}`")
            }

            entry == null -> {
                appendLine("- Request result: `PASS`")
                appendLine("- Entry found: `false`")
                appendLine("- Status: `missing from provider list or provider omitted list status`")
            }

            else -> {
                appendLine("- Request result: `PASS`")
                appendLine("- Entry found: `true`")
                appendLine("- Status: `${entry.status?.name ?: "unknown"}`")
                appendLine("- Watched episodes: `${entry.watchedEpisodes?.toString() ?: "unknown"}`")
                appendLine("- Score: `${entry.score?.toString() ?: "unknown"}`")
                appendLine("- Updated at epoch ms: `${entry.updatedAtEpochMs?.toString() ?: "unknown"}`")
            }
        }
        appendLine()
        appendLine("## Safety")
        appendLine()
        appendLine("This harness only called `fetchListEntry`; it did not send provider update requests.")
    }
}