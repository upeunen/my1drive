package by.w6.my1drive.utils

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import java.io.File

object StorageSpaceUtil {

    /**
     * Returns the available bytes on the target storage device.
     * On Android 8.0+ (API 26+), attempts to query [StorageManager.getAllocatableBytes].
     * Falls back to [Environment.getExternalStorageDirectory].usableSpace if allocatable bytes query fails.
     */
    fun getAvailableStorageBytes(context: Context, targetDirUri: Uri? = null): Long {
        if (targetDirUri != null) {
            try {
                val file = getFileFromUri(context, targetDirUri)
                if (file != null && file.exists()) {
                    val usable = file.usableSpace
                    if (usable > 0) return usable
                }
            } catch (e: Exception) {
                DebugLogBuffer.log("StorageSpaceUtil", "Failed to get usable space for target URI: ${e.localizedMessage}")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val storageManager = context.getSystemService(StorageManager::class.java)
                if (storageManager != null) {
                    val allocatableBytes = storageManager.getAllocatableBytes(StorageManager.UUID_DEFAULT)
                    DebugLogBuffer.log("StorageSpaceUtil", "StorageManager.getAllocatableBytes: $allocatableBytes")
                    if (allocatableBytes > 0) {
                        return allocatableBytes
                    }
                }
            } catch (e: Exception) {
                DebugLogBuffer.log("StorageSpaceUtil", "getAllocatableBytes failed: ${e.localizedMessage}. Falling back to usableSpace.")
            }
        }

        return try {
            Environment.getExternalStorageDirectory().usableSpace
        } catch (e: Exception) {
            DebugLogBuffer.log("StorageSpaceUtil", "usableSpace query failed: ${e.localizedMessage}")
            Long.MAX_VALUE
        }
    }

    private fun getFileFromUri(context: Context, uri: Uri): File? {
        if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path?.let { File(it) }
        }
        if ("content".equals(uri.scheme, ignoreCase = true)) {
            try {
                val projection = arrayOf(android.provider.MediaStore.MediaColumns.DATA)
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
                        if (idx != -1) {
                            val path = cursor.getString(idx)
                            if (!path.isNullOrEmpty()) return File(path)
                        }
                    }
                }
            } catch (_: Exception) {}

            try {
                if (android.provider.DocumentsContract.isDocumentUri(context, uri)) {
                    val docId = android.provider.DocumentsContract.getDocumentId(uri)
                    if (docId != null && docId.startsWith("primary:")) {
                        val relativePath = docId.substringAfter("primary:")
                        return File(Environment.getExternalStorageDirectory(), relativePath)
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }
}
