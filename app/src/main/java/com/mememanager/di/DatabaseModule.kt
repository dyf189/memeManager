package com.mememanager.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mememanager.data.local.AppDatabase
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import com.mememanager.data.local.dao.MediaDao
import com.mememanager.data.local.dao.MediaFtsDao
import com.mememanager.data.local.dao.MediaTagRefDao
import com.mememanager.data.local.dao.TagDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.dataStore

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "meme_manager.db"
        )
            .openHelperFactory(RequerySQLiteOpenHelperFactory())
            .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("""
                        CREATE VIRTUAL TABLE IF NOT EXISTS media_fts USING fts5(
                            name, description, tokenize='ascii'
                        )
                    """)
                    // 存量填充——由 Repository 层调用（不在这里）
                }
                override fun onOpen(db: SupportSQLiteDatabase) {
                    db.execSQL("""
                        CREATE VIRTUAL TABLE IF NOT EXISTS media_fts USING fts5(
                            name, description, tokenize='ascii'
                        )
                    """)
                }
            })
            .fallbackToDestructiveMigration(dropAllTables = true)
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

    @Provides
    fun provideMediaFtsDao(database: AppDatabase): MediaFtsDao {
        return database.mediaFtsDao()
    }
}
