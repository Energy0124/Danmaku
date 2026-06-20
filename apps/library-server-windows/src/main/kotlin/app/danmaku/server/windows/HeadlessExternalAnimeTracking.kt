package app.danmaku.server.windows

import app.danmaku.domain.ExternalAnimeId
import app.danmaku.domain.ExternalAnimeListEntry
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.ExternalAnimeTrackingUpdate
import app.danmaku.domain.displayName
import app.danmaku.provider.external.BangumiTrackingClient
import app.danmaku.provider.external.BangumiTrackingConnection
import app.danmaku.provider.external.ExternalAnimeTrackingClient
import app.danmaku.provider.external.MyAnimeListTrackingClient
import app.danmaku.provider.external.MyAnimeListTrackingConnection
import java.net.URI

internal interface HeadlessExternalAnimeTrackingService {
    fun fetchListEntry(animeId: ExternalAnimeId): ExternalAnimeListEntry?

    fun updateListEntry(update: ExternalAnimeTrackingUpdate): ExternalAnimeListEntry
}

internal class DefaultHeadlessExternalAnimeTrackingService(
    clients: List<ExternalAnimeTrackingClient>,
) : HeadlessExternalAnimeTrackingService {
    private val clientsByProvider = clients.associateBy(ExternalAnimeTrackingClient::provider)

    init {
        require(clientsByProvider.size == clients.size) {
            "external anime tracking clients must be unique by provider"
        }
    }

    override fun fetchListEntry(animeId: ExternalAnimeId): ExternalAnimeListEntry? =
        clientFor(animeId.provider).fetchListEntry(animeId)

    override fun updateListEntry(update: ExternalAnimeTrackingUpdate): ExternalAnimeListEntry =
        clientFor(update.animeId.provider).updateListEntry(update)

    private fun clientFor(provider: ExternalAnimeProvider): ExternalAnimeTrackingClient =
        clientsByProvider[provider]
            ?: throw HeadlessExternalAnimeTrackingUnavailableException(provider)
}

internal fun HeadlessServerSettings.toExternalAnimeTrackingService(): HeadlessExternalAnimeTrackingService =
    DefaultHeadlessExternalAnimeTrackingService(
        buildList {
            externalAnime.myAnimeListAccessToken?.let { accessToken ->
                add(
                    MyAnimeListTrackingClient(
                        MyAnimeListTrackingConnection(accessToken = accessToken),
                    ),
                )
            }
            externalAnime.bangumiAccessToken?.let { accessToken ->
                add(
                    BangumiTrackingClient(
                        BangumiTrackingConnection(
                            accessToken = accessToken,
                            baseUri = URI(externalAnime.bangumiBaseUrl),
                            userAgent = externalAnime.bangumiUserAgent,
                        ),
                    ),
                )
            }
        },
    )

internal class HeadlessExternalAnimeTrackingUnavailableException(
    provider: ExternalAnimeProvider,
) : RuntimeException("${provider.displayName} list sync credentials are not configured")
