package com.mememanager.ui.screen.trash

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.mememanager.data.local.entity.MediaWithTags
import com.mememanager.ui.viewmodel.TrashViewModel
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrashScreen(
    modifier: Modifier = Modifier,
    onNavigateToDetail: (List<MediaWithTags>, Int) -> Unit = { _, _ -> },
    viewModel: TrashViewModel = hiltViewModel()
) {
    val lazyItems = viewModel.deletedItems.collectAsLazyPagingItems()
    var itemToDelete by remember { mutableStateOf<MediaWithTags?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        // 标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("回收站", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }

        if (lazyItems.itemCount == 0 && lazyItems.loadState.isIdle) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("回收站为空", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(lazyItems.itemCount) { index ->
                    lazyItems[index]?.let { media ->
                        TrashGridItem(
                            mediaWithTags = media,
                            onClick = {
                                val snapshot = lazyItems.itemSnapshotList.filterNotNull()
                                val idx = snapshot.indexOf(media)
                                if (idx >= 0) onNavigateToDetail(snapshot, idx)
                            },
                            onRestore = { viewModel.restore(media.media.id) },
                            onDelete = { itemToDelete = media }
                        )
                    }
                }
            }
        }
    }

    // 彻底删除确认
    itemToDelete?.let { media ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("永久删除") },
            text = { Text("「${media.media.name}」将被永久删除，不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.permanentDelete(media.media.id)
                    itemToDelete = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { itemToDelete = null }) { Text("取消") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrashGridItem(
    mediaWithTags: MediaWithTags,
    onClick: () -> Unit = {},
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.combinedClickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Box {
            // 缩略图
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(File(mediaWithTags.media.filePath))
                    .crossfade(true)
                    .build(),
                contentDescription = mediaWithTags.media.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
                loading = {
                    Box(Modifier.fillMaxSize().background(Color(0xFFEEEEEE)))
                },
                error = {
                    Box(Modifier.fillMaxSize().background(Color(0xFFEEEEEE)))
                }
            )
            // 操作按钮 — 底部半透明条
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onRestore, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Restore, "恢复", modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, "彻底删除", modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
