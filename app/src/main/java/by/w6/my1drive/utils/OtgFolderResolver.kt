package by.w6.my1drive.utils

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import by.w6.my1drive.utils.DebugLogBuffer

object OtgFolderResolver {
    
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
     * find or create the "Arhiv-<DeviceName>" folder inside it.
     * Otherwise, if the user explicitly selected a subdirectory, it returns that subdirectory directly.
     */
    fun getArchiveDir(context: Context, rootUri: Uri, createIfNotExist: Boolean = true): DocumentFile? {
        try {
            val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: return null
            if (!rootDoc.exists() || !rootDoc.canRead()) {
                return null
            }
            
            // Check if the selected URI has a subfolder path.
            // If the document ID is just the volume root (e.g., "1234-5678:"), the path segment is empty.
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
            
            // User chose the root of the volume. Resolve/create the "Arhiv-<DeviceName>" folder.
            val folderName = getAutoCreatedFolderName(context)
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
