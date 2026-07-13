package by.w6.my1drive.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import by.w6.my1drive.data.local.ArchiveEntity
import by.w6.my1drive.domain.model.MediaItem
import java.io.File

object MediaShareHelper {

    fun getReadableOtgPath(context: Context, otgUri: String?): String {
        if (otgUri == null) return context.getString(by.w6.my1drive.R.string.unknown_value)
        return try {
            val decoded = Uri.decode(otgUri)
            val documentSegment = decoded.substringAfter("/document/", "")
            if (documentSegment.isNotEmpty()) {
                documentSegment.substringAfter(":", documentSegment)
            } else {
                decoded.substringAfterLast("/")
            }
        } catch (e: Exception) {
            otgUri
        }
    }

    private fun getSharedImageUri(context: Context, sourceFile: File, displayName: String): Uri? {
        return try {
            val sharedTempDir = File(context.cacheDir, "shared_temp")
            sharedTempDir.mkdirs()
            val cleanName = displayName.substringBeforeLast(".") + "_preview.webp"
            val tempFile = File(sharedTempDir, cleanName)
            sourceFile.inputStream().use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun buildShareText(
        context: Context,
        item: MediaItem,
        sizeMb: String,
        archive: ArchiveEntity?
    ): String {
        val archiveName = archive?.name ?: item.archiveName ?: context.getString(by.w6.my1drive.R.string.unknown_drive_name)
        val archiveUuid = item.archiveUuid ?: context.getString(by.w6.my1drive.R.string.unknown_value)
        val readablePath = getReadableOtgPath(context, item.otgUri)
        
        return buildString {
            append(context.getString(by.w6.my1drive.R.string.share_text_name, item.displayName)).append("\n")
            append(context.getString(by.w6.my1drive.R.string.share_text_archive_path, readablePath)).append("\n")
            append(context.getString(by.w6.my1drive.R.string.share_text_size, sizeMb)).append("\n")
            append(context.getString(by.w6.my1drive.R.string.share_text_drive, archiveName)).append("\n")
            append(context.getString(by.w6.my1drive.R.string.share_text_uuid, archiveUuid))
        }
    }

    fun shareFileDetails(
        context: Context,
        item: MediaItem,
        sizeMb: String,
        archive: ArchiveEntity?
    ) {
        val shareText = buildShareText(context, item, sizeMb, archive)

        val path = item.thumbnailPath ?: run {
            val previewDir = File(context.filesDir, PreviewCacheManager.PREVIEW_DIR)
            val cacheFile = File(previewDir, "${item.id}.my1d")
            if (cacheFile.exists()) cacheFile.absolutePath else null
        }

        if (path != null) {
            val file = File(path)
            if (file.exists()) {
                val uri = getSharedImageUri(context, file, item.displayName)
                if (uri != null) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/webp"
                        putExtra(Intent.EXTRA_SUBJECT, item.displayName)
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                        context.startActivity(Intent.createChooser(intent, context.getString(by.w6.my1drive.R.string.share_file_chooser)))
                        return
                    }
            }
        }

        // Fallback: text only
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, item.displayName)
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(by.w6.my1drive.R.string.share_file_chooser)))
    }

    fun copyFileDetailsToClipboard(
        context: Context,
        item: MediaItem,
        sizeMb: String,
        archive: ArchiveEntity?
    ) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val shareText = buildShareText(context, item, sizeMb, archive)

        val path = item.thumbnailPath ?: run {
            val previewDir = File(context.filesDir, PreviewCacheManager.PREVIEW_DIR)
            val cacheFile = File(previewDir, "${item.id}.my1d")
            if (cacheFile.exists()) cacheFile.absolutePath else null
        }

        if (path != null) {
            val file = File(path)
            if (file.exists()) {
                val uri = getSharedImageUri(context, file, item.displayName)
                if (uri != null) {
                    val clip = ClipData.newPlainText("file_details", shareText).apply {
                        addItem(ClipData.Item(uri))
                    }
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, context.getString(by.w6.my1drive.R.string.toast_copied_to_clipboard), Toast.LENGTH_SHORT).show()
                    return
                }
            }
        }

        val clip = ClipData.newPlainText("file_details", shareText)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, context.getString(by.w6.my1drive.R.string.toast_copied_to_clipboard), Toast.LENGTH_SHORT).show()
    }
}
