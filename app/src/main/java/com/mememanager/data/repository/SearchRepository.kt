package com.mememanager.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.sqlite.db.SimpleSQLiteQuery
import com.mememanager.data.local.dao.MediaDao
import com.mememanager.data.local.dao.MediaFtsDao
import com.mememanager.data.local.entity.MediaWithTags
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepository @Inject constructor(
    private val mediaFtsDao: MediaFtsDao,
    private val mediaDao: MediaDao
) {
    companion object {
        private const val PAGE_SIZE = 30
        private const val FTS_SQL = """
            SELECT m.* FROM media m
            JOIN media_fts ON m.id = media_fts.rowid
            WHERE media_fts MATCH ? AND m.isDeleted = 0
            ORDER BY rank
        """
    }

    fun search(query: String): Flow<PagingData<MediaWithTags>> {
        val ftsQuery = com.mememanager.util.JiebaTokenizer.toFtsQuery(query)
        if (ftsQuery.isEmpty()) return kotlinx.coroutines.flow.emptyFlow()

        return Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
            pagingSourceFactory = {
                mediaFtsDao.search(SimpleSQLiteQuery(FTS_SQL, arrayOf(ftsQuery)))
            }
        ).flow
    }

    fun searchPlain(query: String): Flow<PagingData<MediaWithTags>> {
        if (query.isBlank()) return kotlinx.coroutines.flow.emptyFlow()
        return Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
            pagingSourceFactory = { mediaDao.getAlbumPagingSourceByLike("%$query%") }
        ).flow
    }
}
