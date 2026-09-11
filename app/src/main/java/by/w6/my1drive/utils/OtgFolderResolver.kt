package by.w6.my1drive.utils

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.documentfile.provider.DocumentFile
import by.w6.my1drive.utils.DebugLogBuffer
import by.w6.my1drive.data.local.AppDatabase

object OtgFolderResolver {
    
    const val MAIN_CONTAINER_NAME = "My1drive"
    const val GLOBAL_INDEX_FILE_NAME = "my1drive_index.json"
    const val PREVIEWS_DIR_NAME = ".previews"

    fun extractVolumeId(uri: Uri): String? {
        return extractVolumeIdFromPath(uri.path)
    }

    fun extractVolumeIdFromPath(path: String?): String? {
        if (path == null) return null

        val rootSegment = path.substringAfter("/root/", "")
        if (rootSegment.isNotEmpty()) {
            val rawId = rootSegment.substringBefore(":")
            if (rawId.isNotEmpty() && !rawId.contains("/")) return rawId
        }

        val docSegment = path.substringAfter("/document/", "")
        if (docSegment.isNotEmpty()) {
            val rawId = docSegment.substringBefore(":")
            if (rawId.isNotEmpty() && !rawId.contains("/")) return rawId
        }

        val treeSegment = path.substringAfter("/tree/", "")
        if (treeSegment.isNotEmpty()) {
            val rawId = treeSegment.substringBefore(":")
            if (rawId.isNotEmpty() && !rawId.contains("/")) return rawId
        }

        return null
    }

    fun wrapTreeDocument(context: Context, parent: DocumentFile?, uri: Uri): DocumentFile {
        return try {
            val constructor = Class.forName("androidx.documentfile.provider.TreeDocumentFile")
                .getDeclaredConstructor(DocumentFile::class.java, Context::class.java, Uri::class.java)
            constructor.isAccessible = true
            constructor.newInstance(parent, context, uri) as DocumentFile
        } catch (_: Exception) {
            DocumentFile.fromSingleUri(context, uri) ?: DocumentFile.fromTreeUri(context, uri)!!
        }
    }

