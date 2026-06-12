package by.w6.my1drive.domain.model

enum class MediaStatus {
    ON_DEVICE,      // Media is present locally on the phone's memory
    ARCHIVED_OTG    // Original is deleted, it is backed up on OTG drive, app displays local cached thumbnail
}
