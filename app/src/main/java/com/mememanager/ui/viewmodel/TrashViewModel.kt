package com.mememanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import com.mememanager.data.local.entity.MediaWithTags
import com.mememanager.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {

    val deletedItems: Flow<PagingData<MediaWithTags>> = mediaRepository.getDeletedFlow()
        .cachedIn(viewModelScope)

    val deletedCount: StateFlow<Int> = mediaRepository.getDeletedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun restore(id: Long) {
        viewModelScope.launch {
            mediaRepository.restore(id)
        }
    }

    fun permanentDelete(id: Long) {
        viewModelScope.launch {
            mediaRepository.deleteById(id)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            mediaRepository.purgeDeletedBefore(System.currentTimeMillis())
        }
    }
}
