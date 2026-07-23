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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject

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

    fun setQuery(q: String) {
        _query.value = q
    }

    /**
     * 每次调用生成独立的 cold Paging 流，不同查询词之间互不干扰
     */
    fun search(query: String): Flow<PagingData<SearchResultItem>> {
        if (query.isBlank()) return emptyFlow()
        return try {
            searchRepository.search(query).map { pagingData ->
                pagingData.map { SearchResultItem(mediaWithTags = it) }
            }
        } catch (e: Exception) {
            emptyFlow()
        }
    }
}
