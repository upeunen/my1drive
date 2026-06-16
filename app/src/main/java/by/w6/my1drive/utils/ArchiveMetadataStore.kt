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
    val duration: Long? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("hash", hash)
        put("displayName", displayName)
        put("mimeType", mimeType)
        put("size", size)
        put("dateModified", dateModified)
        put("originalRelativePath", originalRelativePath ?: JSONObject.NULL)
        put("duration", duration?.toDouble() ?: JSONObject.NULL)
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
                duration = if (json.isNull("duration")) null else json.getLong("duration")
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
        private const val JSON_VERSION = 1
    }

    /**
     * Read all entries from the metadata file on the OTG drive.
     * Returns empty list if file doesn't exist or is corrupt.
     */
    suspend fun readMetadata(otgUri: Uri): List<JsonEntry> = withContext(Dispatchers.IO) {
        try {
            val dir = DocumentFile.fromTreeUri(context, otgUri) ?: return@withContext emptyList()
            val file = dir.findFile(METADATA_FILE_NAME) ?: return@withContext emptyList()

            val inputStream = context.contentResolver.openInputStream(file.uri) ?: return@withContext emptyList()
            val jsonString = inputStream.bufferedReader().use { it.readText() }

            val root = JSONObject(jsonString)
            val version = root.optInt("version", 0)
            if (version != JSON_VERSION) return@withContext emptyList()

            val filesArray = root.optJSONArray("files") ?: return@withContext emptyList()
            val entries = mutableListOf<JsonEntry>()
            for (i in 0 until filesArray.length()) {
                val entry = JsonEntry.fromJson(filesArray.getJSONObject(i))
                if (entry != null) entries.add(entry)
            }
            entries
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Overwrite the entire metadata file on the OTG drive with given entries.
     */
    suspend fun writeMetadata(otgUri: Uri, entries: List<JsonEntry>) = withContext(Dispatchers.IO) {
        try {
            val dir = DocumentFile.fromTreeUri(context, otgUri) ?: return@withContext

            // Delete existing metadata file if present
            val existing = dir.findFile(METADATA_FILE_NAME)
            if (existing != null) existing.delete()

            val newFile = dir.createFile("application/json", METADATA_FILE_NAME) ?: return@withContext

            val filesArray = JSONArray()
            for (entry in entries) {
                filesArray.put(entry.toJson())
            }

            val root = JSONObject().apply {
                put("version", JSON_VERSION)
                put("files", filesArray)
            }

            context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
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
            val entries = readMetadata(otgUri).toMutableList()
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
            val entries = readMetadata(otgUri).toMutableList()
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
            val entries = readMetadata(otgUri).toMutableList()
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
            val dir = DocumentFile.fromTreeUri(context, otgUri) ?: return@withContext false
            dir.findFile(METADATA_FILE_NAME) != null
        } catch (_: Exception) { false }
    }
}
