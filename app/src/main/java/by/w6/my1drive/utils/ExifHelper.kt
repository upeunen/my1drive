package by.w6.my1drive.utils

import android.content.Context
import android.net.Uri
import android.media.ExifInterface

object ExifHelper {
    /**
     * Reads EXIF metadata from the given Uri using ExifInterface.
     * Extracts Aperture, Shutter Speed, ISO, Focal Length, and Resolution.
     */
    fun readExifMetadata(context: Context, uri: Uri): Map<String, String> {
        val metadata = mutableMapOf<String, String>()
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exifInterface = ExifInterface(inputStream)
                
                val aperture = exifInterface.getAttribute(ExifInterface.TAG_F_NUMBER)
                if (!aperture.isNullOrEmpty()) {
                    metadata[context.getString(by.w6.my1drive.R.string.exif_aperture)] = "f/$aperture"
                }
                
                val exposureTime = exifInterface.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
                if (!exposureTime.isNullOrEmpty()) {
                    val exposureDouble = exposureTime.toDoubleOrNull()
                    if (exposureDouble != null && exposureDouble > 0) {
                        val fraction = Math.round(1 / exposureDouble)
                        metadata[context.getString(by.w6.my1drive.R.string.exif_shutter_speed)] = "1/$fraction ${context.getString(by.w6.my1drive.R.string.exif_unit_s)}"
                    } else {
                        metadata[context.getString(by.w6.my1drive.R.string.exif_shutter_speed)] = "$exposureTime ${context.getString(by.w6.my1drive.R.string.exif_unit_s)}"
                    }
                }
                
                val iso = exifInterface.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
                if (!iso.isNullOrEmpty()) {
                    metadata["ISO"] = iso
                }
                
                val focalLength = exifInterface.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)
                if (!focalLength.isNullOrEmpty()) {
                    val focalDouble = focalLength.substringBefore("/").toDoubleOrNull()
                    metadata[context.getString(by.w6.my1drive.R.string.exif_focal_length)] = "${focalDouble ?: focalLength} ${context.getString(by.w6.my1drive.R.string.exif_unit_mm)}"
                }
                
                val width = exifInterface.getAttribute(ExifInterface.TAG_IMAGE_WIDTH)
                val height = exifInterface.getAttribute(ExifInterface.TAG_IMAGE_LENGTH)
                if (!width.isNullOrEmpty() && !height.isNullOrEmpty()) {
                    metadata[context.getString(by.w6.my1drive.R.string.exif_resolution)] = "${width}x${height}"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return metadata
    }
}
