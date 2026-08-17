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
     * Tall vertical 9:16 item (1x2) + four small 1x1 items in a 2x2 cluster.
     * Total width = 3 columns, total height = 2 units + spacing.
     */
    data class TallWithFourSmall(
        override val key: String,
        val tallItem: MediaItem,
        val small1: MediaItem,
        val small2: MediaItem,
        val small3: MediaItem,
        val small4: MediaItem,
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
        val smallBottom: MediaItem,
        val isTallLeft: Boolean = true
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
     * Two tall vertical 9:16 items in a 3-column row (spans 2 columns, 1 unit empty/tail).
     * Total width = 3 columns, total height = 2 units + spacing.
     */
    data class TwoTall(
        override val key: String,
        val tall1: MediaItem,
        val tall2: MediaItem
    ) : BentoBlock

    /**
     * Four tall vertical 9:16 items in a row (for 4-column compact mode).
     * Total width = 4 columns, total height = 2 units + spacing.
     */
    data class FourTall(
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
     * Remaining items at the end of a section.
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

    fun isPanorama(item: MediaItem): Boolean =
        item.aspectRatio >= 1.85f

    fun isTall(item: MediaItem): Boolean =
        item.aspectRatio in 0.01f..0.76f

    fun isLandscape(item: MediaItem): Boolean =
        item.aspectRatio >= 1.25f && !isPanorama(item)

    fun isHeroCandidate(item: MediaItem): Boolean =
        !isTall(item) && (
            (item.isVideo && item.aspectRatio >= 0.8f) ||
            (item.size > 6_000_000L && item.aspectRatio >= 0.8f) ||
            isLandscape(item) ||
            (item.aspectRatio in 0.85f..1.45f)
        )

    /**
     * Computes the list of Bento blocks for a section of media items with lookahead grouping.
     */
    fun computeBlocks(
        items: List<MediaItem>,
        gridColumnsCount: Int = 3
    ): List<BentoBlock> {
        if (items.isEmpty()) return emptyList()

        val result = mutableListOf<BentoBlock>()
        val pool = items.toMutableList()
        var heroLeft = true
        var blockIdx = 0

        // 4-column compact mode
        if (gridColumnsCount >= 4) {
            while (pool.isNotEmpty()) {
                val item0 = pool[0]
                if (isPanorama(item0)) {
                    pool.removeAt(0)
                    result.add(BentoBlock.PanoramaRow("bento_pano_${item0.id}_$blockIdx", item0))
                    blockIdx++
                } else if (pool.size >= 4 && pool.take(4).all { isTall(it) }) {
                    val sub = listOf(pool.removeAt(0), pool.removeAt(0), pool.removeAt(0), pool.removeAt(0))
                    result.add(BentoBlock.FourTall("bento_4tall_${item0.id}_$blockIdx", sub))
                    blockIdx++
                } else if (pool.size >= 4) {
                    val sub = listOf(pool.removeAt(0), pool.removeAt(0), pool.removeAt(0), pool.removeAt(0))
                    result.add(BentoBlock.QuartetRow("bento_quartet_${item0.id}_$blockIdx", sub))
                    blockIdx++
                } else {
                    val allTall = pool.all { isTall(it) }
                    val sub = pool.toList()
                    pool.clear()
                    result.add(BentoBlock.TailRow("bento_tail_${item0.id}_$blockIdx", sub, isTall = allTall))
                    blockIdx++
                }
            }
            return result
        }

        // Standard 3-column Bento Mosaic mode with window lookahead
        val maxLookahead = 6

        while (pool.isNotEmpty()) {
            val item0 = pool[0]

            // 1. Panorama / full-width item
            if (isPanorama(item0)) {
                pool.removeAt(0)
                result.add(BentoBlock.PanoramaRow("bento_pano_${item0.id}_$blockIdx", item0))
                blockIdx++
                continue
            }

            // 2. Current item is tall (9:16 or portrait)
            if (isTall(item0)) {
                val windowSize = minOf(pool.size, maxLookahead)
                val window = pool.subList(0, windowSize)

                // 2a. Find up to 3 tall items in the window -> ThreeTall
                val tallIndices = window.indices.filter { isTall(window[it]) }
                if (tallIndices.size >= 3) {
                    val idx1 = tallIndices[0]
                    val idx2 = tallIndices[1]
                    val idx3 = tallIndices[2]
                    val t1 = pool[idx1]
                    val t2 = pool[idx2]
                    val t3 = pool[idx3]
                    // Remove in descending index order
                    listOf(idx3, idx2, idx1).sortedDescending().forEach { pool.removeAt(it) }
                    result.add(BentoBlock.ThreeTall("bento_3tall_${t1.id}_$blockIdx", listOf(t1, t2, t3)))
                    blockIdx++
                    continue
                }

                // 2b. Two tall items + Two non-tall small items in window -> TwoTallWithTwoSmall
                if (tallIndices.size >= 2) {
                    val nonTallIndices = window.indices.filter { !isTall(window[it]) && !isPanorama(window[it]) }
                    if (nonTallIndices.size >= 2) {
                        val t1 = pool[tallIndices[0]]
                        val t2 = pool[tallIndices[1]]
                        val s1 = pool[nonTallIndices[0]]
                        val s2 = pool[nonTallIndices[1]]
                        val toRemove = listOf(tallIndices[0], tallIndices[1], nonTallIndices[0], nonTallIndices[1]).sortedDescending()
                        toRemove.forEach { pool.removeAt(it) }
                        result.add(
                            BentoBlock.TwoTallWithTwoSmall(
                                key = "bento_2tall2small_${t1.id}_$blockIdx",
                                tall1 = t1,
                                tall2 = t2,
                                smallTop = s1,
                                smallBottom = s2,
                                isTallLeft = heroLeft
                            )
                        )
                        heroLeft = !heroLeft
                        blockIdx++
                        continue
                    }
                }

                // 2c. One tall item + One Hero 2x2 item (landscape/square/regular) -> TallWithHero
                val heroIdx = window.indices.firstOrNull { it != 0 && isHeroCandidate(window[it]) && !isTall(window[it]) }
                if (heroIdx != null) {
                    val t = pool[0]
                    val h = pool[heroIdx]
                    listOf(heroIdx, 0).sortedDescending().forEach { pool.removeAt(it) }
                    result.add(
                        BentoBlock.TallWithHero(
                            key = "bento_tall_hero_${t.id}_${h.id}_$blockIdx",
                            tallItem = t,
                            heroItem = h,
                            isTallLeft = heroLeft
                        )
                    )
                    heroLeft = !heroLeft
                    blockIdx++
                    continue
                }

                // 2d. One tall item + Four small non-tall items -> TallWithFourSmall
                val nonTallForFour = window.indices.filter { !isTall(window[it]) && !isPanorama(window[it]) }
                if (nonTallForFour.size >= 4) {
                    val t = pool[0]
                    val s1 = pool[nonTallForFour[0]]
                    val s2 = pool[nonTallForFour[1]]
                    val s3 = pool[nonTallForFour[2]]
                    val s4 = pool[nonTallForFour[3]]
                    val toRemove = listOf(0, nonTallForFour[0], nonTallForFour[1], nonTallForFour[2], nonTallForFour[3]).sortedDescending()
                    toRemove.forEach { pool.removeAt(it) }
                    result.add(
                        BentoBlock.TallWithFourSmall(
                            key = "bento_tall_4small_${t.id}_$blockIdx",
                            tallItem = t,
                            small1 = s1,
                            small2 = s2,
                            small3 = s3,
                            small4 = s4,
                            isTallLeft = heroLeft
                        )
                    )
                    heroLeft = !heroLeft
                    blockIdx++
                    continue
                }

                // 2e. Two tall items remaining -> TwoTall
                if (tallIndices.size >= 2) {
                    val t1 = pool[tallIndices[0]]
                    val t2 = pool[tallIndices[1]]
                    val toRemove = listOf(tallIndices[0], tallIndices[1]).sortedDescending()
                    toRemove.forEach { pool.removeAt(it) }
                    result.add(
                        BentoBlock.TwoTall(
                            key = "bento_2tall_${t1.id}_$blockIdx",
                            tall1 = t1,
                            tall2 = t2
                        )
                    )
                    blockIdx++
                    continue
                }

                // 2f. Single tall item left (no matches) -> render as TailRow with isTall = true (doubleHeight)
                pool.removeAt(0)
                result.add(
                    BentoBlock.TailRow(
                        key = "bento_tail_${item0.id}_$blockIdx",
                        items = listOf(item0),
                        isTall = true
                    )
                )
                blockIdx++
                continue
            }

            // 3. Current item is NOT tall
            val windowSize = minOf(pool.size, maxLookahead)
            val window = pool.subList(0, windowSize)

            // 3a. If item0 is a Hero candidate, look for a tall item to make TallWithHero or 2 small items to make HeroWithTwoSmall
            if (isHeroCandidate(item0)) {
                val tallIdx = window.indices.firstOrNull { isTall(window[it]) }
                if (tallIdx != null) {
                    val h = pool[0]
                    val t = pool[tallIdx]
                    listOf(tallIdx, 0).sortedDescending().forEach { pool.removeAt(it) }
                    result.add(
                        BentoBlock.TallWithHero(
                            key = "bento_tall_hero_${t.id}_${h.id}_$blockIdx",
                            tallItem = t,
                            heroItem = h,
                            isTallLeft = heroLeft
                        )
                    )
                    heroLeft = !heroLeft
                    blockIdx++
                    continue
                }

                val nonTallSmalls = window.indices.filter { it != 0 && !isTall(window[it]) && !isPanorama(window[it]) }
                if (nonTallSmalls.size >= 2) {
                    val h = pool[0]
                    val s1 = pool[nonTallSmalls[0]]
                    val s2 = pool[nonTallSmalls[1]]
                    listOf(nonTallSmalls[1], nonTallSmalls[0], 0).sortedDescending().forEach { pool.removeAt(it) }
                    result.add(
                        BentoBlock.HeroWithTwoSmall(
                            key = "bento_hero_${h.id}_$blockIdx",
                            heroItem = h,
                            smallTop = s1,
                            smallBottom = s2,
                            isHeroLeft = heroLeft
                        )
                    )
                    heroLeft = !heroLeft
                    blockIdx++
                    continue
                }
            }

            // 3b. Landscape duet
            if (isLandscape(item0)) {
                val secondLandscapeIdx = window.indices.firstOrNull { it != 0 && isLandscape(window[it]) }
                if (secondLandscapeIdx != null) {
                    val l1 = pool[0]
                    val l2 = pool[secondLandscapeIdx]
                    listOf(secondLandscapeIdx, 0).sortedDescending().forEach { pool.removeAt(it) }
                    result.add(
                        BentoBlock.DuetRow(
                            key = "bento_duet_${l1.id}_$blockIdx",
                            item1 = l1,
                            item2 = l2
                        )
                    )
                    blockIdx++
                    continue
                }
            }

            // 3c. Regular Triplet (3 non-tall items)
            val nonTallIndices = window.indices.filter { !isTall(window[it]) && !isPanorama(window[it]) }
            if (nonTallIndices.size >= 3) {
                val idx1 = nonTallIndices[0]
                val idx2 = nonTallIndices[1]
                val idx3 = nonTallIndices[2]
                val t1 = pool[idx1]
                val t2 = pool[idx2]
                val t3 = pool[idx3]
                listOf(idx3, idx2, idx1).sortedDescending().forEach { pool.removeAt(it) }
                result.add(BentoBlock.TripletRow("bento_triplet_${t1.id}_$blockIdx", listOf(t1, t2, t3)))
                blockIdx++
                continue
            }

            // 3d. Tail with 2 non-tall items
            if (nonTallIndices.size == 2 && pool.size == 2) {
                val item1 = pool.removeAt(0)
                val item2 = pool.removeAt(0)
                result.add(
                    BentoBlock.DuetRow(
                        key = "bento_duet_${item1.id}_$blockIdx",
                        item1 = item1,
                        item2 = item2
                    )
                )
                blockIdx++
                continue
            }

            // 3e. Fallback tail
            pool.removeAt(0)
            result.add(
                BentoBlock.TailRow(
                    key = "bento_tail_${item0.id}_$blockIdx",
                    items = listOf(item0),
                    isTall = isTall(item0)
                )
            )
            blockIdx++
        }

        return result
    }
}
