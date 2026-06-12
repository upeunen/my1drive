package by.w6.my1drive.ui

import androidx.compose.foundation.layout.Row
import by.w6.my1drive.ui.DriveStatus
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GooglePhotosTopBar(
    selectedCount: Int,
    driveStatus: DriveStatus,
    otgUriSet: Boolean,
    onClearSelection: () -> Unit,
    onSelectOtgClick: () -> Unit
) {
    val title = when {
        selectedCount > 0 -> "$selectedCount selected"
        driveStatus == DriveStatus.KNOWN_DRIVE_CONNECTED -> "My1Drive"
        driveStatus == DriveStatus.NO_URI_CONFIGURED -> "My1Drive"
        else -> "My1Drive"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectedCount > 0) {
            IconButton(onClick = onClearSelection) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Clear selection"
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        if (selectedCount == 0) {
            IconButton(onClick = onSelectOtgClick) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}
