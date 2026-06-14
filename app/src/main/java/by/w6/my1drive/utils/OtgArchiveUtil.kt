package by.w6.my1drive.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import by.w6.my1drive.domain.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.security.MessageDigest

sealed class CopyVerifyResult {
    data class Progress(val displayName: String, val step: String, val progressFraction: Float) : CopyVerifyResult()
    data class Success(val item: MediaItem, val hash: String, val otgUri: String, val thumbnailPath: String?) : CopyVerifyResult()
    data class Skipped(val item: MediaItem, val message: String) : CopyVerifyResult()
    data class Error(val displayName: String, val message: String) : CopyVerifyResult()
}

sealed class RestoreResult {
    data class Progress(val displayName: String, val step: String, val progressFraction: Float) : RestoreResult()
    data class Success(val item: MediaItem) : RestoreResult()
    data class Error(val displayName: String, val message: String) : RestoreResult()
}

class OtgArchiveUtil(private val context: Context) {

    fun copyAndVerifyItem(item: MediaItem, targetDirUri: Uri): Flow<CopyVerifyResult> = flow {
        try {
            emit(CopyVerifyResult.Progress(item.displayName, "preparing", 0.05f))

            // Check if the source file is accessible
            try {
                context.contentResolver.openInputStream(item.uri)?.use { }
            } catch (e: Exception) {
                emit(CopyVerifyResult.Skipped(item, "${e.javaClass.name}: ${e.message}"))
                return@flow
            }

            emit(CopyVerifyResult.Progress(item.displayName, "copying", 0.3f))
            
            val dir = DocumentFile.fromTreeUri(context, targetDirUri) ?: throw Exception("otg_access_failed")
            
            val existingFile = dir.findFile(item.displayName)
            val destUri = if (existingFile != null) {
                existingFile.uri
            } else {
                val file = dir.createFile(item.mimeType, item.displayName) ?: throw Exception("otg_create_failed")
                file.uri
            }

            val digest = MessageDigest.getInstance("SHA-256")

            try {
                context.contentResolver.openOutputStream(destUri)?.use { output ->
                    context.contentResolver.openInputStream(item.uri)?.use { input ->
                        var lastEmittedPercent = -1
                        val buffer = ByteArray(65536)
                        var totalBytesCopied = 0L
                        var bytesRead = input.read(buffer)
                        while (bytesRead != -1) {
                            output.write(buffer, 0, bytesRead)
                            digest.update(buffer, 0, bytesRead)
                            totalBytesCopied += bytesRead
                            if (item.size > 0) {
                                val progress = 0.3f + (totalBytesCopied.toFloat() / item.size) * 0.4f
                                val percent = (progress * 100).toInt()
                                if (percent != lastEmittedPercent) {
                                    lastEmittedPercent = percent
                                    emit(CopyVerifyResult.Progress(item.displayName, "copying_percent:$percent", progress))
                                }
                            }
                            bytesRead = input.read(buffer)
                        }
                    }
                } ?: throw Exception("otg_write_failed")

                try {
                    context.contentResolver.openFileDescriptor(destUri, "rw")?.use { pfd ->
                        pfd.fileDescriptor.sync()
                    }
                } catch (e: Exception) {
                    try {
                        context.contentResolver.openFileDescriptor(destUri, "w")?.use { pfd ->
                            pfd.fileDescriptor.sync()
                        }
                    } catch (e2: Exception) {
                        e2.printStackTrace()
                    }
                }
            } catch (se: SecurityException) {
                val createdFile = DocumentFile.fromSingleUri(context, destUri)
                createdFile?.delete()
                emit(CopyVerifyResult.Skipped(item, "COPY PHASE: ${se.javaClass.name}: ${se.message}"))
                return@flow
            } catch (fnf: java.io.FileNotFoundException) {
                val createdFile = DocumentFile.fromSingleUri(context, destUri)
                createdFile?.delete()
                emit(CopyVerifyResult.Skipped(item, "COPY PHASE: ${fnf.javaClass.name}: ${fnf.message}"))
                return@flow
            }

            emit(CopyVerifyResult.Progress(item.displayName, "verifying", 0.8f))
            
            val srcHash = digest.digest().joinToString("") { "%02x".format(it) }
            val createdFile = DocumentFile.fromSingleUri(context, destUri)
            val destSize = createdFile?.length() ?: 0L

            if (item.size > 0 && destSize != item.size) {
                createdFile?.delete()
                throw Exception("verification_failed: size mismatch (expected ${item.size}, got $destSize)")
            }

            // Thumbnail is NOT generated at archive time.
            // It will be loaded on-demand by OtgThumbnailFetcher when the user views the file.
            emit(CopyVerifyResult.Success(item, srcHash, destUri.toString(), thumbnailPath = null))
        } catch (e: Exception) {
            emit(CopyVerifyResult.Error(item.displayName, "${e.javaClass.name}: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Restore a file from OTG archive back to device storage.
     *
     * @param item The archived MediaItem (must have otgUri set)
     * @param targetDirUri SAF URI of target folder (used when originalRelativePath is null or override requested).
     *                     If null and originalRelativePath is available, restores via MediaStore to original path.
     */
    fun restoreItem(item: MediaItem, targetDirUri: Uri?): Flow<RestoreResult> = flow {
        try {
            emit(RestoreResult.Progress(item.displayName, "restore_preparing", 0.05f))

            val otgUriString = item.otgUri
                ?: throw Exception("restore_no_otg_uri")
            val otgUri = Uri.parse(otgUriString)

            emit(RestoreResult.Progress(item.displayName, "restore_reading", 0.15f))

            val destUri: Uri = if (targetDirUri != null) {
                // Write via SAF to chosen folder
                val dir = DocumentFile.fromTreeUri(context, targetDirUri)
                    ?: throw Exception("restore_target_access_failed")
                val existing = dir.findFile(item.displayName)
                if (existing != null) {
                    existing.uri
                } else {
                    dir.createFile(item.mimeType, item.displayName)?.uri
                        ?: throw Exception("restore_create_failed")
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Write via MediaStore to original relative path
                val relativePath = item.originalRelativePath ?: run {
                    if (item.mimeType.startsWith("video/")) "Movies/" else "Pictures/"
                }
                val collection = if (item.mimeType.startsWith("video/")) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, item.displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                    put(MediaStore.MediaColumns.DATE_MODIFIED, item.dateModified)
                    put(MediaStore.MediaColumns.DATE_ADDED, item.dateModified)
                    if (item.mimeType.startsWith("image/")) {
                        put(MediaStore.Images.ImageColumns.DATE_TAKEN, item.dateModified * 1000)
                    } else if (item.mimeType.startsWith("video/")) {
                        put(MediaStore.Video.VideoColumns.DATE_TAKEN, item.dateModified * 1000)
                    }
                }
                val insertedUri = context.contentResolver.insert(collection, values)
                    ?: throw Exception("restore_mediastore_insert_failed")
                insertedUri
            } else {
                throw Exception("restore_api_not_supported")
            }

            emit(RestoreResult.Progress(item.displayName, "restore_writing", 0.4f))

            val digest = MessageDigest.getInstance("SHA-256")

            // Write bytes to destination using stream
            context.contentResolver.openOutputStream(destUri)?.use { output ->
                context.contentResolver.openInputStream(otgUri)?.use { input ->
                    var lastEmittedPercent = -1
                    val buffer = ByteArray(65536)
                    var totalBytesCopied = 0L
                    var bytesRead = input.read(buffer)
                    while (bytesRead != -1) {
                        output.write(buffer, 0, bytesRead)
                        digest.update(buffer, 0, bytesRead)
                        totalBytesCopied += bytesRead
                        if (item.size > 0) {
                            val progress = 0.4f + (totalBytesCopied.toFloat() / item.size) * 0.4f
                            val percent = (progress * 100).toInt()
                            if (percent != lastEmittedPercent) {
                                lastEmittedPercent = percent
                                emit(RestoreResult.Progress(item.displayName, "restore_writing_percent:$percent", progress))
                            }
                        }
                        bytesRead = input.read(buffer)
                    }
                }
            } ?: throw Exception("restore_write_failed")

            // If MediaStore pending, mark as complete
            if (targetDirUri == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                    put(MediaStore.MediaColumns.DATE_MODIFIED, item.dateModified)
                    put(MediaStore.MediaColumns.DATE_ADDED, item.dateModified)
                }
                context.contentResolver.update(destUri, values, null, null)
            }

            emit(RestoreResult.Progress(item.displayName, "restore_verifying", 0.8f))

            // Verify hash
            if (item.hash != null) {
                val destHash = digest.digest().joinToString("") { "%02x".format(it) }
                if (destHash != item.hash) {
                    // Try to delete the failed restore
                    try { context.contentResolver.delete(destUri, null, null) } catch (_: Exception) {}
                    throw Exception("restore_verification_failed")
                }
            }

            emit(RestoreResult.Success(item))
        } catch (e: Exception) {
            val sw = java.io.StringWriter()
            e.printStackTrace(java.io.PrintWriter(sw))
            emit(RestoreResult.Error(item.displayName, "${e.message}\n$sw"))
        }
    }.flowOn(Dispatchers.IO)

        fun calculateSha256(uri: Uri): String {
                val digest = MessageDigest.getInstance("SHA-256")
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("Failed to open stream for $uri")
        stream.use { input ->
            val buffer = ByteArray(65536)
            var bytesRead = input.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
