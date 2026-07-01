package com.mememanager.ui.screen.album

import com.mememanager.data.local.entity.MediaWithTags
import com.mememanager.ui.util.TimeGroup

/**
 * AlbumScreen 使用的 UI 条目模型
 * Header = 时间分组粘性头，Media = 媒体项
 */
sealed class AlbumItem {
    data class Header(val group: TimeGroup) : AlbumItem()
    data class Media(val mediaWithTags: MediaWithTags) : AlbumItem()
}
