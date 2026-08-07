package com.mememanager.ui.screen.album

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.decode.BitmapFactoryDecoder
import com.mememanager.data.local.entity.MediaType
import com.mememanager.data.local.entity.MediaWithTags
import com.mememanager.data.local.entity.StorageType

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

    // 用 Box 而非 Card：网格密集场景 Card(Surface) 的色层/shadow 处理是纯开销
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surface)
            .then(
                if (isSelected) Modifier.border(
                    2.dp, MaterialTheme.colorScheme.primary, RectangleShape
                ) else Modifier
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // ── 图片加载 ──
            val requestBuilder = ImageRequest.Builder(LocalContext.current)
                // 直接传字符串：Coil 自动识别 content:// 为 URI、否则按文件路径处理
                .data(media.filePath)
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
            // 用 AsyncImage 而非 SubcomposeAsyncImage：Subcompose 每个 item 多一层
            // 子组合槽，一屏 12 个可见项 → 每帧 55ms+ 组合开销（滚动卡顿根因）。
            // loading 占位直接用 modifier 背景色（加载中显示浅灰，完成后图片覆盖）。
            AsyncImage(
                model = requestBuilder.build(),
                contentDescription = media.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFEEEEEE))
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

    // 单个 Canvas 画圆点：替代 Row+多个 Box（一屏 12 个 item × 4 个圆点 =
    // 60 个布局节点 → 1 个 draw 节点，滚动 measure/layout 开销大减）
    Canvas(
        modifier = modifier.height(10.dp)
    ) {
        val gap = 10.dp.toPx()
        val dotSize = 8.dp.toPx()
        tagColors.take(displayCount).forEachIndexed { i, colorInt ->
            drawCircle(
                color = Color(colorInt),
                radius = dotSize / 2f,
                center = Offset(dotSize / 2f + i * gap, dotSize / 2f)
            )
        }
        if (overflow > 0) {
            // 溢出计数画在最后（圆形深灰底 + 白字）
            val cx = dotSize / 2f + displayCount * gap
            drawCircle(
                color = Color(0x66000000),
                radius = dotSize / 2f,
                center = Offset(cx, dotSize / 2f)
            )
            drawContext.canvas.nativeCanvas.drawText(
                "+$overflow",
                cx - (if (overflow > 9) 9f else 6f),
                dotSize / 2f + 4.5f,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 9f * density
                    textAlign = android.graphics.Paint.Align.LEFT
                }
            )
        }
    }
}
