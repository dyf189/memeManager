package com.mememanager.ui.viewmodel

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mememanager.data.settings.AppSettings
import com.mememanager.data.settings.SettingsKeys
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    val settings: StateFlow<AppSettings> = dataStore.data
        .map { prefs ->
            AppSettings(
                storageType = prefs[SettingsKeys.STORAGE_TYPE] ?: "私有内部",
                shardSizeMB = prefs[SettingsKeys.SHARD_SIZE_MB] ?: 100,
                themeMode = prefs[SettingsKeys.THEME_MODE] ?: "跟随系统",
                gridColumns = prefs[SettingsKeys.GRID_COLUMNS] ?: 3,
                trashDays = prefs[SettingsKeys.TRASH_DAYS] ?: 30,
                jsonSyncEnabled = prefs[SettingsKeys.JSON_SYNC_ENABLED] ?: false
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

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
}
