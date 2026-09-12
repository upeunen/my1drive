package by.w6.my1drive.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.media.ExifInterface
import java.util.Locale

object ExifHelper {
    /**
     * Reads EXIF metadata from the given Uri using ExifInterface.
     * Extracts Camera Model, Resolution (MP), Aperture, Shutter Speed, ISO, Focal Length.
     */
    fun readExifMetadata(context: Context, uri: Uri): Map<String, String> {
        val metadata = LinkedHashMap<String, String>()
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exifInterface = ExifInterface(inputStream)

                // Camera Model & Make
                val make = exifInterface.getAttribute(ExifInterface.TAG_MAKE)?.trim()
                val model = exifInterface.getAttribute(ExifInterface.TAG_MODEL)?.trim()
                if (!model.isNullOrEmpty()) {
                    val cameraLabel = if (!make.isNullOrEmpty() && !model.startsWith(make, ignoreCase = true)) {
                        "$make $model"
                    } else {
                        model
                    }
                    metadata[context.getString(by.w6.my1drive.R.string.exif_camera)] = cameraLabel
                }

                // Resolution & Megapixels
                val widthStr = exifInterface.getAttribute(ExifInterface.TAG_IMAGE_WIDTH)
                val heightStr = exifInterface.getAttribute(ExifInterface.TAG_IMAGE_LENGTH)
                val width = widthStr?.toIntOrNull()
                val height = heightStr?.toIntOrNull()
                if (width != null && height != null && width > 0 && height > 0) {
                    val mp = (width.toLong() * height.toLong()) / 1_000_000.0
                    val mpFormatted = if (mp >= 1.0) String.format(Locale.US, " (%.1f MP)", mp) else ""
                    metadata[context.getString(by.w6.my1drive.R.string.exif_resolution)] = "${width} × ${height}$mpFormatted"
                } else if (!widthStr.isNullOrEmpty() && !heightStr.isNullOrEmpty()) {
                    metadata[context.getString(by.w6.my1drive.R.string.exif_resolution)] = "${widthStr} × ${heightStr}"
                }

                // Aperture
                val aperture = exifInterface.getAttribute(ExifInterface.TAG_F_NUMBER)
                if (!aperture.isNullOrEmpty()) {
                    metadata[context.getString(by.w6.my1drive.R.string.exif_aperture)] = "f/$aperture"
                }

                // Shutter Speed / Exposure Time
                val exposureTime = exifInterface.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
                if (!exposureTime.isNullOrEmpty()) {
                    val exposureDouble = exposureTime.toDoubleOrNull()
                    if (exposureDouble != null && exposureDouble > 0) {
                        if (exposureDouble < 1.0) {
                            val fraction = Math.round(1 / exposureDouble)
                            metadata[context.getString(by.w6.my1drive.R.string.exif_shutter_speed)] = "1/$fraction ${context.getString(by.w6.my1drive.R.string.exif_unit_s)}"
                        } else {
                            metadata[context.getString(by.w6.my1drive.R.string.exif_shutter_speed)] = "${String.format(Locale.US, "%.1f", exposureDouble)} ${context.getString(by.w6.my1drive.R.string.exif_unit_s)}"
                        }
                    } else {
                        metadata[context.getString(by.w6.my1drive.R.string.exif_shutter_speed)] = "$exposureTime ${context.getString(by.w6.my1drive.R.string.exif_unit_s)}"
                    }
                }

                // ISO
                val iso = exifInterface.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
                if (!iso.isNullOrEmpty()) {
                    metadata["ISO"] = iso
                }

                // Focal length
                val focalLength = exifInterface.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)
                if (!focalLength.isNullOrEmpty()) {
                    val focalDouble = focalLength.substringBefore("/").toDoubleOrNull()
                    metadata[context.getString(by.w6.my1drive.R.string.exif_focal_length)] = "${focalDouble ?: focalLength} ${context.getString(by.w6.my1drive.R.string.exif_unit_mm)}"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return metadata
    }

    /**
     * Reads EXIF orientation from the given Uri and returns a rotated Bitmap if needed.
     * Recycles the input bitmap if a new rotated bitmap is created.
     */
    fun rotateBitmapIfNeeded(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ExifInterface(inputStream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        return applyOrientation(bitmap, orientation)
    }

    /**
     * Reads EXIF orientation from the given FileDescriptor and returns a rotated Bitmap if needed.
     * Recycles the input bitmap if a new rotated bitmap is created.
     */
    fun rotateBitmapIfNeeded(fd: java.io.FileDescriptor, bitmap: Bitmap): Bitmap {
        val orientation = try {
            ExifInterface(fd).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        return applyOrientation(bitmap, orientation)
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        return try {
            val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
            val rotated = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            rotated
        } catch (_: Exception) {
            bitmap
        }
    }

    /**
     * Reads image or video dimensions (swapping width and height if rotated 90/270 degrees)
     * using inJustDecodeBounds / MediaMetadataRetriever without decoding pixels into memory.
     */
    fun getImageDimensions(context: Context, uri: Uri, mimeType: String): Pair<Int, Int> {
        if (mimeType.startsWith("video/")) {
            val retriever = android.media.MediaMetadataRetriever()
            return try {
                retriever.setDataSource(context, uri)
                val w = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val h = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val rotation = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                if (rotation == 90 || rotation == 270) Pair(h, w) else Pair(w, h)
            } catch (_: Exception) {
                Pair(0, 0)
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        } else {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    android.graphics.BitmapFactory.decodeStream(input, null, bounds)
                }
            } catch (_: Exception) {
                return Pair(0, 0)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return Pair(0, 0)
            val orientation = try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }
            return if (orientation == ExifInterface.ORIENTATION_ROTATE_90 || orientation == ExifInterface.ORIENTATION_ROTATE_270) {
                Pair(bounds.outHeight, bounds.outWidth)
            } else {
                Pair(bounds.outWidth, bounds.outHeight)
            }
        }
    }
}
