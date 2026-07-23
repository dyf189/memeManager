package com.mememanager.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.mememanager.data.local.dao.MediaDao
import com.mememanager.data.local.dao.MediaFtsDao
import com.mememanager.data.local.dao.MediaTagRefDao
import com.mememanager.data.local.entity.MediaEntity
import com.mememanager.data.local.entity.MediaTagCrossRef
import com.mememanager.data.local.entity.MediaType
import com.mememanager.data.local.entity.MediaWithTags
import com.mememanager.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    private val mediaDao: MediaDao,
    private val mediaTagRefDao: MediaTagRefDao,
    private val mediaFtsDao: MediaFtsDao
) {
    companion object {
        private const val PAGE_SIZE = 30
    }

    // ── 分页查询 ──

    fun getAlbumFlow(type: MediaType? = null): Flow<PagingData<MediaWithTags>> {
        return Pager(PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)) {
            if (type != null) mediaDao.getAlbumPagingSourceByType(type)
            else mediaDao.getAlbumPagingSource()
        }.flow
    }

    fun getDeletedFlow(): Flow<PagingData<MediaWithTags>> {
        return Pager(PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)) {
            mediaDao.getDeletedPagingSource()
        }.flow
    }

    // ── 单条查询 ──

    fun getMediaWithTagsById(id: Long): Flow<MediaWithTags?> =
        mediaDao.getMediaWithTagsById(id)

    suspend fun getMediaById(id: Long): MediaEntity? =
        mediaDao.getByIdSuspend(id)

    // ── CRUD ──

    suspend fun insert(media: MediaEntity): Long {
        val id = mediaDao.insert(media)
        syncFts(id, media.name, media.description ?: "")
        return id
    }

    suspend fun update(media: MediaEntity) {
        mediaDao.update(media)
        syncFts(media.id, media.name, media.description ?: "")
    }

    suspend fun delete(media: MediaEntity) {
        mediaDao.delete(media)
        mediaFtsDao.delete(media.id)
    }

    suspend fun deleteById(id: Long) {
        mediaDao.deleteById(id)
        mediaFtsDao.delete(id)
    }

    // ── 回收站 ──

    suspend fun softDelete(id: Long) {
        mediaDao.softDelete(id, System.currentTimeMillis())
        mediaFtsDao.delete(id)  // 软删也从 FTS 中移除
    }

    suspend fun restore(id: Long) {
        mediaDao.restore(id)
        // 恢复时重建 FTS 条目
        val media = mediaDao.getByIdSuspend(id) ?: return
        syncFts(media.id, media.name, media.description ?: "")
    }

    // ── FTS 同步 ──

    private suspend fun syncFts(id: Long, name: String, description: String) {
        val tokenizedName = com.mememanager.util.JiebaTokenizer.toFtsContent(name)
        val tokenizedDesc = com.mememanager.util.JiebaTokenizer.toFtsContent(description)
        try {
            mediaFtsDao.insert(id, tokenizedName, tokenizedDesc)
        } catch (_: Exception) {
            mediaFtsDao.update(id, tokenizedName, tokenizedDesc)
        }
    }

    suspend fun purgeDeletedBefore(cutoffTime: Long): Int =
        mediaDao.purgeDeletedBefore(cutoffTime)

    fun getDeletedCount(): Flow<Int> = mediaDao.getDeletedCount()

    fun getActiveCount(): Flow<Int> = mediaDao.getActiveCount()

    // ── 标签关联 ──

    fun getTagsForMedia(mediaId: Long): Flow<List<TagEntity>> =
        mediaTagRefDao.getTagsForMedia(mediaId)

    suspend fun addTagToMedia(mediaId: Long, tagId: Long) {
        mediaTagRefDao.insert(MediaTagCrossRef(mediaId = mediaId, tagId = tagId))
    }

    suspend fun removeTagFromMedia(mediaId: Long, tagId: Long) {
        mediaTagRefDao.deleteByMediaIdAndTagId(mediaId, tagId)
    }

    suspend fun setTagsForMedia(mediaId: Long, tagIds: Set<Long>) {
        mediaTagRefDao.deleteByMediaId(mediaId)
        tagIds.forEach { tagId ->
            mediaTagRefDao.insert(MediaTagCrossRef(mediaId = mediaId, tagId = tagId))
        }
    }

    suspend fun getTagIdsForMedia(mediaId: Long): List<Long> =
        mediaTagRefDao.getTagIdsForMedia(mediaId)
}
