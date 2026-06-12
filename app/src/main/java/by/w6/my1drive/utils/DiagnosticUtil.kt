package by.w6.my1drive.utils

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import by.w6.my1drive.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Diagnostic utility that checks all permissions and tries to access
 * media files via various methods, producing a detailed log.
 */
object DiagnosticUtil {

    suspend fun runFullDiagnostic(context: Context, otgUri: android.net.Uri?): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()

        sb.appendLine("╔══════════════════════════════════════╗")
        sb.appendLine("║   My1Drive DIAGNOSTIC REPORT         ║")
        sb.appendLine("╚══════════════════════════════════════╝")
        sb.appendLine()

        // 1. Device info
        sb.appendLine("=== DEVICE INFO ===")
        sb.appendLine("Manufacturer: ${Build.MANUFACTURER}")
        sb.appendLine("Model: ${Build.MODEL}")
        sb.appendLine("Android Version: ${Build.VERSION.RELEASE}")
        sb.appendLine("API Level: ${Build.VERSION.SDK_INT}")
        sb.appendLine("Security Patch: ${Build.VERSION.SECURITY_PATCH}")
        sb.appendLine("App Version: ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})")
        sb.appendLine("Package: ${context.packageName}")
        sb.appendLine("targetSdk: ${context.applicationInfo.targetSdkVersion}")
        sb.appendLine()

        // 2. Permission states
        sb.appendLine("=== PERMISSIONS ===")
        val permsToCheck = mutableListOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permsToCheck.add(Manifest.permission.READ_MEDIA_IMAGES)
            permsToCheck.add(Manifest.permission.READ_MEDIA_VIDEO)
            permsToCheck.add(Manifest.permission.READ_MEDIA_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permsToCheck.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        }
        for (perm in permsToCheck) {
            val status = ContextCompat.checkSelfPermission(context, perm)
            val shortName = perm.substringAfterLast(".")
            sb.appendLine("  $shortName: ${if (status == PackageManager.PERMISSION_GRANTED) "✅ GRANTED" else "❌ DENIED"}")
        }
        sb.appendLine()

        // 3. MediaStore version
        sb.appendLine("=== MEDIASTORE ===")
        try {
            val version = MediaStore.getVersion(context)
            sb.appendLine("MediaStore version: $version")
        } catch (e: Exception) {
            sb.appendLine("MediaStore version: ERROR - ${e.message}")
        }
        sb.appendLine()

        // 4. Query images and test access
        sb.appendLine("=== IMAGE FILES TEST (first 5) ===")
        testMediaAccess(context, sb, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, isImage = true, limit = 5)
        sb.appendLine()

        // 5. Query videos and test access
        sb.appendLine("=== VIDEO FILES TEST (first 5) ===")
        testMediaAccess(context, sb, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, isImage = false, limit = 5)
        sb.appendLine()

        // 6. Try the specific problematic URI
        sb.appendLine("=== SPECIFIC URI TEST ===")
        val problemUris = listOf(
            "content://media/external/video/media/1000011884",
            "content://media/external/video/media/1000011896",
            "content://media/external/images/media/1000011915",
            "content://media/external/images/media/1000011938",
            "content://media/external/images/media/1000011942"
        )
        for (uriStr in problemUris) {
            val uri = android.net.Uri.parse(uriStr)
            sb.appendLine("--- Testing: $uriStr ---")
            testSingleUri(context, sb, uri)
        }
        sb.appendLine()

        // 7. Persistable URI permissions
        sb.appendLine("=== PERSISTABLE URI PERMISSIONS ===")
        try {
            val persistedPermissions = context.contentResolver.persistedUriPermissions
            if (persistedPermissions.isEmpty()) {
                sb.appendLine("  No persistable URI permissions found")
            } else {
                for (perm in persistedPermissions) {
                    sb.appendLine("  URI: ${perm.uri}")
                    sb.appendLine("    Read: ${perm.isReadPermission}, Write: ${perm.isWritePermission}")
                }
            }
        } catch (e: Exception) {
            sb.appendLine("  Error: ${e.message}")
        }
        sb.appendLine()

