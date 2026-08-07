package com.mememanager.ui.viewmodel

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mememanager.data.settings.AppSettings
import com.mememanager.data.settings.SettingsKeys
import com.mememanager.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = dataStore.data
        .map { prefs ->
            AppSettings(
                storageType = prefs[SettingsKeys.STORAGE_TYPE] ?: "私有内部",
                shardSizeMB = prefs[SettingsKeys.SHARD_SIZE_MB] ?: 100,
                themeMode = prefs[SettingsKeys.THEME_MODE] ?: "跟随系统",
                gridColumns = prefs[SettingsKeys.GRID_COLUMNS] ?: 3,
                trashDays = prefs[SettingsKeys.TRASH_DAYS] ?: 30,
                jsonSyncEnabled = prefs[SettingsKeys.JSON_SYNC_ENABLED] ?: false,
                gifAnimationEnabled = prefs[SettingsKeys.GIF_ANIMATION_ENABLED] ?: false,
                jiebaPreloadEnabled = prefs[SettingsKeys.JIEBA_PRELOAD_ENABLED] ?: true,
                exportDirUri = prefs[SettingsKeys.EXPORT_DIR_URI]
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val deletedCount: StateFlow<Int> = mediaRepository.getDeletedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun setStorageType(value: String) {
        viewModelScope.launch {
            dataStore.edit { it[SettingsKeys.STORAGE_TYPE] = value }
        }
    }

    fun setShardSizeMB(value: Int) {
        viewModelScope.launch {
            dataStore.edit { it[SettingsKeys.SHARD_SIZE_MB] = value }
        }
    }

    fun setThemeMode(value: String) {
        viewModelScope.launch {
            dataStore.edit { it[SettingsKeys.THEME_MODE] = value }
        }
    }

    fun setGridColumns(value: Int) {
        viewModelScope.launch {
            dataStore.edit { it[SettingsKeys.GRID_COLUMNS] = value }
        }
    }

    fun setTrashDays(value: Int) {
        viewModelScope.launch {
            dataStore.edit { it[SettingsKeys.TRASH_DAYS] = value }
        }
    }

    fun setJsonSyncEnabled(value: Boolean) {
        viewModelScope.launch {
            dataStore.edit { it[SettingsKeys.JSON_SYNC_ENABLED] = value }
        }
    }

    fun setGifAnimationEnabled(value: Boolean) {
        viewModelScope.launch {
            dataStore.edit { it[SettingsKeys.GIF_ANIMATION_ENABLED] = value }
        }
    }

    fun setJiebaPreloadEnabled(value: Boolean) {
        viewModelScope.launch {
            dataStore.edit { it[SettingsKeys.JIEBA_PRELOAD_ENABLED] = value }
        }
    }

    /** 设置导出目录（SAF tree URI，已持久授权） */
    fun setExportDirUri(uri: String) {
        viewModelScope.launch {
            dataStore.edit { it[SettingsKeys.EXPORT_DIR_URI] = uri }
        }
    }

    fun clearExportDirUri() {
        viewModelScope.launch {
            dataStore.edit { it.remove(SettingsKeys.EXPORT_DIR_URI) }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            mediaRepository.purgeDeletedBefore(System.currentTimeMillis())
        }
    }
}
