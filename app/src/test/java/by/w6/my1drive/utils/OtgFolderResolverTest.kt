package by.w6.my1drive.utils

import org.junit.Assert.*
import org.junit.Test

class OtgFolderResolverTest {

    @Test
    fun extractVolumeId_fromRootSegment_returnsUuid() {
        val path = "/storage/emulated/0/Android/data/com.android.externalstorage.documents/root/1234-5678:MyFolder"
        val uuid = OtgFolderResolver.extractVolumeIdFromPath(path)
        assertEquals("1234-5678", uuid)
    }

    @Test
    fun extractVolumeId_fromTreeSegment_returnsUuid() {
        val path = "/tree/1234-5678:My1drive/document/1234-5678:My1drive/photo.jpg"
        val uuid = OtgFolderResolver.extractVolumeIdFromPath(path)
        assertEquals("1234-5678", uuid)
    }

    @Test
    fun extractVolumeId_fromDocumentSegment_returnsUuid() {
        val path = "/document/ABCD-EF01:Pictures/Camera/img.jpg"
        val uuid = OtgFolderResolver.extractVolumeIdFromPath(path)
        assertEquals("ABCD-EF01", uuid)
    }

    @Test
    fun extractVolumeId_invalidPath_returnsNull() {
        val path = "/some/invalid/path/without/root/or/tree"
        val uuid = OtgFolderResolver.extractVolumeIdFromPath(path)
        assertNull(uuid)
    }

    @Test
    fun extractVolumeId_nullPath_returnsNull() {
        val uuid = OtgFolderResolver.extractVolumeIdFromPath(null)
        assertNull(uuid)
    }
}
