package by.w6.my1drive.utils

import by.w6.my1drive.BuildConfig

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import by.w6.my1drive.domain.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
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

    /**
     * Diagnose current permission state for debugging.
     */
    private fun diagnosePermissions(): String {
        val sb = StringBuilder()
        sb.appendLine("=== Permission Diagnostics ===")
        sb.appendLine("Android API: ${Build.VERSION.SDK_INT}")
        sb.appendLine("App: my1drive v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})")
        sb.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val img = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES)
            val vid = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO)
            sb.appendLine("READ_MEDIA_IMAGES: ${if (img == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"}")
            sb.appendLine("READ_MEDIA_VIDEO: ${if (vid == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"}")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val vus = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            sb.appendLine("READ_MEDIA_VISUAL_USER_SELECTED: ${if (vus == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"}")
        }
        val ext = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        sb.appendLine("READ_EXTERNAL_STORAGE: ${if (ext == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"}")

        return sb.toString()
    }

    /**
     * Check if we can actually read a URI before attempting full operations.
     */
    private fun checkUriAccess(uri: Uri): String {
        val sb = StringBuilder()
        sb.appendLine("=== URI Access Check: $uri ===")

        // Check via checkCallingOrSelfUriPermission
        try {
            val readPerm = context.checkCallingOrSelfUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            sb.appendLine("URI read permission check: ${if (readPerm == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"}")
        } catch (e: Exception) {
            sb.appendLine("URI read permission check error: ${e.javaClass.simpleName}: ${e.message}")
        }

        // Try openFileDescriptor
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                sb.appendLine("openFileDescriptor: SUCCESS (size=${pfd.statSize})")
                pfd.close()
            } else {
                sb.appendLine("openFileDescriptor: returned NULL")
            }
        } catch (e: Exception) {
            sb.appendLine("openFileDescriptor: FAILED - ${e.javaClass.simpleName}: ${e.message}")
        }

        // Try openInputStream
        try {
            val stream = context.contentResolver.openInputStream(uri)
            if (stream != null) {
                sb.appendLine("openInputStream: SUCCESS")
                stream.close()
            } else {
                sb.appendLine("openInputStream: returned NULL")
            }
        } catch (e: Exception) {
            sb.appendLine("openInputStream: FAILED - ${e.javaClass.simpleName}: ${e.message}")
        }

        // Query content resolver for type
        try {
            val type = context.contentResolver.getType(uri)
            sb.appendLine("ContentResolver.getType: $type")
        } catch (e: Exception) {
            sb.appendLine("ContentResolver.getType: FAILED - ${e.javaClass.simpleName}: ${e.message}")
        }

        return sb.toString()
    }

    fun copyAndVerifyItem(item: MediaItem, targetDirUri: Uri): Flow<CopyVerifyResult> = flow {
        try {
            emit(CopyVerifyResult.Progress(item.displayName, "preparing", 0.05f))

            // Diagnose permissions and URI access before doing anything
            val permDiag = diagnosePermissions()
            val uriDiag = checkUriAccess(item.uri)

            emit(CopyVerifyResult.Progress(item.displayName, "preparing", 0.1f))

            val srcHash = try {
                calculateSha256(item.uri)
            } catch (e: Exception) {
                val details = """
                    ${e.javaClass.name}: ${e.message}
                    File: ${item.displayName}
                    URI: ${item.uri}
                    MimeType: ${item.mimeType}
                    Size: ${item.size} bytes
                    $permDiag
                    $uriDiag
                """.trimIndent()
                emit(CopyVerifyResult.Skipped(item, details))
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

            try {
                context.contentResolver.openOutputStream(destUri)?.use { output ->
                    context.contentResolver.openInputStream(item.uri)?.use { input ->
                        val buffer = ByteArray(65536) // 64KB buffer
                        var totalBytesCopied = 0L
                        var bytesRead = input.read(buffer)
                        while (bytesRead != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesCopied += bytesRead
                            if (item.size > 0) {
                                val progress = 0.3f + (totalBytesCopied.toFloat() / item.size) * 0.4f
                                emit(CopyVerifyResult.Progress(item.displayName, "copying_percent:${(progress * 100).toInt()}", progress))
                            }
                            bytesRead = input.read(buffer)
                        }
                    }
                } ?: throw Exception("otg_write_failed")

                // Force sync to physical storage to bypass OS write caching
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
                val details = """
                    COPY PHASE: ${se.javaClass.name}: ${se.message}
                    File: ${item.displayName}
                    URI: ${item.uri}
                    MimeType: ${item.mimeType}
                    Size: ${item.size} bytes
                    $permDiag
                    $uriDiag
                """.trimIndent()
                emit(CopyVerifyResult.Skipped(item, details))
                return@flow
            } catch (fnf: java.io.FileNotFoundException) {
                val createdFile = DocumentFile.fromSingleUri(context, destUri)
                createdFile?.delete()
                val details = """
                    COPY PHASE: ${fnf.javaClass.name}: ${fnf.message}
                    File: ${item.displayName}
                    URI: ${item.uri}
                    $permDiag
                    $uriDiag
                """.trimIndent()
                emit(CopyVerifyResult.Skipped(item, details))
                return@flow
            }

            emit(CopyVerifyResult.Progress(item.displayName, "verifying", 0.8f))
            val otgHash = calculateSha256(destUri)

            if (srcHash != otgHash) {
                val createdFile = DocumentFile.fromSingleUri(context, destUri)
                createdFile?.delete()
                throw Exception("verification_failed")
            }

            // Thumbnail is NOT generated at archive time.
            // It will be loaded on-demand by OtgThumbnailFetcher when the user views the file.
            emit(CopyVerifyResult.Success(item, srcHash, destUri.toString(), thumbnailPath = null))
        } catch (e: Exception) {
            val sw = java.io.StringWriter()
            e.printStackTrace(java.io.PrintWriter(sw))
            val permDiag = try { diagnosePermissions() } catch (_: Exception) { "N/A" }
            val uriDiag = try { checkUriAccess(item.uri) } catch (_: Exception) { "N/A" }
            val details = """
                Exception: ${e.javaClass.name}
                Message: ${e.message}
                File: ${item.displayName}
                URI: ${item.uri}
                MimeType: ${item.mimeType}
                Size: ${item.size} bytes
                $permDiag
                $uriDiag
                Stacktrace:
                $sw
            """.trimIndent()
            emit(CopyVerifyResult.Error(item.displayName, details))
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

            // Read source data from OTG
            val srcBytes = context.contentResolver.openInputStream(otgUri)?.use { it.readBytes() }
                ?: throw Exception("restore_read_failed")

            emit(RestoreResult.Progress(item.displayName, "restore_writing", 0.4f))

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
                }
                val insertedUri = context.contentResolver.insert(collection, values)
                    ?: throw Exception("restore_mediastore_insert_failed")
                insertedUri
            } else {
                throw Exception("restore_api_not_supported")
            }

            // Write bytes to destination
            context.contentResolver.openOutputStream(destUri)?.use { output ->
                output.write(srcBytes)
            } ?: throw Exception("restore_write_failed")

            // If MediaStore pending, mark as complete
            if (targetDirUri == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                context.contentResolver.update(destUri, values, null, null)
            }

            emit(RestoreResult.Progress(item.displayName, "restore_verifying", 0.8f))

            // Verify hash
            if (item.hash != null) {
                val destHash = calculateSha256(destUri)
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
            ?: throw Exception("Не удалось открыть поток для чтения $uri")
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

    fun createThumbnail(uri: Uri, isVideo: Boolean): File? {
        return try {
            val bitmap: Bitmap? = if (isVideo) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    val frame = retriever.frameAtTime
                    if (frame != null) {
                        val scaled = Bitmap.createScaledBitmap(frame, 512, (512 * frame.height / frame.width).coerceAtLeast(1), true)
                        if (scaled != frame) {
                            frame.recycle()
                        }
                        scaled
                    } else null
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                } finally {
                    retriever.release()
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        context.contentResolver.loadThumbnail(uri, android.util.Size(512, 512), null)
                    } catch (e: Exception) {
                        decodeBitmapFallback(uri)
                    }
                } else {
                    decodeBitmapFallback(uri)
                }
            }

            if (bitmap == null) return null

            val thumbnailFile = File(context.filesDir, "thumb_${System.currentTimeMillis()}.jpg")
            FileOutputStream(thumbnailFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            bitmap.recycle()
            thumbnailFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun decodeBitmapFallback(uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(input, null, options)
                var scale = 1
                while (options.outWidth / scale / 2 >= 512 && options.outHeight / scale / 2 >= 512) {
                    scale *= 2
                }
                val options2 = BitmapFactory.Options().apply {
                    inSampleSize = scale
                }
                context.contentResolver.openInputStream(uri)?.use { input2 ->
                    BitmapFactory.decodeStream(input2, null, options2)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun deleteLocalFile(uri: Uri): Boolean {
        val deleted = context.contentResolver.delete(uri, null, null)
        return deleted > 0
    }
    /**
     * Reads the archive UUID from `.my1drive_uuid` in the OTG root.
     * If the file doesn't exist, creates a new UUID and writes it.
     * Returns the UUID string, or null if the directory is not writable.
     */
    fun getOrCreateArchiveId(dir: DocumentFile): String? {
        return try {
            val uuidFile = dir.findFile(".my1drive_uuid")
            if (uuidFile != null && uuidFile.exists()) {
                // Read existing UUID
                context.contentResolver.openInputStream(uuidFile.uri)?.use { stream ->
                    stream.bufferedReader().readText().trim().takeIf { it.isNotEmpty() }
                }
            } else {
                // Create new UUID and write it
                val newUuid = java.util.UUID.randomUUID().toString()
                val newFile = dir.createFile("text/plain", ".my1drive_uuid")
                    ?: return null
                context.contentResolver.openOutputStream(newFile.uri)?.use { out ->
                    out.write(newUuid.toByteArray())
                }
                newUuid
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
