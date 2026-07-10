package by.w6.my1drive.utils

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Data class representing a single file entry in the archive metadata JSON.
 *
 * Stored on the OTG drive as .my1drive_db.json.
 * This is the source of truth; Room on the device is a cached copy.
 */
data class JsonEntry(
    val hash: String,
    val displayName: String,
    val mimeType: String,
    val size: Long,
    val dateModified: Long,
    val originalRelativePath: String?,
    val duration: Long? = null,
    val dateArchived: Long = System.currentTimeMillis() / 1000
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("hash", hash)
        put("displayName", displayName)
        put("mimeType", mimeType)
        put("size", size)
        put("dateModified", dateModified)
        put("originalRelativePath", originalRelativePath ?: JSONObject.NULL)
        put("duration", duration?.toDouble() ?: JSONObject.NULL)
        put("dateArchived", dateArchived)
    }

    companion object {
        private const val METADATA_FILE_NAME = ".my1drive_db.json"

        fun fromJson(json: JSONObject): JsonEntry? = try {
            JsonEntry(
                hash = json.getString("hash"),
                displayName = json.getString("displayName"),
                mimeType = json.getString("mimeType"),
                size = json.getLong("size"),
                dateModified = json.getLong("dateModified"),
                originalRelativePath = if (json.isNull("originalRelativePath")) null else json.getString("originalRelativePath"),
                duration = if (json.isNull("duration")) null else json.getLong("duration"),
                dateArchived = json.optLong("dateArchived", json.optLong("dateModified", System.currentTimeMillis() / 1000))
            )
        } catch (e: Exception) { null }
    }
}

/**
 * Manages the archive metadata file (.my1drive_db.json) on the OTG drive.
 *
 * The file on the OTG drive is the source of truth.
 * Every archive mutation (add, delete, restore) MUST:
 *   1. Write to JSON on the OTG drive
 *   2. Then update Room on the device
 */
class ArchiveMetadataStore(private val context: Context) {

    companion object {
        private const val METADATA_FILE_NAME = ".my1drive_db.json"
        private const val JSON_VERSION = 2
    }

