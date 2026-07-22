package com.mememanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.mememanager.data.local.entity.MediaWithTags
import com.mememanager.data.repository.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * 搜索页搜索结果项
 *
 * @param mediaWithTags 原始媒体数据
 * @param titleAnnotated 标题 AnnotatedString（匹配高亮+截断）
 * @param descAnnotated  描述 AnnotatedString（匹配高亮+截断，可为 null）
 */
data class SearchResultItem(
    val mediaWithTags: MediaWithTags,
    val titleAnnotated: String = "",
    val descAnnotated: String = ""
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    // ── 搜索结果 ──
    @OptIn(FlowPreview::class)
    val searchResults: Flow<PagingData<SearchResultItem>> = _query
        .debounce(300)
        .filter { it.isNotBlank() }
        .flatMapLatest { rawQuery ->
            searchRepository.search(rawQuery).map { pagingData ->
                pagingData.map { media ->
                    SearchResultItem(mediaWithTags = media)
                }
            }
        }
        .cachedIn(viewModelScope)

    fun setQuery(q: String) {
        _query.value = q
    }
}
