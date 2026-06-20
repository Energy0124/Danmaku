package app.danmaku.server.windows

import app.danmaku.domain.LibraryCatalog
import app.danmaku.server.PublishedLibrary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal class HeadlessLibraryCatalogStore(
    private val file: Path,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    },
) {
    @Synchronized
    fun load(): HeadlessStoredLibrary? {
        if (!Files.isRegularFile(file)) return null
        return runCatching {
            val root = json.parseToJsonElement(Files.readString(file)).jsonObject
            val schemaVersion = root["schemaVersion"]?.jsonPrimitive?.intOrNull
                ?: return@runCatching null
            if (schemaVersion != SCHEMA_VERSION) {
                return@runCatching null
            }

            val catalog = json.decodeFromJsonElement<LibraryCatalog>(
                root["catalog"] ?: return@runCatching null,
            )
            HeadlessStoredLibrary(
                publishedLibrary = PublishedLibrary(
                    catalog = catalog,
                    filesById = root["filesById"].toPathMap(),
                    subtitleFilesById = root["subtitleFilesById"].toPathMap(),
                    posterFilesById = root["posterFilesById"].toPathMap(),
                ),
                savedAtEpochMs = root["savedAtEpochMs"]?.jsonPrimitive?.longOrNull
                    ?: catalog.indexedAtEpochMs,
            )
        }.getOrNull()
    }

    @Synchronized
    fun save(library: PublishedLibrary): HeadlessStoredLibrary {
        val stored = HeadlessStoredLibrary(
            publishedLibrary = library,
            savedAtEpochMs = System.currentTimeMillis(),
        )
        writeSnapshot(stored)
        return stored
    }

    private fun writeSnapshot(stored: HeadlessStoredLibrary) {
        val parent = file.parent
        if (parent != null) {
            Files.createDirectories(parent)
        }
        val temp = file.resolveSibling("${file.fileName}.tmp")
        Files.writeString(
            temp,
            json.encodeToString(JsonObject.serializer(), stored.toJsonObject()),
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

    private fun HeadlessStoredLibrary.toJsonObject(): JsonObject =
        buildJsonObject {
            put("schemaVersion", SCHEMA_VERSION)
            put("savedAtEpochMs", savedAtEpochMs)
            put("catalog", json.encodeToJsonElement(publishedLibrary.catalog))
            put("filesById", publishedLibrary.filesById.toJsonObject())
            put("subtitleFilesById", publishedLibrary.subtitleFilesById.toJsonObject())
            put("posterFilesById", publishedLibrary.posterFilesById.toJsonObject())
        }

    private fun Map<String, Path>.toJsonObject(): JsonObject =
        buildJsonObject {
            entries
                .sortedBy { it.key }
                .forEach { (id, path) ->
                    put(id, path.toAbsolutePath().normalize().toString())
                }
        }

    private fun JsonElement?.toPathMap(): Map<String, Path> =
        this
            ?.jsonObject
            ?.entries
            ?.sortedBy { it.key }
            ?.associateTo(linkedMapOf()) { (id, path) -> id to Path.of(path.jsonPrimitive.content) }
            ?: emptyMap()

    private companion object {
        const val SCHEMA_VERSION = 1
    }
}

internal data class HeadlessStoredLibrary(
    val publishedLibrary: PublishedLibrary,
    val savedAtEpochMs: Long,
)
