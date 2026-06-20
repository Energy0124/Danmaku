package app.danmaku.host

import kotlin.test.Test
import kotlin.test.assertFailsWith

class LibraryHostServiceTest {
    @Test
    fun validatesHostConfig() {
        assertFailsWith<IllegalArgumentException> {
            LibraryHostConfig(dataDirectory = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            LibraryHostConfig(dataDirectory = "data", port = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            LibraryHostConfig(dataDirectory = "data", libraryRoots = listOf(" "))
        }
        assertFailsWith<IllegalArgumentException> {
            LibraryHostConfig(dataDirectory = "data", pairingToken = "")
        }
    }

    @Test
    fun validatesRuntimeStatus() {
        assertFailsWith<IllegalArgumentException> {
            LibraryHostRuntimeStatus(
                mode = LibraryHostMode.EMBEDDED_DESKTOP,
                baseUrls = listOf(" "),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            LibraryHostRuntimeStatus(
                mode = LibraryHostMode.HEADLESS_SERVER,
                baseUrls = emptyList(),
                itemCount = -1,
            )
        }
    }
}
