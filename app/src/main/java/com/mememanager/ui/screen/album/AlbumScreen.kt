package com.mememanager.ui.screen.album

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.mememanager.data.local.entity.MediaType
import com.mememanager.ui.components.ShareMenu
import com.mememanager.ui.util.TimeGroupUtil
import com.mememanager.ui.viewmodel.AlbumViewModel
import com.mememanager.ui.viewmodel.GroupViewModel
import com.mememanager.ui.viewmodel.SettingsViewModel

/**
 * 主相册页面
 *
 * 数据来源：AlbumViewModel（Room → Repository → Paging 3）
 * 布局：
 * ┌──────────────────────────────┐
 * │ 🔍 搜索栏              筛选 │
 * ├──────────────────────────────┤
 * │ [全部] [标签1] [标签2] …     │  ← TagChipRow
 * ├──────────────────────────────┤
 * │ 筛选面板（覆盖层）           │  ← FilterPanel
 * ├──────────────────────────────┤
 * │  网格视图 + 粘性时间分组头   │  ← LazyVerticalGrid + stickyHeader
 * │                          [+] │  ← FAB 导入
 * └──────────────────────────────┘
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    viewModel: AlbumViewModel = hiltViewModel(),
    onNavigateToDetail: (index: Int) -> Unit = {},
    onSearchClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val columns = settings.gridColumns
    val lazyPagingItems = viewModel.pagingDataFlow.collectAsLazyPagingItems()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val groupViewModel: GroupViewModel = hiltViewModel()
    var showGroupView by remember { mutableStateOf(false) }

    // ── 图片选择器 ──
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importMedia(uris)
        }
    }

    // ── 文件选择器（SAF） ──
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importMedia(uris)
        }
    }

    // ── .mpak 分片选择器 ──
    val context = LocalContext.current
    val mpakPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importMpak(uri) { result ->
                val msg = if (result.failed.isEmpty()) {
                    "导入成功：${result.success} 个媒体"
                } else {
                    "成功 ${result.success} 个，失败 ${result.failed.size} 个：${result.failed.first()}"
                }
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    var showImportMenu by remember { mutableStateOf(false) }

    // ── 纯 UI 状态（不进入 ViewModel） ──
    var isFilterPanelVisible by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf<MediaType?>(null) }
    var selectedTagIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showExportDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        val items = lazyPagingItems.itemSnapshotList
        Column(modifier = Modifier.fillMaxSize()) {
            // ── 批量操作栏 ──
            if (uiState.isMultiSelectMode) {
                val allIds = items.mapNotNull { it?.media?.id }.toSet()
                val allSelected = uiState.selectedMediaIds.containsAll(allIds)
                var shareExpanded by remember { mutableStateOf(false) }
                BatchActionBar(
                    selectedCount = uiState.selectedMediaIds.size,
                    totalCount = allIds.size,
                    onCancel = { viewModel.exitMultiSelectMode() },
                    onSelectAll = {
                        if (allSelected) viewModel.unselectGroup(allIds)
                        else viewModel.selectAll(allIds)
                    },
                    onShare = { shareExpanded = true },
                    onExport = { showExportDialog = true },
                    onDelete = { viewModel.softDeleteSelected() }
                )
                // 分享下拉
                val firstSelectedMedia = items.mapNotNull { it?.media }.firstOrNull { it.id in uiState.selectedMediaIds }
                if (firstSelectedMedia != null) {
                    ShareMenu(expanded = shareExpanded, onDismiss = { shareExpanded = false }, media = firstSelectedMedia)
                }
                // 导出预览弹窗
                if (showExportDialog) {
                    val selectedMedia = items.filter { it != null && it.media.id in uiState.selectedMediaIds }
                        .map { it!! }
                    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
                    ExportDialog(
                        mediaList = selectedMedia,
                        initialShardSizeMB = settings.shardSizeMB,
                        exportDirUri = settings.exportDirUri,
                        onDismiss = { showExportDialog = false }
                    )
                }
            }
            // ── 搜索栏 + 筛选按钮 ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { onSearchClick() },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "搜索",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "搜索表情…",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { isFilterPanelVisible = !isFilterPanelVisible }) {
                    Icon(
                        if (isFilterPanelVisible) Icons.Default.Close else Icons.Default.FilterList,
                        contentDescription = "筛选",
                        tint = if (isFilterPanelVisible)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { showGroupView = !showGroupView }) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = "组别",
                        tint = if (showGroupView)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── 标签胶囊栏 ──
            TagChipRow(
                tags = tags,
                selectedTagIds = selectedTagIds,
                onTagSelected = { ids ->
                    selectedTagIds = ids
                    viewModel.setTagFilter(ids)
                }
            )

            // ── 组别视图 / 网格 + 筛选覆盖层 ──
            if (showGroupView) {
                GroupView(
                    viewModel = groupViewModel,
                    onBack = { showGroupView = false },
                    onMediaClick = { mwt ->
                        val list = listOf(mwt)
                        viewModel.setCurrentItems(list)
                        onNavigateToDetail(0)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                )
            } else {
            Box(modifier = Modifier.fillMaxSize()) {
                // ── 网格视图 ──
                if (items.isEmpty() && lazyPagingItems.loadState.refresh is LoadState.Loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "加载中…",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    for (i in 0 until items.size) {
                        val mediaWithTags = items[i] ?: continue

                        // ── 时间分组粘性头（跨列全宽） ──
                        val group = TimeGroupUtil.getGroup(mediaWithTags.media.createdAt)
                        val prevItem = if (i > 0) items[i - 1] else null
                        val prevGroup = prevItem?.let { TimeGroupUtil.getGroup(it.media.createdAt) }
                        if (prevGroup == null || group.sortKey != prevGroup.sortKey) {
                            stickyHeader(key = "header_${group.sortKey}") {
                                val groupIds = items.drop(i).mapNotNull { item ->
                                    val m = item ?: return@mapNotNull null
                                    val g = TimeGroupUtil.getGroup(m.media.createdAt)
                                    if (g.sortKey == group.sortKey) m.media.id else null
                                }.takeWhile { it != null }.map { it!! }.toSet()
                                val allGroupSelected = groupIds.isNotEmpty() && groupIds.all { it in uiState.selectedMediaIds }
                                TimeGroupHeader(
                                    group = group,
                                    isMultiSelectMode = uiState.isMultiSelectMode,
                                    isGroupSelected = allGroupSelected,
                                    onGroupToggle = {
                                        if (allGroupSelected) viewModel.unselectGroup(groupIds)
                                        else viewModel.selectGroup(groupIds)
                                    }
                                )
                            }
                        }

                        item(key = mediaWithTags.media.id) {
                            AlbumGridItem(
                                mediaWithTags = mediaWithTags,
                                isSelected = mediaWithTags.media.id in uiState.selectedMediaIds,
                                onClick = {
                                    if (uiState.isMultiSelectMode) {
                                        viewModel.toggleSelection(mediaWithTags.media.id)
                                    } else {
                                        val currentList = items.mapNotNull { it }
                                        viewModel.setCurrentItems(currentList)
                                        val idx = currentList.indexOf(mediaWithTags)
                                        onNavigateToDetail(if (idx >= 0) idx else 0)
                                    }
                                },
                                onLongClick = {
                                    viewModel.enterMultiSelectMode(mediaWithTags.media.id)
                                }
                            )
                        }
                    }
                }

                // ── 筛选面板覆盖层 ──
                FilterPanel(
                    visible = isFilterPanelVisible,
                    onDismiss = { isFilterPanelVisible = false },
                    selectedType = selectedType,
                    onTypeSelected = {
                        selectedType = it
                        viewModel.setTypeFilter(it)
                    },
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
            } // else（组别视图/网格切换）
        }

        // ── FAB ──
        if (!uiState.isMultiSelectMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                FloatingActionButton(
                    onClick = { showImportMenu = true }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "导入媒体")
                }
                DropdownMenu(
                    expanded = showImportMenu,
                    onDismissRequest = { showImportMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("从相册导入") },
                        onClick = {
                            showImportMenu = false
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("从文件导入") },
                        onClick = {
                            showImportMenu = false
                            filePickerLauncher.launch(arrayOf("image/*", "video/*"))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("导入 .mpak 分片") },
                        onClick = {
                            showImportMenu = false
                            mpakPickerLauncher.launch(arrayOf("application/octet-stream"))
                        }
                    )
                }
            }
        }
    }
}
