package by.w6.my1drive.ui

import by.w6.my1drive.domain.model.MediaItem

sealed class GalleryItem {
    data class Header(val title: String) : GalleryItem()
    data class Media(val item: MediaItem) : GalleryItem()
}
