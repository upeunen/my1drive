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
            var folderName = if (volumeUuid != null) {
                val db = AppDatabase.getDatabase(context)
                val archive = db.archiveDao().getById(volumeUuid)
                if (archive != null) {
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
            } else {
                null
            }

            // 2. If no folderName is registered yet, use the auto-created fallback folder name
            if (folderName == null) {
                folderName = getAutoCreatedFolderName(context)
            }

            val subDir = rootDoc.findFile(folderName)
            if (subDir != null) {
                return subDir
            }
            
            if (createIfNotExist) {
                return rootDoc.createDirectory(folderName)
            }
            return null
        } catch (e: Exception) {
            DebugLogBuffer.log("OtgFolderResolver", "Error resolving archive dir: ${e.localizedMessage}")
            return null
        }
    }
}
