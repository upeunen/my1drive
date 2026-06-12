package by.w6.my1drive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ArchiveBreadcrumbBar(
    currentPath: String?,
    onNavigateUp: () -> Unit,
    onNavigateRoot: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A2A))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (currentPath != null) {
            IconButton(
                onClick = onNavigateRoot,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = "Root", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Text("/", color = Color.Gray, fontSize = 14.sp)
            val segments = currentPath.split('/')
            for ((i, segment) in segments.withIndex()) {
                if (segment.isNotBlank()) {
                    Text(
                        text = segment,
                        color = Color(0xFFFFA000),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                    if (i < segments.size - 1) {
                        Text("/", color = Color.Gray, fontSize = 14.sp)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = onNavigateRoot,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Icons.AutoMirrored.Filled.ArrowBack.let { Icon(it, contentDescription = "Up", tint = Color.White, modifier = Modifier.size(16.dp)) }
                Spacer(Modifier.width(2.dp))
                Text("Up", color = Color.White, fontSize = 12.sp)
            }
        } else {
            Icon(
                Icons.Default.FolderOpen,
                contentDescription = "Root",
                tint = Color(0xFFFFA000),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text("Archive root", color = Color.Gray, fontSize = 14.sp)
        }
    }
}
