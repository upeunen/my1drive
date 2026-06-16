package by.w6.my1drive.ui.components

import android.os.Build
import androidx.compose.runtime.remember
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import by.w6.my1drive.R

sealed class Screen(val route: String, val titleResId: Int, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    object Photos : Screen("photos", R.string.tab_photos, Icons.Filled.Photo, Icons.Outlined.Photo)
    object Archive : Screen("archive", R.string.tab_archive, Icons.Filled.Archive, Icons.Outlined.Archive)
    object Settings : Screen("settings", R.string.tab_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun BottomNavigationBar(
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    val items = listOf(
        Screen.Photos,
        Screen.Archive,
        Screen.Settings
    )

    val deviceTabName = remember {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val cleanModel = model.replace(Regex("[^a-zA-Z\\s]"), "").replace(Regex("\\s+"), " ").trim()
        val cleanManufacturer = manufacturer.replace(Regex("[^a-zA-Z\\s]"), "").replace(Regex("\\s+"), " ").trim()
        val modelFirstWord = cleanModel.split(" ").firstOrNull() ?: ""
        val name = if (modelFirstWord.length > 2) {
            modelFirstWord
        } else {
            cleanManufacturer.split(" ").firstOrNull() ?: ""
        }
        if (name.isNotEmpty()) {
            name.lowercase().replaceFirstChar { it.uppercase() }
        } else {
            "Устройство"
        }
    }

    NavigationBar {
        items.forEach { screen ->
            val isSelected = currentRoute == screen.route
            val title = if (screen == Screen.Photos) deviceTabName else stringResource(screen.titleResId)
            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(screen.route) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                        contentDescription = title
                    )
                },
                label = { Text(title) }
            )
        }
    }
}
