package com.mememanager.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mememanager.data.local.entity.MediaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(media: MediaEntity): Long

    @Update
    suspend fun update(media: MediaEntity)

    @Delete
    suspend fun delete(media: MediaEntity)

    @Query("SELECT * FROM media WHERE id = :id")
    fun getById(id: Long): Flow<MediaEntity?>

    @Query("SELECT * FROM media ORDER BY createdAt DESC")
    fun getAll(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE type = :type ORDER BY createdAt DESC")
    fun getByType(type: String): Flow<List<MediaEntity>>

    @Query("DELETE FROM media WHERE id = :id")
    suspend fun deleteById(id: Long)
}
