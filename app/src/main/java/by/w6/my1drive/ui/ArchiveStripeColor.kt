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
 * Возвращает уникальный идентификатор физического накопителя для данного архива.
 * Если driveUuid заполнен — используется он, иначе uuid архива.
 */
fun getDriveKey(archive: ArchiveEntity): String {
    return archive.driveUuid.ifEmpty { archive.uuid }
}

/**
 * Цвет по физическому носителю (флешке).
 * Все архивы, находящиеся на одном физическом диске, получают один и тот же цвет.
 * Диск 1 → цвет[0], Диск 2 → цвет[1] и т.д. в порядке первого добавления диска.
 */
fun archiveStripeColor(uuid: String?, knownArchives: List<ArchiveEntity>): Color {
    if (uuid.isNullOrEmpty() || knownArchives.isEmpty()) return archiveStripeColor(uuid)

    // Находим целевой архив
    val targetArchive = knownArchives.find { it.uuid == uuid }
    val targetDriveKey = if (targetArchive != null) {
        getDriveKey(targetArchive)
    } else {
        uuid
    }

    // Упорядочиваем уникальные физические носители по времени самого раннего добавления
    val orderedDriveKeys = knownArchives
        .groupBy { getDriveKey(it) }
        .entries
        .sortedBy { (_, list) -> list.minOfOrNull { it.dateCreated.takeIf { d -> d > 0 } ?: it.lastConnected } ?: 0L }
        .map { it.key }

    val driveIndex = orderedDriveKeys.indexOf(targetDriveKey)
    return if (driveIndex >= 0) {
        ARCHIVE_STRIPE_COLORS[driveIndex % ARCHIVE_STRIPE_COLORS.size]
    } else {
        archiveStripeColor(targetDriveKey)
    }
}

/** Fallback без списка (по детерминированному хэшу) — когда список knownArchives недоступен */
fun archiveStripeColor(uuid: String?): Color {
    if (uuid.isNullOrEmpty()) return ARCHIVE_STRIPE_COLORS[0]
    val idx = Math.abs(uuid.hashCode()) % ARCHIVE_STRIPE_COLORS.size
    return ARCHIVE_STRIPE_COLORS[idx]
}
