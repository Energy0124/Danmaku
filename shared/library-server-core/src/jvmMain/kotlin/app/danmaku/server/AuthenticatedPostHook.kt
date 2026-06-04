package app.danmaku.server

import java.security.MessageDigest

class AuthenticatedPostHook(
    val path: String,
    token: String,
    private val onAccepted: () -> Unit,
) {
    private val tokenBytes = token.toByteArray()

    init {
        require(path.startsWith("/api/hooks/")) {
            "authenticated hook path must start with /api/hooks/"
        }
        require('?' !in path && '#' !in path) {
            "authenticated hook path must not include a query or fragment"
        }
        require(token.length >= MINIMUM_TOKEN_LENGTH) {
            "authenticated hook token must contain at least $MINIMUM_TOKEN_LENGTH characters"
        }
    }

    internal fun isAuthorized(suppliedToken: String?): Boolean =
        suppliedToken != null && MessageDigest.isEqual(
            tokenBytes,
            suppliedToken.toByteArray(),
        )

    internal fun accept() {
        onAccepted()
    }

    override fun toString(): String =
        "AuthenticatedPostHook(path=$path, token=<redacted>)"

    private companion object {
        const val MINIMUM_TOKEN_LENGTH = 16
    }
}
