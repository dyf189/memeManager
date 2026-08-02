package com.mememanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mememanager.data.local.entity.GroupEntity
import com.mememanager.data.local.entity.MediaWithTags
import com.mememanager.data.repository.GroupRepository
import com.mememanager.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 组别视图的一个区块：组 + 组内媒体 */
data class GroupSection(
    val group: GroupEntity? = null,   // null = 未分组区块
    val media: List<MediaWithTags> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GroupViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    val groups: StateFlow<List<GroupEntity>> = groupRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 组别视图区块：每个组 + 未分组，各自带媒体列表 */
    val sections: StateFlow<List<GroupSection>> = groups.flatMapLatest { gs ->
        val groupFlows: List<Flow<GroupSection>> = gs.map { g ->
            mediaRepository.getMediaWithTagsByGroup(g.id).map { media -> GroupSection(g, media) }
        }
        val ungrouped: Flow<GroupSection> = mediaRepository.getUngroupedMediaWithTags()
            .map { media -> GroupSection(null, media) }
        if (groupFlows.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(groupFlows) { arr -> arr.toList() }
                .combine(ungrouped) { groupSections, ung -> groupSections + ung }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun create(name: String, color: Int) {
        viewModelScope.launch { groupRepository.create(name, color) }
    }

    fun update(group: GroupEntity) {
        viewModelScope.launch { groupRepository.update(group) }
    }

    fun delete(group: GroupEntity) {
        viewModelScope.launch {
            // 组内媒体全部归为未分组
            mediaRepository.clearGroup(group.id)
            groupRepository.delete(group)
        }
    }

    /** 详情页/批量操作：设置或移除媒体的组 */
    fun setMediaGroup(mediaId: Long, groupId: Long?) {
        viewModelScope.launch { mediaRepository.setMediaGroup(mediaId, groupId) }
    }
}
