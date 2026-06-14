package by.w6.my1drive.ui

import by.w6.my1drive.domain.model.MediaItem

data class FullscreenState(
    val items: List<MediaItem>,
    val initialIndex: Int,
    val sourceTab: SourceTab
)

enum class SourceTab { PHOTOS, ARCHIVE }
