package com.mememanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.mememanager.data.local.entity.MediaWithTags
import com.mememanager.data.repository.SearchRepository
import com.mememanager.util.JiebaTokenizer
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

data class SearchResultItem(
    val mediaWithTags: MediaWithTags,
    /** 结巴分词结果（用于 UI 高亮，不存 AnnotatedString） */
    val segments: List<String> = emptyList()
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(FlowPreview::class)
    val searchResults: Flow<PagingData<SearchResultItem>> = _query
        .debounce(300)
        .filter { it.isNotBlank() }
        .flatMapLatest { rawQuery ->
            searchRepository.search(rawQuery).map { pagingData ->
                val segments = JiebaTokenizer.segment(rawQuery)
                pagingData.map { media ->
                    SearchResultItem(mediaWithTags = media, segments = segments)
                }
            }
        }
        .cachedIn(viewModelScope)

    fun setQuery(q: String) {
        _query.value = q
    }
}
