package com.mememanager.ui.viewmodel

import com.mememanager.data.local.entity.MediaType

/**
 * 相册筛选条件
 */
data class FilterState(
    val type: MediaType? = null,
    val source: String? = null,
    val hasDescription: Boolean? = null, // true=有描述, false=无描述, null=不限
    val tagIds: Set<Long> = emptySet(),
    val exported: Boolean? = null, // true=已导出, false=未导出, null=不限
    val startTime: Long? = null,
    val endTime: Long? = null,
    val minSize: Long? = null,
    val maxSize: Long? = null
) {
    val isActive: Boolean
        get() = type != null || source != null || hasDescription != null ||
                tagIds.isNotEmpty() || exported != null || startTime != null ||
                endTime != null || minSize != null || maxSize != null
}

enum class ViewMode { GRID, LIST }

/**
 * 相册 UI 全局状态
 */
data class AlbumUiState(
    val activeFilters: FilterState = FilterState(),
    val isMultiSelectMode: Boolean = false,
    val selectedMediaIds: Set<Long> = emptySet(),
    val viewMode: ViewMode = ViewMode.GRID
)
