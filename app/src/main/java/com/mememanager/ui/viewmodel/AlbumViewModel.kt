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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
            mediaRepository.getAlbumFlow(type = filters.type)
        }
        .cachedIn(viewModelScope)

    // ── UI 状态 ──

    private val _uiState = MutableStateFlow(AlbumUiState())
    val uiState: StateFlow<AlbumUiState> = _uiState.asStateFlow()

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
}
