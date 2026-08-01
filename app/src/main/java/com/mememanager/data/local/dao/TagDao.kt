package com.mememanager.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mememanager.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tag: TagEntity): Long

    @Update
    suspend fun update(tag: TagEntity)

    /** 批量更新 sortOrder — 单事务原子提交，杜绝中间态重复 sortOrder 泄漏到 UI */
    @Transaction
    suspend fun updateSortOrders(tags: List<TagEntity>) {
        tags.forEach { update(it) }
    }

    @Delete
    suspend fun delete(tag: TagEntity)

    @Query("SELECT * FROM tags WHERE id = :id")
    fun getById(id: Long): Flow<TagEntity?>

    @Query("SELECT * FROM tags ORDER BY sortOrder ASC, id ASC")
    fun getAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun search(query: String): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): TagEntity?
}
