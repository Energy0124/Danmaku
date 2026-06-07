package app.danmaku.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExternalAnimeTrackingTest {
    @Test
    fun buildsProviderLinks() {
        assertEquals(
            "https://myanimelist.net/anime/1",
            ExternalAnimeId(ExternalAnimeProvider.MY_ANIME_LIST, 1).webUrl,
        )
        assertEquals(
            "https://bangumi.tv/subject/2",
            ExternalAnimeId(ExternalAnimeProvider.BANGUMI, 2).webUrl,
        )
    }

    @Test
    fun exposesMultilingualAndAlternateNamesWithoutDuplicates() {
        val titles = ExternalAnimeTitleSet(
            primary = "葬送のフリーレン",
            chinese = "葬送的芙莉莲",
            english = "Frieren: Beyond Journey's End",
            japanese = "葬送のフリーレン",
            alternateNames = listOf("Frieren", "葬送のフリーレン"),
        )

        assertEquals(
            listOf("葬送のフリーレン", "葬送的芙莉莲", "Frieren: Beyond Journey's End", "Frieren"),
            titles.displayNames,
        )
    }

    @Test
    fun ranksCandidatesByAlternateTitleEpisodeCountAndYear() {
        val query = ExternalAnimeMatchQuery(
            title = "Frieren Beyond Journeys End",
            episodeCount = 28,
            startYear = 2023,
        )
        val candidates = listOf(
            animeInfo(
                id = ExternalAnimeId(ExternalAnimeProvider.BANGUMI, 999),
                primary = "Wrong Show",
                episodeCount = 28,
                startYear = 2023,
            ),
            animeInfo(
                id = ExternalAnimeId(ExternalAnimeProvider.MY_ANIME_LIST, 52991),
                primary = "Sousou no Frieren",
                english = "Frieren: Beyond Journey's End",
                alternateNames = listOf("Frieren Beyond Journey's End"),
                episodeCount = 28,
                startYear = 2023,
            ),
            animeInfo(
                id = ExternalAnimeId(ExternalAnimeProvider.BANGUMI, 400602),
                primary = "葬送のフリーレン",
                chinese = "葬送的芙莉莲",
                english = "Frieren",
                episodeCount = 28,
                startYear = 2023,
            ),
        )

        val ranked = rankExternalAnimeMatches(query, candidates)

        assertEquals(3, ranked.size)
        assertEquals(52991, ranked.first().anime.id.value)
        assertEquals("Frieren: Beyond Journey's End", ranked.first().matchedTitle)
        assertEquals(1.0, ranked.first().confidence)
    }

    @Test
    fun validatesOptionalTrackingAndRatingUpdates() {
        val animeId = ExternalAnimeId(ExternalAnimeProvider.MY_ANIME_LIST, 1)

        ExternalAnimeTrackingUpdate(
            animeId = animeId,
            status = ExternalAnimeListStatus.WATCHING,
            watchedEpisodes = 3,
            score = 8,
        )

        ExternalAnimeTrackingUpdate(
            animeId = animeId,
            status = ExternalAnimeListStatus.WATCHING,
            trackingEnabled = false,
            ratingEnabled = false,
        )

        assertFailsWith<IllegalArgumentException> {
            ExternalAnimeTrackingUpdate(animeId = animeId, watchedEpisodes = 1, trackingEnabled = false)
        }
        assertFailsWith<IllegalArgumentException> {
            ExternalAnimeTrackingUpdate(animeId = animeId, score = 11)
        }
    }

    @Test
    fun validatesManualAndAutomaticMappings() {
        val mapping = ExternalAnimeMapping(
            localSeriesId = "frieren",
            animeId = ExternalAnimeId(ExternalAnimeProvider.BANGUMI, 400602),
            source = ExternalAnimeMappingSource.MANUAL,
            confidence = 1.0,
            mappedAtEpochMs = 123,
        )

        assertEquals(ExternalAnimeMappingSource.MANUAL, mapping.source)
        assertFailsWith<IllegalArgumentException> {
            mapping.copy(confidence = 1.1)
        }
    }

    private fun animeInfo(
        id: ExternalAnimeId,
        primary: String,
        chinese: String? = null,
        english: String? = null,
        japanese: String? = null,
        alternateNames: List<String> = emptyList(),
        episodeCount: Int? = null,
        startYear: Int? = null,
    ): ExternalAnimeInfo =
        ExternalAnimeInfo(
            id = id,
            titles = ExternalAnimeTitleSet(
                primary = primary,
                chinese = chinese,
                english = english,
                japanese = japanese,
                alternateNames = alternateNames,
            ),
            episodeCount = episodeCount,
            startYear = startYear,
        )
}
