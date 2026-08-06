package com.mememanager.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.mememanager.data.local.entity.TagEntity
import com.mememanager.data.local.entity.MediaType
import com.mememanager.data.local.entity.MediaWithTags
import com.mememanager.data.repository.MediaRepository
import com.mememanager.data.repository.TagRepository
import com.mememanager.util.MediaImporter
import com.mememanager.util.MpakImporter
import com.mememanager.util.MpakImporter.ImportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val tagRepository: TagRepository,
    private val application: Application
) : ViewModel() {

    // ── 标签数据 ──

    val tags: StateFlow<List<TagEntity>> = tagRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── 筛选状态 ──

    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    // ── 分页数据流（筛选变化时自动重建） ──

    val pagingDataFlow: Flow<PagingData<MediaWithTags>> = _filterState
        .flatMapLatest { filters ->
            mediaRepository.getAlbumFlow(type = filters.type, tagIds = filters.tagIds)
        }
        .cachedIn(viewModelScope)

    /** 当前筛选集总数（详情页分母） */
    val totalCount: StateFlow<Int> = _filterState
        .flatMapLatest { filters ->
            mediaRepository.getActiveMediaCount(type = filters.type, tagIds = filters.tagIds)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // ── UI 状态 ──

    private val _uiState = MutableStateFlow(AlbumUiState())
    val uiState: StateFlow<AlbumUiState> = _uiState.asStateFlow()

    // ── 当前媒体列表缓存（供详情页共享） ──

    private val _currentItems = MutableStateFlow<List<MediaWithTags>>(emptyList())
    val currentItems: StateFlow<List<MediaWithTags>> = _currentItems.asStateFlow()

    fun setCurrentItems(items: List<MediaWithTags>) {
        _currentItems.value = items
    }

    fun setCurrentItemsFromTrash(items: List<MediaWithTags>) {
        _currentItems.value = items
    }

    // ── 操作 ──

    fun setTypeFilter(type: MediaType?) {
        _filterState.update { it.copy(type = type) }
    }

    fun setSourceFilter(source: String?) {
        _filterState.update { it.copy(source = source) }
    }

    fun setDescriptionFilter(hasDescription: Boolean?) {
        _filterState.update { it.copy(hasDescription = hasDescription) }
    }

    fun setTagFilter(tagIds: Set<Long>) {
        _filterState.update { it.copy(tagIds = tagIds) }
    }

    fun setExportedFilter(exported: Boolean?) {
        _filterState.update { it.copy(exported = exported) }
    }

    fun setTimeRange(start: Long?, end: Long?) {
        _filterState.update { it.copy(startTime = start, endTime = end) }
    }

    fun setSizeRange(min: Long?, max: Long?) {
        _filterState.update { it.copy(minSize = min, maxSize = max) }
    }

    fun clearFilters() {
        _filterState.value = FilterState()
    }

    fun setViewMode(mode: ViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
    }

    // ── 多选模式 ──

    fun enterMultiSelectMode(mediaId: Long) {
        _uiState.update {
            it.copy(
                isMultiSelectMode = true,
                selectedMediaIds = setOf(mediaId)
            )
        }
    }

    fun toggleSelection(mediaId: Long) {
        _uiState.update { state ->
            val newSelection = if (mediaId in state.selectedMediaIds) {
                state.selectedMediaIds - mediaId
            } else {
                state.selectedMediaIds + mediaId
            }
            state.copy(
                selectedMediaIds = newSelection,
                isMultiSelectMode = newSelection.isNotEmpty()
            )
        }
    }

    fun exitMultiSelectMode() {
        _uiState.update {
            it.copy(isMultiSelectMode = false, selectedMediaIds = emptySet())
        }
    }

    /** 全选当前筛选集全部媒体（查库拿完整 id 列表，不受分页加载限制） */
    fun selectAll() {
        viewModelScope.launch {
            val filters = _filterState.value
            val ids = mediaRepository.getActiveMediaIds(
                type = filters.type,
                tagIds = filters.tagIds
            )
            _uiState.update {
                it.copy(isMultiSelectMode = true, selectedMediaIds = ids.toSet())
            }
        }
    }

    /** 取消全选：清空整个选中集（不能只取消已加载项，未加载的会残留） */
    fun unselectAll() {
        _uiState.update { it.copy(selectedMediaIds = emptySet()) }
    }

    fun selectGroup(ids: Set<Long>) {
        _uiState.update { state ->
            val merged = state.selectedMediaIds + ids
            state.copy(selectedMediaIds = merged, isMultiSelectMode = merged.isNotEmpty())
        }
    }

    fun unselectGroup(ids: Set<Long>) {
        _uiState.update { state ->
            val remaining = state.selectedMediaIds - ids
            state.copy(selectedMediaIds = remaining, isMultiSelectMode = remaining.isNotEmpty())
        }
    }

    // ── 批量操作 ──

    fun softDeleteSelected() {
        viewModelScope.launch {
            _uiState.value.selectedMediaIds.forEach { id ->
                mediaRepository.softDelete(id)
            }
            exitMultiSelectMode()
        }
    }

    fun addTagToSelected(tagId: Long) {
        viewModelScope.launch {
            _uiState.value.selectedMediaIds.forEach { mediaId ->
                mediaRepository.addTagToMedia(mediaId, tagId)
            }
        }
    }

    fun removeTagFromSelected(tagId: Long) {
        viewModelScope.launch {
            _uiState.value.selectedMediaIds.forEach { mediaId ->
                mediaRepository.removeTagFromMedia(mediaId, tagId)
            }
        }
    }

    // ── 媒体导入 ──

    fun importMedia(uris: List<Uri>) {
        viewModelScope.launch {
            uris.forEach { uri ->
                val entity = MediaImporter.importFromUri(application, uri)
                mediaRepository.insert(entity)
            }
        }
    }

    /** 导入 .mpak 分片：解析校验 + 入库 + 标签合并 */
    fun importMpak(uri: Uri, onDone: (ImportResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = MpakImporter.importMpak(application, uri)
            // 逐条入库 + 标签合并（[已导出] 已在解析时过滤）
            for (parsed in result.items) {
                val mediaId = mediaRepository.insert(parsed.entity)
                for (tagName in parsed.tagNames) {
                    val tagId = tagRepository.getByName(tagName)?.id
                        ?: tagRepository.save(
                            TagEntity(name = tagName, bgColor = 0xFFCCCCCC.toInt())
                        )
                    mediaRepository.addTagToMedia(mediaId, tagId)
                }
            }
            onDone(result)
        }
    }

    // ── 单个媒体操作（详情页用） ──

    fun updateDescription(mediaId: Long, description: String) {
        viewModelScope.launch {
            val entity = mediaRepository.getMediaById(mediaId) ?: return@launch
            mediaRepository.update(entity.copy(description = description.ifEmpty { null }))
            // 刷新 currentItems 缓存
            val items = _currentItems.value.toMutableList()
            val idx = items.indexOfFirst { it.media.id == mediaId }
            if (idx >= 0) {
                items[idx] = items[idx].copy(
                    media = items[idx].media.copy(description = description.ifEmpty { null })
                )
                _currentItems.value = items
            }
        }
    }

    fun addTag(mediaId: Long, tagId: Long) {
        viewModelScope.launch {
            mediaRepository.addTagToMedia(mediaId, tagId)
            // 从 Room 重新加载当前媒体的完整数据
            val updated = mediaRepository.getMediaWithTagsById(mediaId).first() ?: return@launch
            val items = _currentItems.value.toMutableList()
            val idx = items.indexOfFirst { it.media.id == mediaId }
            if (idx >= 0) items[idx] = updated
            _currentItems.value = items
        }
    }

    fun removeTag(mediaId: Long, tagId: Long) {
        viewModelScope.launch {
            mediaRepository.removeTagFromMedia(mediaId, tagId)
            // 从 Room 重新加载当前媒体的完整数据
            val updated = mediaRepository.getMediaWithTagsById(mediaId).first() ?: return@launch
            val items = _currentItems.value.toMutableList()
            val idx = items.indexOfFirst { it.media.id == mediaId }
            if (idx >= 0) items[idx] = updated
            _currentItems.value = items
        }
    }
}
