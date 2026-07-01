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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mememanager.data.local.entity.MediaEntity
import com.mememanager.data.local.entity.MediaType
import com.mememanager.data.local.entity.MediaWithTags
import com.mememanager.data.local.entity.StorageType
import com.mememanager.data.local.entity.TagEntity
import com.mememanager.ui.util.TimeGroupUtil
import com.mememanager.ui.theme.MemeManagerTheme
import androidx.compose.ui.tooling.preview.Preview

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

// ── Preview ──

private fun makeSample(id: Long, name: String, type: MediaType, time: Long) = MediaEntity(
    id = id, name = name, filePath = "/fake/$name", type = type,
    size = 100_000 + id * 100_000, storageType = StorageType.PRIVATE,
    createdAt = time, takenTime = time, updatedAt = time
)

@Preview(showBackground = true)
@Composable
fun PreviewAlbumScreen() {
    val sampleTags = listOf(
        TagEntity(id = 1, name = "开心", bgColor = 0xFFFF9800.toInt(), sortOrder = 0),
        TagEntity(id = 2, name = "爆笑", bgColor = 0xFFF44336.toInt(), sortOrder = 1),
        TagEntity(id = 3, name = "可爱", bgColor = 0xFFE91E63.toInt(), sortOrder = 2),
        TagEntity(id = 4, name = "沙雕", bgColor = 0xFF4CAF50.toInt(), sortOrder = 3),
        TagEntity(id = 5, name = "猫猫", bgColor = 0xFF2196F3.toInt(), sortOrder = 4),
        TagEntity(id = 6, name = "狗狗", bgColor = 0xFF9C27B0.toInt(), sortOrder = 5),
    )
    val now = System.currentTimeMillis()
    val mediaItems = listOf(
        makeSample(id = 1, name = "meme1.jpg", type = MediaType.IMAGE, time = now - 10_000),
        makeSample(id = 2, name = "meme2.gif", type = MediaType.GIF, time = now - 20_000),
        makeSample(id = 3, name = "meme3.jpg", type = MediaType.IMAGE, time = now - 3 * 60_000),
        makeSample(id = 4, name = "meme4.jpg", type = MediaType.IMAGE, time = now - 5 * 60_000),
        makeSample(id = 5, name = "meme5.mp4", type = MediaType.VIDEO, time = now - 2 * 3600_000),
        makeSample(id = 6, name = "meme6.jpg", type = MediaType.IMAGE, time = now - 4 * 3600_000),
        makeSample(id = 7, name = "meme7.jpg", type = MediaType.IMAGE, time = now - 6 * 3600_000),
        makeSample(id = 8, name = "meme8.gif", type = MediaType.GIF, time = now - 25 * 3600_000),
        makeSample(id = 9, name = "meme9.jpg", type = MediaType.IMAGE, time = now - 26 * 3600_000),
        makeSample(id = 10, name = "meme10.jpg", type = MediaType.IMAGE, time = now - 27 * 3600_000),
        makeSample(id = 11, name = "meme11.png", type = MediaType.IMAGE, time = now - 50 * 3600_000),
        makeSample(id = 12, name = "meme12.gif", type = MediaType.GIF, time = now - 51 * 3600_000),
        makeSample(id = 13, name = "meme13.jpg", type = MediaType.IMAGE, time = now - 72 * 3600_000),
        makeSample(id = 14, name = "meme14.jpg", type = MediaType.IMAGE, time = now - 10 * 86400_000L),
        makeSample(id = 15, name = "meme15.gif", type = MediaType.GIF, time = now - 15 * 86400_000L),
        makeSample(id = 16, name = "meme16.jpg", type = MediaType.IMAGE, time = now - 400 * 86400_000L),
    ).mapIndexed { index, m ->
        MediaWithTags(media = m, tags = sampleTags.take((index % 4) + 1))
    }
    MemeManagerTheme {
        AlbumScreen(columns = 3, tags = sampleTags, mediaItems = mediaItems)
    }
}

