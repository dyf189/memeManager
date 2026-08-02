package com.mememanager

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.room.util.copy
import com.mememanager.ui.screen.album.AlbumScreen
import com.mememanager.data.repository.MediaRepository
import com.mememanager.data.settings.SettingsKeys
import com.mememanager.ui.screen.detail.MediaDetailScreen
import com.mememanager.ui.screen.settings.SettingsScreen
import com.mememanager.ui.screen.search.SearchScreen
import com.mememanager.ui.screen.trash.TrashScreen
import com.mememanager.ui.screen.tags.TagsScreen
import com.mememanager.ui.theme.MemeManagerTheme
import com.mememanager.ui.viewmodel.AlbumViewModel
import com.mememanager.ui.viewmodel.GroupViewModel
import com.mememanager.ui.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var pendingShareUris = mutableListOf<Uri>()

    @Inject lateinit var mediaRepository: MediaRepository
    @Inject lateinit var dataStore: DataStore<Preferences>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = androidx.hilt.navigation.compose.hiltViewModel()
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            val forceDark = when (settings.themeMode) {
                "浅色" -> false
                "深色" -> true
                else -> null
            }
            MemeManagerTheme(forceDark = forceDark) {
                AppContent(
                    shareUris = pendingShareUris.toList(),
                    onShareConsumed = { pendingShareUris.clear() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        triggerAutoClean()
    }

    private fun triggerAutoClean() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val trashDays = dataStore.data.first()[SettingsKeys.TRASH_DAYS] ?: 30
            if (trashDays >= 90) return@launch
            val lastClean = dataStore.data.first()[SettingsKeys.LAST_TRASH_CLEAN] ?: 0L
            val now = System.currentTimeMillis()
            if (now - lastClean < 86_400_000L) return@launch
            val cutoff = now - trashDays * 86_400_000L
            mediaRepository.purgeDeletedBefore(cutoff)
            dataStore.edit { it[SettingsKeys.LAST_TRASH_CLEAN] = now }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
        setContent {
            val settingsViewModel: SettingsViewModel = androidx.hilt.navigation.compose.hiltViewModel()
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            val forceDark = when (settings.themeMode) {
                "浅色" -> false
                "深色" -> true
                else -> null
            }
            MemeManagerTheme(forceDark = forceDark) {
                AppContent(
                    shareUris = pendingShareUris.toList(),
                    onShareConsumed = { pendingShareUris.clear() }
                )
            }
        }
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE) {
            val uris = mutableListOf<Uri>()
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.add(it) }
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.addAll(it) }
            if (uris.isNotEmpty()) {
                pendingShareUris.addAll(uris)
            }
        }
    }
}

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Album : Screen("album", "相册", Icons.Filled.Home)
    data object Tags : Screen("tags", "标签", Icons.Filled.Star)
    data object Trash : Screen("trash", "回收站", Icons.Filled.Delete)
    data object Settings : Screen("settings", "设置", Icons.Filled.Settings)
}

@Composable
fun AppContent(
    shareUris: List<Uri> = emptyList(),
    onShareConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val screens = listOf(Screen.Album, Screen.Tags, Screen.Trash, Screen.Settings)

    val activity = LocalActivity.current as ComponentActivity
    val sharedAlbumViewModel: AlbumViewModel = hiltViewModel(viewModelStoreOwner = activity)
    val groupViewModel: GroupViewModel = hiltViewModel(viewModelStoreOwner = activity)

    // 处理分享导入
    LaunchedEffect(shareUris) {
        if (shareUris.isNotEmpty()) {
            sharedAlbumViewModel.importMedia(shareUris)
            onShareConsumed()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (currentDestination?.route?.startsWith("detail") != true &&
                currentDestination?.route != "search"
            ) {
                NavigationBar(
                    modifier = Modifier.height(80.dp)
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
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Album.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Screen.Album.route) {
                AlbumScreen(
                    modifier = Modifier.padding(innerPadding),
                    viewModel = sharedAlbumViewModel,
                    onNavigateToDetail = { index ->
                        navController.navigate("detail/$index")
                    },
                    onSearchClick = {
                        navController.navigate("search")
                    }
                )
            }
            composable(
                route = "detail/{index}",
                arguments = listOf(navArgument("index") { type = NavType.IntType })
            ) { backStackEntry ->
                val index = backStackEntry.arguments?.getInt("index") ?: 0
                val currentItems by sharedAlbumViewModel.currentItems.collectAsStateWithLifecycle()
                val allTags by sharedAlbumViewModel.tags.collectAsStateWithLifecycle()
                val allGroups by groupViewModel.groups.collectAsStateWithLifecycle()
                MediaDetailScreen(
                    mediaItems = currentItems,
                    availableTags = allTags,
                    availableGroups = allGroups,
                    initialIndex = index,
                    onBack = { navController.popBackStack() },
                    onUpdateDescription = { mediaWithTags, desc ->
                        sharedAlbumViewModel.updateDescription(mediaWithTags.media.id, desc)
                    },
                    onAddTag = { mediaWithTags, tag ->
                        sharedAlbumViewModel.addTag(mediaWithTags.media.id, tag.id)
                    },
                    onRemoveTag = { mediaWithTags, tag ->
                        sharedAlbumViewModel.removeTag(mediaWithTags.media.id, tag.id)
                    },
                    onSetGroup = { mediaWithTags, groupId ->
                        groupViewModel.setMediaGroup(mediaWithTags.media.id, groupId)
                    }
                )
            }
            composable("search") {
                SearchScreen(
                    modifier = Modifier.padding(innerPadding),
                    onBack = {
                        if (navController.previousBackStackEntry != null) {
                            navController.popBackStack()
                        }
                    },
                    onNavigateToDetail = { items, index ->
                        sharedAlbumViewModel.setCurrentItems(items)
                        navController.navigate("detail/$index")
                    }
                )
            }
            composable(Screen.Tags.route) { TagsScreen(modifier = Modifier.padding(innerPadding)) }
            composable(Screen.Trash.route) {
                TrashScreen(
                    modifier = Modifier.padding(innerPadding),
                    onNavigateToDetail = { items, index ->
                        sharedAlbumViewModel.setCurrentItemsFromTrash(items)
                        navController.navigate("detail/$index")
                    }
                )
            }
            composable(Screen.Settings.route) { SettingsScreen(modifier = Modifier.padding(innerPadding)) }
        }
    }
}
