package com.mememanager.ui.screen.album

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mememanager.data.local.entity.TagEntity
import com.mememanager.ui.theme.MemeManagerTheme
import androidx.compose.ui.tooling.preview.Preview

/**
 * 标签胶囊筛选栏
 *
 * 横向滑动，每个胶囊显示标签名+背景色
 * 第一项固定为"全部"，选中时高亮
 */
@Composable
fun TagChipRow(
    tags: List<TagEntity>,
    selectedTagId: Long? = null,
    onTagSelected: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // "全部" 胶囊
        TagChip(
            label = "全部",
            backgroundColor = if (selectedTagId == null)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.surfaceVariant,
            textColor = if (selectedTagId == null)
                MaterialTheme.colorScheme.onPrimary
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = { onTagSelected(null) }
        )

        // 各标签胶囊
        tags.forEach { tag ->
            TagChip(
                label = tag.name,
                backgroundColor = if (selectedTagId == tag.id)
                    Color(tag.bgColor)
                else
                    Color(tag.bgColor).copy(alpha = 0.3f),
                textColor = if (selectedTagId == tag.id)
                    Color.White
                else
                    Color(tag.bgColor),
                onClick = { onTagSelected(tag.id) }
            )
        }
    }
}

@Composable
private fun TagChip(
    label: String,
    backgroundColor: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}

// ── Preview ──

@Preview(showBackground = true)
@Composable
private fun PreviewTagChipRow() {
    MemeManagerTheme {
        TagChipRow(
            tags = listOf(
                TagEntity(id = 1, name = "开心", bgColor = 0xFFFF9800.toInt()),
                TagEntity(id = 2, name = "爆笑", bgColor = 0xFFF44336.toInt()),
                TagEntity(id = 3, name = "可爱", bgColor = 0xFFE91E63.toInt()),
                TagEntity(id = 4, name = "沙雕", bgColor = 0xFF4CAF50.toInt()),
                TagEntity(id = 5, name = "猫猫", bgColor = 0xFF2196F3.toInt()),
            ),
            selectedTagId = null,
            onTagSelected = {}
        )
    }
}
