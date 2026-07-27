package com.mememanager.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mememanager.data.local.entity.MediaEntity
import com.mememanager.data.local.entity.MediaType
import com.mememanager.data.local.entity.MediaWithTags
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    // ── 基础 CRUD ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(media: MediaEntity): Long

    @Update
    suspend fun update(media: MediaEntity)

    @Delete
    suspend fun delete(media: MediaEntity)

    @Query("DELETE FROM media WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM media WHERE id = :id")
    fun getById(id: Long): Flow<MediaEntity?>

    @Query("SELECT * FROM media WHERE id = :id")
    suspend fun getByIdSuspend(id: Long): MediaEntity?

    @Query("SELECT * FROM media ORDER BY createdAt DESC")
    fun getAll(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE type = :type ORDER BY createdAt DESC")
    fun getByType(type: MediaType): Flow<List<MediaEntity>>

    // ── 带标签的关系查询 ──

    @Transaction
    @Query("SELECT * FROM media WHERE id = :id")
    fun getMediaWithTagsById(id: Long): Flow<MediaWithTags?>

    @Transaction
    @Query("SELECT * FROM media WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun getAlbumPagingSource(): PagingSource<Int, MediaWithTags>

    @Transaction
    @Query("SELECT * FROM media WHERE isDeleted = 0 AND type = :type ORDER BY createdAt DESC")
    fun getAlbumPagingSourceByType(type: MediaType): PagingSource<Int, MediaWithTags>

    @Transaction
    @Query("""
        SELECT * FROM media WHERE isDeleted = 0 
        AND id IN (
            SELECT DISTINCT r.mediaId FROM media_tag_cross_ref r 
            WHERE r.tagId IN (:tagIds)
        )
        ORDER BY createdAt DESC
    """)
    fun getAlbumPagingSourceByTags(tagIds: Set<Long>): PagingSource<Int, MediaWithTags>

    @Transaction
    @Query("""
        SELECT * FROM media WHERE isDeleted = 0 AND type = :type
        AND id IN (
            SELECT DISTINCT r.mediaId FROM media_tag_cross_ref r 
            WHERE r.tagId IN (:tagIds)
        )
        ORDER BY createdAt DESC
    """)
    fun getAlbumPagingSourceByTypeAndTags(type: MediaType, tagIds: Set<Long>): PagingSource<Int, MediaWithTags>

    // ── 回收站 ──

    @Query("UPDATE media SET isDeleted = 1, deletedTime = :deletedTime WHERE id = :id")
    suspend fun softDelete(id: Long, deletedTime: Long)

    @Query("UPDATE media SET isDeleted = 0, deletedTime = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    @Transaction
    @Query("SELECT * FROM media WHERE isDeleted = 1 ORDER BY deletedTime DESC")
    fun getDeletedPagingSource(): PagingSource<Int, MediaWithTags>

    @Query("SELECT COUNT(*) FROM media WHERE isDeleted = 1")
    fun getDeletedCount(): Flow<Int>

    @Query("DELETE FROM media WHERE isDeleted = 1 AND deletedTime < :cutoffTime")
    suspend fun purgeDeletedBefore(cutoffTime: Long): Int

    // ── 统计 ──

    @Query("SELECT COUNT(*) FROM media WHERE isDeleted = 0")
    fun getActiveCount(): Flow<Int>
}
