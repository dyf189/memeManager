package com.mememanager.ui.screen.album

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.decode.BitmapFactoryDecoder
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
    gifAnimated: Boolean = false,
    thumbSize: Int = 512,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val media = mediaWithTags.media
    val tags = mediaWithTags.sortedTags

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .then(
                if (isSelected) Modifier.border(
                    2.dp, MaterialTheme.colorScheme.primary, RectangleShape
                ) else Modifier
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RectangleShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // ── 图片加载 ──
            val requestBuilder = ImageRequest.Builder(LocalContext.current)
                .data(File(media.filePath))
                // 解码尺寸按网格列数动态计算（3 列约 350px），避免全尺寸解码大图
                // 200 张 512px ≈ 200MB 内存缓存 → LRU 淘汰 + GC 压力
                .size(if (gifAnimated && media.type == MediaType.GIF) minOf(256, thumbSize) else thumbSize)
                // 关闭 crossfade：滚动时一屏多张图同时淡入会产生主线程动画叠加卡顿
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
            if (media.type == MediaType.GIF && !gifAnimated) {
                // 开关默认关闭：网格 GIF 只显示第一帧（静态），动画在网格中
                // 持续解码+重绘是滚动卡顿元凶；详情页仍走全局 GifDecoder 播放
                requestBuilder.decoderFactory(BitmapFactoryDecoder.Factory())
            }
            SubcomposeAsyncImage(
                model = requestBuilder.build(),
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
