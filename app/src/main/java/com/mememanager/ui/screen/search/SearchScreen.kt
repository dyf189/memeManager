package com.mememanager.ui.screen.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.mememanager.data.local.entity.MediaType
import com.mememanager.ui.viewmodel.SearchResultItem
import com.mememanager.ui.viewmodel.SearchViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

/**
 * 搜索页面
 *
 * @param onBack 返回回调
 * @param onNavigateToDetail 点击结果 → 详情页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit = {},
    onNavigateToDetail: (index: Int) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    // 本地输入状态 — 解耦 TextField 与 ViewModel flow，保证输入永远跟手
    var localQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // 防抖同步到 ViewModel
    LaunchedEffect(localQuery) {
        kotlinx.coroutines.delay(300.milliseconds)
        viewModel.setQuery(localQuery)
    }

    // 返回防抖
    var hasNavigatedBack by remember { mutableStateOf(false) }

    // 搜索结果
    val lazyItems = if (localQuery.isBlank()) null
        else viewModel.searchResults.collectAsLazyPagingItems()

    Column(modifier = modifier.fillMaxSize()) {
        // ── 搜索栏 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 3.dp, end = 15.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                    if (!hasNavigatedBack) {
                        hasNavigatedBack = true
                        onBack()
                    }
                }) {
                Icon(Icons.Default.ArrowBack, "返回")
            }
            TextField(
                value = localQuery,
                onValueChange = { localQuery = it },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = { Text("搜索文件名、描述…") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, "搜索") },
                trailingIcon = {
                    if (localQuery.isNotEmpty()) {
                        IconButton(onClick = { localQuery = "" }) {
                            Icon(Icons.Default.Close, "清除")
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(24.dp)
            )
        }

        // ── 内容区 ──
        when {
            localQuery.isBlank() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("输入关键词开始搜索", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            lazyItems == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            lazyItems.itemCount == 0 && lazyItems.loadState.refresh !is LoadState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("无匹配结果", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(lazyItems.itemCount) { index ->
                        val item = lazyItems[index]
                        item?.let { result ->
                            SearchResultRow(
                                item = result,
                                query = localQuery,
                                onClick = {
                                    // TODO: 需要把搜索结果列表传给详情页
                                    // onNavigateToDetail(index)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    item: SearchResultItem,
    query: String,
    onClick: () -> Unit
) {
    val media = item.mediaWithTags.media
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 缩略图 48dp
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(File(media.filePath))
                    .crossfade(true)
                    .build(),
                contentDescription = media.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // 标题 — 匹配高亮
                Text(
                    text = highlightText(media.name, query),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(2.dp))

                // 描述 — 匹配高亮，最多两行，灰色
                val desc = media.description
                if (!desc.isNullOrBlank()) {
                    Text(
                        text = highlightText(desc, query),
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(2.dp))

                // 信息行：大小 + 日期
                Text(
                    text = "${formatSize(media.size)} · ${formatDate(media.createdAt)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/** 简单高亮：query 在 text 中的匹配部分加粗 */
@Composable
private fun highlightText(text: String, query: String) = buildAnnotatedString {
    if (query.isBlank()) {
        append(text)
        return@buildAnnotatedString
    }
    val lower = text.lowercase()
    val q = query.lowercase()
    var pos = 0
    while (pos < text.length) {
        val idx = lower.indexOf(q, pos)
        if (idx < 0) {
            append(text.substring(pos))
            break
        }
        append(text.substring(pos, idx))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFFFF6B35))) {
            append(text.substring(idx, idx + query.length))
        }
        pos = idx + query.length
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "${bytes}B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1fKB".format(kb)
    val mb = kb / 1024.0
    return "%.1fMB".format(mb)
}

private fun formatDate(timestamp: Long): String {
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}
