package com.mememanager.data.repository

import com.mememanager.data.local.dao.GroupDao
import com.mememanager.data.local.entity.GroupEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepository @Inject constructor(
    private val groupDao: GroupDao
) {
    fun getAll(): Flow<List<GroupEntity>> = groupDao.getAll()

    suspend fun getById(id: Long): GroupEntity? = groupDao.getById(id)

    /** 创建组：sortOrder = 当前数量 */
    suspend fun create(name: String, color: Int): Long {
        val size = getAll().first().size
        return groupDao.insert(GroupEntity(name = name, color = color, sortOrder = size))
    }

    suspend fun update(group: GroupEntity) = groupDao.update(group)

    suspend fun delete(group: GroupEntity) = groupDao.delete(group)
}
