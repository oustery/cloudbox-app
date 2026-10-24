package com.example.ui.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.CloudDownload
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.FolderOpen
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Settings2

/** Single source of truth for the app's tab destinations (bottom bar + rail). */
data class NavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val navDestinations = listOf(
    NavDestination("home", "Главная", Lucide.House, Lucide.House),
    NavDestination("explorer", "Проводник", Lucide.FolderOpen, Lucide.Folder),
    NavDestination("downloads", "Загрузки", Lucide.Download, Lucide.CloudDownload),
    NavDestination("settings", "Настройки", Lucide.Settings2, Lucide.Settings)
)

/** Tab icon with a soft spring pop when its tab becomes active. */
@Composable
fun NavIcon(icon: ImageVector, contentDescription: String, selected: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "nav_icon_scale"
    )
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = Modifier.scale(scale)
    )
}
