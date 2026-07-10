package app.danmaku.desktop

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import java.time.Duration as JavaDuration

class RustServerSidecarRuntimeTest {
    @Test
    fun binaryResolverUsesLaunchOverrideBeforeEnvironmentAndCargoTargets() {
        val temp = createTempDirectory("danmaku-rust-sidecar-binary")
        val launchBinary = temp.resolve("launch").resolve(executableName()).also { path ->
            path.parent.createDirectories()
            path.writeText("launch")
        }
        val envBinary = temp.resolve("env").resolve(executableName()).also { path ->
            path.parent.createDirectories()
            path.writeText("env")
        }
        val cargoBinary = temp.resolve("target").resolve("release").resolve(executableName()).also { path ->
            path.parent.createDirectories()
            path.writeText("cargo")
        }

        val resolution = RustServerBinaryResolver.resolve(
            launchOverride = launchBinary,
            environment = mapOf(
                DesktopLaunchOptions.RUST_SERVER_PATH_ENV to envBinary.toString(),
                "CARGO_TARGET_DIR" to temp.resolve("target").toString(),
            ),
            repositoryRoot = temp,
        )

        assertEquals(launchBinary.toAbsolutePath().normalize(), resolution.path)
        assertEquals(RustServerBinaryResolutionSource.OVERRIDE, resolution.source)
        assertTrue(Files.exists(cargoBinary))
    }

    @Test
    fun binaryResolverUsesEnvironmentOverrideBeforeCargoTarget() {
        val temp = createTempDirectory("danmaku-rust-sidecar-env")
        val envBinary = temp.resolve("env").resolve(executableName()).also { path ->
            path.parent.createDirectories()
            path.writeText("env")
        }
        temp.resolve("target").resolve("release").resolve(executableName()).also { path ->
            path.parent.createDirectories()
            path.writeText("cargo")
        }

        val resolution = RustServerBinaryResolver.resolve(
            launchOverride = null,
            environment = mapOf(
                DesktopLaunchOptions.RUST_SERVER_PATH_ENV to envBinary.toString(),
                "CARGO_TARGET_DIR" to temp.resolve("target").toString(),
            ),
            repositoryRoot = temp,
        )

        assertEquals(envBinary.toAbsolutePath().normalize(), resolution.path)
        assertEquals(RustServerBinaryResolutionSource.OVERRIDE, resolution.source)
    }

    @Test
    fun binaryResolverFallsBackToCargoTargetDirectory() {
        val temp = createTempDirectory("danmaku-rust-sidecar-cargo")
        val cargoBinary = temp.resolve("cargo-target").resolve("release").resolve(executableName()).also { path ->
            path.parent.createDirectories()
            path.writeText("cargo")
        }

        val resolution = RustServerBinaryResolver.resolve(
            launchOverride = null,
            environment = mapOf("CARGO_TARGET_DIR" to temp.resolve("cargo-target").toString()),
            repositoryRoot = temp,
        )

        assertEquals(cargoBinary.toAbsolutePath().normalize(), resolution.path)
        assertEquals(RustServerBinaryResolutionSource.CARGO_TARGET, resolution.source)
    }

    @Test
    fun binaryResolverFindsServerStagedInPackagedAppLayout() {
        val temp = createTempDirectory("danmaku-rust-sidecar-packaged")
        val packagedBinary = temp.resolve("app").resolve("server").resolve(executableName()).also { path ->
            path.parent.createDirectories()
            path.writeText("packaged")
        }

        val resolution = RustServerBinaryResolver.resolve(
            launchOverride = null,
            environment = emptyMap(),
            repositoryRoot = temp,
        )

        assertEquals(packagedBinary.toAbsolutePath().normalize(), resolution.path)
        assertEquals(RustServerBinaryResolutionSource.PACKAGED_APP, resolution.source)
    }

    @Test
    fun binaryResolverReportsTypedMissingBinary() {
        val error = assertFailsWith<RustServerSidecarException> {
            RustServerBinaryResolver.resolve(
                launchOverride = null,
                environment = emptyMap(),
                repositoryRoot = createTempDirectory("danmaku-rust-sidecar-missing"),
            )
        }

        assertEquals(RustServerSidecarFailureReason.BINARY_NOT_FOUND, error.reason)
        assertTrue(error.detail.contains("cargo build --release -p library-server"))
    }

