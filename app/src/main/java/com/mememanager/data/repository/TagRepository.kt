package com.mememanager.data.repository

import com.mememanager.data.local.dao.TagDao
import com.mememanager.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TagRepository @Inject constructor(
    private val tagDao: TagDao
) {
    fun getAll(): Flow<List<TagEntity>> = tagDao.getAll()

    fun getById(id: Long): Flow<TagEntity?> = tagDao.getById(id)

    fun search(query: String): Flow<List<TagEntity>> = tagDao.search(query)

    suspend fun getByName(name: String): TagEntity? = tagDao.getByName(name)

    suspend fun save(tag: TagEntity): Long = tagDao.insert(tag)

    suspend fun update(tag: TagEntity) = tagDao.update(tag)

    /** 批量更新 sortOrder（单事务原子提交） */
    suspend fun updateSortOrders(tags: List<TagEntity>) = tagDao.updateSortOrders(tags)

    suspend fun delete(tag: TagEntity) = tagDao.delete(tag)

    /**
     * 创建标签：分配下一个排序号 + 预设颜色
     */
    suspend fun createTag(name: String, presetColors: List<Int>, nextColorIndex: Int): TagEntity {
        val tag = TagEntity(
            name = name,
            bgColor = presetColors.getOrElse(nextColorIndex) { 0xFFCCCCCC.toInt() },
            sortOrder = nextColorIndex
        )
        val id = tagDao.insert(tag)
        return tag.copy(id = id)
    }
}
