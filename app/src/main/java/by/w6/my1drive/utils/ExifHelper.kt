package by.w6.my1drive.utils

import android.content.Context
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
}
