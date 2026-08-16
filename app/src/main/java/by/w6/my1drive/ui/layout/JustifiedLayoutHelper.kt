package by.w6.my1drive.ui.layout

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import by.w6.my1drive.domain.model.MediaItem

/**
 * Single item inside a justified row with its effective aspect ratio and dimensions.
 */
data class JustifiedItem(
    val item: MediaItem,
    val safeAspectRatio: Float,
    val widthDp: Dp
)

/**
 * Single row of items in Justified Layout.
 *
 * @param items List of items in this row.
 * @param heightDp Calculated height of this row in Dp.
 * @param isLastRow True if this is the final row in a section (left-aligned, not stretched).
 */
data class JustifiedRow(
    val items: List<JustifiedItem>,
    val heightDp: Dp,
    val isLastRow: Boolean
)

object JustifiedLayoutHelper {

    private const val MIN_ASPECT_RATIO = 0.33f
    private const val MAX_ASPECT_RATIO = 3.50f
    private const val DEFAULT_ASPECT_RATIO = 1.0f

    /**
     * Returns the ideal target row height based on user's grid zoom level / column preference.
     * 2 = large view (260dp)
     * 3 = normal/standard view (190dp)
     * 4 = compact view (130dp)
     */
    fun targetRowHeightFor(gridColumnsCount: Int): Dp {
        return when (gridColumnsCount) {
            2 -> 260.dp
            3 -> 190.dp
            4 -> 130.dp
            5 -> 100.dp
            else -> if (gridColumnsCount <= 2) 260.dp else (570 / gridColumnsCount).dp
        }
    }

    /**
     * Clamps or defaults an aspect ratio to a safe range [0.33 .. 3.50].
     */
    fun safeAspectRatio(ratio: Float): Float {
        return if (ratio > 0f) {
            ratio.coerceIn(MIN_ASPECT_RATIO, MAX_ASPECT_RATIO)
        } else {
            DEFAULT_ASPECT_RATIO
        }
    }

    /**
     * Computes justified rows for a list of media items.
     *
     * @param items List of MediaItem in the current section.
     * @param containerWidth Available width for the grid in Dp.
     * @param targetRowHeight Ideal target height for rows in Dp (default 160.dp).
     * @param spacing Spacing between items and rows in Dp (default 3.dp).
     * @return List of JustifiedRow ready for rendering.
     */
    fun computeRows(
        items: List<MediaItem>,
        containerWidth: Dp,
        targetRowHeight: Dp = 160.dp,
        spacing: Dp = 3.dp
    ): List<JustifiedRow> {
        if (items.isEmpty() || containerWidth <= 0.dp) return emptyList()

        val containerWidthVal = containerWidth.value
        val targetHeightVal = targetRowHeight.value
        val spacingVal = spacing.value

        val rows = mutableListOf<JustifiedRow>()
        val currentRowItems = mutableListOf<Pair<MediaItem, Float>>() // item to safeAspectRatio
        var currentRowRawWidthSum = 0f

        for (item in items) {
            val ratio = safeAspectRatio(item.aspectRatio)
            val rawWidth = ratio * targetHeightVal

            val newCount = currentRowItems.size + 1
            val totalSpacing = (newCount - 1) * spacingVal
            val prospectiveTotalWidth = currentRowRawWidthSum + rawWidth + totalSpacing

            if (prospectiveTotalWidth > containerWidthVal && currentRowItems.isNotEmpty()) {
                // Finalize currentRow: stretch it to fit containerWidth exactly
                val spacingSum = (currentRowItems.size - 1) * spacingVal
                val availableWidth = (containerWidthVal - spacingSum).coerceAtLeast(1f)
                val scale = availableWidth / currentRowRawWidthSum.coerceAtLeast(1f)
                val finalHeight = targetHeightVal * scale

                val justifiedItems = currentRowItems.map { (rowItem, rowRatio) ->
                    val finalWidth = (rowRatio * targetHeightVal) * scale
                    JustifiedItem(
                        item = rowItem,
                        safeAspectRatio = rowRatio,
                        widthDp = finalWidth.dp
                    )
                }

                rows.add(
                    JustifiedRow(
                        items = justifiedItems,
                        heightDp = finalHeight.dp,
                        isLastRow = false
                    )
                )

                currentRowItems.clear()
                currentRowItems.add(item to ratio)
                currentRowRawWidthSum = rawWidth
            } else {
                currentRowItems.add(item to ratio)
                currentRowRawWidthSum += rawWidth
            }
        }

        // Final row in the section: left-aligned at targetRowHeight without scaling up
        if (currentRowItems.isNotEmpty()) {
            val justifiedItems = currentRowItems.map { (rowItem, rowRatio) ->
                val width = rowRatio * targetHeightVal
                JustifiedItem(
                    item = rowItem,
                    safeAspectRatio = rowRatio,
                    widthDp = width.dp
                )
            }
            rows.add(
                JustifiedRow(
                    items = justifiedItems,
                    heightDp = targetRowHeight,
                    isLastRow = true
                )
            )
        }

        return rows
    }
}
