package by.w6.my1drive.ui.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import by.w6.my1drive.domain.model.MediaItem
import by.w6.my1drive.domain.model.MediaStatus
import by.w6.my1drive.ui.GooglePhotosGridItem
import coil.ImageLoader

@Composable
fun BentoBlockView(
    block: BentoBlock,
    unitSize: Dp,
    spacing: Dp = 3.dp,
    selectedIds: Set<String>,
    imageLoader: ImageLoader,
    isOtgConnected: Boolean = true,
    activeArchiveUuid: String? = null,
    archivingItemIds: Set<String> = emptySet(),
    copiedItemIds: Set<String> = emptySet(),
    archiveStripeColorProvider: ((MediaItem) -> Color?)? = null,
    onItemClick: (MediaItem) -> Unit,
    onItemLongClick: (MediaItem) -> Unit
) {
    @Composable
    fun RenderItem(item: MediaItem, modifier: Modifier = Modifier) {
        val isSelected = selectedIds.contains(item.id)
        val isArchiving = archivingItemIds.contains(item.id)
        val isCopied = copiedItemIds.contains(item.id)
        val connected = if (item.status == MediaStatus.ARCHIVED_OTG) {
            isOtgConnected && item.archiveUuid == activeArchiveUuid
        } else isOtgConnected

        GooglePhotosGridItem(
            item = item,
            isSelected = isSelected,
            imageLoader = imageLoader,
            isOtgConnected = connected,
            isArchiving = isArchiving,
            isCopied = isCopied,
            archiveStripeOverrideColor = archiveStripeColorProvider?.invoke(item),
            modifier = modifier,
            onClick = { onItemClick(item) },
            onLongClick = { onItemLongClick(item) }
        )
    }

    val doubleHeight = (unitSize * 2) + spacing

    when (block) {
        is BentoBlock.HeroWithTwoSmall -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(doubleHeight),
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                if (block.isHeroLeft) {
                    RenderItem(block.heroItem, modifier = Modifier.weight(2f).fillMaxHeight())
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(spacing)
                    ) {
                        RenderItem(block.smallTop, modifier = Modifier.fillMaxWidth().weight(1f))
                        RenderItem(block.smallBottom, modifier = Modifier.fillMaxWidth().weight(1f))
                    }
                } else {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(spacing)
                    ) {
                        RenderItem(block.smallTop, modifier = Modifier.fillMaxWidth().weight(1f))
                        RenderItem(block.smallBottom, modifier = Modifier.fillMaxWidth().weight(1f))
                    }
                    RenderItem(block.heroItem, modifier = Modifier.weight(2f).fillMaxHeight())
                }
            }
        }

        is BentoBlock.TallWithHero -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(doubleHeight),
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                if (block.isTallLeft) {
                    RenderItem(block.tallItem, modifier = Modifier.weight(1f).fillMaxHeight())
                    RenderItem(block.heroItem, modifier = Modifier.weight(2f).fillMaxHeight())
                } else {
                    RenderItem(block.heroItem, modifier = Modifier.weight(2f).fillMaxHeight())
                    RenderItem(block.tallItem, modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
        }

        is BentoBlock.TwoTallWithTwoSmall -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(doubleHeight),
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                RenderItem(block.tall1, modifier = Modifier.weight(1f).fillMaxHeight())
                RenderItem(block.tall2, modifier = Modifier.weight(1f).fillMaxHeight())
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    RenderItem(block.smallTop, modifier = Modifier.fillMaxWidth().weight(1f))
                    RenderItem(block.smallBottom, modifier = Modifier.fillMaxWidth().weight(1f))
                }
            }
        }

        is BentoBlock.ThreeTall -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(doubleHeight),
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                block.items.forEach { item ->
                    RenderItem(item, modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
        }

        is BentoBlock.TripletRow -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(unitSize),
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                block.items.forEach { item ->
                    RenderItem(item, modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
        }

        is BentoBlock.DuetRow -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(unitSize * 1.15f),
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                RenderItem(block.item1, modifier = Modifier.weight(1f).fillMaxHeight())
                RenderItem(block.item2, modifier = Modifier.weight(1f).fillMaxHeight())
            }
        }

        is BentoBlock.PanoramaRow -> {
            val panoHeight = (unitSize * block.heightRatio).coerceIn(150.dp, 240.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(panoHeight)
            ) {
                RenderItem(block.item, modifier = Modifier.fillMaxSize())
            }
        }

        is BentoBlock.QuartetRow -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(unitSize),
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                block.items.forEach { item ->
                    RenderItem(item, modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
        }

        is BentoBlock.TailRow -> {
            val tailHeight = if (block.isTall) doubleHeight else unitSize
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(tailHeight),
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                block.items.forEach { item ->
                    RenderItem(item, modifier = Modifier.width(unitSize).fillMaxHeight())
                }
            }
        }
    }
}
