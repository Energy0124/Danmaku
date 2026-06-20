package app.danmaku.server.windows

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.SecureRandom

internal class HeadlessServerSettingsStore(
    private val file: Path,
    private val random: SecureRandom = SecureRandom(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    },
) {
    @Synchronized
    fun loadOrCreate(explicitPairingToken: String?): HeadlessServerSettings {
        val token = explicitPairingToken
            ?: load()?.pairingToken
            ?: random.nextPairingToken()
        val settings = HeadlessServerSettings(pairingToken = token)
        writeSnapshot(settings)
        return settings
    }

    private fun load(): HeadlessServerSettings? {
        if (!Files.isRegularFile(file)) return null
        return runCatching {
            val root = json.parseToJsonElement(Files.readString(file)).jsonObject
            val schemaVersion = root["schemaVersion"]?.jsonPrimitive?.intOrNull
                ?: return@runCatching null
            if (schemaVersion != SCHEMA_VERSION) {
                return@runCatching null
            }
            val token = root["pairingToken"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.takeIf(String::isNotBlank)
                ?: return@runCatching null
            HeadlessServerSettings(pairingToken = token)
        }.getOrNull()
    }

    private fun writeSnapshot(settings: HeadlessServerSettings) {
        val parent = file.parent
        if (parent != null) {
            Files.createDirectories(parent)
        }
        val temp = file.resolveSibling("${file.fileName}.tmp")
        Files.writeString(
            temp,
            json.encodeToString(JsonObject.serializer(), settings.toJsonObject()),
        )
        runCatching {
            Files.move(
                temp,
                file,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrElse { error ->
            if (error is AtomicMoveNotSupportedException) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
            } else {
                throw error
            }
        }
    }

    private fun HeadlessServerSettings.toJsonObject(): JsonObject =
        buildJsonObject {
            put("schemaVersion", SCHEMA_VERSION)
            put("pairingToken", pairingToken)
        }

    private fun SecureRandom.nextPairingToken(): String =
        nextInt(1_000_000).toString().padStart(6, '0')

    private companion object {
        const val SCHEMA_VERSION = 1
    }
}

internal data class HeadlessServerSettings(
    val pairingToken: String,
) {
    init {
        require(pairingToken.isNotBlank()) { "pairingToken must not be blank" }
    }
}
