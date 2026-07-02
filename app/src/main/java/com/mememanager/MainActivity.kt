package com.mememanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mememanager.ui.screen.album.AlbumScreen
import com.mememanager.ui.screen.detail.PreviewMediaDetailScreen
import com.mememanager.ui.screen.settings.SettingsScreen
import com.mememanager.ui.screen.tags.TagsScreen
import com.mememanager.ui.theme.MemeManagerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MemeManagerTheme {
                AppContent()
            }
        }
    }
}

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Album : Screen("album", "相册", Icons.Filled.Home)
    data object Tags : Screen("tags", "标签", Icons.Filled.Star)
    data object Settings : Screen("settings", "设置", Icons.Filled.Settings)
    data object Tests : Screen("tests", "测试", Icons.Filled.Build)
}

@Composable
fun AppContent() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val screens = listOf(Screen.Album, Screen.Tags, Screen.Settings, Screen.Tests)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar (
                modifier = Modifier.height(110.dp)
            ) {
                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Album.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Album.route) { AlbumScreen() }
            composable(Screen.Tags.route) { TagsScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
            composable(Screen.Tests.route) { PreviewMediaDetailScreen() }
        }
    }
}
