package com.mememanager.ui.screen.tags

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mememanager.data.local.entity.TagEntity
import com.mememanager.ui.theme.MemeManagerTheme

/**
 * 标签管理器页面
 *
 * 展示所有标签，支持编辑、删除、新建。
 * 保留标签（isReserved）不可删除，显示锁图标。
 *
 * @param tags 当前标签列表
 * @param onEdit 编辑标签回调
 * @param onDelete 删除标签回调（保留标签不可调用）
 * @param onAdd 新建标签回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(
    tags: List<TagEntity> = emptyList(),
    onEdit: (TagEntity) -> Unit = {},
    onDelete: (TagEntity) -> Unit = {},
    onAdd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("标签管理器") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "新建标签")
            }
        }
    ) { innerPadding ->
        if (tags.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "暂无标签",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 16.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                itemsIndexed(tags, key = { _, tag -> tag.id }) { _, tag ->
                    TagRow(
                        tag = tag,
                        onEdit = { onEdit(tag) },
                        onDelete = if (tag.isReserved) null else { { onDelete(tag) } }
                    )
                }
            }
        }
    }
}

/**
 * 单行标签项
 *
 * [≡] [🟠颜色圆块] 标签名          [✏️] [🗑/🔒]
 */
@Composable
private fun TagRow(
    tag: TagEntity,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 拖拽手柄
        Icon(
            Icons.Default.DragHandle,
            contentDescription = "拖拽排序",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )

        // 颜色圆块
        Box(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(tag.bgColor))
        )

        // 标签名
        Text(
            text = tag.name,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )

        // 编辑
        IconButton(onClick = onEdit) {
            Icon(
                Icons.Default.Edit,
                contentDescription = "编辑",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 删除 / 锁定
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        } else {
            Icon(
                Icons.Default.Lock,
                contentDescription = "保留标签不可删除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(12.dp).size(24.dp)
            )
        }
    }
}

// ── Preview ──

@Preview(showBackground = true)
@Composable
private fun PreviewTagsScreen() {
    val sampleTags = listOf(
        TagEntity(id = 1, name = "开心", bgColor = 0xFFFF9800.toInt(), sortOrder = 0, isReserved = false),
        TagEntity(id = 2, name = "爆笑", bgColor = 0xFFF44336.toInt(), sortOrder = 1, isReserved = false),
        TagEntity(id = 3, name = "可爱", bgColor = 0xFFE91E63.toInt(), sortOrder = 2, isReserved = false),
        TagEntity(id = 4, name = "沙雕", bgColor = 0xFF4CAF50.toInt(), sortOrder = 3, isReserved = false),
        TagEntity(id = 5, name = "猫猫", bgColor = 0xFF2196F3.toInt(), sortOrder = 4, isReserved = false),
        TagEntity(id = 6, name = "[已导出]", bgColor = 0xFF607D8B.toInt(), sortOrder = 5, isReserved = true),
    )
    MemeManagerTheme {
        TagsScreen(tags = sampleTags)
    }
}
