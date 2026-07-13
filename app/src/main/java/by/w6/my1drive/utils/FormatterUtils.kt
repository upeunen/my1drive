package by.w6.my1drive.utils

import java.util.Locale

object FormatterUtils {
    /**
     * Formats bytes to KB, MB, or GB.
     * Uses 1024 as the base for calculations.
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024))
            else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }
}
