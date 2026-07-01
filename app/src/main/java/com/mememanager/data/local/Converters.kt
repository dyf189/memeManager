package com.mememanager.data.local

import androidx.room.TypeConverter
import com.mememanager.data.local.entity.MediaType
import com.mememanager.data.local.entity.StorageType

class Converters {

    @TypeConverter
    fun fromMediaType(value: MediaType): String = value.name

    @TypeConverter
    fun toMediaType(value: String): MediaType = MediaType.valueOf(value)

    @TypeConverter
    fun fromStorageType(value: StorageType): String = value.name

    @TypeConverter
    fun toStorageType(value: String): StorageType = StorageType.valueOf(value)
}
