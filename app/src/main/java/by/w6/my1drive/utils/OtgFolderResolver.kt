package by.w6.my1drive.utils

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import by.w6.my1drive.utils.DebugLogBuffer
import by.w6.my1drive.data.local.AppDatabase

object OtgFolderResolver {
    
    fun extractVolumeId(uri: Uri): String? {
        val path = uri.path ?: return null

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

    fun getAutoCreatedFolderName(context: Context): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val cleanModel = model.replace(Regex("[^a-zA-Z0-9\\s]"), "").replace(Regex("\\s+"), " ").trim()
        val cleanManufacturer = manufacturer.replace(Regex("[^a-zA-Z0-9\\s]"), "").replace(Regex("\\s+"), " ").trim()
        val segments = cleanModel.split(" ")
        val first = segments.getOrNull(0) ?: ""
        val second = segments.getOrNull(1) ?: ""
        val secondContainsDigit = second.any { it.isDigit() }
        val name = if (first.length > 2) {
            if (second.isNotEmpty() && (second.matches(Regex("\\d+")) || (second.length <= 3 && secondContainsDigit))) {
                first + second
            } else {
                first
            }
        } else {
            cleanManufacturer.split(" ").firstOrNull() ?: ""
        }
        val formattedName = if (name.isNotEmpty()) {
            name.lowercase().replaceFirstChar { it.uppercase() }
        } else {
            "Device"
        }
        return "Arhiv-$formattedName"
    }

    /**
     * Scans the drive root and first-level directories for .my1drive_db.json.
     * If found, automatically registers the archive in Room.
     */
    fun scanAndRecoverArchive(context: Context, rootUri: Uri): by.w6.my1drive.data.local.ArchiveEntity? {
        try {
            val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: return null
            if (!rootDoc.exists() || !rootDoc.canRead()) return null

            val store = ArchiveMetadataStore(context)
            val db = AppDatabase.getDatabase(context)

            // 1. Check root directory first
            val rootMetadataFile = rootDoc.findFile("my1drive_db.json") ?: rootDoc.findFile(".my1drive_db.json")
            if (rootMetadataFile != null && rootMetadataFile.exists()) {
                val identity = store.readArchiveIdentity(rootMetadataFile)
                if (identity != null) {
                    val (uuid, name) = identity
                    val folderName = "" // Located directly in the root
                    val entity = by.w6.my1drive.data.local.ArchiveEntity(
                        uuid = uuid,
                        name = name,
                        folderName = folderName,
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

            // 2. Scan first-level subdirectories using fast ContentResolver query
            val docId = try {
                android.provider.DocumentsContract.getDocumentId(rootUri)
            } catch (e: Exception) {
                android.provider.DocumentsContract.getTreeDocumentId(rootUri)
            }
            val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
                rootUri, docId
            )
            val projection = arrayOf(
                android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)

                while (cursor.moveToNext()) {
                    val mime = cursor.getString(mimeIdx) ?: ""
                    if (mime != android.provider.DocumentsContract.Document.MIME_TYPE_DIR) continue

                    val folderName = cursor.getString(nameIdx) ?: ""
                    if (folderName.startsWith("Arhiv", ignoreCase = true) || folderName.startsWith("Архив", ignoreCase = true)) {
                        val folderDocId = cursor.getString(idIdx)
                        val folderUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(rootUri, folderDocId)
                        val folderDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, folderUri)
                        
                        val metadataFile = folderDoc?.findFile("my1drive_db.json") ?: folderDoc?.findFile(".my1drive_db.json")
                        if (metadataFile != null && metadataFile.exists()) {
                            val identity = store.readArchiveIdentity(metadataFile)
                            if (identity != null) {
                                val (uuid, name) = identity
                                val entity = by.w6.my1drive.data.local.ArchiveEntity(
                                    uuid = uuid,
                                    name = name,
                                    folderName = folderName,
                                    dateCreated = System.currentTimeMillis(),
                                    lastConnected = System.currentTimeMillis()
                                )
                                val existing = db.archiveDao().getById(uuid)
                                if (existing == null) {
                                    db.archiveDao().insert(entity)
                                    db.mediaDao().migrateLegacyArchiveUuid(uuid)
                                    DebugLogBuffer.log("OtgFolderResolver", "Recovered archive from subfolder $folderName: name=$name, uuid=$uuid")
                                    return entity
                                } else {
                                    return existing
                                }
                            }
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
     * If the URI is the root of the volume (no subfolder path in document ID), it will
     * find or create the folder inside it based on the archive's folderName.
     * Otherwise, if the user explicitly selected a subdirectory, it returns that subdirectory directly.
     */
    fun getArchiveDir(context: Context, rootUri: Uri, createIfNotExist: Boolean = true): DocumentFile? {
        try {
            val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: return null
            if (!rootDoc.exists() || !rootDoc.canRead()) {
                return null
            }
            
            // Check if the selected URI has a subfolder path.
            val treeDocId = try {
                DocumentsContract.getTreeDocumentId(rootUri)
            } catch (e: Exception) {
                ""
            }
            val pathSegment = treeDocId.substringAfter(":", "").trim('/', '\\')
            
            if (pathSegment.isNotEmpty()) {
                // User chose a specific subfolder (not the root), use it directly.
                return rootDoc
            }
            
            // User chose the root of the volume.
            // 1. Try to find the folder name from the database based on the volume UUID.
            val volumeUuid = extractVolumeId(rootUri)
            val db = AppDatabase.getDatabase(context)
            
            // 1. Try to find the archive in the database
            val archive = if (volumeUuid != null) db.archiveDao().getById(volumeUuid) else null
            
            var folderName = if (archive != null) {
                if (archive.folderName.isNotEmpty()) {
                    archive.folderName
                } else {
                    // For backwards compatibility: update the folderName to "Arhiv-${archive.name}"
                    val name = "Arhiv-${archive.name}"
                    db.archiveDao().insert(archive.copy(folderName = name))
                    name
                }
            } else {
                null
            }

            // 3. If no folderName is registered yet, use the auto-created fallback folder name
            if (folderName == null) {
                folderName = getAutoCreatedFolderName(context)
            }

            val subDir = if (folderName.isEmpty()) {
                rootDoc
            } else {
                // Optimize: Build child URI directly to avoid slow rootDoc.listFiles() caused by findFile
                val rootDocId = android.provider.DocumentsContract.getTreeDocumentId(rootUri)
                val childDocId = if (rootDocId.endsWith(":")) "$rootDocId$folderName" else "$rootDocId/$folderName"
                val childUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(rootUri, childDocId)
                val directDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, childUri)
                if (directDoc != null && directDoc.exists()) directDoc else null
            }
            if (subDir != null) {
                return subDir
            }
            
            if (createIfNotExist && folderName.isNotEmpty()) {
                return rootDoc.createDirectory(folderName)
            }
            return if (folderName.isEmpty()) rootDoc else null
        } catch (e: Exception) {
            DebugLogBuffer.log("OtgFolderResolver", "Error resolving archive dir: ${e.localizedMessage}")
            return null
        }
    }
}
