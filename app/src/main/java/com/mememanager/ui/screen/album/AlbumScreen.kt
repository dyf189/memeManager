package com.mememanager.ui.screen.album

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mememanager.data.local.entity.MediaEntity
import com.mememanager.data.local.entity.MediaType
import com.mememanager.data.local.entity.MediaWithTags
import com.mememanager.data.local.entity.StorageType
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
    modifier: Modifier = Modifier
) {
    // ── 样本数据（后续 2.1 替换为 ViewModel 数据源） ──
    val sampleTags = remember {
        listOf(
            TagEntity(id = 1, name = "开心", bgColor = 0xFFFF9800.toInt(), sortOrder = 0),
            TagEntity(id = 2, name = "爆笑", bgColor = 0xFFF44336.toInt(), sortOrder = 1),
            TagEntity(id = 3, name = "可爱", bgColor = 0xFFE91E63.toInt(), sortOrder = 2),
            TagEntity(id = 4, name = "沙雕", bgColor = 0xFF4CAF50.toInt(), sortOrder = 3),
            TagEntity(id = 5, name = "猫猫", bgColor = 0xFF2196F3.toInt(), sortOrder = 4),
            TagEntity(id = 6, name = "狗狗", bgColor = 0xFF9C27B0.toInt(), sortOrder = 5),
        )
    }

    val now = System.currentTimeMillis()
    val sampleMedia = remember {
        listOf(
            // 刚刚
            makeMedia(id = 1, name = "meme1.jpg", type = MediaType.IMAGE, time = now - 10_000),
            makeMedia(id = 2, name = "meme2.gif", type = MediaType.GIF, time = now - 20_000),
            // 几分钟前
            makeMedia(id = 3, name = "meme3.jpg", type = MediaType.IMAGE, time = now - 3 * 60_000),
            makeMedia(id = 4, name = "meme4.jpg", type = MediaType.IMAGE, time = now - 5 * 60_000),
            // 几小时前
            makeMedia(id = 5, name = "meme5.mp4", type = MediaType.VIDEO, time = now - 2 * 3600_000),
            makeMedia(id = 6, name = "meme6.jpg", type = MediaType.IMAGE, time = now - 4 * 3600_000),
            makeMedia(id = 7, name = "meme7.jpg", type = MediaType.IMAGE, time = now - 6 * 3600_000),
            // 昨天
            makeMedia(id = 8, name = "meme8.gif", type = MediaType.GIF, time = now - 25 * 3600_000),
            makeMedia(id = 9, name = "meme9.jpg", type = MediaType.IMAGE, time = now - 26 * 3600_000),
            makeMedia(id = 10, name = "meme10.jpg", type = MediaType.IMAGE, time = now - 27 * 3600_000),
            // 前天
            makeMedia(id = 11, name = "meme11.png", type = MediaType.IMAGE, time = now - 50 * 3600_000),
            makeMedia(id = 12, name = "meme12.gif", type = MediaType.GIF, time = now - 51 * 3600_000),
            // 三天前
            makeMedia(id = 13, name = "meme13.jpg", type = MediaType.IMAGE, time = now - 72 * 3600_000),
            // 更早
            makeMedia(id = 14, name = "meme14.jpg", type = MediaType.IMAGE, time = now - 10 * 86400_000L),
            makeMedia(id = 15, name = "meme15.gif", type = MediaType.GIF, time = now - 15 * 86400_000L),
            makeMedia(id = 16, name = "meme16.jpg", type = MediaType.IMAGE, time = now - 400 * 86400_000L),
        )
    }

    val sampleMediaWithTags = remember {
        sampleMedia.mapIndexed { index, media ->
            val tagCount = (index % 4) + 1
            MediaWithTags(
                media = media,
                tags = sampleTags.take(tagCount)
            )
        }
    }

    // ── 构建带时间分组的 AlbumItem 列表 ──
    val albumItems = remember(sampleMediaWithTags) {
        buildAlbumItems(sampleMediaWithTags)
    }

    // ── UI 状态 ──
    var isFilterExpanded by remember { mutableStateOf(false) }
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
        Box(modifier = Modifier.padding(innerPadding)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // ── 标签胶囊栏 + 筛选面板（跨列） ──
                item(span = { GridItemSpan(maxLineSpan) }) {
                    TagChipRow(
                        tags = sampleTags,
                        selectedTagId = selectedTagId,
                        onTagSelected = { selectedTagId = it }
                    )
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    FilterPanel(
                        expanded = isFilterExpanded,
                        onToggle = { isFilterExpanded = !isFilterExpanded },
                        selectedType = selectedType,
                        onTypeSelected = { selectedType = it },
                        onApply = { isFilterExpanded = false },
                        onClear = {
                            selectedType = null
                            selectedTagId = null
                        }
                    )
                }

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
        }
    }
}

/**
 * 将 MediaWithTags 列表按时间分组转换为 AlbumItem 列表
 */
private fun buildAlbumItems(items: List<MediaWithTags>): List<AlbumItem> {
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

/**
 * 辅助：快速创建 MediaEntity 样本
 */
private fun makeMedia(
    id: Long,
    name: String,
    type: MediaType,
    time: Long,
    filePath: String = "/fake/$name",
    size: Long = (100_000L..5_000_000L).random()
): MediaEntity {
    return MediaEntity(
        id = id,
        name = name,
        filePath = filePath,
        type = type,
        size = size,
        width = if (type == MediaType.VIDEO) null else 480,
        height = if (type == MediaType.VIDEO) null else 480,
        storageType = StorageType.PRIVATE,
        source = listOf("相机", "微信", "QQ", "下载", null).random(),
        description = if (id % 3 == 0L) "这是一个示例描述 #$id" else null,
        takenTime = time,
        createdAt = time,
        updatedAt = time
    )
}
