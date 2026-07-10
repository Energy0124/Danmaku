package app.danmaku.desktop

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal const val MY_ANIME_LIST_OAUTH_CALLBACK_PATH = "/api/oauth/myanimelist/callback"

internal class DesktopMyAnimeListOAuthCallbackRuntime private constructor(
    val baseUrl: String,
    private val server: HttpServer,
    private val executor: ExecutorService,
) : AutoCloseable {
    override fun close() {
        server.stop(0)
        executor.shutdownNow()
    }

    companion object {
        fun start(
            oauthService: MyAnimeListOAuthService,
            onSettingsUpdated: (ExternalAnimeProviderSettings) -> Unit,
            onDiagnostic: (String) -> Unit,
        ): DesktopMyAnimeListOAuthCallbackRuntime {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            val executor = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "danmaku-mal-oauth-callback").apply {
                    isDaemon = true
                }
            }
            server.executor = executor
            server.createContext(MY_ANIME_LIST_OAUTH_CALLBACK_PATH) { exchange ->
                if (exchange.requestMethod != "GET") {
                    exchange.sendResponseHeaders(405, -1)
                    exchange.close()
                    return@createContext
                }
                runCatching {
                    oauthService.completeAuthorization(exchange.requestURI.rawQuery.toQueryParameters())
                }.fold(
                    onSuccess = { settings ->
                        onSettingsUpdated(settings)
                        onDiagnostic("MyAnimeList OAuth authorization complete")
                        exchange.sendHtml(
                            status = 200,
                            body = "<!doctype html><title>Danmaku</title><h1>MyAnimeList connected</h1>" +
                                "<p>You can close this tab and return to Danmaku.</p>",
                        )
                    },
                    onFailure = { error ->
                        val message = error.message ?: "Unknown error"
                        onDiagnostic("MyAnimeList OAuth authorization failed: $message")
                        exchange.sendHtml(
                            status = 400,
                            body = "<!doctype html><title>Danmaku</title><h1>MyAnimeList authorization failed</h1>" +
                                "<p>${message.escapeHtml()}</p>",
                        )
                    },
                )
            }
            server.start()
            return DesktopMyAnimeListOAuthCallbackRuntime(
                baseUrl = "http://127.0.0.1:${server.address.port}",
                server = server,
                executor = executor,
            )
        }
    }
}

private fun String?.toQueryParameters(): Map<String, String> =
    this
        ?.split('&')
        ?.filter(String::isNotBlank)
        ?.associate { part ->
            val (name, value) = part.split('=', limit = 2).let { pieces ->
                pieces.first() to pieces.getOrElse(1) { "" }
            }
            URLDecoder.decode(name, StandardCharsets.UTF_8) to
                URLDecoder.decode(value, StandardCharsets.UTF_8)
        }
        .orEmpty()

private fun HttpExchange.sendHtml(status: Int, body: String) {
    val bytes = body.toByteArray(StandardCharsets.UTF_8)
    responseHeaders["Content-Type"] = listOf("text/html; charset=utf-8")
    sendResponseHeaders(status, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
    close()
}
