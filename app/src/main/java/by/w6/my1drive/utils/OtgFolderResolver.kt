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

    /**
     * Updates or creates the global index file (My1drive/my1drive_index.json) at root of drive
     * so any phone can instantly discover archives regardless of custom nested folder paths.
     */
    fun updateGlobalIndex(context: Context, rootUri: Uri, uuid: String, archiveName: String, relativePath: String) {
        try {
            val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: return
            var container = rootDoc.findFile(MAIN_CONTAINER_NAME)
            if (container == null || !container.isDirectory) {
                container = rootDoc.createDirectory(MAIN_CONTAINER_NAME) ?: return
            }
            val indexFile = container.findFile(GLOBAL_INDEX_FILE_NAME) ?: container.createFile("application/json", GLOBAL_INDEX_FILE_NAME) ?: return
            
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
            
            context.contentResolver.openOutputStream(indexFile.uri, "w")?.use { out ->
                out.bufferedWriter().use { it.write(rootObj.toString(2)) }
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
    }

    /**
     * Scans the drive for My1drive archives via global index file (My1drive/my1drive_index.json) or folder scan.
     * If found, automatically registers the archive in Room.
     */
    fun scanAndRecoverArchive(context: Context, rootUri: Uri): by.w6.my1drive.data.local.ArchiveEntity? {
        try {
            val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: return null
            if (!rootDoc.exists() || !rootDoc.canRead()) return null

            val store = ArchiveMetadataStore(context)
            val db = AppDatabase.getDatabase(context)

            // 1. Try reading global index file first (My1drive/my1drive_index.json)
            val containerDoc = rootDoc.findFile(MAIN_CONTAINER_NAME)
            if (containerDoc != null && containerDoc.isDirectory) {
                val indexFile = containerDoc.findFile(GLOBAL_INDEX_FILE_NAME)
                if (indexFile != null && indexFile.exists()) {
                    try {
                        val jsonStr = context.contentResolver.openInputStream(indexFile.uri)?.use { it.bufferedReader().readText() }
                        if (!jsonStr.isNullOrEmpty()) {
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
                                        if (existing == null) {
                                            db.archiveDao().insert(entity)
                                            db.mediaDao().migrateLegacyArchiveUuid(uuid)
                                            DebugLogBuffer.log("OtgFolderResolver", "Recovered archive from global index: name=$name, uuid=$uuid, path=$relPath")
                                            return entity
                                        } else {
                                            return existing
                                        }
                                    }
                                }
                            }
                        }
                    } catch (ex: Exception) {
                        DebugLogBuffer.log("OtgFolderResolver", "Error reading global index: ${ex.localizedMessage}")
                    }
                }
            }

            // 2. Fallback: Check My1drive container folder subdirs
            val dirsToScan = mutableListOf<DocumentFile>()
            if (containerDoc != null && containerDoc.isDirectory) {
                containerDoc.listFiles().filter { it.isDirectory }.forEach { dirsToScan.add(it) }
            }
            
            // Fallback scan: first-level subdirectories of root (e.g. Arhiv-* or root itself)
            rootDoc.listFiles().filter { it.isDirectory && it.name != MAIN_CONTAINER_NAME }.forEach { dirsToScan.add(it) }

            // Also check root folder itself
            val rootMetadataFile = rootDoc.findFile("my1drive_db.json") ?: rootDoc.findFile(".my1drive_db.json")
            if (rootMetadataFile != null && rootMetadataFile.exists()) {
                val identity = store.readArchiveIdentity(rootMetadataFile)
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
                    if (existing == null) {
                        db.archiveDao().insert(entity)
                        db.mediaDao().migrateLegacyArchiveUuid(uuid)
                        DebugLogBuffer.log("OtgFolderResolver", "Recovered archive from root: name=$name, uuid=$uuid")
                        return entity
                    } else {
                        return existing
                    }
                }
            }

            for (dir in dirsToScan) {
                val metadataFile = dir.findFile("my1drive_db.json") ?: dir.findFile(".my1drive_db.json")
                if (metadataFile != null && metadataFile.exists()) {
                    val identity = store.readArchiveIdentity(metadataFile)
                    if (identity != null) {
                        val (uuid, name) = identity
                        val relativeFolderName = if (dir.parentFile?.name == MAIN_CONTAINER_NAME) {
                            "$MAIN_CONTAINER_NAME/${dir.name}"
                        } else {
                            dir.name ?: ""
                        }
                        val entity = by.w6.my1drive.data.local.ArchiveEntity(
                            uuid = uuid,
                            name = name,
                            folderName = relativeFolderName,
                            dateCreated = System.currentTimeMillis(),
                            lastConnected = System.currentTimeMillis()
                        )
                        val existing = db.archiveDao().getById(uuid)
                        if (existing == null) {
                            db.archiveDao().insert(entity)
                            db.mediaDao().migrateLegacyArchiveUuid(uuid)
                            DebugLogBuffer.log("OtgFolderResolver", "Recovered archive from subfolder $relativeFolderName: name=$name, uuid=$uuid")
                            return entity
                        } else {
                            return existing
                        }
                    }
                }
            }
        } catch (e: Exception) {
            DebugLogBuffer.log("OtgFolderResolver", "scanAndRecoverArchive exception: ${e.localizedMessage}")
        }
        return null
    }

    /**
     * Resolves the actual archive directory DocumentFile from the saved root/folder tree URI.
     * Uses the standardized My1drive/<Device Name>/ structure.
     */
    fun getArchiveDir(context: Context, rootUri: Uri, createIfNotExist: Boolean = true): DocumentFile? {
        try {
            val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: return null
            if (!rootDoc.exists() || !rootDoc.canRead()) {
                return null
            }
            
            // Check if the selected URI is already a specific subfolder.
            val treeDocId = try {
                DocumentsContract.getTreeDocumentId(rootUri)
            } catch (e: Exception) {
                ""
            }
            val pathSegment = treeDocId.substringAfter(":", "").trim('/', '\\')
            if (pathSegment.isNotEmpty() && !pathSegment.equals(MAIN_CONTAINER_NAME, ignoreCase = true)) {
                return rootDoc
            }

            // Obtain or create the main container directory: /My1drive/
            var containerDir = rootDoc.findFile(MAIN_CONTAINER_NAME)
            if (containerDir == null && createIfNotExist) {
                containerDir = rootDoc.createDirectory(MAIN_CONTAINER_NAME)
            }
            val targetParent = containerDir ?: rootDoc

            val volumeUuid = extractVolumeId(rootUri)
            val db = AppDatabase.getDatabase(context)
            val archive = if (volumeUuid != null) db.archiveDao().getById(volumeUuid) else null

            var subFolderName = if (archive != null && archive.folderName.isNotEmpty()) {
                archive.folderName.substringAfterLast("/")
            } else {
                getAutoCreatedFolderName(context)
            }

            var subDir = targetParent.findFile(subFolderName)
            if (subDir != null && subDir.isDirectory) {
                return subDir
            }

            if (createIfNotExist) {
                subDir = targetParent.createDirectory(subFolderName)
                if (subDir != null) {
                    val fullPath = "$MAIN_CONTAINER_NAME/$subFolderName"
                    if (archive != null) {
                        db.archiveDao().insert(archive.copy(folderName = fullPath))
                        updateGlobalIndex(context, rootUri, archive.uuid, archive.name, fullPath)
                    } else if (volumeUuid != null) {
                        updateGlobalIndex(context, rootUri, volumeUuid, subFolderName, fullPath)
                    }
                }
                return subDir
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
}


