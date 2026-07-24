package by.w6.my1drive.ui.model

import by.w6.my1drive.domain.model.MediaItem

data class MonthGroup(
    val monthIndex: Int,
    val monthName: String,
    val items: List<MediaItem>,
    val chunkedItems: List<List<MediaItem>>
)

data class YearGroup(
    val year: Int,
    val months: List<MonthGroup>
)
