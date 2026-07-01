package com.mememanager.ui.screen.album

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mememanager.data.local.entity.MediaType
import com.mememanager.data.local.entity.MediaWithTags
import com.mememanager.data.local.entity.TagEntity
import com.mememanager.ui.util.TimeGroupUtil

/**
 * 主相册页面
 *
 * 布局：
 * ┌──────────────────────────────┐
 * │ 🔍 搜索栏（占位）       [🔽]│
 * ├──────────────────────────────┤
 * │ [全部] [标签1] [标签2] …     │  ← TagChipRow
 * ├──────────────────────────────┤
 * │ 筛选面板（可折叠）           │  ← FilterPanel
 * ├──────────────────────────────┤
 * │  网格视图（3列）            │  ← LazyVerticalGrid + stickyHeader
 * │  ┌───┐ ┌───┐ ┌───┐         │
 * │  │ ● │ │   │ │ ●●│         │
 * └──────────────────────────────┘
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    columns: Int = 3,
    tags: List<TagEntity> = emptyList(),
    mediaItems: List<MediaWithTags> = emptyList(),
    modifier: Modifier = Modifier
) {
    // ── 构建带时间分组的 AlbumItem 列表 ──
    val albumItems = remember(mediaItems) {
        buildAlbumItems(mediaItems)
    }

    // ── UI 状态 ──
    var searchQuery by remember { mutableStateOf("") }
    var isFilterPanelVisible by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf<MediaType?>(null) }
    var selectedTagId by remember { mutableStateOf<Long?>(null) }
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }

    Scaffold(
        topBar = {
            if (isMultiSelectMode) {
                BatchActionBar(
                    selectedCount = selectedIds.size,
                    onCancel = {
                        isMultiSelectMode = false
                        selectedIds = emptySet()
                    },
                    onDelete = {
                        // 假数据：不做实际删除
                        isMultiSelectMode = false
                        selectedIds = emptySet()
                    }
                )
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // ── 搜索栏 + 筛选按钮 ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索表情…", fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "搜索",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { isFilterPanelVisible = !isFilterPanelVisible }) {
                    Text(
                        text = "筛选",
                        fontSize = 14.sp,
                        color = if (isFilterPanelVisible)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── 标签胶囊栏 ──
            TagChipRow(
                tags = tags,
                selectedTagId = selectedTagId,
                onTagSelected = { selectedTagId = it }
            )

            // ── 网格 + 筛选覆盖层 ──
            Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // ── 时间分组头（粘性） + 媒体网格 ──
                    albumItems.forEach { item ->
                        when (item) {
                            is AlbumItem.Header -> {
                                stickyHeader {
                                    TimeGroupHeader(group = item.group)
                                }
                            }

                            is AlbumItem.Media -> {
                                val mediaId = item.mediaWithTags.media.id
                                item(key = mediaId) {
                                    AlbumGridItem(
                                        mediaWithTags = item.mediaWithTags,
                                        isSelected = mediaId in selectedIds,
                                        onClick = {
                                            if (isMultiSelectMode) {
                                                selectedIds = if (mediaId in selectedIds)
                                                    selectedIds - mediaId
                                                else
                                                    selectedIds + mediaId
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // ── 筛选面板覆盖层 ──
                FilterPanel(
                    visible = isFilterPanelVisible,
                    onDismiss = { isFilterPanelVisible = false },
                    selectedType = selectedType,
                    onTypeSelected = { selectedType = it },
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }
}

/**
 * 将 MediaWithTags 列表按时间分组转换为 AlbumItem 列表
 */
fun buildAlbumItems(items: List<MediaWithTags>): List<AlbumItem> {
    val result = mutableListOf<AlbumItem>()
    var lastGroupKey = -1L
    for (item in items) {
        val group = TimeGroupUtil.getGroup(item.media.createdAt)
        if (group.sortKey != lastGroupKey) {
            result.add(AlbumItem.Header(group))
            lastGroupKey = group.sortKey
        }
        result.add(AlbumItem.Media(item))
    }
    return result
}

