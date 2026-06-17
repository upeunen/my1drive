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
        var createdFile: DocumentFile? = null
        var success = false
        val logTag = "ArchiveCopy"
        try {
            DebugLogBuffer.log(logTag, "Start copyAndVerifyItem: ${item.displayName}, size=${item.size}, mime=${item.mimeType}, targetDir=$targetDirUri")
            emit(CopyVerifyResult.Progress(item.displayName, "preparing", 0.0f))

            // Reject zero-size source files (cloud stubs / remote-only files)
            // They cannot be read and would create empty archive entries
            if (item.size <= 0) {
                DebugLogBuffer.log(logTag, "Skipping file ${item.displayName}: size <= 0 (${item.size})")
                emit(CopyVerifyResult.Skipped(item, "SKIP: source has zero size (cloud/remote file? size=${item.size})"))
                return@flow
            }

            val dir = DocumentFile.fromTreeUri(context, targetDirUri)
                ?: throw Exception("otg_access_failed")

            val existingFile = dir.findFile(item.displayName)
            val file = if (existingFile != null) {
                DebugLogBuffer.log(logTag, "File ${item.displayName} already exists on OTG, reusing existing file")
                existingFile
            } else {
                DebugLogBuffer.log(logTag, "Creating new file ${item.displayName} on OTG")
                dir.createFile(item.mimeType, item.displayName)
                    ?: throw Exception("otg_create_failed")
            }
            createdFile = file
            val destUri = file.uri
            DebugLogBuffer.log(logTag, "Target file URI: $destUri")

            val digest = MessageDigest.getInstance("SHA-256")
            var totalBytesCopied = 0L
            val buffer = ByteArray(262144) // 256 KB buffer

            DebugLogBuffer.log(logTag, "Opening streams for copy...")
            val input = try {
                context.contentResolver.openInputStream(item.uri)
                    ?: throw Exception("otg_read_stream_failed: input stream is null")
            } catch (e: java.io.FileNotFoundException) {
                DebugLogBuffer.log(logTag, "Source file not found on device (stale MediaStore): ${item.displayName}")
                emit(CopyVerifyResult.Skipped(item, "SKIP: source file not found on device: ${e.localizedMessage}"))
                return@flow
            }

            input.use { inputStream ->
                context.contentResolver.openFileDescriptor(destUri, "w")?.use { pfd ->
                    java.io.FileOutputStream(pfd.fileDescriptor).use { output ->
                        var lastEmittedStep = -1
                        var bytesRead = inputStream.read(buffer)
                        while (bytesRead != -1) {
                            output.write(buffer, 0, bytesRead)
                            digest.update(buffer, 0, bytesRead)
                            totalBytesCopied += bytesRead
                            if (item.size > 0) {
                                val pct = ((totalBytesCopied.toFloat() / item.size) * 100).toInt()
                                val step = pct / 10 // emit every 10%
                                if (step != lastEmittedStep) {
                                      lastEmittedStep = step
                                      emit(CopyVerifyResult.Progress(
                                          item.displayName,
                                          "copying",
                                          (totalBytesCopied.toFloat() / item.size).coerceAtMost(0.95f)
                                      ))
                                }
                            }
                            bytesRead = inputStream.read(buffer)
                        }
                        output.flush()
                    }
                    try {
                        DebugLogBuffer.log(logTag, "Syncing file descriptor to disk...")
                        pfd.fileDescriptor.sync()
                        DebugLogBuffer.log(logTag, "Sync completed successfully")
                    } catch (syncEx: Exception) {
                        DebugLogBuffer.log(logTag, "Failed to sync file descriptor: ${syncEx.localizedMessage}")
                    }
                } ?: throw Exception("otg_write_failed")
            }

            emit(CopyVerifyResult.Progress(item.displayName, "verifying", 0.95f))

            val srcHash = digest.digest().joinToString("") { "%02x".format(it) }
            DebugLogBuffer.log(logTag, "Copy finished. Expected size: ${item.size}, Copied: $totalBytesCopied. Hash: $srcHash")

            // Verify by actual bytes copied, not by DocumentFile.length() (which may lie)
            if (totalBytesCopied != item.size) {
                DebugLogBuffer.log(logTag, "Size mismatch: expected ${item.size}, copied $totalBytesCopied. Skipped.")
                emit(CopyVerifyResult.Skipped(item,
                    "SIZE MISMATCH: expected ${item.size} bytes, copied $totalBytesCopied bytes"))
                return@flow
            }

            // Thumbnail is NOT generated at archive time.
            // It will be loaded on-demand by OtgThumbnailFetcher when the user views the file.
            success = true
            DebugLogBuffer.log(logTag, "Successfully archived ${item.displayName}")
            emit(CopyVerifyResult.Success(item, srcHash, destUri.toString(), thumbnailPath = null))
        } catch (e: Exception) {
            DebugLogBuffer.log(logTag, "Error copying ${item.displayName}: ${e.javaClass.name} - ${e.localizedMessage}")
            val sw = java.io.StringWriter()
            e.printStackTrace(java.io.PrintWriter(sw))
            DebugLogBuffer.log(logTag, "Stacktrace: $sw")
            if (e is kotlinx.coroutines.CancellationException) {
                DebugLogBuffer.log(logTag, "Copying cancelled for ${item.displayName}")
                throw e
            }
            emit(CopyVerifyResult.Error(item.displayName, "${e.javaClass.name}: ${e.message}"))
        } finally {
            if (!success) {
                createdFile?.let { f ->
                    try {
                        val deleted = f.delete()
                        DebugLogBuffer.log(logTag, "Cleanup: deleted failed/cancelled file ${item.displayName}, success=$deleted, uri=${f.uri}")
                    } catch (cleanupEx: Exception) {
                        DebugLogBuffer.log(logTag, "Cleanup error: failed to delete ${item.displayName}: ${cleanupEx.localizedMessage}")
                    }
                }
            }
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
        val logTag = "ArchiveRestore"
        var destUri: Uri? = null
        var createdFile: DocumentFile? = null
        try {
            DebugLogBuffer.log(logTag, "Start restoreItem: ${item.displayName}, size=${item.size}, hash=${item.hash}, targetDirUri=$targetDirUri")
            emit(RestoreResult.Progress(item.displayName, "restore_preparing", 0.05f))

            val otgUriString = item.otgUri
                ?: throw Exception("restore_no_otg_uri")
            val otgUri = Uri.parse(otgUriString)
            DebugLogBuffer.log(logTag, "Source OTG URI: $otgUri")

            emit(RestoreResult.Progress(item.displayName, "restore_reading", 0.15f))

            destUri = if (targetDirUri != null) {
                // Write via SAF to chosen folder
                DebugLogBuffer.log(logTag, "Restoring via SAF to chosen folder: $targetDirUri")
                val dir = DocumentFile.fromTreeUri(context, targetDirUri)
                    ?: throw Exception("restore_target_access_failed")
                val existing = dir.findFile(item.displayName)
                val file = if (existing != null) {
                    DebugLogBuffer.log(logTag, "File already exists in target folder, overwriting: ${existing.uri}")
                    existing
                } else {
                    DebugLogBuffer.log(logTag, "Creating new file in target folder")
                    dir.createFile(item.mimeType, item.displayName)
                        ?: throw Exception("restore_create_failed")
                }
                createdFile = file
                file.uri
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Write via MediaStore to original relative path
                val relativePath = item.originalRelativePath ?: run {
                    if (item.mimeType.startsWith("video/")) "Movies/" else "Pictures/"
                }
                DebugLogBuffer.log(logTag, "Restoring via MediaStore to original path: $relativePath")
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
                DebugLogBuffer.log(logTag, "MediaStore inserted pending URI: $insertedUri")
                insertedUri
            } else {
                // Android 9 fallback (SDK 28): insert directly to external content
                val collection = if (item.mimeType.startsWith("video/")) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, item.displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
                    put(MediaStore.MediaColumns.DATE_MODIFIED, item.dateModified)
                    put(MediaStore.MediaColumns.DATE_ADDED, item.dateModified)
                }
                val insertedUri = context.contentResolver.insert(collection, values)
                    ?: throw Exception("restore_mediastore_insert_failed")
                DebugLogBuffer.log(logTag, "MediaStore legacy inserted URI: $insertedUri")
                insertedUri
            }

            emit(RestoreResult.Progress(item.displayName, "restore_writing", 0.4f))

            // Use real file size from OTG if item.size is zero (damaged metadata)
            val effectiveSize = if (item.size > 0) {
                item.size
            } else {
                try {
                    val length = DocumentFile.fromSingleUri(context, otgUri)?.length() ?: 0L
                    DebugLogBuffer.log(logTag, "MediaItem size is 0, queried physical length: $length")
                    length
                } catch (lenEx: Exception) {
                    DebugLogBuffer.log(logTag, "Failed to query physical length: ${lenEx.localizedMessage}")
                    0L
                }
            }
            DebugLogBuffer.log(logTag, "Effective restore size: $effectiveSize")
            
            // If both are zero, this is a genuine empty file — skip it
            if (effectiveSize <= 0) {
                throw Exception("restore_empty_file: archived file has zero bytes")
            }

            val digest = MessageDigest.getInstance("SHA-256")

            // Write bytes to destination using stream with fsync
            DebugLogBuffer.log(logTag, "Opening streams for restore...")
            context.contentResolver.openFileDescriptor(destUri, "w")?.use { pfd ->
                java.io.FileOutputStream(pfd.fileDescriptor).use { output ->
                    context.contentResolver.openInputStream(otgUri)?.use { input ->
                        var lastEmittedPercent = -1
                        val buffer = ByteArray(65536)
                        var totalBytesCopied = 0L
                        var bytesRead = input.read(buffer)
                        while (bytesRead != -1) {
                            output.write(buffer, 0, bytesRead)
                            digest.update(buffer, 0, bytesRead)
                            totalBytesCopied += bytesRead
                            if (effectiveSize > 0) {
                                val progress = 0.4f + (totalBytesCopied.toFloat() / effectiveSize) * 0.4f
                                val percent = (progress * 100).toInt()
                                if (percent != lastEmittedPercent) {
                                    lastEmittedPercent = percent
                                    emit(RestoreResult.Progress(item.displayName, "restore_writing_percent:$percent", progress))
                                }
                            }
                            bytesRead = input.read(buffer)
                        }
                    } ?: throw Exception("restore_read_stream_failed: input stream is null")
                    output.flush()
                }
                try {
                    DebugLogBuffer.log(logTag, "Syncing destination file descriptor to disk...")
                    pfd.fileDescriptor.sync()
                    DebugLogBuffer.log(logTag, "Sync completed successfully")
                } catch (syncEx: Exception) {
                    DebugLogBuffer.log(logTag, "Failed to sync file descriptor: ${syncEx.localizedMessage}")
                }
            } ?: throw Exception("restore_write_failed")

            // If MediaStore pending, mark as complete
            if (targetDirUri == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                DebugLogBuffer.log(logTag, "Marking MediaStore file as not pending")
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
                DebugLogBuffer.log(logTag, "Verifying restored hash. Expected: ${item.hash}, Restored: $destHash")
                if (destHash != item.hash) {
                    throw Exception("restore_verification_failed: hash mismatch (expected ${item.hash}, got $destHash)")
                }
            }

            DebugLogBuffer.log(logTag, "Successfully restored ${item.displayName}")
            emit(RestoreResult.Success(item))
        } catch (e: Exception) {
            DebugLogBuffer.log(logTag, "Error restoring ${item.displayName}: ${e.javaClass.name} - ${e.localizedMessage}")
            val sw = java.io.StringWriter()
            e.printStackTrace(java.io.PrintWriter(sw))
            DebugLogBuffer.log(logTag, "Stacktrace: $sw")
            
            if (destUri != null) {
                try {
                    val deleted = if (createdFile != null) {
                        createdFile.delete()
                    } else {
                        context.contentResolver.delete(destUri, null, null) > 0
                    }
                    DebugLogBuffer.log(logTag, "Cleanup: deleted failed restore file ${item.displayName}, success=$deleted, uri=$destUri")
                } catch (cleanupEx: Exception) {
                    DebugLogBuffer.log(logTag, "Cleanup error during restore: ${cleanupEx.localizedMessage}")
                }
            }
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
