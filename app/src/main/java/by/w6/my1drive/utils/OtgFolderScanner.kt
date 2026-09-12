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

    internal val BLACKLIST_NAMES = setOf(
        "android",
        "lost.dir",
        "\$recycle.bin",
        "system volume information",
        ".thumbnails",
        ".previews",
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
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            )
            val subdirsToCheck = mutableListOf<String>()
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                var checkedCount = 0
                while (cursor.moveToNext()) {
                    val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) else null
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                    val foundId = if (idIdx >= 0) cursor.getString(idIdx) else null

                    if (name.equals("my1drive_db.json", ignoreCase = true)) {
                        return true
                    }
                    val isMedia = (mime != null && (mime.startsWith("image/") || mime.startsWith("video/"))) ||
                            (name != null && MEDIA_EXTENSIONS.contains(name.substringAfterLast('.', "").lowercase()))
                    if (isMedia) {
                        return true
                    }
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR && foundId != null && subdirsToCheck.size < 5) {
                        val cleanName = name?.lowercase()?.trim() ?: ""
                        if (!cleanName.startsWith(".") && cleanName !in BLACKLIST_NAMES) {
                            subdirsToCheck.add(foundId)
                        }
                    }
                    checkedCount++
                    if (checkedCount > 100) {
                        break
                    }
                }
            }

            for (subId in subdirsToCheck) {
                val subChildrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, subId)
                context.contentResolver.query(subChildrenUri, projection, null, null, null)?.use { cursor ->
                    val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    while (cursor.moveToNext()) {
                        val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) else null
                        val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                        val isMedia = (mime != null && (mime.startsWith("image/") || mime.startsWith("video/"))) ||
                                (name != null && MEDIA_EXTENSIONS.contains(name.substringAfterLast('.', "").lowercase()))
                        if (isMedia) {
                            return true
                        }
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
            val metaUri = OtgFolderResolver.buildDirectChildUri(rootUri, "$relPath/my1drive_db.json")
            val metaExists = try {
                context.contentResolver.openInputStream(metaUri)?.use { true } ?: false
            } catch (_: Exception) { false }

            val targetFileUri = if (!metaExists) {
                DocumentsContract.createDocument(
                    context.contentResolver,
                    folder.uri,
                    "application/json",
                    "my1drive_db.json"
                ) ?: metaUri
            } else {
                metaUri
            }

            val rootJson = JSONObject().apply {
                put("version", 2)
                put("archiveUuid", uuid)
                put("archiveName", archiveName)
                put("folderPath", relPath)
                put("dateCreated", now)
                put("files", org.json.JSONArray())
            }
            context.contentResolver.openOutputStream(targetFileUri, "w")?.use { out ->
                out.bufferedWriter().use { it.write(rootJson.toString(2)) }
            }
        } catch (e: Exception) {
            DebugLogBuffer.log("OtgFolderScanner", "Failed to write my1drive_db.json: ${e.message}")
        }

        // 1.1 Ensure .nomedia in folder
        try {
            val nomediaUri = OtgFolderResolver.buildDirectChildUri(rootUri, "$relPath/.nomedia")
            val hasNomedia = try {
                context.contentResolver.openInputStream(nomediaUri)?.use { true } ?: false
            } catch (_: Exception) { false }
            if (!hasNomedia) {
                DocumentsContract.createDocument(context.contentResolver, folder.uri, "application/octet-stream", ".nomedia")
            }
        } catch (_: Exception) {}

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

    /**
     * Fast Level 3 scanner: Traverses root subdirectories up to maxDepth (default 3)
     * looking for my1drive_db.json / .my1drive_db.json across all folders.
     * Skips BLACKLIST_NAMES and already known paths.
     * Does not prune branches, discovering nested archives (e.g. Photo/Album/...).
     */
    suspend fun scanRootForJsonArchives(
        context: Context,
        rootUri: Uri,
        knownPaths: Set<String>,
        maxDepth: Int = 3
    ): List<ArchiveEntity> = withContext(Dispatchers.IO) {
        val discovered = mutableListOf<ArchiveEntity>()
        val seenUuids = mutableSetOf<String>()
        try {
            val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext emptyList()
            if (!rootDoc.exists() || !rootDoc.canRead()) return@withContext emptyList()

            val store = ArchiveMetadataStore(context)
            val db = AppDatabase.getDatabase(context)
            val volumeId = OtgFolderResolver.extractVolumeId(rootUri)

            fun scanDir(currentDoc: DocumentFile, currentRelPath: String, depth: Int) {
                if (depth > maxDepth) return

                val subDirs = OtgFolderResolver.fastListDirectSubdirs(context, currentDoc)
                for (sub in subDirs) {
                    val subName = sub.name ?: continue
                    val cleanSub = subName.lowercase().trim()
                    if (cleanSub.startsWith(".") || cleanSub in BLACKLIST_NAMES) continue

                    val relPath = if (currentRelPath.isEmpty()) subName else "$currentRelPath/$subName"
                    val metaFile = OtgFolderResolver.fastFindChild(context, sub, "my1drive_db.json")
                        ?: OtgFolderResolver.fastFindChild(context, sub, ".my1drive_db.json")

                    if (metaFile != null && metaFile.exists()) {
                        val identity = store.readArchiveIdentity(metaFile)
                        if (identity != null) {
                            val (rawUuid, name) = identity
                            val finalUuid = OtgFolderResolver.normalizeArchiveUuid(rawUuid, volumeId, relPath)
                            if (seenUuids.add(finalUuid)) {
                                val entity = ArchiveEntity(
                                    uuid = finalUuid,
                                    name = name,
                                    folderName = relPath,
                                    dateCreated = System.currentTimeMillis(),
                                    lastConnected = System.currentTimeMillis()
                                )
                                val existing = db.archiveDao().getById(finalUuid)
                                OtgFolderResolver.updateGlobalIndex(context, rootUri, finalUuid, name, relPath)
                                val finalEntity = existing ?: entity
                                discovered.add(finalEntity)
                            }
                        }
                    }

                    // Scan subdirectories further up to maxDepth
                    if (depth < maxDepth) {
                        scanDir(sub, relPath, depth + 1)
                    }
                }
            }

            scanDir(rootDoc, "", 1)
        } catch (e: Exception) {
            DebugLogBuffer.log("OtgFolderScanner", "scanRootForJsonArchives error: ${e.message}")
        }
        discovered
    }
}
