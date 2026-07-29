package com.mememanager.ui.screen.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.mememanager.ui.viewmodel.SearchResultItem
import com.mememanager.ui.viewmodel.SearchViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit = {},
    onNavigateToDetail: (List<com.mememanager.data.local.entity.MediaWithTags>, Int) -> Unit = { _, _ -> },
    viewModel: SearchViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    var localQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    var hasNavigatedBack by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    LaunchedEffect(localQuery) {
        if (localQuery.isNotBlank()) kotlinx.coroutines.delay(300)
        viewModel.setQuery(localQuery)
    }

    val lazyItems = viewModel.searchResults.collectAsLazyPagingItems()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val smartMode by viewModel.smartMode.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        // 搜索栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 3.dp, end = 15.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (!hasNavigatedBack) { hasNavigatedBack = true; onBack() } }) {
                Icon(Icons.Default.ArrowBack, "返回")
            }
            TextField(
                value = localQuery,
                onValueChange = { localQuery = it },
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                placeholder = { Text("搜索文件名、描述…") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, "搜索") },
                trailingIcon = {
                    if (localQuery.isNotEmpty()) IconButton(onClick = { localQuery = "" }) { Icon(Icons.Default.Close, "清除") }
                },
                colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                shape = RoundedCornerShape(24.dp)
            )
        }

        // 智能搜索开关
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("智能搜索", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Switch(
                checked = smartMode,
                onCheckedChange = { viewModel.smartMode.value = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                )
            )
        }

        Spacer(Modifier.height(4.dp))

        when {
            localQuery.isBlank() -> {
                // 搜索历史
                if (history.isNotEmpty()) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("搜索历史", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            TextButton(onClick = { viewModel.clearHistory() }) {
                                Text("清空历史", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            history.forEach { h ->
                                FilterChip(
                                    selected = false,
                                    onClick = { localQuery = h },
                                    label = { Text(h, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                )
                            }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("输入关键词开始搜索", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            lazyItems.loadState.refresh is LoadState.Loading && lazyItems.itemCount == 0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            lazyItems.itemCount == 0 && lazyItems.loadState.refresh !is LoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("无匹配结果", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(lazyItems.itemCount) { index ->
                    lazyItems[index]?.let { result ->
                        SearchResultRow(item = result, onClick = {
                            val snapshot = lazyItems.itemSnapshotList.filterNotNull()
                            val idx = snapshot.indexOf(result)
                            if (idx >= 0) {
                                viewModel.recordHistory(localQuery.trim())
                                onNavigateToDetail(snapshot.map { it.mediaWithTags }, idx)
                            }
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(item: SearchResultItem, onClick: () -> Unit) {
    val media = item.mediaWithTags.media
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context).data(File(media.filePath)).crossfade(true).build(),
                contentDescription = media.name, contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = highlightTitle(media.name, item.segments), fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface
                )
                if (!media.description.isNullOrBlank()) {
                    Text(
                        text = highlightDesc(media.description, item.segments), fontSize = 12.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${formatSize(media.size)} · ${formatDate(media.createdAt)}",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

// ── 智能高亮截断 ──

private const val TITLE_MAX = 20
private const val DESC_MAX = 40

@Composable
private fun highlightTitle(text: String, segments: List<String>) = buildAnnotatedString {
    val matches = findMatches(text, segments)
    if (matches.isEmpty()) { append(truncate(text, TITLE_MAX)); return@buildAnnotatedString }
    val m = matches.first()
    val start = (m.first - TITLE_MAX / 2).coerceAtLeast(0)
    val end = (start + TITLE_MAX).coerceAtMost(text.length)
    appendWindow(text, matches, start, end, start > 0, end < text.length)
}

@Composable
private fun highlightDesc(text: String, segments: List<String>) = buildAnnotatedString {
    val matches = findMatches(text, segments)
    if (matches.isEmpty()) { append(truncate(text, DESC_MAX)); return@buildAnnotatedString }
    val span = matches.last().second - matches.first().first
    if (span <= DESC_MAX) {
        val start = (matches.first().first - 4).coerceAtLeast(0)
        appendWindow(text, matches, start, (start + DESC_MAX).coerceAtMost(text.length), start > 0, (start + DESC_MAX) < text.length)
    } else {
        val m = matches.first()
        val start = (m.first - 6).coerceAtLeast(0)
        appendWindow(text, matches, start, (m.second + DESC_MAX - 6).coerceAtMost(text.length), start > 0, (m.second + DESC_MAX - 6) < text.length)
    }
}

private fun findMatches(text: String, segments: List<String>): List<Pair<Int, Int>> {
    if (segments.isEmpty()) return emptyList()
    val lower = text.lowercase()
    val m = mutableListOf<Pair<Int, Int>>()
    for (seg in segments) {
        val q = seg.lowercase(); var p = 0
        while (p < text.length) {
            val i = lower.indexOf(q, p); if (i < 0) break
            m.add(i to i + seg.length); p = i + 1
        }
    }
    m.sortBy { it.first }
    val r = mutableListOf<Pair<Int, Int>>()
    for ((s, e) in m) {
        if (r.isNotEmpty() && s <= r.last().second) r[r.lastIndex] = r.last().first to maxOf(r.last().second, e)
        else r.add(s to e)
    }
    return r
}

private fun truncate(text: String, max: Int) = if (text.length <= max) text else text.take(max - 1) + "…"

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendWindow(
    text: String, matches: List<Pair<Int, Int>>, ws: Int, we: Int, left: Boolean, right: Boolean
) {
    if (left) append("…")
    var p = ws
    for ((s, e) in matches) {
        if (e <= ws) continue; if (s >= we) break
        val cs = s.coerceIn(ws, we); val ce = e.coerceIn(ws, we)
        if (cs > p) append(text.substring(p, cs))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFFFF6B35))) { append(text.substring(cs, ce)) }
        p = ce
    }
    if (p < we) append(text.substring(p, we))
    if (right) append("…")
}

// ── 工具函数 ──

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
    else -> "${"%.1f".format(bytes / (1024.0 * 1024 * 1024))} GB"
}

private fun formatDate(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000}分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000}小时前"
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
    }
}