    /**
     * Fast child document finder using direct ContentResolver query.
     * Unlike DocumentFile.findFile(), this does NOT instantiate DocumentFile objects for all files,
     * completely avoiding ANRs on drives with thousands of files.
     */
    fun fastFindChild(
        context: Context,
        parentDoc: DocumentFile,
        childName: String,
        isDirectoryOnly: Boolean = false
    ): DocumentFile? {
        try {
            val parentUri = parentDoc.uri
            val docId = try {
                DocumentsContract.getDocumentId(parentUri)
            } catch (_: Exception) {
                DocumentsContract.getTreeDocumentId(parentUri)
            }
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, docId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx) ?: continue
                    if (name.equals(childName, ignoreCase = true)) {
                        val mime = cursor.getString(mimeIdx) ?: ""
                        val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                        if (isDirectoryOnly && !isDir) continue
                        val foundId = cursor.getString(idIdx)
                        val childUri = DocumentsContract.buildDocumentUriUsingTree(parentUri, foundId)
                        return if (isDir) {
                            wrapTreeDocument(context, parentDoc, childUri)
                        } else {
                            DocumentFile.fromSingleUri(context, childUri)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            DebugLogBuffer.log("OtgFolderResolver", "fastFindChild error for $childName: ${e.message}")
        }
        return null
    }

    fun fastListDirectSubdirs(context: Context, parentDoc: DocumentFile): List<DocumentFile> {
        val results = mutableListOf<DocumentFile>()
        try {
            val parentUri = parentDoc.uri
            val docId = try {
                DocumentsContract.getDocumentId(parentUri)
            } catch (_: Exception) {
                DocumentsContract.getTreeDocumentId(parentUri)
            }
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, docId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val mime = cursor.getString(mimeIdx) ?: ""
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val foundId = cursor.getString(idIdx)
                        val childUri = DocumentsContract.buildDocumentUriUsingTree(parentUri, foundId)
                        val dir = wrapTreeDocument(context, parentDoc, childUri)
                        results.add(dir)
                    }
                }
            }
        } catch (e: Exception) {
            DebugLogBuffer.log("OtgFolderResolver", "fastListDirectSubdirs error: ${e.message}")
        }
        return results
    }

    /**
     * Конструирует прямой Document URI внутри дерева SAF без обращения к ContentResolver.query.
     * Не выполняет I/O операций на диске.
     */
    fun buildDirectChildUri(rootUri: Uri, relativePath: String): Uri {
        val treeDocId = try {
            DocumentsContract.getTreeDocumentId(rootUri)
        } catch (_: Exception) {
            ""
        }
        val cleanRelPath = relativePath.trim('/', '\\')
        val childDocId = if (cleanRelPath.isEmpty()) {
            treeDocId
        } else if (treeDocId.endsWith(":")) {
            "$treeDocId$cleanRelPath"
        } else {
            "$treeDocId/$cleanRelPath"
        }
        return DocumentsContract.buildDocumentUriUsingTree(rootUri, childDocId)
    }

    /**
     * Updates or creates the global index file (My1drive/my1drive_index.json) at root of drive
     * so any phone can instantly discover archives regardless of custom nested folder paths.
     */
    fun updateGlobalIndex(context: Context, rootUri: Uri, uuid: String, archiveName: String, relativePath: String) {
        try {
            val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: return
            val treeDocId = try { DocumentsContract.getTreeDocumentId(rootUri) } catch (_: Exception) { "" }
            val pathSegment = treeDocId.substringAfter(":", "").trim('/', '\\')
            if (pathSegment.isNotEmpty() && !pathSegment.equals(MAIN_CONTAINER_NAME, ignoreCase = true)) {
                return // subfolder tree URI cannot access drive root/My1drive index
            }
            val containerUri = buildDirectChildUri(rootUri, MAIN_CONTAINER_NAME)
            var container = DocumentFile.fromTreeUri(context, containerUri) ?: DocumentFile.fromSingleUri(context, containerUri)
            if (container == null || !container.exists()) {
                container = rootDoc.createDirectory(MAIN_CONTAINER_NAME) ?: return
            }
            val indexUri = buildDirectChildUri(rootUri, "$MAIN_CONTAINER_NAME/$GLOBAL_INDEX_FILE_NAME")
            var indexFile = DocumentFile.fromSingleUri(context, indexUri)
            if (indexFile == null || !indexFile.exists()) {
                indexFile = container.createFile("application/json", GLOBAL_INDEX_FILE_NAME) ?: return
            }
            
            val jsonString = context.contentResolver.openInputStream(indexFile.uri)?.use { it.bufferedReader().readText() } ?: "{}"
            val rootObj = try { org.json.JSONObject(jsonString) } catch (_: Exception) { org.json.JSONObject() }
            val archivesArray = rootObj.optJSONArray("archives") ?: org.json.JSONArray()
            
            val updatedArray = org.json.JSONArray()
            var exists = false
            for (i in 0 until archivesArray.length()) {
                val item = archivesArray.optJSONObject(i) ?: continue
                if (item.optString("uuid") == uuid) {
                    exists = true
                    updatedArray.put(org.json.JSONObject().apply {
                        put("uuid", uuid)
                        put("name", archiveName)
                        put("path", relativePath)
                    })
                } else {
                    updatedArray.put(item)
                }
            }
            if (!exists) {
                updatedArray.put(org.json.JSONObject().apply {
                    put("uuid", uuid)
                    put("name", archiveName)
                    put("path", relativePath)
                })
            }
            rootObj.put("version", 1)
            rootObj.put("archives", updatedArray)
            
            val jsonContent = rootObj.toString(2)
            context.contentResolver.openOutputStream(indexFile.uri, "w")?.use { out ->
                out.bufferedWriter().use { it.write(jsonContent) }
            }
            DebugLogBuffer.log("OtgFolderResolver", "Global index updated for archive $archiveName at $relativePath")
        } catch (e: Exception) {
            DebugLogBuffer.log("OtgFolderResolver", "Failed to update global index: ${e.localizedMessage}")
        }
    }

    /**
     * Generates a clean, human-readable device subfolder name.
     * Uses device_name setting if available, otherwise Build.MODEL or Manufacturer + Model.
     * Strips illegal FAT32/exFAT filesystem characters: / \ : * ? " < > |
     */
    fun getAutoCreatedFolderName(context: Context): String {
        val deviceName = try {
            Settings.Global.getString(context.contentResolver, "device_name")
        } catch (_: Exception) {
            null
        }

        val rawName = if (!deviceName.isNullOrBlank()) {
            deviceName
        } else {
            val model = Build.MODEL
            val manufacturer = Build.MANUFACTURER
            if (model.startsWith(manufacturer, ignoreCase = true)) {
                model
            } else {
                "$manufacturer $model"
            }
        }

        val sanitized = sanitizeFolderName(rawName)
        return if (sanitized.isNotBlank()) sanitized else "Device"
    }

    fun sanitizeFolderName(name: String): String {
        return name.replace(Regex("[/\\\\:*?\"<>|]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(30)
            .trim()
    }

    /**
     * Scans the drive for My1drive archives via global index file (My1drive/my1drive_index.json) or folder scan.
     * Scans the drive root to recover and register all existing archives into Room.
     * Uses Level 1 (my1drive_index.json) and Level 2 (My1drive/ subdirectories).
     */
    fun scanAndRecoverAllArchives(context: Context, rootUri: Uri): List<by.w6.my1drive.data.local.ArchiveEntity> {
        val recovered = mutableListOf<by.w6.my1drive.data.local.ArchiveEntity>()
        val seenUuids = mutableSetOf<String>()
        try {
            val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: return emptyList()
            if (!rootDoc.exists() || !rootDoc.canRead()) return emptyList()

            val store = ArchiveMetadataStore(context)
            val db = AppDatabase.getDatabase(context)

            // 1. Try reading global index file first (My1drive/my1drive_index.json) directly without root scanning
            val indexUri = buildDirectChildUri(rootUri, "$MAIN_CONTAINER_NAME/$GLOBAL_INDEX_FILE_NAME")
            val jsonStr = try {
                context.contentResolver.openInputStream(indexUri)?.use { it.bufferedReader().readText() }
            } catch (_: Exception) {
                null
            }

            if (!jsonStr.isNullOrEmpty()) {
                try {
                    val rootObj = org.json.JSONObject(jsonStr)
                    val archivesArr = rootObj.optJSONArray("archives")
                    if (archivesArr != null) {
                        for (i in 0 until archivesArr.length()) {
                            val item = archivesArr.optJSONObject(i) ?: continue
                            val uuid = item.optString("uuid")
                            val name = item.optString("name")
                            val relPath = item.optString("path")
                            if (uuid.isNotEmpty() && relPath.isNotEmpty()) {
                                val entity = by.w6.my1drive.data.local.ArchiveEntity(
                                    uuid = uuid,
                                    name = name.ifEmpty { "Archive" },
                                    folderName = relPath,
                                    dateCreated = System.currentTimeMillis(),
                                    lastConnected = System.currentTimeMillis()
                                )
                                val existing = db.archiveDao().getById(uuid)
                                val finalEntity = if (existing == null) {
                                    db.archiveDao().insert(entity)
                                    DebugLogBuffer.log("OtgFolderResolver", "Recovered archive from global index: name=$name, uuid=$uuid, path=$relPath")
                                    entity
                                } else {
                                    existing
                                }
                                if (seenUuids.add(uuid)) {
                                    recovered.add(finalEntity)
                                }
                            }
                        }
                    }
                } catch (ex: Exception) {
                    DebugLogBuffer.log("OtgFolderResolver", "Error reading global index: ${ex.localizedMessage}")
                }
            }

            // 2. Also check My1drive container subdirectories directly
            val containerUri = buildDirectChildUri(rootUri, MAIN_CONTAINER_NAME)
            var containerDoc: DocumentFile? = wrapTreeDocument(context, rootDoc, containerUri)
            if (containerDoc == null || !containerDoc.exists() || !containerDoc.isDirectory) {
                containerDoc = fastFindChild(context, rootDoc, MAIN_CONTAINER_NAME, isDirectoryOnly = true)
            }
            if (containerDoc != null && containerDoc.exists() && containerDoc.isDirectory) {
                val my1driveSubDirs = fastListDirectSubdirs(context, containerDoc)
                for (dir in my1driveSubDirs) {
                    val metadataFile = fastFindChild(context, dir, "my1drive_db.json") ?: fastFindChild(context, dir, ".my1drive_db.json")
                    if (metadataFile != null && metadataFile.exists()) {
                        val identity = store.readArchiveIdentity(metadataFile)
                        if (identity != null) {
                            val (uuid, name) = identity
                            val relativeFolderName = "$MAIN_CONTAINER_NAME/${dir.name}"
                            val entity = by.w6.my1drive.data.local.ArchiveEntity(
                                uuid = uuid,
                                name = name,
                                folderName = relativeFolderName,
                                dateCreated = System.currentTimeMillis(),
                                lastConnected = System.currentTimeMillis()
                            )
                            val existing = db.archiveDao().getById(uuid)
                            updateGlobalIndex(context, rootUri, uuid, name, relativeFolderName)
                            val finalEntity = if (existing == null) {
                                db.archiveDao().insert(entity)
                                DebugLogBuffer.log("OtgFolderResolver", "Recovered archive from My1drive subdir $relativeFolderName: name=$name, uuid=$uuid")
                                entity
                            } else {
                                if (existing.folderName != relativeFolderName) {
                                    val updated = existing.copy(folderName = relativeFolderName)
                                    db.archiveDao().insert(updated)
                                    updated
                                } else {
                                    existing
                                }
                            }
                            if (seenUuids.add(uuid)) {
                                recovered.add(finalEntity)
                            }
                        }
                    }
                }
            } else {
                // 3. Fallback: check direct metadata file at root without listing directories
                for (metaName in listOf("my1drive_db.json", ".my1drive_db.json")) {
                    val rootMetaUri = buildDirectChildUri(rootUri, metaName)
                    try {
                        val hasFile = context.contentResolver.openInputStream(rootMetaUri)?.use { true } ?: false
                        if (hasFile) {
                            val directDoc = DocumentFile.fromSingleUri(context, rootMetaUri)
                            if (directDoc != null && directDoc.exists()) {
                                val identity = store.readArchiveIdentity(directDoc)
                                if (identity != null) {
                                    val (uuid, name) = identity
                                    val entity = by.w6.my1drive.data.local.ArchiveEntity(
                                        uuid = uuid,
                                        name = name,
                                        folderName = "",
                                        dateCreated = System.currentTimeMillis(),
                                        lastConnected = System.currentTimeMillis()
                                    )
                                    val existing = db.archiveDao().getById(uuid)
                                    updateGlobalIndex(context, rootUri, uuid, name, "")
                                    val finalEntity = if (existing == null) {
                                        db.archiveDao().insert(entity)
                                        DebugLogBuffer.log("OtgFolderResolver", "Recovered legacy archive from root: name=$name, uuid=$uuid")
                                        entity
                                    } else {
                                        existing
                                    }
                                    if (seenUuids.add(uuid)) {
                                        recovered.add(finalEntity)
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            DebugLogBuffer.log("OtgFolderResolver", "scanAndRecoverAllArchives exception: ${e.localizedMessage}")
        }
        return recovered.sortedByDescending { maxOf(it.lastConnected, it.dateCreated) }
    }

    fun scanAndRecoverArchive(context: Context, rootUri: Uri): by.w6.my1drive.data.local.ArchiveEntity? {
        return scanAndRecoverAllArchives(context, rootUri).firstOrNull()
    }

    /**
     * Resolves the actual archive directory DocumentFile from the saved root/folder tree URI.
     * Uses the standardized My1drive/<Device Name>/ structure.
     */
    fun getArchiveDir(context: Context, rootUri: Uri, createIfNotExist: Boolean = true): DocumentFile? {
        try {
            val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: return null
            if (!rootDoc.exists()) return null

            val treeDocId = try { DocumentsContract.getTreeDocumentId(rootUri) } catch (_: Exception) { "" }
            val pathSegment = treeDocId.substringAfter(":", "").trim('/', '\\')
            if (pathSegment.isNotEmpty() && !pathSegment.equals(MAIN_CONTAINER_NAME, ignoreCase = true)) {
                return rootDoc
            }

            val prefs = context.getSharedPreferences("my1drive_prefs", Context.MODE_PRIVATE)
            val activeUuid = prefs.getString("active_archive_uuid", null)
            val volumeUuid = extractVolumeId(rootUri)
            val db = AppDatabase.getDatabase(context)
            val archive = if (!activeUuid.isNullOrEmpty()) {
                db.archiveDao().getById(activeUuid)
            } else if (volumeUuid != null) {
                db.archiveDao().getById(volumeUuid)
            } else {
                null
            }

            val targetRelPath = if (archive != null && archive.folderName.isNotEmpty()) {
                archive.folderName
            } else {
                val subFolderName = getAutoCreatedFolderName(context)
                "$MAIN_CONTAINER_NAME/$subFolderName"
            }

            if (targetRelPath.isEmpty()) {
                return rootDoc
            }

            // Direct check using buildDirectChildUri (NO root scanning)
            val directUri = buildDirectChildUri(rootUri, targetRelPath)
            val directDoc = wrapTreeDocument(context, rootDoc, directUri)
            if (directDoc.exists() && directDoc.isDirectory) {
                return directDoc
            }

            // Fallback: resolve path segments via fastFindChild
            var currentDoc: DocumentFile? = rootDoc
            val segments = targetRelPath.split("/").filter { it.isNotEmpty() }
            for (segment in segments) {
                currentDoc = fastFindChild(context, currentDoc ?: break, segment, isDirectoryOnly = true)
            }
            if (currentDoc != null && currentDoc.exists() && currentDoc.isDirectory) {
                return currentDoc
            }

            // Self-healing fallback 1: If path didn't start with My1drive/, check My1drive/$targetRelPath
            if (!targetRelPath.startsWith("$MAIN_CONTAINER_NAME/")) {
                val candidatePath = "$MAIN_CONTAINER_NAME/$targetRelPath"
                val candidateUri = buildDirectChildUri(rootUri, candidatePath)
                val candidateDoc = wrapTreeDocument(context, rootDoc, candidateUri)
                val foundDir = if (candidateDoc.exists() && candidateDoc.isDirectory) {
                    candidateDoc
                } else {
                    var cDoc: DocumentFile? = rootDoc
                    for (seg in candidatePath.split("/").filter { it.isNotEmpty() }) {
                        cDoc = fastFindChild(context, cDoc ?: break, seg, isDirectoryOnly = true)
                    }
                    if (cDoc != null && cDoc.exists() && cDoc.isDirectory) cDoc else null
                }
                if (foundDir != null) {
                    if (archive != null) {
                        db.archiveDao().insert(archive.copy(folderName = candidatePath))
                        updateGlobalIndex(context, rootUri, archive.uuid, archive.name, candidatePath)
                    }
                    return foundDir
                }
            } else {
                // Self-healing fallback 2: If path started with My1drive/ but legacy folder is directly at root
                val folderNameOnly = targetRelPath.substringAfterLast('/')
                val rootCandidateUri = buildDirectChildUri(rootUri, folderNameOnly)
                val rootCandidateDoc = wrapTreeDocument(context, rootDoc, rootCandidateUri)
                val foundDir = if (rootCandidateDoc.exists() && rootCandidateDoc.isDirectory) {
                    rootCandidateDoc
                } else {
                    val cDoc = fastFindChild(context, rootDoc, folderNameOnly, isDirectoryOnly = true)
                    if (cDoc != null && cDoc.exists() && cDoc.isDirectory) cDoc else null
                }
                if (foundDir != null) {
                    if (archive != null) {
                        db.archiveDao().insert(archive.copy(folderName = folderNameOnly))
                        updateGlobalIndex(context, rootUri, archive.uuid, archive.name, folderNameOnly)
                    }
                    return foundDir
                }
            }

            // If not found yet and createIfNotExist, create folder under My1drive container
            if (createIfNotExist) {
                val containerUri = buildDirectChildUri(rootUri, MAIN_CONTAINER_NAME)
                var containerDoc: DocumentFile? = wrapTreeDocument(context, rootDoc, containerUri)
                if (containerDoc == null || !containerDoc.exists() || !containerDoc.isDirectory) {
                    containerDoc = fastFindChild(context, rootDoc, MAIN_CONTAINER_NAME, isDirectoryOnly = true)
                        ?: rootDoc.createDirectory(MAIN_CONTAINER_NAME)
                }
                val parentDoc = containerDoc ?: rootDoc
                val folderNameOnly = targetRelPath.substringAfterLast('/')
                val newDir = parentDoc.createDirectory(folderNameOnly)
                if (newDir != null) {
                    val fullPath = if (parentDoc == rootDoc) folderNameOnly else "$MAIN_CONTAINER_NAME/$folderNameOnly"
                    if (archive != null) {
                        db.archiveDao().insert(archive.copy(folderName = fullPath))
                        updateGlobalIndex(context, rootUri, archive.uuid, archive.name, fullPath)
                    } else if (volumeUuid != null) {
                        updateGlobalIndex(context, rootUri, volumeUuid, folderNameOnly, fullPath)
                    }
                    try {
                        newDir.createFile("application/octet-stream", ".nomedia")
                    } catch (_: Exception) {}
                    return newDir
                }
            }

            return null
        } catch (e: Exception) {
            DebugLogBuffer.log("OtgFolderResolver", "Error resolving archive dir: ${e.localizedMessage}")
            return null
        }
    }

    /**
     * Renames an archive's physical folder on the USB drive and updates DB.
     */
    suspend fun renameArchive(context: Context, rootUri: Uri, archiveUuid: String, newName: String): Boolean {
        try {
            val db = AppDatabase.getDatabase(context)
            val archive = db.archiveDao().getById(archiveUuid) ?: return false

            val sanitizedNewName = sanitizeFolderName(newName)
            if (sanitizedNewName.isEmpty()) return false

            val currentDir = getArchiveDir(context, rootUri, createIfNotExist = false)
            if (currentDir != null && currentDir.exists()) {
                val success = currentDir.renameTo(sanitizedNewName)
                if (success) {
                    val newRelativePath = "$MAIN_CONTAINER_NAME/$sanitizedNewName"
                    db.archiveDao().insert(archive.copy(name = sanitizedNewName, folderName = newRelativePath))

                    // Update metadata file inside the renamed folder
                    val metadataStore = ArchiveMetadataStore(context)
                    metadataStore.writeMetadata(rootUri, metadataStore.readMetadata(rootUri) ?: emptyList())
                    
                    // Update global index file at root of USB drive
                    updateGlobalIndex(context, rootUri, archiveUuid, sanitizedNewName, newRelativePath)
                    
                    DebugLogBuffer.log("OtgFolderResolver", "Successfully renamed archive directory to $sanitizedNewName")
                    return true
                }
            }
        } catch (e: Exception) {
            DebugLogBuffer.log("OtgFolderResolver", "Failed to rename archive: ${e.localizedMessage}")
        }
        return false
    }

    /**
     * Programmatically ensures a .nomedia marker exists at the root of the OTG storage volume
     * and in the /My1drive container directory.
     * This prevents Android 14/15 ModernMediaScanner from misclassifying OTG drives as internal
     * and running recursive Files.walkFileTree (REASON_DEMAND) across tens of thousands of files.
     */
    fun ensureOtgNomediaMarker(context: Context, rootUri: Uri) {
        try {
            // 1. Check & create in root of OTG drive
            val rootNomediaUri = buildDirectChildUri(rootUri, ".nomedia")
            val rootExists = try {
                context.contentResolver.openInputStream(rootNomediaUri)?.use { true } ?: false
            } catch (_: Exception) {
                false
            }

            if (!rootExists) {
                val rootDoc = DocumentFile.fromTreeUri(context, rootUri)
                if (rootDoc != null && rootDoc.exists() && rootDoc.canWrite()) {
                    try {
                        val created = rootDoc.createFile("application/octet-stream", ".nomedia")
                        DebugLogBuffer.log("OtgFolderResolver", "Created .nomedia at root of OTG drive: ${created?.uri}")
                    } catch (e: Exception) {
                        DebugLogBuffer.log("OtgFolderResolver", "Failed to create .nomedia at root: ${e.localizedMessage}")
                    }
                }
            }

            // 2. Check & create in My1drive container directory (if rootUri is volume root)
            val treeDocId = try { DocumentsContract.getTreeDocumentId(rootUri) } catch (_: Exception) { "" }
            val pathSegment = treeDocId.substringAfter(":", "").trim('/', '\\')
            if (pathSegment.isEmpty()) {
                val containerUri = buildDirectChildUri(rootUri, MAIN_CONTAINER_NAME)
                val containerDoc = DocumentFile.fromTreeUri(context, containerUri)
                if (containerDoc != null && containerDoc.exists() && containerDoc.canWrite()) {
                    val containerNomediaUri = buildDirectChildUri(containerUri, ".nomedia")
                    val containerExists = try {
                        context.contentResolver.openInputStream(containerNomediaUri)?.use { true } ?: false
                    } catch (_: Exception) {
                        false
                    }
                    if (!containerExists) {
                        try {
                            val created = containerDoc.createFile("application/octet-stream", ".nomedia")
                            DebugLogBuffer.log("OtgFolderResolver", "Created .nomedia in My1drive container: ${created?.uri}")
                        } catch (e: Exception) {
                            DebugLogBuffer.log("OtgFolderResolver", "Failed to create .nomedia in container: ${e.localizedMessage}")
                        }
                    }
                }
            }

            // 3. Fallback to direct java.io.File if accessible via Linux mount
            val volumeId = extractVolumeId(rootUri)
            if (volumeId != null) {
                listOf(
                    java.io.File("/storage/$volumeId/.nomedia"),
                    java.io.File("/mnt/media_rw/$volumeId/.nomedia"),
                    java.io.File("/storage/$volumeId/$MAIN_CONTAINER_NAME/.nomedia"),
                    java.io.File("/mnt/media_rw/$volumeId/$MAIN_CONTAINER_NAME/.nomedia")
                ).forEach { f ->
                    try {
                        if (!f.exists()) {
                            f.parentFile?.mkdirs()
                            f.createNewFile()
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            DebugLogBuffer.log("OtgFolderResolver", "ensureOtgNomediaMarker error: ${e.localizedMessage}")
        }
    }

    /**
     * Resolves or creates the .previews directory inside the archive directory.
     * Places a .nomedia file inside to hide thumbnails from Android gallery scanners.
     */
    fun getOrCreatePreviewsDir(context: Context, archiveDir: DocumentFile): DocumentFile? {
        return try {
            var previewsDir = fastFindChild(context, archiveDir, PREVIEWS_DIR_NAME, isDirectoryOnly = true)
            if (previewsDir == null || !previewsDir.exists() || !previewsDir.isDirectory) {
                previewsDir = archiveDir.createDirectory(PREVIEWS_DIR_NAME)
                if (previewsDir != null) {
                    try {
                        previewsDir.createFile("application/octet-stream", ".nomedia")
                    } catch (_: Exception) {}
                }
            }
            previewsDir
        } catch (e: Exception) {
            DebugLogBuffer.log("OtgFolderResolver", "Error getting/creating .previews dir: ${e.message}")
            null
        }
    }

    /**
     * Gets the DocumentFile for a preview in the .previews directory on OTG, if it exists.
     */
    fun findPreviewDocument(context: Context, previewsDir: DocumentFile, hash: String): DocumentFile? {
        return try {
            val fileName = "$hash.my1d"
            val directUri = buildDirectChildUri(previewsDir.uri, fileName)
            val doc = DocumentFile.fromSingleUri(context, directUri)
            if (doc != null && doc.exists() && doc.length() > 0) {
                doc
            } else {
                fastFindChild(context, previewsDir, fileName, isDirectoryOnly = false)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Attempts to copy an existing preview from the OTG .previews folder directly into the local cache file.
     * Returns true if successfully copied, avoiding heavy bitmap decode.
     */
    fun tryCopyPreviewFromOtg(context: Context, itemUri: Uri, hash: String, targetLocalFile: java.io.File): Boolean {
        return try {
            val authority = itemUri.authority ?: return false
            val treeId = try { DocumentsContract.getTreeDocumentId(itemUri) } catch (_: Exception) { "" }
            val docId = try { DocumentsContract.getDocumentId(itemUri) } catch (_: Exception) { "" }
            if (docId.isEmpty() || !docId.contains(':')) return false

            val volume = docId.substringBefore(':')
            val path = docId.substringAfter(':')

            val candidates = mutableListOf<String>()
            val parentPath = path.substringBeforeLast('/', "")
            if (parentPath.isNotEmpty()) {
                candidates.add("$volume:$parentPath/$PREVIEWS_DIR_NAME/$hash.my1d")
            }
            if (path.contains("$MAIN_CONTAINER_NAME/")) {
                val afterContainer = path.substringAfter("$MAIN_CONTAINER_NAME/")
                val archiveSubfolder = afterContainer.substringBefore('/')
                if (archiveSubfolder.isNotEmpty()) {
                    candidates.add("$volume:$MAIN_CONTAINER_NAME/$archiveSubfolder/$PREVIEWS_DIR_NAME/$hash.my1d")
                }
            }

            for (candidateDocId in candidates.distinct()) {
                val previewDocUri = if (treeId.isNotEmpty()) {
                    DocumentsContract.buildDocumentUriUsingTree(itemUri, candidateDocId)
                } else {
                    DocumentsContract.buildDocumentUri(authority, candidateDocId)
                }
                val stream = try {
                    context.contentResolver.openInputStream(previewDocUri)
                } catch (_: Exception) {
                    null
                }
                if (stream != null) {
                    stream.use { input ->
                        targetLocalFile.outputStream().buffered().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (targetLocalFile.exists() && targetLocalFile.length() > 0) {
                        return true
                    }
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Saves a locally created preview into the corresponding .previews folder on the OTG drive.
     */
    fun trySavePreviewToOtg(context: Context, itemUri: Uri, hash: String, sourceLocalFile: java.io.File) {
        try {
            val treeId = try { DocumentsContract.getTreeDocumentId(itemUri) } catch (_: Exception) { "" }
            val docId = try { DocumentsContract.getDocumentId(itemUri) } catch (_: Exception) { "" }
            if (docId.isEmpty() || !docId.contains(':')) return

            val volume = docId.substringBefore(':')
            val path = docId.substringAfter(':')

            val parentPath = if (path.contains("$MAIN_CONTAINER_NAME/")) {
                val afterContainer = path.substringAfter("$MAIN_CONTAINER_NAME/")
                val archiveSubfolder = afterContainer.substringBefore('/')
                "$MAIN_CONTAINER_NAME/$archiveSubfolder"
            } else {
                path.substringBeforeLast('/', "")
            }

            val parentDocId = if (parentPath.isNotEmpty()) "$volume:$parentPath" else "$volume:"
            val parentUri = if (treeId.isNotEmpty()) {
                DocumentsContract.buildDocumentUriUsingTree(itemUri, parentDocId)
            } else {
                itemUri
            }
            val parentDoc = wrapTreeDocument(context, null, parentUri)
            if (!parentDoc.exists() || !parentDoc.isDirectory) return

            val previewsDir = getOrCreatePreviewsDir(context, parentDoc) ?: return
            val existingDoc = findPreviewDocument(context, previewsDir, hash)
            if (existingDoc != null && existingDoc.exists() && existingDoc.length() > 0) {
                return
            }
            val targetDoc = existingDoc ?: previewsDir.createFile("image/webp", "$hash.my1d") ?: return
            context.contentResolver.openOutputStream(targetDoc.uri, "w")?.use { out ->
                sourceLocalFile.inputStream().buffered().use { input ->
                    input.copyTo(out)
                }
            }
            DebugLogBuffer.log("OtgFolderResolver", "Saved preview to OTG .previews: $hash.my1d")
        } catch (e: Exception) {
            DebugLogBuffer.log("OtgFolderResolver", "Failed to save preview to OTG: ${e.message}")
        }
    }
}



