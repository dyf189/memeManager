package com.mememanager

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.mememanager.data.repository.MediaRepository
import com.mememanager.data.settings.SettingsKeys
import com.mememanager.util.JiebaTokenizer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MemeManagerApp : Application() {

    @Inject lateinit var mediaRepository: MediaRepository
    @Inject lateinit var dataStore: DataStore<Preferences>

    override fun onCreate() {
        super.onCreate()
        // 后台预热 Jieba 词典，避免首次搜索卡顿
        CoroutineScope(Dispatchers.IO).launch {
            JiebaTokenizer.warmUp()
            // 回收站自动清理
            autoCleanTrash()
        }
    }

    private suspend fun autoCleanTrash() {
        val trashDays = dataStore.data.first()[SettingsKeys.TRASH_DAYS] ?: 30
        if (trashDays >= 90) return // 90 = 永不清理

        val lastClean = dataStore.data.first()[SettingsKeys.LAST_TRASH_CLEAN] ?: 0L
        val now = System.currentTimeMillis()
        if (now - lastClean < 86_400_000L) return // 距上次清理不足 1 天，跳过

        val cutoff = now - trashDays * 86_400_000L
        mediaRepository.purgeDeletedBefore(cutoff)
        dataStore.edit { it[SettingsKeys.LAST_TRASH_CLEAN] = now }
    }
}