        // 8. OTG Access and Write test
        sb.appendLine("=== OTG STORAGE TEST ===")
        if (otgUri == null) {
            sb.appendLine("  No OTG folder selected in ViewModel.")
        } else {
            sb.appendLine("  Selected OTG URI: $otgUri")
            try {
                val dir = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, otgUri)
                if (dir == null) {
                    sb.appendLine("  DocumentFile.fromTreeUri returned NULL")
                } else {
                    sb.appendLine("  Can read: ${dir.canRead()}")
                    sb.appendLine("  Can write: ${dir.canWrite()}")
                    sb.appendLine("  Exists: ${dir.exists()}")
                    
                    // Try to create, write, and delete a test file
                    val testFileName = "my1drive_test_${System.currentTimeMillis()}.txt"
                    val testFile = dir.createFile("text/plain", testFileName)
                    if (testFile == null) {
                        sb.appendLine("  ❌ Failed to create test file in OTG folder")
                    } else {
                        sb.appendLine("  ✅ Successfully created test file: ${testFile.uri}")
                        try {
                            context.contentResolver.openOutputStream(testFile.uri)?.use { out ->
                                out.write("Hello World from My1Drive".toByteArray())
                            }
                            sb.appendLine("  ✅ Successfully wrote to test file")
                            
                            // Read back
                            context.contentResolver.openInputStream(testFile.uri)?.use { input ->
                                val buffer = ByteArray(100)
                                val read = input.read(buffer)
                                val text = if (read > 0) String(buffer, 0, read) else ""
                                sb.appendLine("  ✅ Successfully read back: '$text'")
                            }
                        } catch (e: Exception) {
                            sb.appendLine("  ❌ IO operations failed: ${e.javaClass.simpleName}: ${e.message}")
                        } finally {
                            val deleted = testFile.delete()
                            sb.appendLine("  Delete test file: ${if (deleted) "✅ SUCCESS" else "❌ FAILED"}")
                        }
                    }
                }
            } catch (e: Exception) {
                sb.appendLine("  ❌ OTG Test failed: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
        sb.appendLine()

        sb.appendLine("=== END OF DIAGNOSTIC REPORT ===")
        sb.toString()
    }

    private fun testMediaAccess(
        context: Context,
        sb: StringBuilder,
        collectionUri: android.net.Uri,
        isImage: Boolean,
        limit: Int
    ) {
        try {
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.RELATIVE_PATH
            )

            val query = context.contentResolver.query(
                collectionUri,
                projection,
                null,
                null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )

            if (query == null) {
                sb.appendLine("  Query returned NULL cursor")
                return
            }

            sb.appendLine("  Total rows in cursor: ${query.count}")

            query.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val pathCol = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)

                var tested = 0
                while (cursor.moveToNext() && tested < limit) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "?"
                    val size = cursor.getLong(sizeCol)
                    val path = if (pathCol >= 0) cursor.getString(pathCol) ?: "?" else "?"

                    val contentUri = ContentUris.withAppendedId(collectionUri, id)
                    sb.appendLine("  [$tested] $name (${size / 1024}KB, path=$path)")
                    sb.appendLine("      URI: $contentUri")

                    testSingleUri(context, sb, contentUri)
                    tested++
                }

                if (tested == 0) {
                    sb.appendLine("  No files found in this collection")
                }
            }
        } catch (e: Exception) {
            sb.appendLine("  Query FAILED: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun testSingleUri(context: Context, sb: StringBuilder, uri: android.net.Uri) {
        // Test 1: checkCallingOrSelfUriPermission
        try {
            val perm = context.checkCallingOrSelfUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            sb.appendLine("      checkUriPermission: ${if (perm == PackageManager.PERMISSION_GRANTED) "✅ GRANTED" else "❌ DENIED"}")
        } catch (e: Exception) {
            sb.appendLine("      checkUriPermission: ⚠️ ${e.javaClass.simpleName}: ${e.message}")
        }

        // Test 2: getType
        try {
            val type = context.contentResolver.getType(uri)
            sb.appendLine("      getType: $type")
        } catch (e: Exception) {
            sb.appendLine("      getType: ❌ ${e.javaClass.simpleName}: ${e.message}")
        }

        // Test 3: openFileDescriptor
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                sb.appendLine("      openFileDescriptor: ✅ OK (size=${pfd.statSize})")
                pfd.close()
            } else {
                sb.appendLine("      openFileDescriptor: ❌ NULL")
            }
        } catch (e: Exception) {
            sb.appendLine("      openFileDescriptor: ❌ ${e.javaClass.simpleName}: ${e.message}")
        }

        // Test 4: openInputStream
        try {
            val stream = context.contentResolver.openInputStream(uri)
            if (stream != null) {
                // Try to read first byte
                val firstByte = stream.read()
                sb.appendLine("      openInputStream: ✅ OK (first byte=$firstByte)")
                stream.close()
            } else {
                sb.appendLine("      openInputStream: ❌ NULL")
            }
        } catch (e: Exception) {
            sb.appendLine("      openInputStream: ❌ ${e.javaClass.simpleName}: ${e.message}")
        }

        // Test 5: query metadata
        try {
            val projection = arrayOf(
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE
            )
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0) ?: "?"
                    val size = cursor.getLong(1)
                    val mime = cursor.getString(2) ?: "?"
                    sb.appendLine("      query metadata: ✅ name=$name, size=$size, mime=$mime")
                } else {
                    sb.appendLine("      query metadata: ❌ cursor empty")
                }
            } ?: sb.appendLine("      query metadata: ❌ NULL cursor")
        } catch (e: Exception) {
            sb.appendLine("      query metadata: ❌ ${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
