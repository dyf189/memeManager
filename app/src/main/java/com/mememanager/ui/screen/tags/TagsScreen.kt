package com.mememanager.ui.screen.tags

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
 * @param tags 当前标签列表
 * @param onEdit 编辑标签回调
 * @param onDelete 删除标签回调（保留标签不可调用）
 * @param onAdd 新建标签回调
 */
@Composable
fun TagsScreen(
    tags: List<TagEntity> = emptyList(),
    onEdit: (TagEntity) -> Unit = {},
    onDelete: (TagEntity) -> Unit = {},
    onAdd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // ── 标题栏 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "标签管理器",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text("新建")
            }
        }

        // ── 列表 ──
        if (tags.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(tags, key = { _, tag -> tag.id }) { _, tag ->
                    TagCard(
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
 * 标签卡片
 *
 * ┌──────────────────────────────────┐
 * │ ≡  🟠  标签名           ✏️  🗑 │
 * └──────────────────────────────────┘
 */
@Composable
private fun TagCard(
    tag: TagEntity,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 拖拽手柄
            Text(
                text = "≡",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(end = 10.dp)
            )

            // 颜色圆块
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(tag.bgColor))
            )

            Spacer(Modifier.width(14.dp))

            // 标签名
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tag.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                if (tag.isReserved) {
                    Text(
                        text = "系统保留",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            // 编辑
            IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "编辑",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 删除 / 锁定
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "保留标签不可删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.padding(10.dp).size(18.dp)
                )
            }
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
