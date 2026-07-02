package com.mememanager.ui.screen.album

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.mememanager.data.local.entity.MediaType
import com.mememanager.data.local.entity.MediaWithTags
import com.mememanager.data.local.entity.StorageType
import java.io.File

/**
 * 相册网格缩略图
 *
 * 使用 Coil 加载图片/GIF/视频帧，加载中显示类型色块占位，加载失败显示错误图标。
 * 右下角最多显示 4 个标签圆点，超出显示灰色 "+N"。
 */

@Composable
fun AlbumGridItem(
    mediaWithTags: MediaWithTags,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val media = mediaWithTags.media
    val tags = mediaWithTags.tags

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .then(
                if (isSelected) Modifier.border(
                    2.dp, MaterialTheme.colorScheme.primary, RectangleShape
                ) else Modifier
            )
            .clickable(onClick = onClick),
        shape = RectangleShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // ── 图片加载 ──
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(media.filePath))
                    .crossfade(true)
                    .build(),
                contentDescription = media.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    // 加载中：浅灰占位
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color(0xFFEEEEEE))
                    )
                },
                error = {
                    // 加载失败：浅灰占位
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color(0xFFE0E0E0))
                    )
                }
            )
            // ── 类型角标（左上角，GIF/视频） ──
            if (media.type != MediaType.IMAGE) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 3.dp, end = 3.dp)
                        .background(Color(0x99000000), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 0.dp)
                        .height(17.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (media.type) {
                            MediaType.GIF -> "GIF"
                            MediaType.VIDEO -> "VIDEO"
                            else -> ""
                        },
                        fontSize = 9.sp,
                        color = Color.White,
                        lineHeight = 9.sp
                    )
                }
            }

            // ── 选中蒙层 ──
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(20.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✓",
                            color = Color.White,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // ── 右下角标签圆点 ──
            if (tags.isNotEmpty()) {
                TagDots(
                    tagColors = tags.map { it.bgColor },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                )
            }

            // ── 存储类型标记 ──
            if (media.storageType == StorageType.EXTERNAL) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 30.dp)
                        .background(Color(0x99000000), RoundedCornerShape(3.dp))
                        .padding(horizontal = 3.dp, vertical = 1.dp)
                        .height(17.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🔗", fontSize = 9.sp, lineHeight = 9.sp)
                }
            }
        }
    }
}

@Composable
private fun TagDots(
    tagColors: List<Int>,
    modifier: Modifier = Modifier
) {
    val displayCount = minOf(tagColors.size, 4)
    val overflow = tagColors.size - 4

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tagColors.take(displayCount).forEach { colorInt ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(colorInt))
            )
        }
        if (overflow > 0) {
            Text(
                text = "+$overflow",
                fontSize = 9.sp,
                color = Color.Gray,
                modifier = Modifier.padding(start = 1.dp)
            )
        }
    }
}
