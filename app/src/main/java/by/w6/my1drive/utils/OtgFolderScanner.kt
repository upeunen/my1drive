package by.w6.my1drive.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import by.w6.my1drive.data.local.AppDatabase
import by.w6.my1drive.data.local.ArchiveEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

data class DiscoveredFolder(
    val name: String,
    val relativePath: String,
    val uri: Uri
)

object OtgFolderScanner {

    private val BLACKLIST_NAMES = setOf(
        "android",
        "lost.dir",
        "\$recycle.bin",
        "system volume information",
        ".thumbnails",
        ".trashes",
        "windows",
        "appdata",
        "node_modules",
        ".my1drive_cache"
    )

    private val MEDIA_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "heic", "heif", "webp", "gif", "bmp",
        "mp4", "mkv", "mov", "avi", "3gp", "webm"
    )

    /**
     * Scans the given root OTG drive up to maxDepth (default 2).
     * Returns folders containing photos/videos.
     */
    suspend fun scanMediaFolders(
        context: Context,
        rootUri: Uri,
        knownFolderPaths: Set<String>,
        maxDepth: Int = 2
    ): List<DiscoveredFolder> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DiscoveredFolder>()
        try {
            val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext emptyList()
            if (!rootDoc.exists() || !rootDoc.canRead()) return@withContext emptyList()

            // Level 1: direct subdirectories of root
            val level1Dirs = OtgFolderResolver.fastListDirectSubdirs(context, rootDoc)

            for (dir1 in level1Dirs) {
                val dir1Name = dir1.name ?: continue
                val cleanDir1 = dir1Name.lowercase().trim()
                if (cleanDir1.startsWith(".") || cleanDir1 in BLACKLIST_NAMES) continue

                val relPath1 = dir1Name
                val hasMedia1 = checkFolderHasMedia(context, dir1.uri)
                if (hasMedia1 && !isAlreadyKnown(relPath1, knownFolderPaths)) {
                    results.add(
                        DiscoveredFolder(
                            name = dir1Name,
                            relativePath = relPath1,
                            uri = dir1.uri
                        )
                    )
                }

                if (maxDepth >= 2) {
                    scanSubdirectories(
                        context = context,
                        parentDoc = dir1,
                        parentRelPath = relPath1,
                        currentDepth = 2,
                        maxDepth = maxDepth,
                        knownFolderPaths = knownFolderPaths,
                        results = results
                    )
                }
            }
        } catch (e: Exception) {
            DebugLogBuffer.log("OtgFolderScanner", "scanMediaFolders error: ${e.message}")
        }
        results
    }

    private fun scanSubdirectories(
        context: Context,
        parentDoc: DocumentFile,
        parentRelPath: String,
        currentDepth: Int,
        maxDepth: Int,
        knownFolderPaths: Set<String>,
        results: MutableList<DiscoveredFolder>
    ) {
        val subDirs = OtgFolderResolver.fastListDirectSubdirs(context, parentDoc)
        for (sub in subDirs) {
            val subName = sub.name ?: continue
            val cleanSub = subName.lowercase().trim()
            if (cleanSub.startsWith(".") || cleanSub in BLACKLIST_NAMES) continue

            val relPath = "$parentRelPath/$subName"
            val hasMedia = checkFolderHasMedia(context, sub.uri)
            if (hasMedia && !isAlreadyKnown(relPath, knownFolderPaths)) {
                results.add(
                    DiscoveredFolder(
                        name = subName,
                        relativePath = relPath,
                        uri = sub.uri
                    )
                )
            }

            if (currentDepth < maxDepth) {
                scanSubdirectories(
                    context = context,
                    parentDoc = sub,
                    parentRelPath = relPath,
                    currentDepth = currentDepth + 1,
                    maxDepth = maxDepth,
                    knownFolderPaths = knownFolderPaths,
                    results = results
                )
            }
        }
    }

    private fun checkFolderHasMedia(context: Context, folderUri: Uri): Boolean {
        try {
            val docId = try {
                DocumentsContract.getDocumentId(folderUri)
            } catch (_: Exception) {
                DocumentsContract.getTreeDocumentId(folderUri)
            }
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, docId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            )
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                var checkedCount = 0
                while (cursor.moveToNext()) {
                    val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) else null
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null

                    val isMedia = (mime != null && (mime.startsWith("image/") || mime.startsWith("video/"))) ||
                            (name != null && MEDIA_EXTENSIONS.contains(name.substringAfterLast('.', "").lowercase()))
                    if (isMedia) {
                        return true
                    }
                    checkedCount++
                    if (checkedCount > 50) {
                        break
                    }
                }
            }
        } catch (_: Exception) {}
        return false
    }

    private fun isAlreadyKnown(relPath: String, knownPaths: Set<String>): Boolean {
        val normalized = relPath.trim('/', '\\').lowercase()
        return knownPaths.any { it.trim('/', '\\').lowercase() == normalized }
    }

    /**
     * Registers a discovered folder as an archive in Room, writes my1drive_db.json into the folder,
     * and updates the global index (My1drive/my1drive_index.json) on the drive.
     */
    suspend fun registerDiscoveredFolderAsArchive(
        context: Context,
        rootUri: Uri,
        folder: DiscoveredFolder
    ): ArchiveEntity = withContext(Dispatchers.IO) {
        val uuid = UUID.randomUUID().toString()
        val archiveName = folder.name
        val relPath = folder.relativePath
        val now = System.currentTimeMillis()

        // 1. Create/update my1drive_db.json inside the folder
        try {
            val folderDoc = DocumentFile.fromTreeUri(context, folder.uri) ?: DocumentFile.fromSingleUri(context, folder.uri)
            if (folderDoc != null && folderDoc.exists()) {
                var metaFile = folderDoc.findFile("my1drive_db.json")
                if (metaFile == null || !metaFile.exists()) {
                    metaFile = folderDoc.createFile("application/json", "my1drive_db.json")
                }
                if (metaFile != null) {
                    val rootJson = JSONObject().apply {
                        put("version", 2)
                        put("archiveUuid", uuid)
                        put("archiveName", archiveName)
                        put("folderPath", relPath)
                        put("dateCreated", now)
                        put("files", org.json.JSONArray())
                    }
                    context.contentResolver.openOutputStream(metaFile.uri, "w")?.use { out ->
                        out.bufferedWriter().use { it.write(rootJson.toString(2)) }
                    }
                }
            }
        } catch (e: Exception) {
            DebugLogBuffer.log("OtgFolderScanner", "Failed to write my1drive_db.json: ${e.message}")
        }

        // 2. Update global index on OTG drive (My1drive/my1drive_index.json)
        try {
            OtgFolderResolver.updateGlobalIndex(context, rootUri, uuid, archiveName, relPath)
        } catch (e: Exception) {
            DebugLogBuffer.log("OtgFolderScanner", "Failed to update global index: ${e.message}")
        }

        // 3. Save to Room database
        val entity = ArchiveEntity(
            uuid = uuid,
            name = archiveName,
            folderName = relPath,
            dateCreated = now,
            lastConnected = now
        )
        val db = AppDatabase.getDatabase(context)
        db.archiveDao().insert(entity)
        DebugLogBuffer.log("OtgFolderScanner", "Registered new archive '$archiveName' ($uuid) at '$relPath'")

        entity
    }
}
