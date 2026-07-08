package com.mememanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mememanager.data.local.entity.TagEntity
import com.mememanager.data.repository.TagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TagsViewModel @Inject constructor(
    private val tagRepository: TagRepository
) : ViewModel() {

    val tags: StateFlow<List<TagEntity>> = tagRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addTag(name: String, bgColor: Int, sortOrder: Int) {
        viewModelScope.launch {
            tagRepository.save(TagEntity(name = name, bgColor = bgColor, sortOrder = sortOrder))
        }
    }

    fun updateTag(tag: TagEntity) {
        viewModelScope.launch { tagRepository.update(tag) }
    }

    fun deleteTag(tag: TagEntity) {
        viewModelScope.launch { tagRepository.delete(tag) }
    }
}
