package by.w6.my1drive.ui.layout

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import by.w6.my1drive.domain.model.MediaItem

/**
 * Geometric Bento block models for the Apple Photos style modular grid.
 */
sealed interface BentoBlock {
    val key: String

    /**
     * Hero 2x2 on one side and two 1x1 small photos stacked on the other.
     * Total width = 3 columns, total height = 2 units + spacing.
     */
    data class HeroWithTwoSmall(
        override val key: String,
        val heroItem: MediaItem,
        val smallTop: MediaItem,
        val smallBottom: MediaItem,
        val isHeroLeft: Boolean
    ) : BentoBlock

    /**
     * Tall vertical 9:16 item (1x2) and a large Hero (2x2).
     * Total width = 3 columns, total height = 2 units + spacing.
     * Zero crop on 9:16 vertical photos/videos!
     */
    data class TallWithHero(
        override val key: String,
        val tallItem: MediaItem,
        val heroItem: MediaItem,
        val isTallLeft: Boolean
    ) : BentoBlock

    /**
     * Two tall vertical 9:16 items (1x2 each) + two small 1x1 items stacked.
     * Total width = 3 columns, total height = 2 units + spacing.
     */
    data class TwoTallWithTwoSmall(
        override val key: String,
        val tall1: MediaItem,
        val tall2: MediaItem,
        val smallTop: MediaItem,
        val smallBottom: MediaItem
    ) : BentoBlock

    /**
     * Three tall vertical 9:16 items in a row.
     * Total width = 3 columns, total height = 2 units + spacing.
     */
    data class ThreeTall(
        override val key: String,
        val items: List<MediaItem>
    ) : BentoBlock

    /**
     * Three regular 1x1 items in a row.
     * Total width = 3 columns, total height = 1 unit.
     */
    data class TripletRow(
        override val key: String,
        val items: List<MediaItem>
    ) : BentoBlock

    /**
     * Two horizontal landscape items across the row (1.5x1 each).
     * Total width = 3 columns, total height = 1.15 units.
     */
    data class DuetRow(
        override val key: String,
        val item1: MediaItem,
        val item2: MediaItem
    ) : BentoBlock

    /**
     * Full-width panorama or widescreen video (3x1.3).
     */
    data class PanoramaRow(
        override val key: String,
        val item: MediaItem,
        val heightRatio: Float = 1.3f
    ) : BentoBlock

    /**
     * Four compact 1x1 items (used in compact 4-column mode).
     * Total width = 4 columns, total height = 1 unit.
     */
    data class QuartetRow(
        override val key: String,
        val items: List<MediaItem>
    ) : BentoBlock

    /**
     * Remaining 1-2 items at the end of a section.
     */
    data class TailRow(
        override val key: String,
        val items: List<MediaItem>,
        val isTall: Boolean
    ) : BentoBlock
}

object BentoLayoutHelper {

    /**
     * Calculates the unit size (U) of a single 1x1 cell in Dp.
     */
    fun calculateUnitSize(containerWidth: Dp, columns: Int, spacing: Dp = 3.dp): Dp {
        if (containerWidth <= 0.dp || columns <= 0) return 120.dp
        val totalSpacing = (columns - 1) * spacing.value
        val available = (containerWidth.value - totalSpacing).coerceAtLeast(1f)
        return (available / columns).dp
    }

    private fun isPanorama(item: MediaItem): Boolean =
        item.aspectRatio >= 1.85f

    private fun isTall(item: MediaItem): Boolean =
        item.aspectRatio in 0.01f..0.68f

    private fun isLandscape(item: MediaItem): Boolean =
        item.aspectRatio >= 1.25f && !isPanorama(item)

    private fun isHeroCandidate(item: MediaItem): Boolean =
        item.isVideo || item.size > 6_000_000L || isLandscape(item) || (item.aspectRatio in 0.85f..1.45f)

