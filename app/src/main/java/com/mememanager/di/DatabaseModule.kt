package com.mememanager.di

import android.content.Context
import androidx.room.Room
import com.mememanager.data.local.AppDatabase
import com.mememanager.data.local.dao.MediaDao
import com.mememanager.data.local.dao.MediaTagRefDao
import com.mememanager.data.local.dao.TagDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "meme_manager.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideMediaDao(database: AppDatabase): MediaDao {
        return database.mediaDao()
    }

    @Provides
    fun provideTagDao(database: AppDatabase): TagDao {
        return database.tagDao()
    }

    @Provides
    fun provideMediaTagRefDao(database: AppDatabase): MediaTagRefDao {
        return database.mediaTagRefDao()
    }
}
