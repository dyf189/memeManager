package com.mememanager.ui.components

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.mememanager.data.local.entity.MediaEntity
import com.mememanager.util.ShareUtil

@Composable
fun ShareMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    media: MediaEntity,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, modifier = modifier) {
        DropdownMenuItem(
            text = { Text("分享到…") },
            onClick = {
                onDismiss()
                ShareUtil.shareMedia(context, media)
            },
            leadingIcon = { Icon(Icons.Default.Share, null) }
        )
        DropdownMenuItem(
            text = { Text("保存到相册") },
            onClick = {
                onDismiss()
                ShareUtil.saveToGallery(context, media)
            },
            leadingIcon = { Icon(Icons.Default.SaveAlt, null) }
        )
    }
}