    /**
     * Computes the list of Bento blocks for a section of media items.
     */
    fun computeBlocks(
        items: List<MediaItem>,
        gridColumnsCount: Int = 3
    ): List<BentoBlock> {
        if (items.isEmpty()) return emptyList()

        val result = mutableListOf<BentoBlock>()
        var i = 0
        var heroLeft = true

        // 4-column compact mode
        if (gridColumnsCount >= 4) {
            while (i < items.size) {
                val remaining = items.size - i
                val item0 = items[i]
                if (isPanorama(item0)) {
                    result.add(BentoBlock.PanoramaRow("bento_pano_${item0.id}", item0))
                    i += 1
                } else if (remaining >= 4) {
                    val sub = items.subList(i, i + 4)
                    result.add(BentoBlock.QuartetRow("bento_quartet_${item0.id}_$i", sub))
                    i += 4
                } else {
                    val sub = items.subList(i, items.size)
                    result.add(BentoBlock.TailRow("bento_tail_${item0.id}_$i", sub, isTall = false))
                    i = items.size
                }
            }
            return result
        }

        // Standard 3-column Bento Mosaic mode
        while (i < items.size) {
            val remaining = items.size - i
            val item0 = items[i]

            // 1. Panorama / full-width video
            if (isPanorama(item0)) {
                result.add(BentoBlock.PanoramaRow("bento_pano_${item0.id}", item0))
                i += 1
                continue
            }

            // 2. Three tall vertical 9:16 in a row
            if (remaining >= 3 && isTall(item0) && isTall(items[i + 1]) && isTall(items[i + 2])) {
                val sub = listOf(item0, items[i + 1], items[i + 2])
                result.add(BentoBlock.ThreeTall("bento_3tall_${item0.id}_$i", sub))
                i += 3
                continue
            }

            // 3. Two tall vertical 9:16 + Two small 1x1
            if (remaining >= 4 && isTall(item0) && isTall(items[i + 1])) {
                result.add(
                    BentoBlock.TwoTallWithTwoSmall(
                        key = "bento_2tall2small_${item0.id}_$i",
                        tall1 = item0,
                        tall2 = items[i + 1],
                        smallTop = items[i + 2],
                        smallBottom = items[i + 3]
                    )
                )
                i += 4
                continue
            }

            // 4. One tall vertical 9:16 + Hero 2x2
            if (remaining >= 2 && (isTall(item0) || isTall(items[i + 1]))) {
                val tallItem = if (isTall(item0)) item0 else items[i + 1]
                val heroItem = if (isTall(item0)) items[i + 1] else item0
                val isTallLeft = isTall(item0)
                result.add(
                    BentoBlock.TallWithHero(
                        key = "bento_tall_hero_${tallItem.id}_${heroItem.id}_$i",
                        tallItem = tallItem,
                        heroItem = heroItem,
                        isTallLeft = isTallLeft
                    )
                )
                i += 2
                continue
            }

            // 5. Hero 2x2 + Two small 1x1
            if (remaining >= 3 && (isHeroCandidate(item0) || isHeroCandidate(items[i + 1]) || isHeroCandidate(items[i + 2]))) {
                val trio = listOf(items[i], items[i + 1], items[i + 2])
                val heroIndex = when {
                    isHeroCandidate(items[i]) -> 0
                    isHeroCandidate(items[i + 1]) -> 1
                    else -> 2
                }
                val hero = trio[heroIndex]
                val smalls = trio.filterIndexed { index, _ -> index != heroIndex }
                result.add(
                    BentoBlock.HeroWithTwoSmall(
                        key = "bento_hero_${hero.id}_$i",
                        heroItem = hero,
                        smallTop = smalls[0],
                        smallBottom = smalls[1],
                        isHeroLeft = heroLeft
                    )
                )
                heroLeft = !heroLeft
                i += 3
                continue
            }

            // 6. Duet of two landscapes
            if (remaining >= 2 && isLandscape(item0) && isLandscape(items[i + 1])) {
                result.add(
                    BentoBlock.DuetRow(
                        key = "bento_duet_${item0.id}_$i",
                        item1 = item0,
                        item2 = items[i + 1]
                    )
                )
                i += 2
                continue
            }

            // 7. Regular Triplet (3x 1x1)
            if (remaining >= 3) {
                val sub = listOf(items[i], items[i + 1], items[i + 2])
                result.add(BentoBlock.TripletRow("bento_triplet_${item0.id}_$i", sub))
                i += 3
                continue
            }

            // 8. Section Tail (1-2 items remaining)
            if (remaining == 2) {
                result.add(
                    BentoBlock.DuetRow(
                        key = "bento_duet_${item0.id}_$i",
                        item1 = items[i],
                        item2 = items[i + 1]
                    )
                )
                i += 2
            } else {
                result.add(
                    BentoBlock.TailRow(
                        key = "bento_tail_${item0.id}_$i",
                        items = listOf(item0),
                        isTall = isTall(item0)
                    )
                )
                i += 1
            }
        }

        return result
    }
}
