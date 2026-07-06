package com.mememanager.ui.screen.detail

import android.R.attr.layoutDirection
import android.graphics.Paint
import android.graphics.Region
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mememanager.data.local.entity.MediaEntity
import com.mememanager.data.local.entity.MediaType
import com.mememanager.data.local.entity.MediaWithTags
import com.mememanager.data.local.entity.StorageType
import com.mememanager.data.local.entity.TagEntity
import com.mememanager.ui.theme.MemeManagerTheme
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius

/**
 * 媒体详情页
 *
 * 全屏大图 + 底部 ModalBottomSheet 展示可编辑的描述和标签。
 *
 * @param mediaItems 媒体列表（支持 HorizontalPager 左右滑动）
 * @param availableTags 可选标签列表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailScreen(
    mediaItems: List<MediaWithTags> = emptyList(),
    availableTags: List<TagEntity> = emptyList(),
    initialIndex: Int = 0,
    onBack: () -> Unit = {},
    onEdit: (MediaWithTags) -> Unit = {},
    onShare: (MediaWithTags) -> Unit = {},
    onUpdateDescription: (MediaWithTags, String) -> Unit = { _, _ -> },
    onAddTag: (MediaWithTags, TagEntity) -> Unit = { _, _ -> },
    onRemoveTag: (MediaWithTags, TagEntity) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    if (mediaItems.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("无媒体", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (mediaItems.size - 1).coerceAtLeast(0)),
        pageCount = { mediaItems.size }
    )
    val currentMedia = mediaItems[pagerState.currentPage]

    var isMenuExpanded by remember { mutableStateOf(false) }
    var showDescriptionDialog by remember { mutableStateOf(false) }
    var showTagPicker by remember { mutableStateOf(false) }
    var editingDescription by remember(currentMedia) { mutableStateOf(currentMedia.media.description ?: "") }
    var showBottomSheet by remember { mutableStateOf(true) }
    var isTagDeleteMode by remember { mutableStateOf(false) }

    val sheetState = rememberBottomSheetScaffoldState()

    BottomSheetScaffold(
        modifier = modifier,
        scaffoldState = sheetState,
        sheetPeekHeight = 48.dp,
        sheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        sheetDragHandle = { BottomSheetDefaults.DragHandle() },
        sheetContent = {
            MediaBottomSheetContent(
                media = currentMedia,
                isTagDeleteMode = isTagDeleteMode,
                onEnterTagDeleteMode = { isTagDeleteMode = true },
                onExitTagDeleteMode = { isTagDeleteMode = false },
                onEditDescription = { showDescriptionDialog = true },
                onAddTagClick = { showTagPicker = true },
                onRemoveTag = { tag -> onRemoveTag(currentMedia, tag) }
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentMedia.media.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 16.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { onShare(currentMedia) }) {
                        Icon(Icons.Default.Share, contentDescription = "分享")
                    }
                    Box {
                        IconButton(onClick = { isMenuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = isMenuExpanded,
                            onDismissRequest = { isMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("保存到相册") },
                                onClick = { isMenuExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("查看原始信息") },
                                onClick = { isMenuExpanded = false }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val media = mediaItems[page]
                MediaDisplay(media = media)
            }

            if (mediaItems.size > 1) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${mediaItems.size}",
                    fontSize = 12.sp,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }
    }

    // ── 描述编辑弹窗 ──
    if (showDescriptionDialog) {
        AlertDialog(
            onDismissRequest = { showDescriptionDialog = false },
            title = { Text("编辑描述") },
            text = {
                OutlinedTextField(
                    value = editingDescription,
                    onValueChange = { editingDescription = it },
                    placeholder = { Text("输入描述…") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateDescription(currentMedia, editingDescription)
                    showDescriptionDialog = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDescriptionDialog = false }) { Text("取消") }
            }
        )
    }

    // ── 标签选择弹窗 ──
    if (showTagPicker) {
        val currentTagIds = currentMedia.tags.map { it.id }.toSet()
        val filtered = availableTags/*.filter { it.id !in currentTagIds }*/

        var selectedIds by remember { mutableStateOf(setOf<Long>()) }

        Dialog(
            onDismissRequest = { showTagPicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "选择标签",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        tonalElevation = 2.dp,
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        if (filtered.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("所有标签已添加", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            key(selectedIds) {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    contentPadding = PaddingValues(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.heightIn(max = 400.dp)
                                ) {
                                    items(filtered.size,key = { index -> filtered[index].id }) { index ->
                                        val tag = filtered[index]
                                        val isSelected = tag.id in selectedIds
                                        TagItem(
                                            tag = tag,
                                            isSelected = isSelected,
                                            onClick = {
                                                selectedIds = if (isSelected)
                                                    selectedIds - tag.id
                                                else
                                                    selectedIds + tag.id
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(30.dp, Alignment.End)
                    ) {
                        FilledTonalButton(
                            onClick = { showTagPicker = false },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) { Text("取消") }

                        FilledTonalButton(
                            onClick = {
                                val toAdd = filtered.filter { it.id in selectedIds }
                                toAdd.forEach { tag -> onAddTag(currentMedia, tag) }
                                showTagPicker = false
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) { Text("确定") }
                    }
                }
            }
        }
    }
}


fun Modifier.innerShadow(
    shape: androidx.compose.ui.graphics.Shape,
    color: Color = Color.Black.copy(alpha = 0.15f),
    blur: Dp = 12.dp,
    offsetY: Dp = 2.dp
): Modifier = this.drawWithContent {
    // 先绘制原有内容（背景和前景）
    drawContent()

    // 然后在其上绘制内阴影
    val strokeWidth = blur.toPx()
    val offsetPx = offsetY.toPx()

    // 多层描边，从外向内逐渐变淡变细，模拟模糊扩散
    for (i in 0..10) {
        val fraction = i / 10f
        val currentOffset = offsetPx * (1f - fraction)
        val currentWidth = strokeWidth * (1f - fraction)
        val currentAlpha = 0.15f * (1f - fraction) * (1f - fraction)

        drawRoundRect(
            color = color.copy(alpha = currentAlpha),
            topLeft = Offset(currentOffset, currentOffset),
            size = Size(size.width - currentOffset * 2, size.height - currentOffset * 2),
            cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
            style = Stroke(width = currentWidth)
        )
    }
}

// ── 媒体展示区 ──

@Composable
private fun MediaDisplay(media: MediaWithTags) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = when (media.media.type) {
                    MediaType.IMAGE -> "🖼️"
                    MediaType.GIF -> "🎞️"
                    MediaType.VIDEO -> "🎬"
                },
                fontSize = 64.sp
            )
            Spacer(Modifier.height(12.dp))
            Text(media.media.name, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
            Text(formatSize(media.media.size), fontSize = 11.sp, color = Color.White.copy(alpha = 0.4f))
        }
    }
}

// ── BottomSheet 内容 ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaBottomSheetContent(
    media: MediaWithTags,
    isTagDeleteMode: Boolean,
    onEnterTagDeleteMode: () -> Unit,
    onExitTagDeleteMode: () -> Unit,
    onEditDescription: () -> Unit,
    onAddTagClick: () -> Unit,
    onRemoveTag: (TagEntity) -> Unit
) {
    val maxSheetHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * 2 / 3

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .heightIn(max = maxSheetHeight)
                .verticalScroll(rememberScrollState())
        ) {

            Spacer(modifier = Modifier.height(10.dp))

            // ── 描述 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "描述",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(5.dp))

                // 编辑图标按钮（铅笔）
                IconButton(
                    onClick = onEditDescription,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "编辑描述",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = media.media.description ?: "暂无描述",
                style = MaterialTheme.typography.bodyMedium,
                color = if (media.media.description != null)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 2.dp)
            )

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        // 标签
        Text(
            "标签",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (media.tags.isEmpty()) {
                Text(
                    "暂无标签",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
            /*
            media.tags.forEach { tag ->
                val tagColor = Color(tag.bgColor)

                Surface(
                    shape = RoundedCornerShape(50.dp),
                    color = tagColor.copy(alpha = 0.12f),   // 极淡的背景，仅作为彩色滤镜
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(
                            start = 12.dp, end = if (isTagDeleteMode) 4.dp else 12.dp,
                            top = 4.dp, bottom = 4.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tag.name,
                            color = MaterialTheme.colorScheme.onSurface,   // 统一深色文字
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (isTagDeleteMode) {
                            Spacer(Modifier.width(2.dp))
                            IconButton(
                                onClick = { onRemoveTag(tag) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "删除",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)  // 统一半透明深色
                                )
                            }
                        }
                    }
                }
            }
            */
            media.tags.forEach { tag ->
                val tagColor = Color(tag.bgColor)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, tagColor.copy(alpha = 0.5f)),
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(
                            start = 12.dp, end = if (isTagDeleteMode) 4.dp else 12.dp,
                            top = 4.dp, bottom = 4.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tag.name,
                            color = tagColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (isTagDeleteMode) {
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = { onRemoveTag(tag) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "删除",
                                    modifier = Modifier.size(14.dp),
                                    tint = tagColor.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            if (isTagDeleteMode) {
                FilledTonalIconButton(
                    onClick = onExitTagDeleteMode,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Check, "完成", modifier = Modifier.size(18.dp))
                }
            } else {
                FilledTonalIconButton(
                    onClick = onAddTagClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Add, "添加标签", modifier = Modifier.size(22.dp))
                }
                if (!media.tags.isEmpty()) {
                    FilledTonalIconButton(
                        onClick = onEnterTagDeleteMode,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text(
                            "−",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        // 元数据
        MetaDataItem("来源", media.media.source ?: "未知")
        MetaDataItem("时间", formatDate(media.media.createdAt))
        MetaDataItem("大小", formatSize(media.media.size))
        MetaDataItem("存储", when (media.media.storageType) {
            StorageType.PRIVATE -> "私有内部"
            StorageType.PUBLIC -> "公共目录"
            StorageType.EXTERNAL -> "外部索引"
        })
        if (media.media.width != null && media.media.height != null) {
            MetaDataItem("分辨率", "${media.media.width}×${media.media.height}")
        }
        }
    }
}

@Composable
private fun TagItem(
    tag: TagEntity,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) Modifier.border(
                    width = 2.dp,
                    color = Color(0xFF1976D2), // Material Blue 700
                    shape = RoundedCornerShape(16.dp)
                ) else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 7.dp else 2.dp)
    ) {
//    Card(
//        modifier = Modifier.fillMaxWidth(),
//        shape = RoundedCornerShape(12.dp),
//        colors = CardDefaults.cardColors(
//            containerColor = if (isSelected)
//                MaterialTheme.colorScheme.primaryContainer
//            else
//                MaterialTheme.colorScheme.surface   // 改成纯 surface，完全不透明
//        ),
//        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 1.dp)
//    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    indication = ripple(),
                    interactionSource = remember { MutableInteractionSource() }
                ) { onClick() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color(tag.bgColor))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = tag.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MetaDataItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> "%.1f MB".format(mb)
        kb >= 1.0 -> "%.0f KB".format(kb)
        else -> "$bytes B"
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

// ── Preview ──

@Preview(showBackground = true)
@Composable
fun PreviewMediaDetailScreen() {
    val now = System.currentTimeMillis()
    val allTags = listOf(
        TagEntity(id = 1, name = "开心", bgColor = 0xFFFF9800.toInt(), sortOrder = 0),
        TagEntity(id = 2, name = "爆笑", bgColor = 0xFFF44336.toInt(), sortOrder = 1),
        TagEntity(id = 3, name = "可爱", bgColor = 0xFFE91E63.toInt(), sortOrder = 2),
        TagEntity(id = 4, name = "沙雕", bgColor = 0xFF4CAF50.toInt(), sortOrder = 3),
    )

    var mediaItems by remember {
        mutableStateOf(
            listOf(
                MediaWithTags(
                    media = MediaEntity(
                        id = 1, name = "搞笑猫咪.gif", filePath = "/fake/cat.gif",
                        type = MediaType.GIF, size = 2_345_678,
                        width = 720, height = 1280,
                        storageType = StorageType.PRIVATE,
                        source = "微信", description = "一只超级搞笑的橘猫表情包",
                        createdAt = now - 3 * 3600_000, takenTime = now - 3 * 3600_000, updatedAt = now
                    ),
                    tags = listOf(allTags[0], allTags[1])
                ),
                MediaWithTags(
                    media = MediaEntity(
                        id = 2, name = "沙雕狗子.mp4", filePath = "/fake/dog.mp4",
                        type = MediaType.VIDEO, size = 15_678_900,
                        width = 1080, height = 1920,
                        storageType = StorageType.PUBLIC,
                        source = "QQ", description = null,
                        createdAt = now - 5 * 3600_000, takenTime = now - 5 * 3600_000, updatedAt = now
                    ),
                    tags = emptyList()
                ),
                MediaWithTags(
                    media = MediaEntity(
                        id = 3, name = "熊猫头.jpg", filePath = "/fake/panda.jpg",
                        type = MediaType.IMAGE, size = 512_000,
                        width = 480, height = 480,
                        storageType = StorageType.EXTERNAL,
                        source = null, description = "经典熊猫头，懂的都懂",
                        createdAt = now - 86400_000, takenTime = now - 86400_000, updatedAt = now
                    ),
                    tags = listOf(allTags[2])
                ),
            )
        )
    }

    MemeManagerTheme {
        MediaDetailScreen(
            mediaItems = mediaItems,
            availableTags = allTags,
            initialIndex = 1,
            onUpdateDescription = { media, newDesc ->
                mediaItems = mediaItems.map { item ->
                    if (item.media.id == media.media.id) {
                        item.copy(media = item.media.copy(description = newDesc.ifEmpty { null }))
                    } else item
                }
            },
            onAddTag = { media, tag ->
                mediaItems = mediaItems.map { item ->
                    if (item.media.id == media.media.id && tag !in item.tags) {
                        item.copy(tags = item.tags + tag)
                    } else item
                }
            },
            onRemoveTag = { media, tag ->
                mediaItems = mediaItems.map { item ->
                    if (item.media.id == media.media.id) {
                        item.copy(tags = item.tags.filter { it.id != tag.id })
                    } else item
                }
            }
        )
    }
}
