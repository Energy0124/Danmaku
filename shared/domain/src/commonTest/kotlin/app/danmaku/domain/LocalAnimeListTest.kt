package app.danmaku.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LocalAnimeListTest {
    @Test
    fun validatesLocalAnimeListEntries() {
        val entry = LocalAnimeListEntry(
            localSeriesId = "frieren",
            status = LocalAnimeListStatus.WATCHING,
            score = 9,
            notes = "Great rewatch with friends",
            updatedAtEpochMs = 123,
        )

        assertEquals("frieren", entry.localSeriesId)
        assertFailsWith<IllegalArgumentException> {
            entry.copy(localSeriesId = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            entry.copy(score = 11)
        }
        assertFailsWith<IllegalArgumentException> {
            entry.copy(notes = "")
        }
        assertFailsWith<IllegalArgumentException> {
            entry.copy(updatedAtEpochMs = -1)
        }
    }
}
