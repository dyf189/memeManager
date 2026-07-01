package com.mememanager.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mememanager.data.local.entity.MediaTagCrossRef
import com.mememanager.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaTagRefDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(crossRef: MediaTagCrossRef)

    @Delete
    suspend fun delete(crossRef: MediaTagCrossRef)

    @Query("DELETE FROM media_tag_cross_ref WHERE mediaId = :mediaId")
    suspend fun deleteByMediaId(mediaId: Long)

    @Query("DELETE FROM media_tag_cross_ref WHERE tagId = :tagId")
    suspend fun deleteByTagId(tagId: Long)

    @Query("DELETE FROM media_tag_cross_ref WHERE mediaId = :mediaId AND tagId = :tagId")
    suspend fun deleteByMediaIdAndTagId(mediaId: Long, tagId: Long)

    @Query("SELECT t.* FROM tags t INNER JOIN media_tag_cross_ref r ON t.id = r.tagId WHERE r.mediaId = :mediaId ORDER BY t.sortOrder ASC, t.name ASC")
    fun getTagsForMedia(mediaId: Long): Flow<List<TagEntity>>

    @Query("SELECT tagId FROM media_tag_cross_ref WHERE mediaId = :mediaId")
    suspend fun getTagIdsForMedia(mediaId: Long): List<Long>

    @Query("SELECT mediaId FROM media_tag_cross_ref WHERE tagId = :tagId")
    fun getMediaIdsForTag(tagId: Long): Flow<List<Long>>
}