    /**
     * Read archive metadata fields (UUID and name) from JSON without parsing file list.
     */
    fun readArchiveIdentity(file: DocumentFile): Pair<String, String>? {
        return try {
            context.contentResolver.openInputStream(file.uri)?.use { inputStream ->
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonString)
                val uuid = root.optString("archiveUuid")
                val name = root.optString("archiveName")
                if (uuid.isNotEmpty() && name.isNotEmpty()) {
                    Pair(uuid, name)
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Read all entries from the metadata file on the OTG drive.
     * Returns empty list if file doesn't exist, and null if it fails to read/parse.
     */
    suspend fun readMetadata(otgUri: Uri): List<JsonEntry> = withContext(Dispatchers.IO) {
        try {
            val dir = by.w6.my1drive.utils.OtgFolderResolver.getArchiveDir(context, otgUri, createIfNotExist = false) ?: return@withContext emptyList()
            val file = dir.findFile("my1drive_db.json") ?: dir.findFile(".my1drive_db.json") ?: return@withContext emptyList()

            val inputStream = context.contentResolver.openInputStream(file.uri) ?: return@withContext emptyList()
            val entries = mutableListOf<JsonEntry>()
            try {
                android.util.JsonReader(inputStream.bufferedReader()).use { reader ->
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "files" -> {
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    reader.beginObject()
                                    var hash = ""
                                    var displayName = ""
                                    var mimeType = ""
                                    var size = 0L
                                    var dateModified = 0L
                                    var originalRelativePath: String? = null
                                    var duration: Long? = null
                                    var dateArchived = 0L

                                    while (reader.hasNext()) {
                                        when (reader.nextName()) {
                                            "hash" -> hash = reader.nextString()
                                            "displayName" -> displayName = reader.nextString()
                                            "mimeType" -> mimeType = reader.nextString()
                                            "size" -> size = reader.nextLong()
                                            "dateModified" -> dateModified = reader.nextLong()
                                            "originalRelativePath" -> if (reader.peek() == android.util.JsonToken.NULL) { reader.nextNull() } else { originalRelativePath = reader.nextString() }
                                            "duration" -> if (reader.peek() == android.util.JsonToken.NULL) { reader.nextNull() } else { duration = reader.nextLong() }
                                            "dateArchived" -> dateArchived = reader.nextLong()
                                            else -> reader.skipValue()
                                        }
                                    }
                                    reader.endObject()
                                    
                                    if (dateArchived == 0L) dateArchived = dateModified
                                    if (hash.isNotEmpty() && displayName.isNotEmpty()) {
                                        entries.add(JsonEntry(hash, displayName, mimeType, size, dateModified, originalRelativePath, duration, dateArchived))
                                    }
                                }
                                reader.endArray()
                            }
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
            } catch (e: Exception) {
                by.w6.my1drive.utils.DebugLogBuffer.log("MetadataStore", "Invalid JSON content or read error: ${e.localizedMessage}. Returning read entries.")
            }
            entries
        } catch (e: Exception) {
            by.w6.my1drive.utils.DebugLogBuffer.log("MetadataStore", "readMetadata error: ${e.localizedMessage}. Returning empty entries list to self-heal.")
            emptyList()
        }
    }

    /**
     * Overwrite the entire metadata file on the OTG drive with given entries.
     */
    suspend fun writeMetadata(otgUri: Uri, entries: List<JsonEntry>) = withContext(Dispatchers.IO) {
        try {
            val dir = by.w6.my1drive.utils.OtgFolderResolver.getArchiveDir(context, otgUri, createIfNotExist = true) ?: return@withContext

            val file = dir.findFile("my1drive_db.json") ?: dir.findFile(".my1drive_db.json") ?: dir.createFile("application/json", "my1drive_db.json") ?: return@withContext

            val uuid = by.w6.my1drive.utils.OtgFolderResolver.extractVolumeId(otgUri) ?: otgUri.toString().hashCode().toString()
            val db = by.w6.my1drive.data.local.AppDatabase.getDatabase(context)
            val archive = db.archiveDao().getById(uuid)
            val archiveName = archive?.name ?: "USB-накопитель"

            context.contentResolver.openOutputStream(file.uri, "w")?.use { output ->
                android.util.JsonWriter(output.bufferedWriter()).use { writer ->
                    writer.setIndent("  ")
                    writer.beginObject()
                    writer.name("version").value(JSON_VERSION)
                    writer.name("archiveUuid").value(uuid)
                    writer.name("archiveName").value(archiveName)
                    writer.name("files").beginArray()
                    
                    for (entry in entries) {
                        writer.beginObject()
                        writer.name("hash").value(entry.hash)
                        writer.name("displayName").value(entry.displayName)
                        writer.name("mimeType").value(entry.mimeType)
                        writer.name("size").value(entry.size)
                        writer.name("dateModified").value(entry.dateModified)
                        
                        writer.name("originalRelativePath")
                        if (entry.originalRelativePath != null) writer.value(entry.originalRelativePath) else writer.nullValue()
                        
                        writer.name("duration")
                        if (entry.duration != null) writer.value(entry.duration) else writer.nullValue()
                        
                        writer.name("dateArchived").value(entry.dateArchived)
                        writer.endObject()
                    }
                    
                    writer.endArray()
                    writer.endObject()
                }
            }
        } catch (e: Exception) {
            by.w6.my1drive.utils.DebugLogBuffer.log("MetadataStore", "writeMetadata error: ${e.localizedMessage}")
        }
    }

    /**
     * Add a single entry to the metadata file on the OTG drive.
     * Reads existing entries, appends the new one, writes back.
     */
    suspend fun addEntry(otgUri: Uri, entry: JsonEntry) = withContext(Dispatchers.IO) {
        try {
            val entries = readMetadata(otgUri)?.toMutableList() ?: throw Exception("metadata_read_failed")
            // Replace if exists, else add
            val existingIndex = entries.indexOfFirst { it.hash == entry.hash }
            if (existingIndex >= 0) {
                entries[existingIndex] = entry
            } else {
                entries.add(entry)
            }
            writeMetadata(otgUri, entries)
        } catch (_: Exception) { }
    }

    /**
     * Add multiple entries to the metadata file at once.
     */
    suspend fun addEntries(otgUri: Uri, newEntries: List<JsonEntry>) = withContext(Dispatchers.IO) {
        try {
            val entries = readMetadata(otgUri)?.toMutableList() ?: throw Exception("metadata_read_failed")
            val existingHashes = entries.map { it.hash }.toHashSet()
            val toAdd = newEntries.filter { it.hash !in existingHashes }
            if (toAdd.isEmpty()) return@withContext
            entries.addAll(toAdd)
            writeMetadata(otgUri, entries)
        } catch (_: Exception) { }
    }

    /**
     * Remove a single entry from the metadata file by hash.
     */
    suspend fun removeEntry(otgUri: Uri, hash: String) = withContext(Dispatchers.IO) {
        try {
            val entries = readMetadata(otgUri)?.toMutableList() ?: throw Exception("metadata_read_failed")
            if (entries.removeAll { it.hash == hash }) {
                writeMetadata(otgUri, entries)
            }
        } catch (_: Exception) { }
    }

    /**
     * Check if metadata file exists on the OTG drive.
     */
    suspend fun metadataExists(otgUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = by.w6.my1drive.utils.OtgFolderResolver.getArchiveDir(context, otgUri, createIfNotExist = false) ?: return@withContext false
            dir.findFile("my1drive_db.json") != null || dir.findFile(".my1drive_db.json") != null
        } catch (_: Exception) { false }
    }
}
