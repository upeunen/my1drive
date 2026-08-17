package by.w6.my1drive.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Eject
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import by.w6.my1drive.R

import androidx.compose.material3.CircularProgressIndicator
import by.w6.my1drive.utils.FormatterUtils

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun GooglePhotosTopBar(
    selectedCount: Int,
    selectedBytes: Long = 0L,
    isOtgConnected: Boolean,
    otgUriSet: Boolean,
    isGroupExpanded: Boolean = false,
    deleteEnabled: Boolean = true,
    isPremium: Boolean = false,
    isTrialActive: Boolean = false,
    remainingTrialDays: Int = 0,
    photosCount: Int = 0,
    maxPhotos: Int = 100,
    videosCount: Int = 0,
    maxVideos: Int = 5,
    showLimitBubble: Boolean = false,
    onDismissLimitBubble: () -> Unit = {},
    onProClick: () -> Unit = {},
    onClearSelection: () -> Unit,
    onEjectClick: () -> Unit,
    onGroupClick: () -> Unit = {},
    onShare: () -> Unit = {},
    onDelete: () -> Unit = {},
    gridColumnsCount: Int = 3,
    onToggleGridColumns: () -> Unit = {},
    showGridToggle: Boolean = true,
    isSyncing: Boolean = false,
    syncProgressText: String? = null
) {
    val title = when {
        selectedCount > 0 -> {
            val sizeFormatted = if (selectedBytes > 0L) " • ${FormatterUtils.formatFileSize(selectedBytes)}" else ""
            "$selectedCount$sizeFormatted"
        }
        isPremium -> "My1Drive PRO"
        else -> "My1Drive"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectedCount > 0) {
            // ← Back / clear selection
            IconButton(
                onClick = onClearSelection,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.topbar_content_desc_deselect)
                )
            }

            // Counter & Size with smooth ticker animation
            AnimatedContent(
                targetState = title,
                transitionSpec = {
                    (slideInVertically { height -> height } + fadeIn())
                        .togetherWith(slideOutVertically { height -> -height } + fadeOut())
                },
                modifier = Modifier.weight(1f),
                label = "SelectionCounterAnimation"
            ) { targetTitle ->
                Text(
                    text = targetTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Группа toggle button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onGroupClick)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.topbar_group),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = if (isGroupExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.width(4.dp))

            // Share button (48dp touch target, 28dp icon)
            IconButton(
                onClick = onShare,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = stringResource(R.string.topbar_content_desc_share),
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Delete button (conditionally enabled, 48dp touch target, 28dp icon)
            if (deleteEnabled) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.topbar_content_desc_delete),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        } else {
            // Normal (non-selection) mode
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )

            Spacer(Modifier.weight(1f))

            // Live Syncing / Archiving indicator pill
            if (isSyncing && syncProgressText != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = syncProgressText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Laconic Smart Chip for Trial / Free Limit
            if (!isPremium) {
                val chipText = if (isTrialActive) {
                    stringResource(R.string.topbar_chip_trial, remainingTrialDays)
                } else {
                    stringResource(R.string.topbar_chip_limits, photosCount, maxPhotos, videosCount, maxVideos)
                }

                Box(contentAlignment = Alignment.TopEnd) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable(onClick = onProClick)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = chipText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (showLimitBubble) {
                        val photosLeft = (maxPhotos - photosCount).coerceAtLeast(0)
                        val videosLeft = (maxVideos - videosCount).coerceAtLeast(0)
                        val bubbleMessage = if (isTrialActive) {
                            stringResource(R.string.topbar_bubble_trial_text, remainingTrialDays)
                        } else {
                            stringResource(R.string.topbar_bubble_limits_text, photosLeft, videosLeft)
                        }

                        Popup(
                            alignment = Alignment.TopEnd,
                            offset = IntOffset(x = 0, y = 100),
                            onDismissRequest = onDismissLimitBubble,
                            properties = PopupProperties(
                                focusable = false,
                                dismissOnClickOutside = true,
                                dismissOnBackPress = true
                            )
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                tonalElevation = 6.dp,
                                shadowElevation = 8.dp,
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .padding(horizontal = 8.dp)
                                    .widthIn(min = 200.dp, max = 280.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Text(
                                        text = bubbleMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Button(
                                            onClick = {
                                                onDismissLimitBubble()
                                                onProClick()
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(
                                                horizontal = 12.dp,
                                                vertical = 4.dp
                                            ),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.topbar_bubble_action_pro),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.width(4.dp))
            }

            if (showGridToggle) {
                IconButton(onClick = onToggleGridColumns) {
                    Icon(
                        imageVector = if (gridColumnsCount == 3) Icons.Default.GridView else Icons.Default.GridOn,
                        contentDescription = "Toggle Grid Columns",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            if (isOtgConnected && otgUriSet) {
                IconButton(onClick = onEjectClick) {
                    Icon(
                        imageVector = Icons.Default.Eject,
                        contentDescription = "Safe Eject",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}