package by.w6.my1drive.ui

import androidx.compose.ui.graphics.Color
import by.w6.my1drive.data.local.ArchiveEntity

/** Приглушённые пастельные оттенки — хорошо различимы, не кричат. Максимум 5 флешек. */
val ARCHIVE_STRIPE_COLORS = listOf(
    Color(0xFF5B8DB8), // стальной синий   — флешка 1
    Color(0xFFB87A7A), // пыльная роза     — флешка 2
    Color(0xFF7A9E7E), // шалфей           — флешка 3
    Color(0xFF8B7AB8), // лаванда          — флешка 4
    Color(0xFFB8A05B), // янтарь           — флешка 5
)

/**
 * Цвет по ПОЗИЦИИ флешки в списке known архивов.
 * Флешка 1 → цвет[0], флешка 2 → цвет[1] и т.д.
 * Используй эту версию везде где есть список архивов.
 */
fun archiveStripeColor(uuid: String?, knownArchives: List<ArchiveEntity>): Color {
    return archiveStripeColor(uuid)
}

/** Fallback без списка (по хэшу) — оставлен для совместимости */
fun archiveStripeColor(uuid: String?): Color {
    if (uuid.isNullOrEmpty()) return ARCHIVE_STRIPE_COLORS[0]
    val idx = Math.abs(uuid.hashCode()) % ARCHIVE_STRIPE_COLORS.size
    return ARCHIVE_STRIPE_COLORS[idx]
}
