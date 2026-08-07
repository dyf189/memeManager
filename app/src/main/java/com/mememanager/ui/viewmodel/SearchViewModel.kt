package com.mememanager.ui.viewmodel

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.mememanager.data.local.entity.MediaWithTags
import com.mememanager.data.repository.SearchRepository
import com.mememanager.data.settings.SettingsKeys
import com.mememanager.util.JiebaTokenizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchResultItem(
    val mediaWithTags: MediaWithTags,
    val segments: List<String> = emptyList()
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** 智能搜索默认关闭：分词词典在后台预热（延迟 5s），刚启动时打开可能卡 */
    val smartMode = MutableStateFlow(false)

    val history: StateFlow<List<String>> = dataStore.data
        .map { prefs ->
            (prefs[SettingsKeys.SEARCH_HISTORY] ?: emptySet()).toList().sortedByDescending { it }
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 解析历史条目：query|smartMode → query, 纯文本 → mode=false（与默认关闭一致） */
    fun parseHistoryEntry(entry: String): Pair<String, Boolean> {
        if (entry.endsWith("|true")) {
            return entry.removeSuffix("|true") to true
        }
        val clean = if (entry.endsWith("|false")) entry.removeSuffix("|false") else entry
        return clean to false
    }

    @OptIn(FlowPreview::class)
    val searchResults: Flow<PagingData<SearchResultItem>> = combine(
            _query.debounce(300).filter { it.isNotBlank() },
            smartMode.debounce(0)
        ) { rawQuery, useSmart -> rawQuery to useSmart }
        .flatMapLatest { (rawQuery, useSmart) ->
            if (useSmart) {
                searchRepository.search(rawQuery).map { pagingData ->
                    val segments = JiebaTokenizer.segment(rawQuery)
                    pagingData.map { SearchResultItem(it, segments) }
                }
            } else {
                searchRepository.searchPlain(rawQuery).map { pagingData ->
                    val segments = if (rawQuery.isNotBlank()) listOf(rawQuery) else emptyList()
                    pagingData.map { SearchResultItem(it, segments) }
                }
            }
        }
        .cachedIn(viewModelScope)

    fun setQuery(q: String) {
        _query.value = q
    }

    /** 记录搜索历史（用户点击结果进入详情时调用） */
    fun recordHistory(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            val suffix = if (smartMode.value) "|true" else "|false"
            dataStore.edit { prefs ->
                val existing = prefs[SettingsKeys.SEARCH_HISTORY] ?: emptySet()
                // 先删旧条目（同 query 不同 mode），再插入新条目
                val clean = existing.filter { e ->
                    val q = e.removeSuffix("|true").removeSuffix("|false")
                    q != query
                }.toMutableSet()
                clean.add(query + suffix)
                prefs[SettingsKeys.SEARCH_HISTORY] = clean
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            dataStore.edit { it[SettingsKeys.SEARCH_HISTORY] = emptySet() }
        }
    }
}
