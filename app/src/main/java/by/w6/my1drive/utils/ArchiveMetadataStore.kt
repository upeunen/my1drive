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
            val jsonString = inputStream.bufferedReader().use { it.readText() }

            if (jsonString.trim().isEmpty()) {
                by.w6.my1drive.utils.DebugLogBuffer.log("MetadataStore", "Metadata file is empty, returning empty entries list.")
                return@withContext emptyList()
            }

            val root = try {
                JSONObject(jsonString)
            } catch (e: Exception) {
                by.w6.my1drive.utils.DebugLogBuffer.log("MetadataStore", "Invalid JSON content: ${e.localizedMessage}. Returning empty entries list to self-heal.")
                return@withContext emptyList()
            }

            val version = root.optInt("version", 0)
            if (version != 1 && version != 2) {
                by.w6.my1drive.utils.DebugLogBuffer.log("MetadataStore", "Unsupported version $version, returning empty entries list.")
                return@withContext emptyList()
            }

            val filesArray = root.optJSONArray("files") ?: return@withContext emptyList()
            val entries = mutableListOf<JsonEntry>()
            for (i in 0 until filesArray.length()) {
                val entry = JsonEntry.fromJson(filesArray.getJSONObject(i))
                if (entry != null) entries.add(entry)
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

            val filesArray = JSONArray()
            for (entry in entries) {
                filesArray.put(entry.toJson())
            }

            val uuid = by.w6.my1drive.utils.OtgFolderResolver.extractVolumeId(otgUri) ?: otgUri.toString().hashCode().toString()
            val db = by.w6.my1drive.data.local.AppDatabase.getDatabase(context)
            val archive = db.archiveDao().getById(uuid)
            val archiveName = archive?.name ?: "USB-накопитель"

            val root = JSONObject().apply {
                put("version", JSON_VERSION)
                put("archiveUuid", uuid)
                put("archiveName", archiveName)
                put("files", filesArray)
            }

            context.contentResolver.openOutputStream(file.uri, "w")?.use { output ->
                output.write(root.toString(2).toByteArray(Charsets.UTF_8))
            }
        } catch (_: Exception) { }
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
