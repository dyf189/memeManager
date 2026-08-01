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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/** 预设标签颜色（Material3 色板） */
val PRESET_COLORS = listOf(
    0xFFEF5350.toInt(), // 红
    0xFFFF9800.toInt(), // 橙
    0xFFFFC107.toInt(), // 琥珀
    0xFF4CAF50.toInt(), // 绿
    0xFF2196F3.toInt(), // 蓝
    0xFF9C27B0.toInt(), // 紫
    0xFFE91E63.toInt(), // 粉
    0xFF00BCD4.toInt(), // 青
    0xFF795548.toInt(), // 棕
    0xFF607D8B.toInt(), // 蓝灰
)

@HiltViewModel
class TagsViewModel @Inject constructor(
    private val tagRepository: TagRepository
) : ViewModel() {

    val tags: StateFlow<List<TagEntity>> = tagRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 下一个预设颜色索引（排除保留标签占用的颜色） */
    fun nextPresetColor(existingTags: List<TagEntity>): Int {
        val usedIndices = existingTags.mapNotNull { tag ->
            PRESET_COLORS.indexOf(tag.bgColor).takeIf { it >= 0 }
        }.toSet()
        val idx = (0..<PRESET_COLORS.size).firstOrNull { it !in usedIndices }
            ?: (existingTags.size % PRESET_COLORS.size)
        return PRESET_COLORS[idx]
    }

    fun addTag(name: String, bgColor: Int) {
        viewModelScope.launch {
            val sortOrder = tags.value.size
            tagRepository.save(TagEntity(name = name, bgColor = bgColor, sortOrder = sortOrder))
        }
    }

    fun updateTag(tag: TagEntity) {
        viewModelScope.launch { tagRepository.update(tag) }
    }

    fun deleteTag(tag: TagEntity) {
        viewModelScope.launch { tagRepository.delete(tag) }
    }

    /** 拖拽排序后批量更新 sortOrder — Mutex 串行化，连续拖拽的更新排队不交错 */
    private val sortMutex = Mutex()

    fun updateSortOrder(reordered: List<TagEntity>) {
        viewModelScope.launch {
            sortMutex.withLock {
                // 全量更新（不做 tag.sortOrder != index 跳过判断——对象旧值可能已被污染）
                tagRepository.updateSortOrders(
                    reordered.mapIndexed { index, tag -> tag.copy(sortOrder = index) }
                )
            }
        }
    }
}
