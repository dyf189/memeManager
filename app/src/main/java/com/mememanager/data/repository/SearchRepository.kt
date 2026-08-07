package com.mememanager.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.sqlite.db.SimpleSQLiteQuery
import com.mememanager.data.local.dao.MediaDao
import com.mememanager.data.local.dao.MediaFtsDao
import com.mememanager.data.local.entity.MediaWithTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepository @Inject constructor(
    private val mediaFtsDao: MediaFtsDao,
    private val mediaDao: MediaDao,
    private val mediaRepository: MediaRepository
) {
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
        // Jieba 分词（含首次词典加载约 10 秒）必须在 IO 线程——
        // 此函数被 viewModelScope（Main）调用，直接同步分词会 ANR
        return flow {
            val ftsQuery = withContext(Dispatchers.IO) {
                // 首次加载（预热关闭场景）：加载后补全 FTS 索引——
                // 预热关闭期间导入的媒体跳过了 FTS 同步，这里一次性补上
                val firstLoad = com.mememanager.util.JiebaTokenizer.ensureLoaded()
                if (firstLoad) mediaRepository.seedAllFts()
                com.mememanager.util.JiebaTokenizer.toFtsQuery(query)
            }
            if (ftsQuery.isEmpty()) return@flow
            emitAll(
                Pager(
                    config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
                    pagingSourceFactory = {
                        mediaFtsDao.search(SimpleSQLiteQuery(FTS_SQL, arrayOf(ftsQuery)))
                    }
                ).flow
            )
        }
    }

    fun searchPlain(query: String): Flow<PagingData<MediaWithTags>> {
        if (query.isBlank()) return kotlinx.coroutines.flow.emptyFlow()
        return Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
            pagingSourceFactory = { mediaDao.getAlbumPagingSourceByLike("%$query%") }
        ).flow
    }
}