    @Test
    fun portSelectorChoosesFreePortAndRejectsUnavailableExplicitPort() {
        val chosen = RustSidecarPortSelector.choosePort(null)
        assertTrue(chosen in 1..65_535)

        ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { occupied ->
            val error = assertFailsWith<RustServerSidecarException> {
                RustSidecarPortSelector.choosePort(occupied.localPort)
            }

            assertEquals(RustServerSidecarFailureReason.PORT_UNAVAILABLE, error.reason)
        }
    }

    @Test
    fun portSelectorRespectsAvailableExplicitPort() {
        val explicit = RustSidecarPortSelector.choosePort(null)

        assertEquals(explicit, RustSidecarPortSelector.choosePort(explicit))
    }

    @Test
    fun readinessPollerReturnsWhenStatusEndpointIsReady() {
        withStatusServer(statusCode = 200) { baseUrl ->
            RustServerReadinessPoller(
                timeout = JavaDuration.ofSeconds(1),
                interval = JavaDuration.ofMillis(10),
            ).awaitReady(baseUrl)
        }
    }

    @Test
    fun readinessPollerTimesOutWhenStatusEndpointIsUnavailable() {
        withStatusServer(statusCode = 503) { baseUrl ->
            val error = assertFailsWith<RustServerSidecarException> {
                RustServerReadinessPoller(
                    timeout = JavaDuration.ofMillis(40),
                    interval = JavaDuration.ofMillis(10),
                ).awaitReady(baseUrl)
            }

            assertEquals(RustServerSidecarFailureReason.READINESS_TIMEOUT, error.reason)
        }
    }

    @Test
    fun restartBackoffAllowsThreeCrashesInFiveMinutesThenStops() {
        val policy = RustSidecarRestartBackoffPolicy(
            maxRestarts = 3,
            windowMillis = JavaDuration.ofMinutes(5).toMillis(),
            baseDelay = JavaDuration.ofMillis(100),
        )

        assertEquals(100.milliseconds.inWholeMilliseconds, policy.recordCrashAndNextDelay(1_000)?.toMillis())
        assertEquals(200, policy.recordCrashAndNextDelay(2_000)?.toMillis())
        assertEquals(400, policy.recordCrashAndNextDelay(3_000)?.toMillis())
        assertEquals(null, policy.recordCrashAndNextDelay(4_000))
        assertNotEquals(null, policy.recordCrashAndNextDelay(1_000 + JavaDuration.ofMinutes(6).toMillis()))
    }

    @Test
    fun readsPairingTokenFromServerSettingsJson() {
        val temp = createTempDirectory("danmaku-rust-sidecar-settings")
        temp.resolve("server-settings.json").writeText(
            """
            {
              "schemaVersion": 1,
              "pairingToken": "123456"
            }
            """.trimIndent(),
        )

        assertEquals("123456", RustServerSidecarRuntime.readPairingToken(temp))
    }

    @Test
    fun launchCommandPassesDataDirPortRootsAndOptionalSettings() {
        val launch = RustServerSidecarLaunch(
            executablePath = Path.of("target/release/library-server.exe"),
            dataDirectory = Path.of("S:/Data/Danmaku/rust-server"),
            port = 19081,
            libraryRoots = listOf(Path.of("W:/Anime"), Path.of("D:/AniRss")),
            pairingToken = "123456",
            webAssetsRoot = Path.of("apps/web-ui/dist"),
            logPath = Path.of("S:/Data/Danmaku/rust-server/sidecar.log"),
        )

        assertEquals(
            listOf(
                Path.of("target/release/library-server.exe").toAbsolutePath().normalize().toString(),
                "--data-dir",
                Path.of("S:/Data/Danmaku/rust-server").toAbsolutePath().normalize().toString(),
                "--port",
                "19081",
                "--pairing-token",
                "123456",
                "--web-assets-dir",
                Path.of("apps/web-ui/dist").toAbsolutePath().normalize().toString(),
                "--root",
                Path.of("W:/Anime").toAbsolutePath().normalize().toString(),
                "--root",
                Path.of("D:/AniRss").toAbsolutePath().normalize().toString(),
            ),
            launch.command(),
        )
    }

    private fun withStatusServer(
        statusCode: Int,
        block: (String) -> Unit,
    ) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/server/status") { exchange ->
            val body = """{"apiVersion":1}""".toByteArray()
            exchange.sendResponseHeaders(statusCode, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}")
        } finally {
            server.stop(0)
        }
    }

    private fun executableName(): String =
        if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            "library-server.exe"
        } else {
            "library-server"
        }
}
